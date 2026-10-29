package io.github.ditsjohann.outbreak.client.walker;

import io.github.ditsjohann.outbreak.entity.Walker;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** Everything a walker's pose needs for one frame. */
public class WalkerRenderState extends LivingEntityRenderState {
	public int skin;
	/** 0 reaching, 1 one arm out, 2 arms hanging, 3 limping, 4 hunched: how this one walks. */
	public int gait;
	/** A lasting tilt of the head, different for every walker. */
	public float headTilt;
	public boolean crawler;
	public boolean aggressive;
	public Walker.Posture posture = Walker.Posture.STANDING;
	/** Ticks since the posture changed (with the partial tick). */
	public float postureTime;
	/** Ticks since death, if dead. */
	public float deadTime;
	/** Which way it's falling, relative to where its body faces (degrees; 0 forward, 90 its right). */
	public float fallAngle;
	public float attack;
	/** Spring offsets from hits: see {@link WalkerAnimator}. */
	public final float[] springs = new float[WalkerAnimator.CHANNELS];
}
