package io.github.ditsjohann.outbreak.client.vehicle;

import java.util.EnumMap;
import java.util.Map;

import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import io.github.ditsjohann.outbreak.Outbreak;
import io.github.ditsjohann.outbreak.vehicle.Vehicle;
import io.github.ditsjohann.outbreak.vehicle.VehicleType;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.phys.Vec3;

/**
 * Draws every vehicle type with its own model: the detailed base texture, the paint over it tinted
 * in the vehicle's colour (or the burnt-out texture of a wreck), then the glass, see-through. When
 * you drive in first person it also draws your arms, hands on the wheel or the handlebars.
 */
public class VehicleRenderer extends EntityRenderer<Vehicle, VehicleRenderState> {
	public static final Map<VehicleType, ModelLayerLocation> LAYERS = new EnumMap<>(VehicleType.class);
	/** The body models, for the client's ignition check (set once the renderers are made). */
	static final Map<VehicleType, VehicleModel> MODELS = new EnumMap<>(VehicleType.class);
	/** km/h on the speedometer's full sweep. */
	private static final float SPEEDO_KMH = 160.0F;
	/** Your arms in first person are drawn a bit thinner: they're right in front of your face. */
	private static final float ARM_THICKNESS = 0.75F;

	static {
		for (VehicleType type : VehicleType.values()) {
			LAYERS.put(type, new ModelLayerLocation(Outbreak.id(type.id()), "main"));
		}
	}

	private final Map<VehicleType, VehicleModel> models = new EnumMap<>(VehicleType.class);
	private final Map<VehicleType, VehicleModel> glass = new EnumMap<>(VehicleType.class);
	private final PlayerModel wideArms;
	private final PlayerModel slimArms;

	public VehicleRenderer(EntityRendererProvider.Context context) {
		super(context);
		for (VehicleType type : VehicleType.values()) {
			VehicleModel model = new VehicleModel(context.bakeLayer(LAYERS.get(type)), type, false);
			this.models.put(type, model);
			this.glass.put(type, new VehicleModel(context.bakeLayer(LAYERS.get(type)), type, true));
			MODELS.put(type, model);
		}

		this.wideArms = arms(new PlayerModel(context.bakeLayer(ModelLayers.PLAYER), false));
		this.slimArms = arms(new PlayerModel(context.bakeLayer(ModelLayers.PLAYER_SLIM), true));
		this.shadowRadius = 1.0F;
	}

	/** Our own copy of a player's arms, posed at the origin (the shoulder) pointing down. */
	private static PlayerModel arms(PlayerModel model) {
		for (ModelPart arm : new ModelPart[] {model.rightArm, model.leftArm}) {
			arm.x = arm.y = arm.z = 0.0F;
			arm.xRot = arm.yRot = arm.zRot = 0.0F;
		}

		return model;
	}

	public static Identifier texture(VehicleType type, String layer) {
		return Outbreak.id("textures/entity/vehicle/" + type.id() + "/" + layer + ".png");
	}

	@Override
	public VehicleRenderState createRenderState() {
		return new VehicleRenderState();
	}

	@Override
	public void extractRenderState(Vehicle vehicle, VehicleRenderState state, float partialTicks) {
		super.extractRenderState(vehicle, state, partialTicks);
		VehicleType type = vehicle.vehicleType();
		state.type = type;
		state.yRot = vehicle.getYRot(partialTicks);
		state.hurtTime = vehicle.getHurtTime() - partialTicks;
		state.hurtDir = vehicle.getHurtDir();
		state.wheelRotation = Mth.lerp(partialTicks, vehicle.wheelRotationO, vehicle.wheelRotation);
		// Steering, read from how fast the vehicle turns (works for everyone watching, not just the driver).
		float turning = Mth.wrapDegrees(vehicle.getYRot() - vehicle.yRotO);
		float moving = (float) Math.hypot(vehicle.getX() - vehicle.xo, vehicle.getZ() - vehicle.zo);
		float steer = moving > 0.01F ? Mth.clamp(-turning / (moving * 30.0F), -0.5F, 0.5F) : 0.0F;
		state.steer = Mth.lerp(0.5F, state.steer, steer);
		for (int wheel = 0; wheel < type.wheels; wheel++) {
			state.tires[wheel] = vehicle.tire(wheel);
		}

		state.showGlass = type.hasWindows && vehicle.windows() > 0 && !vehicle.isWreck();
		state.color = vehicle.color();
		state.wreck = vehicle.isWreck();
		state.keyInside = vehicle.isKeyInside();
		state.running = vehicle.isRunning();
		state.speedFraction = moving * 72.0F / SPEEDO_KMH;
		state.fuelFraction = type.hasEngine ? vehicle.fuel() / type.tankSize : 0.0F;

		state.driverEye = null;
		Minecraft minecraft = Minecraft.getInstance();
		Entity camera = minecraft.getCameraEntity();
		if (minecraft.options.getCameraType().isFirstPerson() && camera instanceof AbstractClientPlayer player
			&& vehicle.getControllingPassenger() == player && !player.isInvisible()) {
			Vec3 eye = player.getEyePosition(partialTicks).subtract(vehicle.getPosition(partialTicks));
			state.driverEye = toModel(new Vector3f((float) eye.x, (float) eye.y, (float) eye.z), state.yRot);
			state.driverSkin = player.getSkin().body().texturePath();
			state.driverSlim = player.getSkin().model() == PlayerModelType.SLIM;
			state.driverSleeves = player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE);
		}
	}

	/** A world offset from the vehicle into its model's space (blocks: x right, y down, z forward). */
	static Vector3f toModel(Vector3f offset, float yRot) {
		offset.rotateY(yRot * Mth.DEG_TO_RAD);
		return offset.set(-offset.x, -offset.y, offset.z);
	}

	/** The other way: a point in the model's space to an offset from the vehicle in the world. */
	static Vector3f toWorld(Vector3f model, float yRot) {
		return new Vector3f(-model.x, -model.y, model.z).rotateY(-yRot * Mth.DEG_TO_RAD);
	}

	@Override
	public void submit(VehicleRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.rotateDegrees(Axis.YP, -state.yRot);
		if (state.hurtTime > 0.0F) {
			poseStack.rotateDegrees(Axis.ZP, Mth.sin(state.hurtTime) * state.hurtTime / 10.0F * state.hurtDir);
		}

		poseStack.scale(-1.0F, -1.0F, 1.0F);
		VehicleModel model = this.models.get(state.type);
		int light = state.lightCoords;
		if (state.wreck) {
			collector.submitModel(model, state, poseStack, texture(state.type, "wreck"), light, OverlayTexture.NO_OVERLAY, state.outlineColor);
		} else {
			collector.submitModel(model, state, poseStack, texture(state.type, "base"), light, OverlayTexture.NO_OVERLAY, state.outlineColor);
			collector.submitModel(model, state, poseStack, model.renderType(texture(state.type, "paint")), light, OverlayTexture.NO_OVERLAY,
				model.tint(state.color), null, 0);
			if (state.showGlass) {
				collector.submitModel(this.glass.get(state.type), state, poseStack, RenderTypes.entityTranslucent(texture(state.type, "base")),
					light, OverlayTexture.NO_OVERLAY, -1, null, 0);
			}
		}

		if (state.driverEye != null && state.driverSkin != null) {
			this.submitArms(model, state, poseStack, collector);
		}

		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}

	/**
	 * Your arms in first person, from your shoulders to the grips: each arm is laid along the line
	 * from shoulder to grip with the hand on the grip (the shoulder end is out of sight behind you).
	 */
	private void submitArms(VehicleModel model, VehicleRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
		PlayerModel arms = state.driverSlim ? this.slimArms : this.wideArms;
		arms.rightSleeve.visible = state.driverSleeves;
		arms.leftSleeve.visible = state.driverSleeves;
		for (int side = -1; side <= 1; side += 2) {
			Vector3f grip = model.locate(state, side < 0 ? "grip_left" : "grip_right");
			if (grip == null) {
				return;
			}

			// The shoulder, a bit lower and further out than it really is, so the arms come up from the
			// bottom corners of the screen instead of past your eyes.
			Vector3f shoulder = new Vector3f(state.driverEye).add(side * 7.0F / 16.0F, 9.0F / 16.0F, -1.0F / 16.0F);
			Vector3f along = new Vector3f(grip).sub(shoulder).normalize();
			// The arm's own x (towards your left) kept as level as it can be.
			Vector3f across = new Vector3f(-1.0F, 0.0F, 0.0F).fma(along.x, along).normalize();
			Vector3f front = new Vector3f(across).cross(along);
			Quaternionf rotation = new Quaternionf().setFromNormalized(new Matrix3f(across, along, front));
			poseStack.pushPose();
			poseStack.translate(grip.x, grip.y, grip.z);
			poseStack.rotate(rotation);
			poseStack.scale(ARM_THICKNESS, 1.0F, ARM_THICKNESS);
			// The middle of the hand is 10 pixels down the arm and one to its outside.
			poseStack.translate(side * 1.0F / 16.0F, -10.0F / 16.0F, 0.0F);
			ModelPart arm = side < 0 ? arms.leftArm : arms.rightArm;
			collector.submitModelPart(arm, poseStack, RenderTypes.entityTranslucent(state.driverSkin), state.lightCoords, OverlayTexture.NO_OVERLAY, null);
			poseStack.popPose();
		}
	}

	/** Where a vehicle's ignition is in the world right now, or null if it has none. */
	public static @Nullable Vec3 ignition(Vehicle vehicle, float partialTicks) {
		VehicleModel model = MODELS.get(vehicle.vehicleType());
		if (model == null || !model.has("ignition")) {
			return null;
		}

		VehicleRenderState state = new VehicleRenderState();
		state.type = vehicle.vehicleType();
		state.yRot = vehicle.getYRot(partialTicks);
		for (int wheel = 0; wheel < state.type.wheels; wheel++) {
			state.tires[wheel] = vehicle.tire(wheel);
		}

		Vector3f at = model.locate(state, "ignition");
		if (at == null) {
			return null;
		}

		Vector3f offset = toWorld(at, state.yRot);
		return vehicle.getPosition(partialTicks).add(offset.x, offset.y, offset.z);
	}
}
