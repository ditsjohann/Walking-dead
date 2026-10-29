package io.github.ditsjohann.outbreak.client.walker;

import java.util.Map;
import java.util.WeakHashMap;

import io.github.ditsjohann.outbreak.entity.Walker;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * The physics of being hit, per walker, on the client: a set of damped springs (torso, head, arms,
 * legs) that a hit kicks in the direction it came from, and that wobble back. A bat to the side of
 * the head snaps it over and swings the arms out; a shove from the front rocks the torso back.
 * Stepped at a fixed rate from the render loop, so it's smooth at any frame rate.
 */
public final class WalkerAnimator {
	public static final int TORSO_PITCH = 0;
	public static final int TORSO_ROLL = 1;
	public static final int HEAD_PITCH = 2;
	public static final int HEAD_ROLL = 3;
	public static final int HEAD_YAW = 4;
	public static final int RIGHT_ARM_OUT = 5;
	public static final int LEFT_ARM_OUT = 6;
	public static final int RIGHT_ARM_SWING = 7;
	public static final int LEFT_ARM_SWING = 8;
	public static final int RIGHT_LEG_OUT = 9;
	public static final int LEFT_LEG_OUT = 10;
	public static final int CHANNELS = 11;

	/** Spring stiffness and damping per channel (per tick): heads and arms are floppier than the torso. */
	private static final float[] STIFFNESS = {0.30F, 0.30F, 0.45F, 0.40F, 0.40F, 0.20F, 0.20F, 0.22F, 0.22F, 0.35F, 0.35F};
	private static final float[] DAMPING = {0.28F, 0.28F, 0.30F, 0.28F, 0.30F, 0.16F, 0.16F, 0.18F, 0.18F, 0.35F, 0.35F};
	private static final float STEP = 0.25F;
	private static final Map<Walker, WalkerAnimator> ANIMATORS = new WeakHashMap<>();

	private final float[] position = new float[CHANNELS];
	private final float[] velocity = new float[CHANNELS];
	private final RandomSource random = RandomSource.create();
	private int lastHit = -1;
	private float time = Float.NaN;

	private WalkerAnimator() {
	}

	public static WalkerAnimator of(Walker walker) {
		return ANIMATORS.computeIfAbsent(walker, w -> new WalkerAnimator());
	}

	/** Brings the springs up to time {@code now} (in ticks), kicking them first if there's a new hit. */
	public void update(Walker walker, float now, float bodyRot, float[] out) {
		Walker.Hit hit = walker.lastHit();
		if (this.lastHit == -1) {
			// Don't replay a hit that happened before we first saw this walker.
			this.lastHit = hit.sequence();
		} else if (hit.sequence() != this.lastHit) {
			this.lastHit = hit.sequence();
			this.kick(hit, bodyRot);
		}

		if (Float.isNaN(this.time) || now - this.time > 10.0F || now < this.time) {
			this.time = now;
		}

		while (this.time + STEP <= now) {
			this.time += STEP;
			for (int i = 0; i < CHANNELS; i++) {
				float accel = -STIFFNESS[i] * this.position[i] - DAMPING[i] * this.velocity[i];
				this.velocity[i] += accel * STEP;
				this.position[i] += this.velocity[i] * STEP;
			}
		}

		System.arraycopy(this.position, 0, out, 0, CHANNELS);
	}

	private void kick(Walker.Hit hit, float bodyRot) {
		float s = hit.strength() / 8.0F;
		// The push in the walker's own frame: back (away from its face) and towards its right.
		float rel = (hit.yaw() - bodyRot) * Mth.DEG_TO_RAD;
		float back = -Mth.cos(rel);
		float right = Mth.sin(rel);
		float jitter = 0.7F + this.random.nextFloat() * 0.6F;
		this.velocity[TORSO_PITCH] += -back * s * 0.24F * jitter;
		this.velocity[TORSO_ROLL] += right * s * 0.26F * jitter;
		if (hit.head()) {
			// The head takes it: it snaps over, the body follows late.
			this.velocity[HEAD_PITCH] += -back * s * 0.55F;
			this.velocity[HEAD_ROLL] += right * s * 0.6F;
			this.velocity[HEAD_YAW] += right * s * 0.45F * jitter;
		} else {
			// Whiplash: the head lags behind the body.
			this.velocity[HEAD_PITCH] += back * s * 0.18F;
			this.velocity[HEAD_ROLL] -= right * s * 0.22F;
		}

		// Arms fly out, away from the blow; the legs step out to catch the stumble.
		this.velocity[RIGHT_ARM_OUT] += (0.2F + right * 0.4F) * s * jitter;
		this.velocity[LEFT_ARM_OUT] += (0.2F - right * 0.4F) * s * (1.4F - jitter * 0.5F);
		this.velocity[RIGHT_ARM_SWING] += (back * 0.4F + (this.random.nextFloat() - 0.5F) * 0.5F) * s;
		this.velocity[LEFT_ARM_SWING] += (back * 0.4F + (this.random.nextFloat() - 0.5F) * 0.5F) * s;
		this.velocity[RIGHT_LEG_OUT] += Math.max(0.0F, right) * s * 0.25F;
		this.velocity[LEFT_LEG_OUT] += Math.max(0.0F, -right) * s * 0.25F;
	}
}
