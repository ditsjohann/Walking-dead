package io.github.ditsjohann.outbreak.entity.ai;

import java.util.EnumSet;

import io.github.ditsjohann.outbreak.entity.Walker;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/** Walk to the last thing this walker heard, linger there a moment, then forget about it. */
public class InvestigateNoiseGoal extends Goal {
	private static final int LINGER_TICKS = 60;
	private static final int GIVE_UP_TICKS = 20 * 30;

	private final Walker walker;
	private final double speed;
	private int version;
	private int ticks;
	private int lingerTicks;

	public InvestigateNoiseGoal(Walker walker, double speed) {
		this.walker = walker;
		this.speed = speed;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		return this.walker.getTarget() == null && this.walker.getNoiseTarget() != null;
	}

	@Override
	public boolean canContinueToUse() {
		return this.walker.getTarget() == null && this.walker.getNoiseTarget() != null && this.ticks < GIVE_UP_TICKS;
	}

	@Override
	public void start() {
		this.ticks = 0;
		this.lingerTicks = 0;
		this.moveToNoise();
	}

	@Override
	public void stop() {
		this.walker.getNavigation().stop();
	}

	@Override
	public void tick() {
		this.ticks++;
		Vec3 target = this.walker.getNoiseTarget();
		if (target == null) {
			return;
		}

		if (this.version != this.walker.getNoiseVersion()) {
			this.moveToNoise();
		}

		this.walker.getLookControl().setLookAt(target.x, target.y + 1.0, target.z);
		if (this.walker.horizontalDistanceSqr(target) < 2.5 * 2.5 || this.walker.getNavigation().isDone()) {
			if (++this.lingerTicks > LINGER_TICKS) {
				this.walker.clearNoise();
			}
		}
	}

	private void moveToNoise() {
		Vec3 target = this.walker.getNoiseTarget();
		this.version = this.walker.getNoiseVersion();
		this.lingerTicks = 0;
		if (target != null) {
			this.walker.getNavigation().moveTo(target.x, target.y, target.z, this.speed);
		}
	}
}
