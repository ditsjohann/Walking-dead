package io.github.ditsjohann.outbreak.entity.ai;

import java.util.EnumSet;

import io.github.ditsjohann.outbreak.entity.Walker;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.entity.ai.goal.Goal;

/** Keep a walker close to its herd's leader, so herds drift through the city together. */
public class FollowHerdLeaderGoal extends Goal {
	private static final double START_DISTANCE = 8.0;
	private static final double STOP_DISTANCE = 4.0;

	private final Walker walker;
	private final double speed;
	private @Nullable Walker leader;
	private int repathCooldown;

	public FollowHerdLeaderGoal(Walker walker, double speed) {
		this.walker = walker;
		this.speed = speed;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		if (this.walker.getTarget() != null || this.walker.getNoiseTarget() != null || this.walker.getRandom().nextInt(10) != 0) {
			return false;
		}

		this.leader = this.walker.getLeader();
		return this.leader != null && this.walker.distanceToSqr(this.leader) > START_DISTANCE * START_DISTANCE;
	}

	@Override
	public boolean canContinueToUse() {
		return this.leader != null
			&& this.leader.isAlive()
			&& this.walker.getTarget() == null
			&& this.walker.getNoiseTarget() == null
			&& this.walker.distanceToSqr(this.leader) > STOP_DISTANCE * STOP_DISTANCE;
	}

	@Override
	public void start() {
		this.repathCooldown = 0;
	}

	@Override
	public void stop() {
		this.leader = null;
		this.walker.getNavigation().stop();
	}

	@Override
	public void tick() {
		if (this.leader != null && --this.repathCooldown <= 0) {
			this.repathCooldown = 20;
			this.walker.getNavigation().moveTo(this.leader, this.speed);
		}
	}
}
