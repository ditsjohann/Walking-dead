package io.github.ditsjohann.outbreak.survivor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import io.github.ditsjohann.outbreak.entity.OutbreakEntities;
import io.github.ditsjohann.outbreak.entity.Walker;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Everyone is infected: whoever dies, of whatever cause, gets back up as a walker a little later. */
public final class Turning {
	private static final int TURN_DELAY_TICKS = 20 * 30;
	private static final List<PendingTurn> PENDING = new ArrayList<>();

	private Turning() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				PENDING.add(new PendingTurn(player.level().dimension(), player.position(), player.getName().getString(), TURN_DELAY_TICKS));
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(Turning::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> PENDING.clear());
	}

	private static void tick(MinecraftServer server) {
		Iterator<PendingTurn> it = PENDING.iterator();
		while (it.hasNext()) {
			PendingTurn turn = it.next();
			if (--turn.ticksLeft > 0) {
				continue;
			}

			it.remove();
			ServerLevel level = server.getLevel(turn.dimension);
			if (level != null && turn.pos.y >= level.getMinY()) {
				rise(level, turn);
			}
		}
	}

	private static void rise(ServerLevel level, PendingTurn turn) {
		Walker walker = OutbreakEntities.WALKER.create(level, EntitySpawnReason.CONVERSION);
		if (walker == null) {
			return;
		}

		walker.snapTo(turn.pos.x, turn.pos.y, turn.pos.z, level.getRandom().nextFloat() * 360.0F, 0.0F);
		walker.finalizeSpawn(level, level.getCurrentDifficultyAt(walker.blockPosition()), EntitySpawnReason.CONVERSION, null);
		walker.setCustomName(Component.literal(turn.name));
		// Your old self sticks around for you to find.
		walker.setPersistenceRequired();
		level.addFreshEntity(walker);
	}

	private static final class PendingTurn {
		final ResourceKey<Level> dimension;
		final Vec3 pos;
		final String name;
		int ticksLeft;

		PendingTurn(ResourceKey<Level> dimension, Vec3 pos, String name, int ticksLeft) {
			this.dimension = dimension;
			this.pos = pos;
			this.name = name;
			this.ticksLeft = ticksLeft;
		}
	}
}
