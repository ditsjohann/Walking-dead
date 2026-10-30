package io.github.ditsjohann.outbreak.client.world;

import com.mojang.blaze3d.vertex.PoseStack;

import io.github.ditsjohann.outbreak.Outbreak;

import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;

/** A survivor covered in walker guts (see Camouflage): dark blood and worse, smeared all over. */
public class GutsLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final Identifier TEXTURE = Outbreak.id("textures/entity/guts_overlay.png");

	/** Mixed into the humanoid render state: whether to draw the guts. */
	public interface Holder {
		boolean outbreak$guts();

		void outbreak$setGuts(boolean guts);
	}

	public GutsLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
		super(renderer);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
		if (((Holder) state).outbreak$guts()) {
			coloredCutoutModelCopyLayerRender(this.getParentModel(), TEXTURE, poseStack, collector, light, state, -1, 1);
		}
	}
}
