package io.github.ditsjohann.outbreak.survivor;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import io.github.ditsjohann.outbreak.entity.Walker;
import io.github.ditsjohann.outbreak.survivor.body.BodyPart;
import io.github.ditsjohann.outbreak.survivor.body.Wound;
import io.github.ditsjohann.outbreak.survivor.body.WoundType;

import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;

/**
 * Runs every survivor's body once a second: bleeding, healing, bandages getting dirty, the Knox
 * infection, thirst, fatigue, panic, and the effects all of that has.
 */
public final class BodySimulation {
	private static final int STEP = 20;

	/** The Knox infection kills this long after infection (~1.2 in-game days). */
	public static final int KNOX_TICKS = 20 * 60 * 24;
	private static final float THIRST_PER_STEP = 1.0F / (60 * 40);
	private static final float FATIGUE_PER_STEP = 1.0F / (60 * 20);
	private static final int REGEN_INTERVAL = 20 * 30;
	private static final int DEHYDRATION_INTERVAL = 20 * 10;
	private static final int INFECTION_DRAIN_INTERVAL = 20 * 6;
	private static final float PANIC_RADIUS = 12.0F;

	private static final Map<UUID, Timers> TIMERS = new HashMap<>();
	private static final Map<UUID, Long> SLEEP_START = new HashMap<>();

	private BodySimulation() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % STEP == 0) {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					if (player.isAlive() && !player.isCreative() && !player.isSpectator()) {
						step(player);
					}
				}
			}
		});
		// Healing is ours: no free regeneration from a full food bar.
		ServerLifecycleEvents.SERVER_STARTED.register(server -> server.getGameRules().set(GameRules.NATURAL_HEALTH_REGENERATION, false, server));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			TIMERS.clear();
			SLEEP_START.clear();
		});
		EntitySleepEvents.START_SLEEPING.register((entity, pos) -> {
			if (entity instanceof ServerPlayer player) {
				SLEEP_START.put(player.getUUID(), player.level().getOverworldClockTime());
			}
		});
		EntitySleepEvents.STOP_SLEEPING.register((entity, pos) -> {
			if (entity instanceof ServerPlayer player) {
				Long start = SLEEP_START.remove(player.getUUID());
				if (start != null && player.level().getOverworldClockTime() - start >= 6000) {
					// Slept through the night: rested, but you wake up thirsty.
					SurvivorState state = Survivors.get(player);
					state.fatigue = 0.0F;
					state.thirst = Math.min(1.0F, state.thirst + 0.15F);
					Survivors.sync(player);
				}
			}
		});
	}

	private static void step(ServerPlayer player) {
		SurvivorState state = Survivors.get(player);
		Timers timers = TIMERS.computeIfAbsent(player.getUUID(), id -> new Timers());

		stepNeeds(player, state);
		stepWounds(player, state);
		stepSickness(player, state, timers);
		stepPanic(player, state);
		stepRegeneration(player, state, timers);
		applyEffects(player, state);

		if (player.isAlive()) {
			Survivors.sync(player);
		}
	}

	private static void stepNeeds(ServerPlayer player, SurvivorState state) {
		int load = Moodle.HEAVY.level(player, state);
		float exertion = (player.isSprinting() ? 2.0F : 1.0F) * (1.0F + 0.5F * load);
		state.thirst = Math.min(1.0F, state.thirst + THIRST_PER_STEP * (player.isSprinting() ? 1.5F : 1.0F));
		if (player.isSleeping()) {
			state.fatigue = Math.max(0.0F, state.fatigue - 0.05F);
		} else {
			state.fatigue = Math.min(1.0F, state.fatigue + FATIGUE_PER_STEP * exertion);
		}
	}

	private static void stepWounds(ServerPlayer player, SurvivorState state) {
		boolean starving = player.getFoodData().getFoodLevel() <= 2 || state.thirst >= 0.95F;
		Iterator<Map.Entry<BodyPart, Wound>> it = state.wounds.entrySet().iterator();
		while (it.hasNext()) {
			Wound wound = it.next().getValue();

			if (wound.bleeding) {
				wound.bloodTimer += STEP;
				if (wound.bloodTimer >= wound.type.ticksPerBloodLoss()) {
					wound.bloodTimer = 0;
					OutbreakDamageTypes.hurt(player, OutbreakDamageTypes.BLEEDING, 1.0F);
				}

				if (wound.bleedLeft > 0) {
					wound.bleedLeft = Math.max(0, wound.bleedLeft - STEP);
					wound.bleeding = wound.bleedLeft > 0;
				}
			}

			if (wound.isBandaged()) {
				wound.bandageAge += STEP;
				if (wound.bandageAge >= Wound.BANDAGE_INFECTS_TICKS && !wound.disinfected) {
					wound.infected = true;
				}
			}

			if (!wound.bleeding && !wound.infected) {
				float speed = wound.isBandageDirty() ? 0.5F : wound.bandage.healSpeed();
				if (starving) {
					speed *= 0.3F;
				}

				wound.healLeft -= Math.round(STEP * speed);
				if (wound.healLeft <= 0) {
					it.remove();
				}
			}
		}
	}

	private static void stepSickness(ServerPlayer player, SurvivorState state, Timers timers) {
		float knoxSickness = 0.0F;
		if (state.knox) {
			state.knoxTicks += STEP;
			float progress = (float) state.knoxTicks / KNOX_TICKS;
			if (progress >= 1.0F) {
				OutbreakDamageTypes.hurt(player, OutbreakDamageTypes.INFECTION, Float.MAX_VALUE);
				return;
			}

			// The first fifth is silent, then it creeps up: queasy, nauseous, fever, agony.
			knoxSickness = Math.max(0.0F, (progress - 0.2F) / 0.8F);
		}

		float poisonSickness = 0.0F;
		if (state.poisonTicks > 0) {
			state.poisonTicks = Math.max(0, state.poisonTicks - STEP);
			poisonSickness = 0.4F;
		}

		state.sickness = Math.max(knoxSickness, poisonSickness);

		if (state.sickness >= 0.85F) {
			timers.infectionDrain += STEP;
			if (timers.infectionDrain >= INFECTION_DRAIN_INTERVAL) {
				timers.infectionDrain = 0;
				OutbreakDamageTypes.hurt(player, OutbreakDamageTypes.INFECTION, 1.0F);
			}
		}

		if (state.thirst >= 1.0F) {
			timers.dehydration += STEP;
			if (timers.dehydration >= DEHYDRATION_INTERVAL) {
				timers.dehydration = 0;
				OutbreakDamageTypes.hurt(player, OutbreakDamageTypes.DEHYDRATION, 1.0F);
			}
		}
	}

	private static void stepPanic(ServerPlayer player, SurvivorState state) {
		AABB area = player.getBoundingBox().inflate(PANIC_RADIUS);
		int threats = player.level().getEntitiesOfClass(Walker.class, area, w -> w.getTarget() == player || w.hasLineOfSight(player)).size();
		float target = Math.min(1.0F, threats * 0.15F + (state.bleedingCount() > 0 ? 0.1F : 0.0F));
		if (target > state.panic) {
			state.panic = Math.min(target, state.panic + 0.1F);
		} else {
			state.panic = Math.max(target, state.panic - 0.02F);
		}
	}

	private static void stepRegeneration(ServerPlayer player, SurvivorState state, Timers timers) {
		boolean canHeal = state.bleedingCount() == 0
			&& player.getFoodData().getFoodLevel() >= 10
			&& state.thirst < 0.8F
			&& state.sickness < 0.35F;
		if (!canHeal || player.getHealth() >= player.getMaxHealth()) {
			timers.regen = 0;
			return;
		}

		timers.regen += STEP;
		if (timers.regen >= REGEN_INTERVAL) {
			timers.regen = 0;
			player.heal(1.0F);
		}
	}

	private static void applyEffects(ServerPlayer player, SurvivorState state) {
		if (!player.isAlive()) {
			return;
		}

		boolean limping = false;
		for (Map.Entry<BodyPart, Wound> entry : state.wounds.entrySet()) {
			if (entry.getKey().isLeg() && entry.getValue().type != WoundType.SCRATCH) {
				limping = true;
			}
		}

		int load = Moodle.HEAVY.level(player, state);
		if (load >= 2) {
			effect(player, MobEffects.SLOWNESS, load - 2 + (limping ? 1 : 0));
		} else if (limping || state.fatigue >= 0.85F || state.sickness >= 0.6F) {
			effect(player, MobEffects.SLOWNESS, 0);
		}

		if (state.fatigue >= 0.85F || state.pain() >= 0.6F) {
			effect(player, MobEffects.MINING_FATIGUE, 0);
		}

		if (state.sickness >= 0.6F) {
			effect(player, MobEffects.WEAKNESS, 0);
		}

		if (state.sickness >= 0.35F) {
			effect(player, MobEffects.HUNGER, 0);
			// Waves of nausea: a few seconds every half minute.
			if (player.level().getGameTime() % (20 * 30) < STEP) {
				player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 20 * 6, 0, true, false, false));
			}
		}
	}

	private static void effect(ServerPlayer player, Holder<MobEffect> effect, int amplifier) {
		player.addEffect(new MobEffectInstance(effect, STEP * 2 + 5, amplifier, true, false, false));
	}

	/** Server-only bookkeeping that doesn't need saving. */
	private static final class Timers {
		int regen;
		int dehydration;
		int infectionDrain;
	}

	/** Lets commands and tests see the server-only truth. */
	public static float knoxProgress(SurvivorState state) {
		return state.knox ? (float) state.knoxTicks / KNOX_TICKS : 0.0F;
	}
}
