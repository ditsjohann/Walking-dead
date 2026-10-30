package io.github.ditsjohann.outbreak.world;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.ditsjohann.outbreak.Outbreak;
import io.github.ditsjohann.outbreak.character.StartPoint;
import io.github.ditsjohann.outbreak.entity.OutbreakEntities;
import io.github.ditsjohann.outbreak.entity.Walker;
import io.github.ditsjohann.outbreak.noise.NoiseSystem;
import io.github.ditsjohann.outbreak.sandbox.Sandbox;
import io.github.ditsjohann.outbreak.sandbox.SandboxSettings;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Things that happen in the world, out of your hands (Project Zomboid's meta events):
 * <ul>
 * <li>the helicopter: once, on a day in the first two weeks, it comes for one of you;</li>
 * <li>gunfights in the distance: a burst of shots somewhere far off, which every walker near it goes
 * to look at (often in the first days, rarely later);</li>
 * <li>flares over the Euroborg on the nights the evacuation centre still holds out;</li>
 * <li>herds on the move: every couple of minutes some herds near you set off across the city, all
 * drifting the same way (which changes each day).</li>
 * </ul>
 */
public final class MetaEvents extends SavedData {
	private static final Codec<MetaEvents> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.LONG.optionalFieldOf("helicopter", -1L).forGetter(e -> e.helicopter),
		Codec.BOOL.optionalFieldOf("helicopter_done", false).forGetter(e -> e.helicopterDone)
	).apply(i, MetaEvents::new));
	private static final SavedDataType<MetaEvents> TYPE = new SavedDataType<>(Outbreak.id("meta_events"), MetaEvents::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	public static final SimpleParticleType FLARE = Registry.register(BuiltInRegistries.PARTICLE_TYPE, Outbreak.id("flare"), FabricParticleTypes.simple(true));

	/** Nights (day since the outbreak, hour) a flare goes up over the Euroborg. */
	private static final int[][] FLARES = {{2, 21}, {2, 23}, {3, 21}, {3, 22}};
	private static final int MIGRATION_TICKS = 20 * 120;
	private static final double MIGRATION_REACH = 96.0;
	private static final long MIGRATION_LENGTH = 20 * 60 * 5;

	/** A shot still to be fired, in a burst. */
	private record Shot(ServerLevel level, Vec3 pos, long at) {
	}

	private static final List<Shot> SHOTS = new ArrayList<>();
	private static long lastHour = -1;

	/** The clock tick the helicopter comes (-1: not picked yet). */
	private long helicopter;
	private boolean helicopterDone;

	private MetaEvents() {
		this(-1L, false);
	}

	private MetaEvents(long helicopter, boolean helicopterDone) {
		this.helicopter = helicopter;
		this.helicopterDone = helicopterDone;
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(FlarePayload.TYPE, FlarePayload.CODEC);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			MetaEvents events = server.getDataStorage().computeIfAbsent(TYPE);
			if (events.helicopter < 0) {
				RandomSource random = server.overworld().getRandom();
				events.helicopter = (4 + random.nextInt(6)) * Timeline.DAY + 3000 + random.nextInt(9000);
				events.setDirty();
				Outbreak.LOGGER.info("The helicopter comes on day {}", events.helicopter / Timeline.DAY + 1);
			}

			lastHour = Timeline.time(server) / 1000L;
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> SHOTS.clear());
		ServerTickEvents.END_SERVER_TICK.register(MetaEvents::tick);
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> command(dispatcher));
	}

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		for (Iterator<Shot> it = SHOTS.iterator(); it.hasNext();) {
			Shot shot = it.next();
			if (now >= shot.at) {
				it.remove();
				fire(shot.level, shot.pos);
			}
		}

		if (server.getTickCount() % MIGRATION_TICKS == 0) {
			migrate(server);
		}

		long hour = Timeline.time(server) / 1000L;
		if (hour == lastHour) {
			return;
		}

		lastHour = hour;
		List<ServerPlayer> players = server.overworld().players();
		if (players.isEmpty()) {
			return;
		}

		RandomSource random = server.overworld().getRandom();
		int day = Timeline.day(server);
		int clock = Timeline.hour(server);
		if (random.nextFloat() < gunfightChance(day)) {
			gunfight(players.get(random.nextInt(players.size())));
		}

		for (int[] flare : FLARES) {
			if (flare[0] == day && flare[1] == clock) {
				flare(server);
			}
		}

		MetaEvents events = server.getDataStorage().computeIfAbsent(TYPE);
		if (!events.helicopterDone && events.helicopter >= 0 && Timeline.time(server) >= events.helicopter) {
			events.helicopterDone = true;
			events.setDirty();
			Helicopter.send(players.get(random.nextInt(players.size())), OutbreakEntities.HELICOPTER);
		}
	}

	/** Chance of a gunfight somewhere each hour: people still fight in the first days, then they're gone. */
	private static float gunfightChance(int day) {
		return day < 3 ? 0.25F : day < 7 ? 0.15F : day < 14 ? 0.06F : day < 60 ? 0.02F : 0.0F;
	}

	/** A burst of shots far off from this player. */
	public static void gunfight(ServerPlayer player) {
		ServerLevel level = player.level();
		RandomSource random = level.getRandom();
		double angle = random.nextDouble() * Math.PI * 2.0;
		double distance = 80.0 + random.nextDouble() * 80.0;
		Vec3 at = player.position().add(Math.cos(angle) * distance, 0.0, Math.sin(angle) * distance);
		long now = level.getGameTime();
		int shots = 3 + random.nextInt(6);
		long when = now;
		for (int i = 0; i < shots; i++) {
			SHOTS.add(new Shot(level, at.add(random.nextGaussian() * 6.0, 0.0, random.nextGaussian() * 6.0), when));
			when += 4 + random.nextInt(random.nextFloat() < 0.3F ? 40 : 12);
		}
	}

	private static void fire(ServerLevel level, Vec3 pos) {
		BlockPos at = BlockPos.containing(pos);
		double y = level.hasChunkAt(at) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING, at.getX(), at.getZ()) + 1.0 : pos.y;
		Vec3 shot = new Vec3(pos.x, y, pos.z);
		level.playSound(null, shot.x, shot.y, shot.z, OutbreakSounds.GUNSHOT_DISTANT, SoundSource.AMBIENT, 1.0F, 0.85F + level.getRandom().nextFloat() * 0.3F);
		NoiseSystem.emit(level, shot, 56.0F);
	}

	/** A red flare over the Euroborg, seen by everyone. */
	public static void flare(MinecraftServer server) {
		Vec3 at = euroborg(server);
		for (ServerPlayer player : server.overworld().players()) {
			ServerPlayNetworking.send(player, new FlarePayload(at.x, at.z));
		}
	}

	/** Where the Euroborg is on the map (anywhere else: a long way east of spawn). */
	private static Vec3 euroborg(MinecraftServer server) {
		SandboxSettings settings = Sandbox.current();
		double[] bbox = StartPoint.parseBbox(settings.bbox());
		if (settings.map() == SandboxSettings.MapKind.GRONINGEN && bbox != null) {
			BlockPos pos = StartPoint.project(bbox, StartPoint.EUROBORG.lat, StartPoint.EUROBORG.lng);
			return Vec3.atCenterOf(pos);
		}

		BlockPos spawn = server.overworld().getRespawnData().globalPos().pos();
		return Vec3.atCenterOf(spawn).add(400.0, 0.0, 250.0);
	}

	/** Some herds near each player set off across the city, all drifting the way of the day. */
	public static int migrate(MinecraftServer server) {
		ServerLevel level = server.overworld();
		RandomSource random = level.getRandom();
		int day = Timeline.day(server);
		// The way the dead drift today, turning a little through the day.
		double drift = day * 2.4 + Math.sin(Timeline.hour(server) / 24.0 * Math.PI * 2.0) * 0.6;
		int moved = 0;
		for (ServerPlayer player : level.players()) {
			AABB area = player.getBoundingBox().inflate(MIGRATION_REACH, 24.0, MIGRATION_REACH);
			for (Walker walker : level.getEntitiesOfClass(Walker.class, area, w -> w.isAlive() && w.getLeader() == null
				&& w.getMigration() == null && w.getTarget() == null && w.distanceToSqr(player) > 24.0 * 24.0)) {
				if (random.nextFloat() > 0.6F) {
					continue;
				}

				double distance = 48.0 + random.nextDouble() * 48.0;
				double angle = drift + random.nextGaussian() * 0.35;
				BlockPos to = BlockPos.containing(walker.getX() + Math.cos(angle) * distance, walker.getY(), walker.getZ() + Math.sin(angle) * distance);
				walker.migrate(to, MIGRATION_LENGTH);
				moved++;
			}
		}

		return moved;
	}

	private static void command(CommandDispatcher<CommandSourceStack> dispatcher) {
		var event = Commands.literal("event")
			.then(Commands.literal("helicopter").executes(ctx -> {
				Helicopter.send(ctx.getSource().getPlayerOrException(), OutbreakEntities.HELICOPTER);
				return 1;
			}))
			.then(Commands.literal("gunfight").executes(ctx -> {
				gunfight(ctx.getSource().getPlayerOrException());
				return 1;
			}))
			.then(Commands.literal("flare").executes(ctx -> {
				flare(ctx.getSource().getServer());
				Vec3 at = euroborg(ctx.getSource().getServer());
				ctx.getSource().sendSuccess(() -> Component.literal("A flare over the Euroborg at " + (int) at.x + ", " + (int) at.z), false);
				return 1;
			}))
			.then(Commands.literal("migrate").executes(ctx -> {
				int moved = migrate(ctx.getSource().getServer());
				ctx.getSource().sendSuccess(() -> Component.literal(moved + " herds set off"), false);
				return moved;
			}));
		dispatcher.register(Commands.literal("outbreak")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(event));
	}

	/** To a client: a flare goes up at (x, z), a long way off. */
	public record FlarePayload(double x, double z) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<FlarePayload> TYPE = new CustomPacketPayload.Type<>(Outbreak.id("flare"));
		public static final StreamCodec<RegistryFriendlyByteBuf, FlarePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.DOUBLE, FlarePayload::x,
			ByteBufCodecs.DOUBLE, FlarePayload::z,
			FlarePayload::new);

		@Override
		public CustomPacketPayload.Type<FlarePayload> type() {
			return TYPE;
		}
	}
}
