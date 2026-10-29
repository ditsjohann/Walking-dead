package io.github.ditsjohann.outbreak.client.vehicle;

import java.util.EnumMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import io.github.ditsjohann.outbreak.Outbreak;
import io.github.ditsjohann.outbreak.vehicle.Vehicle;
import io.github.ditsjohann.outbreak.vehicle.VehicleType;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/** Draws every vehicle type with its own model, in its colour (or burnt out). */
public class VehicleRenderer extends EntityRenderer<Vehicle, VehicleRenderState> {
	public static final Map<VehicleType, ModelLayerLocation> LAYERS = new EnumMap<>(VehicleType.class);

	static {
		for (VehicleType type : VehicleType.values()) {
			LAYERS.put(type, new ModelLayerLocation(Outbreak.id(type.id()), "main"));
		}
	}

	private final Map<VehicleType, VehicleModel> models = new EnumMap<>(VehicleType.class);

	public VehicleRenderer(EntityRendererProvider.Context context) {
		super(context);
		for (VehicleType type : VehicleType.values()) {
			this.models.put(type, new VehicleModel(context.bakeLayer(LAYERS.get(type)), type));
		}

		this.shadowRadius = 1.0F;
	}

	public static Identifier texture(VehicleType type, String color) {
		return Outbreak.id("textures/entity/vehicle/" + type.id() + "/" + color + ".png");
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

		Minecraft minecraft = Minecraft.getInstance();
		boolean insideFirstPerson = minecraft.options.getCameraType().isFirstPerson() && minecraft.getCameraEntity() != null
			&& vehicle.hasPassenger(minecraft.getCameraEntity());
		state.showGlass = type.hasWindows && vehicle.windows() > 0 && !insideFirstPerson;
		state.color = vehicle.color();
		state.wreck = vehicle.isWreck();
	}

	@Override
	public void submit(VehicleRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.rotateDegrees(Axis.YP, -state.yRot);
		if (state.hurtTime > 0.0F) {
			poseStack.rotateDegrees(Axis.ZP, Mth.sin(state.hurtTime) * state.hurtTime / 10.0F * state.hurtDir);
		}

		poseStack.scale(-1.0F, -1.0F, 1.0F);
		Identifier texture = texture(state.type, state.wreck ? "wreck" : state.type.colors.get(state.color));
		collector.submitModel(this.models.get(state.type), state, poseStack, texture, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}
}
