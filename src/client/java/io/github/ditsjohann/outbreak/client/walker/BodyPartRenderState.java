package io.github.ditsjohann.outbreak.client.walker;

import io.github.ditsjohann.outbreak.gore.Limb;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

public class BodyPartRenderState extends EntityRenderState {
	public Limb limb = Limb.RIGHT_FOREARM;
	public int skin;
	public float yRot;
	/** Degrees it has turned over (tumbling through the air, or lying on its side). */
	public float tumble;
}
