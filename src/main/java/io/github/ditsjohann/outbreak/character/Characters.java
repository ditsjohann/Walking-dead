package io.github.ditsjohann.outbreak.character;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import io.github.ditsjohann.outbreak.Outbreak;
import io.github.ditsjohann.outbreak.entity.Walker;
import io.github.ditsjohann.outbreak.loot.ContainerLoot;
import io.github.ditsjohann.outbreak.sandbox.Sandbox;
import io.github.ditsjohann.outbreak.survivor.StarterKit;
import io.github.ditsjohann.outbreak.survivor.SurvivorState;
import io.github.ditsjohann.outbreak.survivor.Survivors;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Each player's survivor: making one (the character screen), skills levelling up, and what happens
 * to it on death. With permadeath (the default) a dead survivor is gone, and the player makes a new
 * one when they respawn; without it, they come back as the same person, skills and all.
 */
public final class Characters {
	public static final AttachmentType<CharacterData> DATA = AttachmentRegistry.create(Outbreak.id("character"), builder -> builder
		.initializer(CharacterData::new)
		.persistent(CharacterData.CODEC)
		.syncWith(CharacterData.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

	private Characters() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(OpenCreationPayload.TYPE, OpenCreationPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(CreatePayload.TYPE, CreatePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(CreatePayload.TYPE, (payload, context) -> create(context.player(), payload));

		ServerPlayerEvents.JOIN.register(player -> {
			if (!get(player).created) {
				needCharacter(player, null);
			}
		});
		ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
			CharacterData old = get(oldPlayer);
			if (alive || !Sandbox.current().permadeath()) {
				newPlayer.setAttached(DATA, old.copy());
			} else if (old.created) {
				// Permadeath: this survivor is gone. Remember them for the "new survivor" screen.
				newPlayer.setAttached(LAST_LIFE, new LastLife(old.name, daysSurvived(oldPlayer, old), old.kills));
			}
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (!get(newPlayer).created) {
				needCharacter(newPlayer, newPlayer.getAttached(LAST_LIFE));
			}
		});
		// Until they've made their survivor, nothing can hurt them (and walkers ignore them).
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
			!(entity instanceof ServerPlayer player) || get(player).created || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof Walker && source.getEntity() instanceof ServerPlayer player && get(player).created) {
				get(player).kills++;
				addXp(player, Skill.FIGHTING, 5.0F);
			}
		});

		ContainerLoot.lootLuck = Perks::lootLuck;
		CharacterCommand.register();
		SkillPractice.register();
	}

	/** The previous survivor's name, days and kills, kept on a player between death and the next character. */
	private static final AttachmentType<LastLife> LAST_LIFE = AttachmentRegistry.create(Outbreak.id("last_life"));

	public record LastLife(String name, int days, int kills) {
	}

	public static CharacterData get(Player player) {
		return player.getAttachedOrCreate(DATA);
	}

	public static boolean hasCharacter(Player player) {
		return get(player).created;
	}

	public static void sync(Player player) {
		player.setAttached(DATA, get(player).copy());
	}

	public static int daysSurvived(Player player, CharacterData data) {
		return (int) Math.max(0, (player.level().getOverworldClockTime() - data.born) / 24000L);
	}

	/**
	 * A player without a survivor: test players get a plain one straight away; everyone else gets
	 * the character screen.
	 */
	private static void needCharacter(ServerPlayer player, LastLife lastLife) {
		if (isTestPlayer(player)) {
			player.setAttached(DATA, CharacterData.create(player.getName().getString(), Profession.UNEMPLOYED, Set.of(), player.level().getOverworldClockTime()));
			StarterKit.giveTestKit(player);
			return;
		}

		openCreation(player, lastLife);
	}

	/** Show this player the character screen (also used by {@code /outbreak character reset}). */
	public static void openCreation(ServerPlayer player, LastLife lastLife) {
		LastLife last = lastLife != null ? lastLife : new LastLife("", 0, 0);
		// The start points on offer depend on the world's rules: make sure the client has them first.
		ServerPlayNetworking.send(player, new Sandbox.SyncPayload(Sandbox.current()));
		ServerPlayNetworking.send(player, new OpenCreationPayload(last.name(), last.days(), last.kills()));
	}

	/** Carpet's fake players (server test scenarios) and the client game tests can't fill in a form. */
	private static boolean isTestPlayer(ServerPlayer player) {
		return player.getClass().getName().endsWith("EntityPlayerMPFake") || System.getProperty("fabric.client.gametest") != null;
	}

	private static void create(ServerPlayer player, CreatePayload payload) {
		Set<Trait> traits = payload.traits().isEmpty() ? EnumSet.noneOf(Trait.class) : EnumSet.copyOf(payload.traits());
		String name = payload.name().strip();
		if (get(player).created || !CharacterData.isValid(name, payload.profession(), traits)) {
			return;
		}

		start(player, CharacterData.create(name, payload.profession(), traits, player.level().getOverworldClockTime()), payload.start());
	}

	/** Bring a new survivor into the world: fresh body, their things, at their start point. */
	public static void start(ServerPlayer player, CharacterData data, StartPoint start) {
		player.setAttached(DATA, data);
		player.removeAttached(LAST_LIFE);
		player.setAttached(Survivors.STATE, new SurvivorState());
		player.setHealth(player.getMaxHealth());
		player.getFoodData().setFoodLevel(20);

		for (ItemStack stack : Profession.basics()) {
			give(player, stack);
		}

		for (ItemStack stack : data.profession.kit()) {
			give(player, stack);
		}

		if (start != null) {
			ServerLevel level = player.level().getServer().overworld();
			BlockPos pos = start.resolve(level, Sandbox.current(), player.getRandom());
			player.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, Set.of(), player.getYRot(), 0.0F, true);
		}

		long day = player.level().getOverworldClockTime() / 24000L + 1;
		player.sendSystemMessage(Component.translatable("outbreak.character.welcome", data.name,
			Component.translatable(data.profession.translationKey()), day).withStyle(ChatFormatting.GRAY));
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack.copy())) {
			player.spawnAtLocation(player.level(), stack.copy());
		}
	}

	/** Practice: xp towards a skill (more or less for fast or slow learners). Tells the player when they level up. */
	public static void addXp(ServerPlayer player, Skill skill, float amount) {
		CharacterData data = get(player);
		if (!data.created || player.isCreative() || player.isSpectator()) {
			return;
		}

		int before = data.level(skill);
		data.xp.put(skill, Math.min(Skill.xpFor(Skill.MAX_LEVEL), data.xp(skill) + amount * Perks.xpMultiplier(data)));
		int after = data.level(skill);
		if (after > before) {
			player.sendOverlayMessage(Component.translatable("outbreak.skill.level_up", Component.translatable(skill.translationKey()), after).withStyle(ChatFormatting.GOLD));
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.35F, 1.4F);
		}

		sync(player);
	}

	/** Server to client: make your survivor (after your last one, if there was one). */
	public record OpenCreationPayload(String lastName, int lastDays, int lastKills) implements CustomPacketPayload {
		public static final Type<OpenCreationPayload> TYPE = new Type<>(Outbreak.id("character_open"));
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenCreationPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, OpenCreationPayload::lastName,
			ByteBufCodecs.VAR_INT, OpenCreationPayload::lastDays,
			ByteBufCodecs.VAR_INT, OpenCreationPayload::lastKills,
			OpenCreationPayload::new);

		@Override
		public Type<OpenCreationPayload> type() {
			return TYPE;
		}
	}

	/** Client to server: the survivor they made. Checked again on the server. */
	public record CreatePayload(String name, Profession profession, List<Trait> traits, StartPoint start) implements CustomPacketPayload {
		public static final Type<CreatePayload> TYPE = new Type<>(Outbreak.id("character_create"));
		public static final StreamCodec<RegistryFriendlyByteBuf, CreatePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(64), CreatePayload::name,
			ByteBufCodecs.fromCodec(Profession.CODEC), CreatePayload::profession,
			ByteBufCodecs.fromCodec(Trait.CODEC).apply(ByteBufCodecs.list(32)), CreatePayload::traits,
			ByteBufCodecs.idMapper(i -> StartPoint.values()[Math.floorMod(i, StartPoint.values().length)], StartPoint::ordinal), CreatePayload::start,
			CreatePayload::new);

		@Override
		public Type<CreatePayload> type() {
			return TYPE;
		}
	}
}
