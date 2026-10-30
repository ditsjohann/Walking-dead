package io.github.ditsjohann.outbreak.combat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import io.github.ditsjohann.outbreak.Outbreak;
import io.github.ditsjohann.outbreak.entity.Walker;
import io.github.ditsjohann.outbreak.gore.Blood;
import io.github.ditsjohann.outbreak.gore.Limb;
import io.github.ditsjohann.outbreak.noise.NoiseSystem;
import io.github.ditsjohann.outbreak.world.OutbreakSounds;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Shooting, on the server: the client says "fire" (and whether you're aiming) or "reload"; the
 * server checks the gun, spends the round and traces each bullet from your eye, with the gun's
 * spread. A bullet hits the first thing in its way: a walker (the same rules as any hit, so aim for
 * the head), or a block (glass shatters). Every shot is heard a long way off.
 */
public final class Guns {
	/** The next game tick each player may fire again. */
	private static final Map<UUID, Long> READY = new HashMap<>();
	/** Reloads under way: when the next round (or the magazine) goes in. */
	private static final Map<UUID, Long> RELOADING = new HashMap<>();

	private Guns() {
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(GunPayload.TYPE, GunPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(GunPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			switch (payload.action()) {
				case GunPayload.FIRE -> fire(player, false);
				case GunPayload.FIRE_AIMED -> fire(player, true);
				case GunPayload.RELOAD -> startReload(player);
				default -> {
				}
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				Long at = RELOADING.get(player.getUUID());
				if (at != null && player.level().getGameTime() >= at) {
					finishReload(player);
				}
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			READY.remove(handler.player.getUUID());
			RELOADING.remove(handler.player.getUUID());
		});
		// For testing (fake players can't click): /outbreak gun fire [aimed], /outbreak gun reload.
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(Commands.literal("outbreak")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("gun")
				.then(Commands.literal("fire")
					.executes(ctx -> {
						fire(ctx.getSource().getPlayerOrException(), false);
						return 1;
					})
					.then(Commands.literal("aimed").executes(ctx -> {
						fire(ctx.getSource().getPlayerOrException(), true);
						return 1;
					})))
				.then(Commands.literal("reload").executes(ctx -> {
					startReload(ctx.getSource().getPlayerOrException());
					return 1;
				})))));
	}

	private static ItemStack gun(ServerPlayer player) {
		ItemStack held = player.getMainHandItem();
		return held.getItem() instanceof GunItem ? held : ItemStack.EMPTY;
	}

	public static void fire(ServerPlayer player, boolean aiming) {
		ItemStack stack = gun(player);
		if (stack.isEmpty() || player.isSpectator()) {
			return;
		}

		GunType type = ((GunItem) stack.getItem()).type;
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (now < READY.getOrDefault(player.getUUID(), 0L)) {
			return;
		}

		int rounds = GunItem.rounds(stack);
		if (rounds <= 0) {
			READY.put(player.getUUID(), now + 8);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), OutbreakSounds.GUN_DRY, SoundSource.PLAYERS, 0.8F, 1.0F);
			player.sendOverlayMessage(Component.translatable("outbreak.gun.empty"));
			return;
		}

		// Firing stops a shotgun being loaded.
		RELOADING.remove(player.getUUID());
		READY.put(player.getUUID(), now + type.cooldown);
		GunItem.setRounds(stack, rounds - 1);
		stack.hurtAndBreak(1, player, InteractionHand.MAIN_HAND.asEquipmentSlot());

		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		float spread = aiming ? type.aimedSpread : type.hipSpread;
		if (player.isSprinting()) {
			spread *= 2.0F;
		}

		for (int i = 0; i < type.pellets; i++) {
			shoot(level, player, type, eye, scatter(look, spread, level.getRandom()), look);
		}

		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), type.sound(), SoundSource.PLAYERS, 4.0F, 0.95F + level.getRandom().nextFloat() * 0.1F);
		if (type == GunType.SHOTGUN || type == GunType.RIFLE) {
			// Racking the pump, working the bolt.
			level.playSound(null, player.getX(), player.getEyeY(), player.getZ(),
				type == GunType.SHOTGUN ? OutbreakSounds.SHOTGUN_PUMP : OutbreakSounds.GUN_RELOAD, SoundSource.PLAYERS, 0.7F, 1.2F);
		}

		Vec3 muzzle = eye.add(look.scale(0.9)).add(0.0, -0.15, 0.0);
		level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 4, 0.03, 0.03, 0.03, 0.01);
		NoiseSystem.emit(level, player.position(), type.noise, player);
	}

	/** A direction within spread degrees of dir. */
	private static Vec3 scatter(Vec3 dir, float spread, RandomSource random) {
		if (spread <= 0.0F) {
			return dir;
		}

		double radians = Math.toRadians(spread) * Math.sqrt(random.nextDouble());
		double around = random.nextDouble() * Math.PI * 2.0;
		Vec3 side = Math.abs(dir.y) > 0.9 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
		Vec3 a = dir.cross(side).normalize();
		Vec3 b = dir.cross(a).normalize();
		Vec3 offset = a.scale(Math.cos(around)).add(b.scale(Math.sin(around))).scale(Math.tan(radians));
		return dir.add(offset).normalize();
	}

	private static void shoot(ServerLevel level, ServerPlayer player, GunType type, Vec3 eye, Vec3 dir, Vec3 look) {
		Vec3 end = eye.add(dir.scale(type.range));
		BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (block.getType() != HitResult.Type.MISS) {
			end = block.getLocation();
		}

		EntityHitResult entity = ProjectileUtil.getEntityHitResult(player, eye, end, new AABB(eye, end).inflate(1.0),
			e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e != player.getVehicle(), eye.distanceToSqr(end));
		if (entity != null) {
			hit(level, player, type, entity.getEntity(), entity.getLocation(), dir);
		} else if (block.getType() != HitResult.Type.MISS) {
			impact(level, block, dir);
		}
	}

	private static void hit(ServerLevel level, ServerPlayer player, GunType type, Entity target, Vec3 at, Vec3 dir) {
		boolean head = target instanceof Walker walker && at.y >= walker.getY() + walker.getBbHeight() * Walker.HEAD_START;
		target.hurtServer(level, level.damageSources().playerAttack(player), type.damage);
		Blood.spray(level, at, dir, head ? 14 : 8, 0.35F);
		if (target instanceof Walker walker && walker.isDeadOrDying() && head && !walker.hasLost(Limb.HEAD)) {
			// A shotgun or a rifle round to the head can take it off.
			float chance = type == GunType.SHOTGUN ? 0.5F : type == GunType.RIFLE ? 0.3F : 0.0F;
			if (level.getRandom().nextFloat() < chance) {
				walker.sever(level, Limb.HEAD, dir.scale(0.5).add(0.0, 0.3, 0.0));
			}
		}
	}

	private static void impact(ServerLevel level, BlockHitResult hit, Vec3 dir) {
		BlockPos pos = hit.getBlockPos();
		BlockState state = level.getBlockState(pos);
		Vec3 at = hit.getLocation();
		if (BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().contains("glass")) {
			level.destroyBlock(pos, false);
			NoiseSystem.emit(level, at, 16.0F);
			return;
		}

		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y, at.z, 6, 0.05, 0.05, 0.05, 0.1);
		level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 2, 0.02, 0.02, 0.02, 0.01);
		level.playSound(null, pos, state.getSoundType().getHitSound(), SoundSource.BLOCKS, 0.6F, 1.4F);
	}

	private static void startReload(ServerPlayer player) {
		ItemStack stack = gun(player);
		if (stack.isEmpty() || RELOADING.containsKey(player.getUUID())) {
			return;
		}

		GunType type = ((GunItem) stack.getItem()).type;
		if (GunItem.rounds(stack) >= type.capacity) {
			return;
		}

		if (ammo(player, type) == 0) {
			player.sendOverlayMessage(Component.translatable("outbreak.gun.no_ammo", type.ammo().getDefaultInstance().getHoverName()));
			return;
		}

		RELOADING.put(player.getUUID(), player.level().getGameTime() + type.reloadTicks);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), OutbreakSounds.GUN_RELOAD, SoundSource.PLAYERS, 0.8F, 0.9F);
	}

	private static void finishReload(ServerPlayer player) {
		RELOADING.remove(player.getUUID());
		ItemStack stack = gun(player);
		if (stack.isEmpty()) {
			return;
		}

		GunType type = ((GunItem) stack.getItem()).type;
		int rounds = GunItem.rounds(stack);
		int wanted = type.oneAtATime ? 1 : type.capacity - rounds;
		int taken = take(player, type, Math.min(wanted, type.capacity - rounds));
		if (taken == 0) {
			return;
		}

		GunItem.setRounds(stack, rounds + taken);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
			type.oneAtATime ? SoundEvents.ARMOR_EQUIP_CHAIN.value() : OutbreakSounds.GUN_RELOAD.value(), SoundSource.PLAYERS, 0.8F, 1.3F);
		// The shotgun: keep feeding shells in until it's full or you run out (or shoot).
		if (type.oneAtATime && rounds + taken < type.capacity && ammo(player, type) > 0) {
			RELOADING.put(player.getUUID(), player.level().getGameTime() + type.reloadTicks);
		}
	}

	private static int ammo(ServerPlayer player, GunType type) {
		int count = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(type.ammo())) {
				count += stack.getCount();
			}
		}

		return count;
	}

	private static int take(ServerPlayer player, GunType type, int wanted) {
		int taken = 0;
		for (int i = 0; i < player.getInventory().getContainerSize() && taken < wanted; i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(type.ammo())) {
				int n = Math.min(stack.getCount(), wanted - taken);
				stack.shrink(n);
				taken += n;
			}
		}

		return taken;
	}

	/** Whether this player is in the middle of reloading (for the HUD). */
	public static boolean isReloading(ServerPlayer player) {
		return RELOADING.containsKey(player.getUUID());
	}

	/** From the client: fire (aimed or from the hip), or reload. */
	public record GunPayload(int action) implements CustomPacketPayload {
		public static final int FIRE = 0;
		public static final int FIRE_AIMED = 1;
		public static final int RELOAD = 2;
		public static final CustomPacketPayload.Type<GunPayload> TYPE = new CustomPacketPayload.Type<>(Outbreak.id("gun"));
		public static final StreamCodec<RegistryFriendlyByteBuf, GunPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, GunPayload::action, GunPayload::new);

		@Override
		public CustomPacketPayload.Type<GunPayload> type() {
			return TYPE;
		}
	}

	/** Recoil for the shooter's own view, worked out the same way on the client. */
	public static float recoil(GunType type, boolean aiming) {
		return aiming ? type.recoil * 0.6F : type.recoil;
	}
}
