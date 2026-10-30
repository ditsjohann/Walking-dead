package io.github.ditsjohann.outbreak.client.walker;

import static io.github.ditsjohann.outbreak.client.walker.WalkerAnimator.*;

import java.util.Map;

import com.google.gson.JsonObject;

import io.github.ditsjohann.outbreak.client.model.BoxModels;
import io.github.ditsjohann.outbreak.entity.Walker;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.util.Mth;

/**
 * A walker with elbows and knees (tools/walkers/make_walkers.py), animated by hand: a shambling
 * walk in one of five styles, crawling, lying where it fell, getting back up, lunging, and on top
 * of all that the hit springs from {@link WalkerAnimator}. The renderer turns the whole body for
 * falls and crawling; this only bends the joints.
 */
public class WalkerModel extends EntityModel<WalkerRenderState> {
	private static final String PATH = "/assets/outbreak/models/walker.json";
	private static final float PI = Mth.PI;

	private final ModelPart waist;
	private final ModelPart chest;
	private final ModelPart head;
	private final ModelPart rightArm;
	private final ModelPart rightForearm;
	private final ModelPart leftArm;
	private final ModelPart leftForearm;
	private final ModelPart rightLeg;
	private final ModelPart rightShin;
	private final ModelPart leftLeg;
	private final ModelPart leftShin;

	public WalkerModel(ModelPart root) {
		super(root);
		Map<String, ModelPart> parts = BoxModels.parts(root, json());
		this.waist = parts.get("waist");
		this.chest = parts.get("chest");
		this.head = parts.get("head");
		this.rightArm = parts.get("right_arm");
		this.rightForearm = parts.get("right_forearm");
		this.leftArm = parts.get("left_arm");
		this.leftForearm = parts.get("left_forearm");
		this.rightLeg = parts.get("right_leg");
		this.rightShin = parts.get("right_shin");
		this.leftLeg = parts.get("left_leg");
		this.leftShin = parts.get("left_shin");
	}

	private static JsonObject json() {
		return BoxModels.read(PATH);
	}

	public static LayerDefinition layer() {
		// Drawn like a vanilla mob: the model's feet at y = 24.
		return BoxModels.layer(json(), 24.0F);
	}

	@Override
	public void setupAnim(WalkerRenderState state) {
		super.setupAnim(state);
		if (state.deadTime > 0.0F) {
			this.poseLimp(state, Math.min(1.0F, state.deadTime / Walker.FALL_TICKS), state.deadTime > 30.0F ? 0.0F : 1.0F);
		} else if (state.posture == Walker.Posture.DOWN) {
			this.poseLimp(state, Math.min(1.0F, state.postureTime / Walker.FALL_TICKS), 1.0F);
		} else if (state.posture == Walker.Posture.GETTING_UP) {
			this.poseGettingUp(state, Mth.clamp(state.postureTime / Walker.GET_UP_TICKS, 0.0F, 1.0F));
		} else if (state.crawler) {
			this.poseCrawling(state);
		} else {
			this.poseWalking(state);
		}

		this.poseAttack(state);
		this.applySprings(state.springs);
	}

	private void poseWalking(WalkerRenderState state) {
		float speed = Math.min(1.0F, state.walkAnimationSpeed);
		float cycle = state.walkAnimationPos * 0.6662F;
		float age = state.ageInTicks;
		float limp = state.gait == 3 ? 0.35F : 1.0F;

		this.rightLeg.xRot = Mth.cos(cycle) * 1.1F * speed * limp;
		this.leftLeg.xRot = Mth.cos(cycle + PI) * 1.1F * speed;
		this.rightShin.xRot = 0.08F + Math.max(0.0F, Mth.sin(cycle)) * 0.9F * speed * limp;
		this.leftShin.xRot = 0.08F + Math.max(0.0F, Mth.sin(cycle + PI)) * 0.9F * speed;
		this.waist.y += Math.abs(Mth.sin(cycle)) * 0.6F * speed;

		// Slouched, swaying; a limper lurches over its bad leg.
		this.chest.xRot = 0.1F + Mth.sin(age * 0.05F) * 0.02F;
		this.chest.zRot = Mth.sin(age * 0.04F) * 0.03F + Mth.sin(cycle) * 0.05F * speed;
		if (state.gait == 3) {
			this.chest.zRot += Mth.sin(cycle) * 0.18F * speed;
			this.waist.zRot = Mth.sin(cycle) * 0.08F * speed;
		}

		boolean reach = state.gait == 0 || state.aggressive;
		float sway = Mth.sin(age * 0.09F) * 0.06F;
		float swing = Mth.cos(cycle + PI) * 0.5F * speed;
		float raised = state.aggressive ? -0.15F : 0.0F;
		if (state.gait == 4) {
			// Hunched over, head low, arms dangling in front.
			this.chest.xRot = 0.5F;
			this.head.xRot -= 0.4F;
			this.armDangling(this.rightArm, this.rightForearm, -0.7F + swing * 0.5F + raised);
			this.armDangling(this.leftArm, this.leftForearm, -0.7F - swing * 0.5F + raised);
		} else {
			if (reach || state.gait == 1 || state.gait == 3) {
				this.armReaching(this.rightArm, this.rightForearm, -1.35F + sway + Mth.cos(cycle) * 0.1F * speed + raised, 1.0F);
			} else {
				this.armDangling(this.rightArm, this.rightForearm, 0.1F + swing);
			}

			if (reach) {
				this.armReaching(this.leftArm, this.leftForearm, -1.35F - sway + Mth.cos(cycle + PI) * 0.1F * speed + raised, -1.0F);
			} else {
				this.armDangling(this.leftArm, this.leftForearm, 0.1F - swing);
			}
		}

		this.lookAt(state, 0.7F);
	}

	private void armReaching(ModelPart arm, ModelPart forearm, float xRot, float side) {
		arm.xRot = xRot;
		arm.zRot = 0.08F * side;
		forearm.xRot = -0.25F;
	}

	private void armDangling(ModelPart arm, ModelPart forearm, float xRot) {
		arm.xRot = xRot;
		arm.zRot = arm == this.rightArm ? 0.12F : -0.12F;
		forearm.xRot = -0.2F;
	}

	private void lookAt(WalkerRenderState state, float amount) {
		this.head.xRot += state.xRot * Mth.DEG_TO_RAD * amount;
		this.head.yRot += state.yRot * Mth.DEG_TO_RAD * amount;
		this.head.zRot += state.headTilt + Mth.sin(state.ageInTicks * 0.13F) * 0.03F;
	}

	/** Lying on its belly (the renderer lays it down), pulling itself along by the arms. */
	private void poseCrawling(WalkerRenderState state) {
		float speed = Math.min(1.0F, state.walkAnimationSpeed);
		float c = state.walkAnimationPos * 0.5F + state.ageInTicks * 0.03F;
		this.rightArm.xRot = -2.5F + 0.7F * Mth.sin(c);
		this.leftArm.xRot = -2.5F + 0.7F * Mth.sin(c + PI);
		this.rightForearm.xRot = -0.6F * Math.max(0.0F, Mth.cos(c));
		this.leftForearm.xRot = -0.6F * Math.max(0.0F, Mth.cos(c + PI));
		this.rightArm.zRot = 0.3F;
		this.leftArm.zRot = -0.3F;
		this.chest.zRot = Mth.sin(c) * 0.1F * (0.5F + speed);
		this.rightLeg.xRot = 0.1F + 0.15F * Mth.sin(c + PI / 2.0F);
		this.leftLeg.xRot = 0.1F - 0.15F * Mth.sin(c + PI / 2.0F);
		this.rightShin.xRot = 0.25F;
		this.leftShin.xRot = 0.15F;
		this.rightLeg.zRot = 0.1F;
		this.leftLeg.zRot = -0.12F;
		// Face up off the ground, looking where it's going.
		this.head.xRot = -1.15F + state.xRot * Mth.DEG_TO_RAD * 0.3F;
		this.head.yRot = state.yRot * Mth.DEG_TO_RAD * 0.5F;
		this.head.zRot = state.headTilt;
	}

	/** Falling or lying there: arms thrown out, then flopped. Twitches while it's (un)dead to the world. */
	private void poseLimp(WalkerRenderState state, float fallen, float twitch) {
		float flail = Mth.sin(fallen * PI);
		this.rightArm.xRot = -1.4F * flail - 0.2F * fallen;
		this.leftArm.xRot = -1.1F * flail - 0.3F * fallen;
		this.rightArm.zRot = 0.4F + 0.6F * fallen;
		this.leftArm.zRot = -0.4F - 0.5F * fallen;
		this.rightForearm.xRot = -0.3F;
		this.leftForearm.xRot = -0.5F;
		this.rightLeg.zRot = 0.18F * fallen;
		this.leftLeg.zRot = -0.12F * fallen;
		this.rightLeg.xRot = -0.25F * flail;
		this.rightShin.xRot = 0.3F * fallen;
		this.leftShin.xRot = 0.1F;
		this.head.zRot = state.headTilt + 0.35F * fallen;
		this.head.xRot = -0.2F * fallen;
		float age = state.ageInTicks;
		if (twitch > 0.0F && Mth.positiveModulo(age + state.skin * 7.0F, 45.0F) < 6.0F) {
			this.rightForearm.xRot += Mth.sin(age * 2.3F) * 0.25F;
			this.head.yRot += Mth.sin(age * 1.7F) * 0.15F;
			this.leftLeg.xRot += Mth.sin(age * 2.0F) * 0.1F;
		}
	}

	/** Pushing itself up: knees under, hands on the ground, then upright. */
	private void poseGettingUp(WalkerRenderState state, float progress) {
		float bent = 1.0F - Mth.clamp((progress - 0.3F) / 0.7F, 0.0F, 1.0F);
		float push = Mth.sin(Math.min(1.0F, progress * 1.5F) * PI);
		this.rightLeg.xRot = -1.2F * bent;
		this.leftLeg.xRot = -0.6F * bent;
		this.rightShin.xRot = 1.8F * bent;
		this.leftShin.xRot = 1.3F * bent;
		this.rightArm.xRot = -0.9F * push - 0.3F * bent;
		this.leftArm.xRot = -0.9F * push - 0.3F * bent;
		this.rightForearm.xRot = -0.8F * push;
		this.leftForearm.xRot = -0.8F * push;
		this.chest.xRot = 0.4F * bent;
		this.head.xRot = -0.3F * bent;
		this.head.zRot = state.headTilt;
	}

	/** The grab: lurches forward, both hands closing in, mouth first. */
	private void poseAttack(WalkerRenderState state) {
		if (state.attack <= 0.0F) {
			return;
		}

		float lunge = Mth.sin(state.attack * PI);
		this.chest.xRot += lunge * 0.45F;
		this.rightArm.xRot = Mth.lerp(lunge, this.rightArm.xRot, -1.75F);
		this.leftArm.xRot = Mth.lerp(lunge, this.leftArm.xRot, -1.65F);
		this.rightArm.zRot = Mth.lerp(lunge, this.rightArm.zRot, -0.35F);
		this.leftArm.zRot = Mth.lerp(lunge, this.leftArm.zRot, 0.35F);
		this.rightForearm.xRot -= lunge * 0.3F;
		this.leftForearm.xRot -= lunge * 0.3F;
		this.head.xRot += lunge * 0.25F;
	}

	private void applySprings(float[] s) {
		this.chest.xRot += s[TORSO_PITCH];
		this.chest.zRot -= s[TORSO_ROLL];
		this.head.xRot += s[HEAD_PITCH];
		this.head.zRot -= s[HEAD_ROLL];
		this.head.yRot += s[HEAD_YAW];
		this.rightArm.zRot += s[RIGHT_ARM_OUT];
		this.leftArm.zRot -= s[LEFT_ARM_OUT];
		this.rightArm.xRot += s[RIGHT_ARM_SWING];
		this.leftArm.xRot += s[LEFT_ARM_SWING];
		this.rightLeg.zRot += s[RIGHT_LEG_OUT];
		this.leftLeg.zRot -= s[LEFT_LEG_OUT];
	}
}
