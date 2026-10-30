package io.github.ditsjohann.outbreak.client.combat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import io.github.ditsjohann.outbreak.combat.WeaponStyle;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

/**
 * How each weapon style is swung, first and third person. Each swing has a wind-up, the blow and
 * a recovery, instead of vanilla's one flick of the wrist (the hit itself lands when you click, so
 * the blow comes early and the recovery takes the rest; heavy weapons take longer, see
 * OutbreakItems#weapon):
 * <ul>
 * <li>the bat: pulled back over the right shoulder, then a flat sweep across to the left, two-handed;</li>
 * <li>hammers, machetes, crowbars: raised high, then chopped down;</li>
 * <li>knives and screwdrivers: a quick thrust;</li>
 * <li>bare hands: real punches, left and right in turn.</li>
 * </ul>
 */
public final class SwingAnimations {
	/** The item in the hand being drawn right now (first person), set just before its swing. */
	public static ItemStack current = ItemStack.EMPTY;

	private SwingAnimations() {
	}

	private static float ease(float t) {
		t = Mth.clamp(t, 0.0F, 1.0F);
		return t * t * (3.0F - 2.0F * t);
	}

	/** Rises over [start, peak], falls over [peak, end]. */
	private static float bump(float t, float start, float peak, float end) {
		if (t <= start || t >= end) {
			return 0.0F;
		}

		return t < peak ? ease((t - start) / (peak - start)) : 1.0F - ease((t - peak) / (end - peak));
	}

	// --- First person ---

	/** Our swing for the held weapon, instead of vanilla's. Returns false to leave vanilla's be. */
	public static boolean firstPerson(ItemStack stack, float t, PoseStack pose, int invert) {
		switch (WeaponStyle.of(stack)) {
			case SWING -> {
				// Pulled back over the right shoulder, then laid flat and swept across in front, right to left.
				float wind = bump(t, 0.0F, 0.08F, 0.2F);
				float sweep = bump(t, 0.05F, 0.3F, 1.0F);
				pose.translate(invert * (0.12F * wind - 0.45F * sweep), 0.1F * wind + 0.32F * sweep, -0.1F * sweep);
				pose.rotateDegrees(Axis.YP, invert * -15.0F * sweep);
				pose.rotateDegrees(Axis.ZP, invert * (-30.0F * wind + 80.0F * sweep));
				pose.rotateDegrees(Axis.XP, 25.0F * wind);
			}
			case CHOP -> {
				// Tipped back, then brought down and forward: the head of the hammer stays in sight.
				float raise = bump(t, 0.0F, 0.1F, 0.25F);
				float strike = bump(t, 0.08F, 0.3F, 1.0F);
				pose.translate(invert * -0.16F * strike, 0.1F * raise + 0.04F * strike, -0.04F * strike);
				pose.rotateDegrees(Axis.XP, 25.0F * raise - 35.0F * strike);
				pose.rotateDegrees(Axis.ZP, invert * 18.0F * strike);
			}
			case STAB -> {
				// A short jab forward, the point dipping towards the target.
				float thrust = bump(t, 0.0F, 0.3F, 1.0F);
				pose.translate(invert * -0.06F * thrust, 0.03F * thrust, -0.22F * thrust);
				pose.rotateDegrees(Axis.XP, -28.0F * thrust);
			}
			default -> {
				return false;
			}
		}

		return true;
	}

	/**
	 * A punched arm, first person: vanilla's arm placement (shoulder bottom right, hand pointing up
	 * and in), with the fist driven in towards the crosshair and the arm turned to point ahead.
	 * Ends where vanilla's would, ready for the hand to be drawn.
	 */
	public static void punchArm(float t, float inverseArmHeight, PoseStack pose, HumanoidArm arm) {
		float invert = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
		float p = t < 0.25F ? ease(t / 0.25F) : 1.0F - ease((t - 0.25F) / 0.75F);
		pose.translate(invert * (0.64F - 0.5F * p), -0.6F + inverseArmHeight * -0.6F + 0.4F * p, -0.72F - 0.14F * p);
		pose.rotateDegrees(Axis.YP, invert * (45.0F - 32.0F * p));
		pose.rotateDegrees(Axis.XP, 22.0F * p);
		pose.translate(invert * -1.0F, 3.6F, 3.5F);
		pose.rotateDegrees(Axis.ZP, invert * 120.0F);
		pose.rotateDegrees(Axis.XP, 200.0F);
		pose.rotateDegrees(Axis.YP, invert * -135.0F);
		pose.translate(invert * 5.6F, 0.0F, 0.0F);
	}

	// --- Third person ---

	/**
	 * Poses the arms for our weapon styles: the swing, and the bat's two-handed grip. Returns true
	 * if it did (vanilla's attack animation is skipped then).
	 */
	public static boolean thirdPerson(HumanoidModel<?> model, HumanoidRenderState state) {
		WeaponStyle style = WeaponStyle.of(state.getMainHandItemStack());
		if (style == WeaponStyle.OTHER || state.isUsingItem) {
			return false;
		}

		boolean swinging = state.swingAnimation > 0.0F && state.currentSwing != null;
		float t = swinging ? state.swingAnimation : 0.0F;
		HumanoidArm main = state.mainArm;
		float side = main == HumanoidArm.RIGHT ? 1.0F : -1.0F;
		ModelPart mainArm = model.getArm(main);
		ModelPart offArm = model.getArm(main.getOpposite());
		switch (style) {
			case SWING -> {
				// Both hands on the handle in front of the chest, the bat up by the shoulder; the
				// sweep turns the whole upper body and the arms go round with it.
				float wind = bump(t, 0.0F, 0.08F, 0.2F);
				float sweep = bump(t, 0.05F, 0.3F, 1.0F);
				float turn = side * (0.5F * wind - 1.1F * sweep);
				model.body.yRot = turn * 0.6F;
				placeArms(model, state);
				mainArm.xRot = -0.95F - 0.9F * wind - 0.6F * sweep;
				mainArm.yRot = side * -0.45F + turn * 0.6F;
				offArm.xRot = -1.15F - 0.7F * wind - 0.45F * sweep;
				offArm.yRot = side * 0.85F + turn * 0.6F;
			}
			case CHOP -> {
				if (!swinging) {
					return false;
				}

				// Raised high, then brought down, the shoulders turning into it (the body doesn't
				// bend: that would pull the torso off the legs).
				float raise = bump(t, 0.0F, 0.1F, 0.25F);
				float strike = bump(t, 0.08F, 0.3F, 1.0F);
				model.body.yRot = side * -0.2F * strike;
				placeArms(model, state);
				mainArm.xRot = -0.3F - 2.6F * raise - 0.9F * strike;
				mainArm.zRot = side * 0.15F * raise;
			}
			case STAB -> {
				if (!swinging) {
					return false;
				}

				float thrust = bump(t, 0.0F, 0.3F, 1.0F);
				model.body.yRot = side * -0.25F * thrust;
				placeArms(model, state);
				mainArm.xRot = -0.4F - 1.2F * thrust;
			}
			case FIST -> {
				if (!swinging) {
					return false;
				}

				// Straight from the shoulder, the body turning into it, the other fist up on guard.
				float p = t < 0.25F ? ease(t / 0.25F) : 1.0F - ease((t - 0.25F) / 0.75F);
				model.body.yRot = side * -0.35F * p;
				placeArms(model, state);
				mainArm.xRot = -1.55F * p + mainArm.xRot * (1.0F - p);
				mainArm.yRot = side * 0.2F * p;
				offArm.xRot = Mth.lerp(p, offArm.xRot, -0.9F);
				offArm.yRot = Mth.lerp(p, offArm.yRot, side * 0.4F);
			}
			default -> {
				return false;
			}
		}

		return true;
	}

	/** Keeps the shoulders on the turned body (as vanilla's swing does). */
	private static void placeArms(HumanoidModel<?> model, HumanoidRenderState state) {
		float scale = state.ageScale;
		model.rightArm.z = Mth.sin(model.body.yRot) * 5.0F * scale;
		model.rightArm.x = -Mth.cos(model.body.yRot) * 5.0F * scale;
		model.leftArm.z = -Mth.sin(model.body.yRot) * 5.0F * scale;
		model.leftArm.x = Mth.cos(model.body.yRot) * 5.0F * scale;
	}
}
