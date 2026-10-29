package io.github.ditsjohann.outbreak.survivor;

import net.minecraft.world.entity.player.Player;

/**
 * The status icons (Project Zomboid's "moodles"). Each has a level from 0 (hidden) to 4 (critical),
 * worked out the same way on the server (for effects) and the client (for the HUD).
 */
public enum Moodle {
	HUNGRY("hungry"),
	THIRSTY("thirsty"),
	TIRED("tired"),
	PANIC("panic"),
	BLEEDING("bleeding"),
	PAIN("pain"),
	SICK("sick"),
	INJURED("injured");

	private final String name;

	Moodle(String name) {
		this.name = name;
	}

	public String id() {
		return this.name;
	}

	public String translationKey(int level) {
		return "outbreak.moodle." + this.name + "." + level;
	}

	public int level(Player player, SurvivorState state) {
		return switch (this) {
			case HUNGRY -> below(player.getFoodData().getFoodLevel(), 14, 10, 6, 2);
			case THIRSTY -> above(state.thirst, 0.4F, 0.6F, 0.8F, 0.95F);
			case TIRED -> above(state.fatigue, 0.5F, 0.7F, 0.85F, 0.95F);
			case PANIC -> above(state.panic, 0.15F, 0.4F, 0.65F, 0.9F);
			case BLEEDING -> Math.min(state.bleedingCount(), 4);
			case PAIN -> above(state.pain(), 0.1F, 0.3F, 0.6F, 0.9F);
			case SICK -> above(state.sickness, 0.1F, 0.35F, 0.6F, 0.85F);
			case INJURED -> below(player.getHealth(), 16, 12, 8, 4);
		};
	}

	private static int above(float value, float l1, float l2, float l3, float l4) {
		return value >= l4 ? 4 : value >= l3 ? 3 : value >= l2 ? 2 : value >= l1 ? 1 : 0;
	}

	private static int below(float value, float l1, float l2, float l3, float l4) {
		return value <= l4 ? 4 : value <= l3 ? 3 : value <= l2 ? 2 : value <= l1 ? 1 : 0;
	}
}
