package io.github.ditsjohann.outbreak.noise;

import java.util.HashMap;
import java.util.Map;

import io.github.ditsjohann.outbreak.entity.Walker;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Turns vanilla game events (the vibrations sculk sensors use) into noise walkers can hear.
 * Every event has a radius in blocks; walkers inside it walk over to have a look.
 */
public final class NoiseSystem {
	private static final Map<ResourceKey<GameEvent>, Float> RADII = new HashMap<>();
	/** Events that make noise whoever (or whatever) caused them: explosions, lightning, music. */
	private static final Map<ResourceKey<GameEvent>, Boolean> SOURCELESS = new HashMap<>();

	static {
		radius(GameEvent.STEP, 4);
		radius(GameEvent.HIT_GROUND, 6);
		radius(GameEvent.SWIM, 5);
		radius(GameEvent.SPLASH, 8);
		radius(GameEvent.EAT, 3);
		radius(GameEvent.DRINK, 3);
		radius(GameEvent.BLOCK_PLACE, 8);
		radius(GameEvent.BLOCK_DESTROY, 12);
		radius(GameEvent.BLOCK_CHANGE, 4);
		radius(GameEvent.BLOCK_ACTIVATE, 6);
		radius(GameEvent.BLOCK_DEACTIVATE, 6);
		radius(GameEvent.BLOCK_OPEN, 8);
		radius(GameEvent.BLOCK_CLOSE, 8);
		radius(GameEvent.CONTAINER_OPEN, 5);
		radius(GameEvent.CONTAINER_CLOSE, 5);
		radius(GameEvent.FLUID_PLACE, 5);
		radius(GameEvent.FLUID_PICKUP, 4);
		radius(GameEvent.ENTITY_PLACE, 6);
		radius(GameEvent.ENTITY_DAMAGE, 10);
		radius(GameEvent.SHEAR, 4);
		radius(GameEvent.PROJECTILE_SHOOT, 10);
		radius(GameEvent.PROJECTILE_LAND, 8);
		radius(GameEvent.PRIME_FUSE, 16);
		radius(GameEvent.TELEPORT, 8);
		sourceless(GameEvent.NOTE_BLOCK_PLAY, 16);
		sourceless(GameEvent.JUKEBOX_PLAY, 32);
		sourceless(GameEvent.INSTRUMENT_PLAY, 48);
		sourceless(GameEvent.LIGHTNING_STRIKE, 48);
		sourceless(GameEvent.EXPLODE, 64);
	}

	private NoiseSystem() {
	}

	private static void radius(Holder.Reference<GameEvent> event, float radius) {
		RADII.put(event.key(), radius);
	}

	private static void sourceless(Holder.Reference<GameEvent> event, float radius) {
		radius(event, radius);
		SOURCELESS.put(event.key(), true);
	}

	public static void onGameEvent(ServerLevel level, Holder<GameEvent> event, Vec3 pos, GameEvent.Context context) {
		ResourceKey<GameEvent> key = event.unwrapKey().orElse(null);
		Float base = key == null ? null : RADII.get(key);
		if (base == null) {
			return;
		}

		Player player = noisyPlayer(context.sourceEntity());
		float radius = base;
		if (player != null) {
			if (player.isCreative() || player.isSpectator()) {
				return;
			}

			if (player.isCrouching()) {
				if (key == GameEvent.STEP.key()) {
					return;
				}

				radius *= 0.5F;
			} else if (player.isSprinting() && key == GameEvent.STEP.key()) {
				radius *= 2.5F;
			}
		} else if (!SOURCELESS.containsKey(key) || context.sourceEntity() instanceof Walker) {
			// Only players and big world events (explosions, music...) make noise walkers care about.
			return;
		}

		emit(level, pos, radius);
	}

	/** Make a noise of the given radius. Usable directly for sounds that aren't game events (gunshots, car alarms...). */
	public static void emit(ServerLevel level, Vec3 pos, float radius) {
		double radiusSqr = radius * radius;
		AABB area = AABB.ofSize(pos, radius * 2.0, radius * 2.0, radius * 2.0);
		for (Walker walker : level.getEntitiesOfClass(Walker.class, area, w -> w.distanceToSqr(pos) <= radiusSqr)) {
			walker.hearNoise(pos, radius);
		}
	}

	private static @Nullable Player noisyPlayer(@Nullable Entity source) {
		if (source instanceof Player player) {
			return player;
		}

		if (source instanceof Projectile projectile && projectile.getOwner() instanceof Player owner) {
			return owner;
		}

		return null;
	}
}
