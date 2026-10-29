package io.github.ditsjohann.outbreak.entity.ai;

import java.util.EnumSet;

import io.github.ditsjohann.outbreak.block.Barricades;
import io.github.ditsjohann.outbreak.entity.Walker;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;

/**
 * When a walker can't get to what it's after and a window or barricade is in the way, it batters
 * it until it gives. Every blow is noise, which brings other walkers.
 */
public class BreakObstacleGoal extends Goal {
	private static final int BLOW_INTERVAL = 20;

	private final Walker walker;
	private @Nullable BlockPos obstacle;
	private int cooldown;
	private int checkTimer;
	private @Nullable Vec3 lastPos;

	public BreakObstacleGoal(Walker walker) {
		this.walker = walker;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		// Looking around for obstacles is cheap but not free: twice a second is plenty.
		if (++this.checkTimer < 10) {
			return false;
		}

		this.checkTimer = 0;
		this.obstacle = this.findObstacle();
		return this.obstacle != null;
	}

	/** Once a walker starts battering something, it keeps at it until it gives way. */
	@Override
	public boolean canContinueToUse() {
		return this.obstacle != null && Barricades.isBreakable(this.walker.level().getBlockState(this.obstacle));
	}

	@Override
	public void start() {
		this.cooldown = 10;
		this.walker.getNavigation().stop();
	}

	@Override
	public void stop() {
		this.obstacle = null;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		if (this.obstacle == null) {
			return;
		}

		this.walker.getLookControl().setLookAt(Vec3.atCenterOf(this.obstacle));
		if (--this.cooldown <= 0 && this.walker.level() instanceof ServerLevel level) {
			this.cooldown = BLOW_INTERVAL;
			this.walker.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			Barricades.hit(level, this.obstacle, this.walker);
		}
	}

	/** Where the walker wants to go: its target, or the last noise it heard. */
	private @Nullable Vec3 goal() {
		LivingEntity target = this.walker.getTarget();
		if (target != null && target.isAlive()) {
			return target.position();
		}

		return this.walker.getNoiseTarget();
	}

	private @Nullable BlockPos findObstacle() {
		Vec3 goal = this.goal();
		if (goal == null || this.walker.horizontalDistanceSqr(goal) < 0.25) {
			return null;
		}

		// Only bother when the normal way in isn't working out: no path, or not getting anywhere.
		Vec3 here = this.walker.position();
		boolean standingStill = this.lastPos != null && here.distanceToSqr(this.lastPos) < 0.01;
		this.lastPos = here;
		if (!this.walker.getNavigation().isDone() && !this.walker.getNavigation().isStuck() && !standingStill) {
			return null;
		}

		Vec3 toGoal = goal.subtract(this.walker.position());
		Direction facing = Direction.getApproximateNearest(toGoal.x, 0.0, toGoal.z);
		BlockPos feet = this.walker.blockPosition();
		for (BlockPos pos : new BlockPos[] {feet.relative(facing), feet.relative(facing).above(), feet}) {
			if (Barricades.isBreakable(this.walker.level().getBlockState(pos))) {
				return pos;
			}
		}

		return null;
	}
}
