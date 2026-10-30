package io.github.ditsjohann.outbreak.entity.ai;

import java.util.EnumSet;

import io.github.ditsjohann.outbreak.entity.Walker;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * A herd on the move: its leader walks towards somewhere far off (see Migration), a stretch at a
 * time, and the rest follow the leader. Anything to chase or a noise to look into comes first.
 */
public class MigrateGoal extends Goal {
	private static final double ARRIVED = 4.0;
	private static final double STRETCH = 16.0;
	private static final int REPATH_TICKS = 60;

	private final Walker walker;
	private final double speed;
	private int repath;

	public MigrateGoal(Walker walker, double speed) {
		this.walker = walker;
		this.speed = speed;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		return this.walker.getMigration() != null && this.walker.getLeader() == null && this.walker.getTarget() == null
			&& this.walker.getNoiseTarget() == null;
	}

	@Override
	public boolean canContinueToUse() {
		return this.canUse();
	}

	@Override
	public void start() {
		this.repath = 0;
	}

	@Override
	public void stop() {
		this.walker.getNavigation().stop();
	}

	@Override
	public void tick() {
		if (--this.repath > 0 && !this.walker.getNavigation().isDone()) {
			return;
		}

		this.repath = REPATH_TICKS;
		BlockPos goal = this.walker.getMigration();
		if (goal == null) {
			return;
		}

		Vec3 here = this.walker.position();
		Vec3 to = new Vec3(goal.getX() + 0.5 - here.x, 0.0, goal.getZ() + 0.5 - here.z);
		double distance = to.length();
		if (distance < ARRIVED) {
			this.walker.clearMigration();
			return;
		}

		// The next stretch: paths only reach so far.
		Vec3 step = distance <= STRETCH ? to : to.scale(STRETCH / distance);
		int x = (int) Math.floor(here.x + step.x);
		int z = (int) Math.floor(here.z + step.z);
		BlockPos at = new BlockPos(x, 0, z);
		double y = this.walker.level().hasChunkAt(at)
			? this.walker.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)
			: here.y;
		if (!this.walker.getNavigation().moveTo(x + 0.5, y, z + 0.5, this.speed)) {
			// Nowhere to go that way (water, a wall): give up on this trip.
			this.walker.clearMigration();
		}
	}
}
