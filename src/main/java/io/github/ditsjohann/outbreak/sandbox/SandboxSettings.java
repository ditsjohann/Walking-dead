package io.github.ditsjohann.outbreak.sandbox;

import java.util.Locale;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.ditsjohann.outbreak.survivor.body.WoundType;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * A world's outbreak rules (PZ's sandbox settings), picked when the world is first played: the
 * scenario (start day), where survivors start, and how hard the dead are. Most of them can be changed
 * later by an operator.
 */
public record SandboxSettings(
	Preset preset,
	Scenario scenario,
	MapKind map,
	String bbox,
	WalkerSpeed walkerSpeed,
	Population population,
	HerdSize herdSize,
	Infection infection,
	Loot loot,
	boolean permadeath,
	boolean vanillaMonsters
) {
	/** Arnis's bounding box for the city of Groningen (see docs/MAP_GRONINGEN.md). */
	public static final String GRONINGEN_BBOX = "53.17,6.48,53.26,6.65";

	public static final SandboxSettings DEFAULT = Preset.APOCALYPSE.apply(Scenario.FIRST_DAY, MapKind.GRONINGEN, GRONINGEN_BBOX);

	public static final Codec<SandboxSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
		Preset.CODEC.optionalFieldOf("preset", Preset.APOCALYPSE).forGetter(SandboxSettings::preset),
		Scenario.CODEC.optionalFieldOf("scenario", Scenario.FIRST_DAY).forGetter(SandboxSettings::scenario),
		MapKind.CODEC.optionalFieldOf("map", MapKind.GRONINGEN).forGetter(SandboxSettings::map),
		Codec.STRING.optionalFieldOf("bbox", GRONINGEN_BBOX).forGetter(SandboxSettings::bbox),
		WalkerSpeed.CODEC.optionalFieldOf("walker_speed", WalkerSpeed.SHAMBLERS).forGetter(SandboxSettings::walkerSpeed),
		Population.CODEC.optionalFieldOf("population", Population.NORMAL).forGetter(SandboxSettings::population),
		HerdSize.CODEC.optionalFieldOf("herd_size", HerdSize.NORMAL).forGetter(SandboxSettings::herdSize),
		Infection.CODEC.optionalFieldOf("infection", Infection.NORMAL).forGetter(SandboxSettings::infection),
		Loot.CODEC.optionalFieldOf("loot", Loot.NORMAL).forGetter(SandboxSettings::loot),
		Codec.BOOL.optionalFieldOf("permadeath", true).forGetter(SandboxSettings::permadeath),
		Codec.BOOL.optionalFieldOf("vanilla_monsters", false).forGetter(SandboxSettings::vanillaMonsters)
	).apply(i, SandboxSettings::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, SandboxSettings> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

	/** Loot found in containers, all things considered (the scenario too). */
	public float lootMultiplier() {
		return this.loot.multiplier * this.scenario.loot;
	}

	/** How many walkers roam, all things considered (the scenario too). */
	public float populationMultiplier() {
		return this.population.multiplier * this.scenario.population;
	}

	/** These rules under a preset; picking "custom" keeps them as they are. */
	public SandboxSettings withPreset(Preset preset) {
		if (preset == Preset.CUSTOM) {
			return new SandboxSettings(Preset.CUSTOM, this.scenario, this.map, this.bbox, this.walkerSpeed, this.population, this.herdSize, this.infection, this.loot, this.permadeath, this.vanillaMonsters);
		}

		return preset.apply(this.scenario, this.map, this.bbox);
	}

	public SandboxSettings withScenario(Scenario scenario) {
		return new SandboxSettings(this.preset, scenario, this.map, this.bbox, this.walkerSpeed, this.population, this.herdSize, this.infection, this.loot, this.permadeath, this.vanillaMonsters);
	}

	public SandboxSettings withMap(MapKind map, String bbox) {
		return new SandboxSettings(this.preset, this.scenario, map, bbox, this.walkerSpeed, this.population, this.herdSize, this.infection, this.loot, this.permadeath, this.vanillaMonsters);
	}

	/** Same rules with one of them changed: the preset becomes "custom". Values are the names used by the command. */
	public SandboxSettings with(String key, String value) {
		WalkerSpeed walkerSpeed = this.walkerSpeed;
		Population population = this.population;
		HerdSize herdSize = this.herdSize;
		Infection infection = this.infection;
		Loot loot = this.loot;
		boolean permadeath = this.permadeath;
		boolean vanillaMonsters = this.vanillaMonsters;
		switch (key) {
			case "walker_speed" -> walkerSpeed = parse(WalkerSpeed.values(), value);
			case "population" -> population = parse(Population.values(), value);
			case "herd_size" -> herdSize = parse(HerdSize.values(), value);
			case "infection" -> infection = parse(Infection.values(), value);
			case "loot" -> loot = parse(Loot.values(), value);
			case "permadeath" -> permadeath = parseBool(value);
			case "vanilla_monsters" -> vanillaMonsters = parseBool(value);
			default -> throw new IllegalArgumentException("Unknown setting: " + key);
		}

		return new SandboxSettings(Preset.CUSTOM, this.scenario, this.map, this.bbox, walkerSpeed, population, herdSize, infection, loot, permadeath, vanillaMonsters);
	}

	/** The keys {@link #with} takes, and their possible values. */
	public static final String[] KEYS = {"walker_speed", "population", "herd_size", "infection", "loot", "permadeath", "vanilla_monsters"};

	public static String[] values(String key) {
		return switch (key) {
			case "walker_speed" -> names(WalkerSpeed.values());
			case "population" -> names(Population.values());
			case "herd_size" -> names(HerdSize.values());
			case "infection" -> names(Infection.values());
			case "loot" -> names(Loot.values());
			default -> new String[] {"true", "false"};
		};
	}

	private static <E extends Enum<E> & StringRepresentable> String[] names(E[] values) {
		String[] names = new String[values.length];
		for (int i = 0; i < values.length; i++) {
			names[i] = values[i].getSerializedName();
		}

		return names;
	}

	private static <E extends Enum<E> & StringRepresentable> E parse(E[] values, String value) {
		for (E e : values) {
			if (e.getSerializedName().equals(value)) {
				return e;
			}
		}

		throw new IllegalArgumentException("Unknown value: " + value);
	}

	private static boolean parseBool(String value) {
		if (!value.equals("true") && !value.equals("false")) {
			throw new IllegalArgumentException("Expected true or false: " + value);
		}

		return Boolean.parseBoolean(value);
	}

	private static String lower(Enum<?> e) {
		return e.name().toLowerCase(Locale.ROOT);
	}

	/** Ready-made sets of rules, from forgiving to brutal. */
	public enum Preset implements StringRepresentable {
		/** TWD-heroic: few walkers, only bites infect, plenty of loot, you keep your character when you die. */
		SURVIVOR(WalkerSpeed.SHAMBLERS, Population.LOW, HerdSize.SMALL, Infection.BITES_ONLY, Loot.COMMON, false),
		/** The default, PZ-style: slow but many, every scratch can kill you, one life. */
		APOCALYPSE(WalkerSpeed.SHAMBLERS, Population.NORMAL, HerdSize.NORMAL, Infection.NORMAL, Loot.NORMAL, true),
		/** For building: hardly any walkers, no infection, loot everywhere. */
		BUILDER(WalkerSpeed.SHAMBLERS, Population.LOW, HerdSize.SMALL, Infection.OFF, Loot.ABUNDANT, false),
		/** Fast, many, in big herds; little to find. */
		NIGHTMARE(WalkerSpeed.SPRINTERS, Population.HIGH, HerdSize.LARGE, Infection.HARSH, Loot.RARE, true),
		/** Anything changed by hand. */
		CUSTOM(WalkerSpeed.SHAMBLERS, Population.NORMAL, HerdSize.NORMAL, Infection.NORMAL, Loot.NORMAL, true);

		public static final Codec<Preset> CODEC = StringRepresentable.fromEnum(Preset::values);
		final WalkerSpeed walkerSpeed;
		final Population population;
		final HerdSize herdSize;
		final Infection infection;
		final Loot loot;
		final boolean permadeath;

		Preset(WalkerSpeed walkerSpeed, Population population, HerdSize herdSize, Infection infection, Loot loot, boolean permadeath) {
			this.walkerSpeed = walkerSpeed;
			this.population = population;
			this.herdSize = herdSize;
			this.infection = infection;
			this.loot = loot;
			this.permadeath = permadeath;
		}

		public SandboxSettings apply(Scenario scenario, MapKind map, String bbox) {
			return new SandboxSettings(this, scenario, map, bbox, this.walkerSpeed, this.population, this.herdSize, this.infection, this.loot, this.permadeath, false);
		}

		@Override
		public String getSerializedName() {
			return lower(this);
		}

		public String translationKey() {
			return "outbreak.sandbox.preset." + lower(this);
		}
	}

	/** When the story starts. Later starts mean less to find, more walkers and more burnt-out cars. */
	public enum Scenario implements StringRepresentable {
		FIRST_DAY(1, 1.0F, 1.0F, 0.4F),
		WEEK_LATER(7, 0.75F, 1.25F, 0.55F),
		SIX_MONTHS(180, 0.45F, 1.6F, 0.8F);

		public static final Codec<Scenario> CODEC = StringRepresentable.fromEnum(Scenario::values);
		public final int startDay;
		final float loot;
		final float population;
		/** Chance a car parked on the road is a burnt-out wreck. */
		public final float wreckChance;

		Scenario(int startDay, float loot, float population, float wreckChance) {
			this.startDay = startDay;
			this.loot = loot;
			this.population = population;
			this.wreckChance = wreckChance;
		}

		@Override
		public String getSerializedName() {
			return lower(this);
		}

		public String translationKey() {
			return "outbreak.scenario." + lower(this);
		}
	}

	/** Whether the world is the Arnis map of Groningen (so start points can be picked by place) or anything else. */
	public enum MapKind implements StringRepresentable {
		GRONINGEN,
		ANY;

		public static final Codec<MapKind> CODEC = StringRepresentable.fromEnum(MapKind::values);

		@Override
		public String getSerializedName() {
			return lower(this);
		}

		public String translationKey() {
			return "outbreak.sandbox.map." + lower(this);
		}
	}

	public enum WalkerSpeed implements StringRepresentable {
		SHAMBLERS(0.18F),
		FAST_SHAMBLERS(0.24F),
		SPRINTERS(0.31F);

		public static final Codec<WalkerSpeed> CODEC = StringRepresentable.fromEnum(WalkerSpeed::values);
		/** Movement speed attribute (a vanilla zombie is 0.23). */
		public final float speed;

		WalkerSpeed(float speed) {
			this.speed = speed;
		}

		@Override
		public String getSerializedName() {
			return lower(this);
		}

		public String translationKey() {
			return "outbreak.sandbox.walker_speed." + lower(this);
		}
	}

	public enum Population implements StringRepresentable {
		NONE(0.0F),
		LOW(0.4F),
		NORMAL(1.0F),
		HIGH(1.6F),
		INSANE(2.5F);

		public static final Codec<Population> CODEC = StringRepresentable.fromEnum(Population::values);
		final float multiplier;

		Population(float multiplier) {
			this.multiplier = multiplier;
		}

		@Override
		public String getSerializedName() {
			return lower(this);
		}

		public String translationKey() {
			return "outbreak.sandbox.population." + lower(this);
		}
	}

	/** How many walkers spawn together (they form a herd behind the first one). */
	public enum HerdSize implements StringRepresentable {
		SMALL(1, 3),
		NORMAL(2, 6),
		LARGE(4, 10),
		HUGE(8, 16);

		public static final Codec<HerdSize> CODEC = StringRepresentable.fromEnum(HerdSize::values);
		public final int min;
		public final int max;

		HerdSize(int min, int max) {
			this.min = min;
			this.max = max;
		}

		@Override
		public String getSerializedName() {
			return lower(this);
		}

		public String translationKey() {
			return "outbreak.sandbox.herd_size." + lower(this);
		}
	}

	/** Chance of the Knox infection from each kind of wound a walker leaves. */
	public enum Infection implements StringRepresentable {
		OFF(0.0F, 0.0F, 0.0F),
		BITES_ONLY(0.0F, 0.0F, 1.0F),
		NORMAL(0.07F, 0.25F, 1.0F),
		HARSH(0.15F, 0.4F, 1.0F);

		public static final Codec<Infection> CODEC = StringRepresentable.fromEnum(Infection::values);
		private final float scratch;
		private final float laceration;
		private final float bite;

		Infection(float scratch, float laceration, float bite) {
			this.scratch = scratch;
			this.laceration = laceration;
			this.bite = bite;
		}

		public float chance(WoundType type) {
			return switch (type) {
				case SCRATCH -> this.scratch;
				case LACERATION -> this.laceration;
				case BITE -> this.bite;
			};
		}

		@Override
		public String getSerializedName() {
			return lower(this);
		}

		public String translationKey() {
			return "outbreak.sandbox.infection." + lower(this);
		}
	}

	public enum Loot implements StringRepresentable {
		VERY_RARE(0.3F),
		RARE(0.6F),
		NORMAL(1.0F),
		COMMON(1.5F),
		ABUNDANT(2.2F);

		public static final Codec<Loot> CODEC = StringRepresentable.fromEnum(Loot::values);
		final float multiplier;

		Loot(float multiplier) {
			this.multiplier = multiplier;
		}

		@Override
		public String getSerializedName() {
			return lower(this);
		}

		public String translationKey() {
			return "outbreak.sandbox.loot." + lower(this);
		}
	}
}
