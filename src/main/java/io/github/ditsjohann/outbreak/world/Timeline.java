package io.github.ditsjohann.outbreak.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.ditsjohann.outbreak.Outbreak;
import io.github.ditsjohann.outbreak.sandbox.Sandbox;
import io.github.ditsjohann.outbreak.sandbox.SandboxSettings;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The outbreak as it goes on: what day it is, and when the water and the power go. Each is cut on a
 * random day (and hour) in the range the sandbox settings give, picked when the world starts (and
 * picked again if an operator changes the setting). Once the power is out the city's lights die
 * ({@link PowerGrid}) and the fuel pumps stop; once the water is out the taps run dry.
 */
public final class Timeline extends SavedData {
	private static final Codec<Timeline> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.LONG.optionalFieldOf("water_off", -1L).forGetter(t -> t.waterOff),
		Codec.LONG.optionalFieldOf("power_off", -1L).forGetter(t -> t.powerOff),
		Codec.STRING.optionalFieldOf("water_rolled", "").forGetter(t -> t.waterRolled),
		Codec.STRING.optionalFieldOf("power_rolled", "").forGetter(t -> t.powerRolled),
		Codec.BOOL.optionalFieldOf("power_went_out", false).forGetter(t -> t.powerWentOut)
	).apply(i, Timeline::new));
	private static final SavedDataType<Timeline> TYPE = new SavedDataType<>(Outbreak.id("timeline"), Timeline::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
	public static final long DAY = 24000L;

	private static volatile boolean powerOn = true;
	private static volatile boolean waterOn = true;

	/** The clock tick the water goes off on (-1: never). */
	private long waterOff;
	private long powerOff;
	/** Which setting each was picked for. */
	private String waterRolled;
	private String powerRolled;
	/** Whether the lights have been put out already (it happens once). */
	private boolean powerWentOut;

	private Timeline() {
		this(-1L, -1L, "", "", false);
	}

	private Timeline(long waterOff, long powerOff, String waterRolled, String powerRolled, boolean powerWentOut) {
		this.waterOff = waterOff;
		this.powerOff = powerOff;
		this.waterRolled = waterRolled;
		this.powerRolled = powerRolled;
		this.powerWentOut = powerWentOut;
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			Timeline timeline = get(server);
			timeline.roll(server, Sandbox.current());
			timeline.update(server, false);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			powerOn = true;
			waterOn = true;
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 == 0) {
				Timeline timeline = get(server);
				timeline.roll(server, Sandbox.current());
				timeline.update(server, true);
			}
		});
		PowerGrid.register();
		FuelPumps.register();
		Taps.register();
		Broadcasts.register();
		OutbreakSounds.register();
		TimelineCommand.register();
	}

	public static Timeline get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	public static boolean isPowerOn() {
		return powerOn;
	}

	public static boolean isWaterOn() {
		return waterOn;
	}

	/** Ticks since the outbreak began (07:00 on day 1 is 1000). */
	public static long time(MinecraftServer server) {
		return server.overworld().getOverworldClockTime();
	}

	/** Days since the outbreak began: 0 on the first day. */
	public static int day(MinecraftServer server) {
		return (int) (time(server) / DAY);
	}

	/** The hour of the day, 0-23. */
	public static int hour(MinecraftServer server) {
		return (int) ((time(server) / 1000L + 6) % 24);
	}

	/** Picks the day for anything whose setting has changed (or that hasn't got one yet). */
	private void roll(MinecraftServer server, SandboxSettings settings) {
		RandomSource random = server.overworld().getRandom();
		if (!this.waterRolled.equals(settings.water().getSerializedName())) {
			this.waterOff = pick(settings.water(), random);
			this.waterRolled = settings.water().getSerializedName();
			this.setDirty();
			Outbreak.LOGGER.info("The water goes off {}", describe(this.waterOff));
		}

		if (!this.powerRolled.equals(settings.power().getSerializedName())) {
			this.powerOff = pick(settings.power(), random);
			this.powerRolled = settings.power().getSerializedName();
			this.setDirty();
			Outbreak.LOGGER.info("The power goes off {}", describe(this.powerOff));
		}
	}

	private static long pick(SandboxSettings.Utilities when, RandomSource random) {
		if (when.first < 0) {
			return -1L;
		}

		if (when.last <= 0) {
			return 0L;
		}

		int day = when.first + random.nextInt(when.last - when.first + 1);
		return day * DAY + random.nextInt((int) DAY);
	}

	private static String describe(long tick) {
		return tick < 0 ? "never" : "on day " + (tick / DAY + 1);
	}

	private void update(MinecraftServer server, boolean announce) {
		long now = time(server);
		waterOn = this.waterOff < 0 || now < this.waterOff;
		boolean power = this.powerOff < 0 || now < this.powerOff;
		powerOn = power;
		if (!power && !this.powerWentOut) {
			this.powerWentOut = true;
			this.setDirty();
			PowerGrid.goDark(server);
			if (announce) {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					player.level().playSound(null, player.getX(), player.getY(), player.getZ(), OutbreakSounds.POWER_OUT, SoundSource.AMBIENT, 0.8F, 1.0F);
					player.sendSystemMessage(Component.translatable("outbreak.timeline.power_out"));
				}
			}
		}
	}

	/** An operator's override: the water (or power) goes off now, or comes back for good. */
	public void set(MinecraftServer server, boolean water, boolean on) {
		long value = on ? -1L : time(server);
		if (water) {
			this.waterOff = value;
		} else {
			this.powerOff = value;
			if (on) {
				this.powerWentOut = false;
			}
		}

		this.setDirty();
		this.update(server, true);
	}

	public long waterOff() {
		return this.waterOff;
	}

	public long powerOff() {
		return this.powerOff;
	}
}
