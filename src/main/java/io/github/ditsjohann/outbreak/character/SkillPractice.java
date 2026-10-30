package io.github.ditsjohann.outbreak.character;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import io.github.ditsjohann.outbreak.Outbreak;
import io.github.ditsjohann.outbreak.entity.Walker;
import io.github.ditsjohann.outbreak.inventory.CarryWeight;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * The skills you practise just by moving, checked once a second: running (fitness), creeping past
 * walkers (sneaking) and lugging a heavy load (strength). Also keeps the athletic trait's speed on.
 */
final class SkillPractice {
	private static final int STEP = 20;
	private static final Identifier TRAIT_SPEED = Outbreak.id("trait_speed");
	private static final Map<UUID, Vec3> LAST_POS = new HashMap<>();

	private SkillPractice() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % STEP != 0) {
				return;
			}

			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (player.isAlive() && Characters.hasCharacter(player)) {
					step(player);
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LAST_POS.clear());
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST_POS.remove(handler.player.getUUID()));
	}

	private static void step(ServerPlayer player) {
		applySpeed(player);
		Vec3 last = LAST_POS.put(player.getUUID(), player.position());
		if (last == null || player.isPassenger() || player.isCreative() || player.isSpectator()) {
			return;
		}

		boolean moved = player.position().subtract(last).horizontalDistanceSqr() > 1.0;
		if (!moved) {
			return;
		}

		if (player.isSprinting()) {
			Characters.addXp(player, Skill.FITNESS, 1.0F);
		}

		if (player.isCrouching() && !player.level().getEntitiesOfClass(Walker.class, player.getBoundingBox().inflate(16.0), w -> w.getTarget() != player).isEmpty()) {
			Characters.addXp(player, Skill.SNEAKING, 1.5F);
		}

		if (CarryWeight.ratio(player) > 0.75F) {
			Characters.addXp(player, Skill.STRENGTH, 1.0F);
		}
	}

	private static void applySpeed(ServerPlayer player) {
		var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}

		double bonus = Perks.speedBonus(player);
		AttributeModifier current = speed.getModifier(TRAIT_SPEED);
		if (current == null ? bonus != 0.0 : current.amount() != bonus) {
			speed.removeModifier(TRAIT_SPEED);
			if (bonus != 0.0) {
				speed.addTransientModifier(new AttributeModifier(TRAIT_SPEED, bonus, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
			}
		}
	}
}
