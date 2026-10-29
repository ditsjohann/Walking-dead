package io.github.ditsjohann.outbreak.vehicle;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import io.github.ditsjohann.outbreak.inventory.ContainerKind;
import io.github.ditsjohann.outbreak.inventory.OutbreakMenus;
import io.github.ditsjohann.outbreak.noise.NoiseSystem;
import io.github.ditsjohann.outbreak.survivor.OutbreakDamageTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A vehicle: bicycle, scooter, car or van. PZ-style, it's a set of parts that each wear out:
 * tires (one per wheel), a battery and an engine, the fuel in the tank, the windows and the body.
 * <p>
 * Like a boat, it's driven on the driver's own client (smooth at any ping) and the server follows;
 * the server owns everything else: fuel, the engine, damage, noise, keys and locks.
 */
public class Vehicle extends VehicleEntity {
	private static final EntityDataAccessor<Float> DATA_FUEL = SynchedEntityData.defineId(Vehicle.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Integer> DATA_ENGINE = SynchedEntityData.defineId(Vehicle.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_WINDOWS = SynchedEntityData.defineId(Vehicle.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_BODY = SynchedEntityData.defineId(Vehicle.class, EntityDataSerializers.INT);
	/** Battery charge, or -1 without a battery. */
	private static final EntityDataAccessor<Float> DATA_BATTERY = SynchedEntityData.defineId(Vehicle.class, EntityDataSerializers.FLOAT);
	/** Per wheel, 2 bits: {@link TireState}. */
	private static final EntityDataAccessor<Integer> DATA_TIRES = SynchedEntityData.defineId(Vehicle.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Byte> DATA_FLAGS = SynchedEntityData.defineId(Vehicle.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(Vehicle.class, EntityDataSerializers.INT);

	private static final int FLAG_RUNNING = 1;
	private static final int FLAG_LOCKED = 2;
	private static final int FLAG_HAS_ALARM = 4;
	private static final int FLAG_ALARM = 8;
	private static final int FLAG_HOTWIRED = 16;
	private static final int FLAG_WRECK = 32;
	private static final int FLAG_KEY_INSIDE = 64;

	/** Litres per tick with the engine idling (about half a litre a minute). */
	private static final float IDLE_CONSUMPTION = 0.0004F;
	private static final float BATTERY_PER_START = 0.04F;
	private static final float MIN_START_CHARGE = 0.08F;
	private static final float REVERSE_SPEED = 0.15F;
	private static final int ALARM_TICKS = 20 * 30;
	/** Car tires wear by 1% about every 120 blocks. */
	private static final float TIRE_WEAR_PER_BLOCK = 1.0F / 120.0F;

	public enum TireState {
		MISSING,
		FLAT,
		WORN,
		GOOD
	}

	private final VehicleType type;
	private final SimpleContainer parts;
	private final @Nullable SimpleContainer trunk;

	// Driving (on whichever side simulates the movement).
	private float speed;
	private float deltaRotation;
	private boolean inputLeft;
	private boolean inputRight;
	private boolean inputUp;
	private boolean inputDown;
	private boolean inputBrake;

	// Server only.
	private @Nullable Vec3 lastServerPos;
	/** Measured speed in blocks per tick, smoothed: the server doesn't drive, it only sees where the car went. */
	private float measuredSpeed;
	private float distanceSinceWear;
	private int alarmTicks;
	private int actionCooldown;
	private final Map<UUID, Integer> recentlyHit = new HashMap<>();

	// Client only: animation.
	public float wheelRotation;
	public float wheelRotationO;

	public Vehicle(EntityType<? extends Vehicle> entityType, Level level) {
		super(entityType, level);
		this.type = Vehicles.typeOf(entityType);
		this.parts = new SimpleContainer(this.type.partSlots()) {
			@Override
			public void setChanged() {
				super.setChanged();
				Vehicle.this.syncParts();
			}
		};
		this.trunk = this.type.trunk == null ? null : new VehicleContainer(this, this.type.trunk.size());
		this.blocksBuilding = true;
	}

	public VehicleType vehicleType() {
		return this.type;
	}

	// --- State ----------------------------------------------------------------------------------

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_FUEL, 0.0F);
		builder.define(DATA_ENGINE, 100);
		builder.define(DATA_WINDOWS, 100);
		builder.define(DATA_BODY, 100);
		builder.define(DATA_BATTERY, -1.0F);
		builder.define(DATA_TIRES, 0);
		builder.define(DATA_FLAGS, (byte) 0);
		builder.define(DATA_COLOR, 0);
	}

	private boolean flag(int flag) {
		return (this.entityData.get(DATA_FLAGS) & flag) != 0;
	}

	private void setFlag(int flag, boolean value) {
		byte flags = this.entityData.get(DATA_FLAGS);
		this.entityData.set(DATA_FLAGS, (byte) (value ? flags | flag : flags & ~flag));
	}

	public float fuel() {
		return this.entityData.get(DATA_FUEL);
	}

	public void setFuel(float litres) {
		this.entityData.set(DATA_FUEL, Mth.clamp(litres, 0.0F, this.type.tankSize));
	}

	/** Engine condition, 0-100. */
	public int engine() {
		return this.entityData.get(DATA_ENGINE);
	}

	public void setEngine(int condition) {
		this.entityData.set(DATA_ENGINE, Mth.clamp(condition, 0, 100));
	}

	public int windows() {
		return this.type.hasWindows ? this.entityData.get(DATA_WINDOWS) : 0;
	}

	public void setWindows(int condition) {
		this.entityData.set(DATA_WINDOWS, Mth.clamp(condition, 0, 100));
	}

	public int body() {
		return this.entityData.get(DATA_BODY);
	}

	public void setBody(int condition) {
		this.entityData.set(DATA_BODY, Mth.clamp(condition, 0, 100));
		if (condition <= 0 && !this.isWreck()) {
			this.setWreck(true);
		}
	}

	/** Battery charge 0-1, or -1 without a battery. */
	public float batteryCharge() {
		return this.entityData.get(DATA_BATTERY);
	}

	public TireState tire(int wheel) {
		return TireState.values()[(this.entityData.get(DATA_TIRES) >> (wheel * 2)) & 3];
	}

	public boolean isRunning() {
		return this.flag(FLAG_RUNNING);
	}

	public boolean isLocked() {
		return this.flag(FLAG_LOCKED);
	}

	public void setLocked(boolean locked) {
		this.setFlag(FLAG_LOCKED, locked && this.type.needsKey);
	}

	public boolean hasAlarm() {
		return this.flag(FLAG_HAS_ALARM);
	}

	public void setHasAlarm(boolean alarm) {
		this.setFlag(FLAG_HAS_ALARM, alarm && this.type.isCar());
	}

	public boolean isAlarmSounding() {
		return this.flag(FLAG_ALARM);
	}

	public boolean isHotwired() {
		return this.flag(FLAG_HOTWIRED);
	}

	public boolean isWreck() {
		return this.flag(FLAG_WRECK);
	}

	public void setWreck(boolean wreck) {
		this.setFlag(FLAG_WRECK, wreck);
		if (wreck) {
			this.setEngine(0);
			this.setFlag(FLAG_RUNNING, false);
		}
	}

	public boolean isKeyInside() {
		return this.flag(FLAG_KEY_INSIDE);
	}

	public void setKeyInside(boolean inside) {
		this.setFlag(FLAG_KEY_INSIDE, inside && this.type.needsKey);
	}

	public int color() {
		return Math.floorMod(this.entityData.get(DATA_COLOR), this.type.colors.size());
	}

	public void setColor(int color) {
		this.entityData.set(DATA_COLOR, Math.floorMod(color, this.type.colors.size()));
	}

	public String colorName() {
		return this.type.colors.get(this.color());
	}

	public Component describe() {
		return VehicleComponents.VehicleKey.describe(this.type.id(), this.colorName());
	}

	public Container parts() {
		return this.parts;
	}

	public @Nullable Container trunk() {
		return this.trunk;
	}

	public ItemStack battery() {
		return this.type.hasBattery ? this.parts.getItem(this.type.batterySlot()) : ItemStack.EMPTY;
	}

	/** The tire item this vehicle takes. */
	public Item tireItem() {
		return this.type.tireSize == VehicleType.TireSize.CAR ? VehicleItems.TIRE : VehicleItems.BIKE_TIRE;
	}

	/** Puts the synced summary of the parts (tires and battery) in step with the part slots. */
	private void syncParts() {
		if (this.level().isClientSide()) {
			return;
		}

		int tires = 0;
		for (int wheel = 0; wheel < this.type.wheels; wheel++) {
			ItemStack tire = this.parts.getItem(wheel);
			TireState state;
			if (tire.isEmpty()) {
				state = TireState.MISSING;
			} else if (tire.getDamageValue() >= tire.getMaxDamage()) {
				state = TireState.FLAT;
			} else if (tire.getDamageValue() > tire.getMaxDamage() * 0.6F) {
				state = TireState.WORN;
			} else {
				state = TireState.GOOD;
			}

			tires |= state.ordinal() << (wheel * 2);
		}

		this.entityData.set(DATA_TIRES, tires);
		ItemStack battery = this.battery();
		this.entityData.set(DATA_BATTERY, battery.isEmpty() ? -1.0F : CarBatteryItem.charge(battery));
	}

	/** Fills every part slot with a new part: used for vehicles spawned in good shape. */
	public void fitNewParts() {
		for (int wheel = 0; wheel < this.type.wheels; wheel++) {
			this.parts.setItem(wheel, new ItemStack(this.tireItem()));
		}

		if (this.type.hasBattery) {
			ItemStack battery = new ItemStack(VehicleItems.CAR_BATTERY);
			CarBatteryItem.setCharge(battery, 1.0F);
			this.parts.setItem(this.type.batterySlot(), battery);
		}
	}

	// --- Driving --------------------------------------------------------------------------------

	public void setInput(boolean left, boolean right, boolean up, boolean down, boolean brake) {
		this.inputLeft = left;
		this.inputRight = right;
		this.inputUp = up;
		this.inputDown = down;
		this.inputBrake = brake;
	}

	public float speed() {
		return this.speed;
	}

	/** Slows the vehicle down after hitting something (told by the server). */
	public void impact(float keep) {
		this.speed *= keep;
	}

	public boolean canDrive() {
		if (this.isWreck()) {
			return false;
		}

		return !this.type.hasEngine || (this.isRunning() && this.fuel() > 0.0F);
	}

	/** Top speed now, from the engine, tires and ground. */
	public float topSpeed() {
		float top = this.type.maxSpeed;
		if (this.type.hasEngine) {
			top *= 0.55F + 0.45F * this.engine() / 100.0F;
		}

		for (int wheel = 0; wheel < this.type.wheels; wheel++) {
			switch (this.tire(wheel)) {
				case MISSING -> top = Math.min(top, 0.04F);
				case FLAT -> top *= 0.6F;
				case WORN -> top *= 0.95F;
				default -> {
				}
			}
		}

		BlockState ground = this.level().getBlockState(this.getBlockPosBelowThatAffectsMyMovement());
		if (!ground.isAir() && !ground.is(Vehicles.ROAD_SURFACE) && !ground.is(Vehicles.CYCLE_PATH)) {
			top *= this.type.isCar() ? 0.65F : 0.8F;
		}

		return top;
	}

	/** Flat tires on one side pull the vehicle that way: degrees per tick per block-per-tick of speed. */
	private float pull() {
		if (this.type.wheels < 4) {
			return 0.0F;
		}

		float pull = 0.0F;
		for (int wheel = 0; wheel < 4; wheel++) {
			if (this.tire(wheel) == TireState.FLAT) {
				// Wheels 0 and 2 are on the left.
				pull += wheel % 2 == 0 ? -1.5F : 1.5F;
			}
		}

		return pull;
	}

	private void drive() {
		boolean powered = this.canDrive();
		float top = this.topSpeed();
		if (this.inputUp && powered) {
			if (this.speed < 0.0F) {
				this.speed = Math.min(0.0F, this.speed + this.type.braking);
			} else if (this.speed < top) {
				float accel = this.type.acceleration * (this.type.hasEngine ? 0.6F + 0.4F * this.engine() / 100.0F : 1.0F);
				this.speed = Math.min(top, this.speed + accel);
			}
		} else if (this.inputDown) {
			if (this.speed > 0.01F) {
				this.speed = Math.max(0.0F, this.speed - this.type.braking);
			} else if (powered) {
				this.speed = Math.max(-REVERSE_SPEED, this.speed - this.type.acceleration * 0.6F);
			}
		}

		if (this.inputBrake) {
			this.speed *= 0.85F;
		}

		// Rolling resistance, and a gentle slow-down above the top speed (e.g. leaving the road).
		this.speed *= this.onGround() ? 0.988F : 0.996F;
		if (this.speed > top) {
			this.speed = Math.max(top, this.speed * 0.96F);
		}

		if (Math.abs(this.speed) < 0.002F) {
			this.speed = 0.0F;
		}

		float steer = (this.inputLeft ? 1.0F : 0.0F) - (this.inputRight ? 1.0F : 0.0F);
		float grip = Mth.clamp(Math.abs(this.speed) / 0.12F, 0.0F, 1.0F) * (1.0F - 0.35F * Math.min(1.0F, Math.abs(this.speed) / this.type.maxSpeed));
		this.deltaRotation = -steer * this.type.turnRate * grip * Math.signum(this.speed) + this.pull() * this.speed;
		this.setYRot(this.getYRot() + this.deltaRotation);
	}

	@Override
	public void tick() {
		if (this.getHurtTime() > 0) {
			this.setHurtTime(this.getHurtTime() - 1);
		}

		if (this.getDamage() > 0.0F) {
			this.setDamage(this.getDamage() - 1.0F);
		}

		super.tick();
		if (this.isLocalInstanceAuthoritative()) {
			if (this.level().isClientSide() && this.getControllingPassenger() instanceof Player) {
				this.drive();
			} else {
				// Nobody driving: roll to a stop.
				this.deltaRotation = 0.0F;
				this.speed *= 0.9F;
				if (Math.abs(this.speed) < 0.01F) {
					this.speed = 0.0F;
				}
			}

			Vec3 motion = this.getDeltaMovement();
			double fall = this.onGround() ? -0.04 : (motion.y - 0.08) * 0.98;
			if (this.isInWater()) {
				fall = Math.max(fall, -0.05);
			}

			Vec3 forward = this.forward();
			this.setDeltaMovement(forward.x * this.speed, fall, forward.z * this.speed);
			this.move(MoverType.SELF, this.getDeltaMovement());
			if (this.horizontalCollision && Math.abs(this.speed) > 0.05F) {
				if (this.level().isClientSide() && Math.abs(this.speed) > 0.25F) {
					VehicleActionPayload.crash(Math.abs(this.speed));
				}

				this.speed = 0.0F;
			}
		} else {
			this.setDeltaMovement(Vec3.ZERO);
		}

		if (this.level().isClientSide()) {
			this.animateWheels();
		} else {
			this.serverTick((ServerLevel) this.level());
		}
	}

	/** Unit vector the vehicle faces. */
	public Vec3 forward() {
		float yaw = this.getYRot() * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
	}

	/** Unit vector to the vehicle's right. */
	public Vec3 right() {
		float yaw = this.getYRot() * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.cos(yaw), 0.0, -Mth.sin(yaw));
	}

	private void animateWheels() {
		this.wheelRotationO = this.wheelRotation;
		Vec3 moved = new Vec3(this.getX() - this.xo, 0.0, this.getZ() - this.zo);
		double distance = moved.dot(this.forward());
		// Wheels are about 0.7 blocks across.
		this.wheelRotation += (float) (distance / 0.35);
	}

	// --- Server ---------------------------------------------------------------------------------

	private void serverTick(ServerLevel level) {
		Vec3 pos = this.position();
		float moved = 0.0F;
		if (this.lastServerPos != null) {
			moved = (float) Math.sqrt(Math.pow(pos.x - this.lastServerPos.x, 2) + Math.pow(pos.z - this.lastServerPos.z, 2));
			if (moved > 4.0F) {
				// Teleported.
				moved = 0.0F;
			}
		}

		this.lastServerPos = pos;
		this.measuredSpeed = this.measuredSpeed * 0.6F + moved * 0.4F;
		if (this.actionCooldown > 0) {
			this.actionCooldown--;
		}

		if (this.type.hasEngine && this.isRunning()) {
			this.setFuel(this.fuel() - IDLE_CONSUMPTION - this.type.consumption * moved);
			ItemStack battery = this.battery();
			if (!battery.isEmpty() && this.tickCount % 20 == 0) {
				CarBatteryItem.setCharge(battery, CarBatteryItem.charge(battery) + 0.01F);
				this.syncParts();
			}

			if (this.fuel() <= 0.0F) {
				this.stopEngine(Component.translatable("outbreak.vehicle.out_of_fuel"));
			} else if (this.isEyeInFluid(FluidTags.WATER) || this.isUnderWater()) {
				this.setEngine(this.engine() - 15);
				this.stopEngine(Component.translatable("outbreak.vehicle.flooded"));
			} else if (this.engine() < 20 && this.random.nextInt(900) == 0) {
				this.stopEngine(Component.translatable("outbreak.vehicle.stalled"));
			}

			if (this.tickCount % 20 == 0) {
				float load = Math.min(1.0F, this.measuredSpeed / this.type.maxSpeed * 1.5F);
				NoiseSystem.emit(level, this.position(), Mth.lerp(load, this.type.idleNoise, this.type.drivingNoise), this.getControllingPassenger());
			}
		} else if (!this.type.hasEngine && this.measuredSpeed > 0.1F && this.tickCount % 40 == 0) {
			NoiseSystem.emit(level, this.position(), this.type.drivingNoise, this.getControllingPassenger());
		}

		this.wearTires(moved);
		this.hitEntities(level);
		this.tickAlarm(level);
	}

	private void wearTires(float moved) {
		this.distanceSinceWear += moved;
		float perPercent = 1.0F / TIRE_WEAR_PER_BLOCK;
		if (this.distanceSinceWear < perPercent) {
			return;
		}

		this.distanceSinceWear -= perPercent;
		int wheel = this.random.nextInt(this.type.wheels);
		ItemStack tire = this.parts.getItem(wheel);
		if (!tire.isEmpty() && tire.getDamageValue() < tire.getMaxDamage()) {
			tire.setDamageValue(tire.getDamageValue() + 1);
			this.parts.setChanged();
		}
	}

	/** Walkers (and anything else) in the way get run over; the vehicle takes some of it. */
	private void hitEntities(ServerLevel level) {
		this.recentlyHit.replaceAll((uuid, ticks) -> ticks - 1);
		this.recentlyHit.values().removeIf(ticks -> ticks <= 0);
		if (this.measuredSpeed < 0.12F) {
			return;
		}

		float mass = switch (this.type) {
			case BICYCLE -> 0.3F;
			case SCOOTER -> 0.5F;
			case VAN -> 1.3F;
			default -> 1.0F;
		};
		AABB box = this.getBoundingBox().inflate(0.2, 0.0, 0.2).expandTowards(this.forward().scale(0.6));
		List<LivingEntity> hit = level.getEntitiesOfClass(LivingEntity.class, box,
			entity -> entity.isAlive() && !entity.isPassenger() && !entity.isSpectator() && !this.recentlyHit.containsKey(entity.getUUID()));
		for (LivingEntity entity : hit) {
			this.recentlyHit.put(entity.getUUID(), 10);
			LivingEntity driver = this.getControllingPassenger();
			DamageSource source = this.damageSources().source(OutbreakDamageTypes.RUN_OVER, this, driver);
			entity.hurtServer(level, source, this.measuredSpeed * 40.0F * mass);
			Vec3 push = this.forward().scale(this.measuredSpeed * 1.5F * mass);
			entity.push(push.x, 0.3 * mass, push.z);
			this.setBody(this.body() - Math.max(1, Math.round(this.measuredSpeed * 4.0F / mass)));
			level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.NEUTRAL, 1.0F, 0.6F);
			NoiseSystem.emit(level, entity.position(), 16.0F, driver);
			if (driver instanceof ServerPlayer player) {
				VehicleImpactPayload.send(player, this.type.isCar() ? 0.85F : 0.5F);
			}

			// On two wheels, hitting someone at speed throws you off.
			if (!this.type.isCar() && this.measuredSpeed > 0.25F && driver != null) {
				driver.stopRiding();
				driver.hurtServer(level, this.damageSources().flyIntoWall(), 3.0F);
			}
		}
	}

	/** The driver's client says it hit a wall at this speed (blocks per tick). */
	public void crash(ServerPlayer driver, float impact) {
		impact = Math.min(impact, this.type.maxSpeed);
		if (impact < 0.25F || this.getControllingPassenger() != driver) {
			return;
		}

		ServerLevel level = (ServerLevel) this.level();
		this.setBody(this.body() - Math.round((impact - 0.2F) * 60.0F));
		if (impact > 0.3F && this.type.hasEngine) {
			this.setEngine(this.engine() - Math.round((impact - 0.3F) * 40.0F));
		}

		if (impact > 0.35F && this.type.hasWindows) {
			this.setWindows(this.windows() - Math.round((impact - 0.35F) * 80.0F));
		}

		if (impact > 0.4F || !this.type.isCar()) {
			float hurt = this.type.isCar() ? (impact - 0.4F) * 30.0F : impact * 12.0F;
			for (Entity passenger : List.copyOf(this.getPassengers())) {
				passenger.hurtServer(level, this.damageSources().flyIntoWall(), hurt);
				if (!this.type.isCar()) {
					passenger.stopRiding();
				}
			}
		}

		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, Math.min(1.0F, impact * 1.5F), 0.6F);
		NoiseSystem.emit(level, this.position(), 40.0F, driver);
		this.soundAlarm();
	}

	private void tickAlarm(ServerLevel level) {
		if (this.alarmTicks <= 0) {
			return;
		}

		this.alarmTicks--;
		if (this.alarmTicks % 8 == 0) {
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.NEUTRAL,
				3.0F, this.alarmTicks % 16 == 0 ? 1.4F : 1.0F);
		}

		if (this.alarmTicks % 20 == 0) {
			NoiseSystem.emit(level, this.position(), 72.0F);
		}

		if (this.alarmTicks == 0) {
			this.setFlag(FLAG_ALARM, false);
		}
	}

	/** A car with an alarm starts screaming. A free herd magnet. */
	public void soundAlarm() {
		if (this.hasAlarm() && this.alarmTicks <= 0 && this.batteryCharge() > 0.05F) {
			this.alarmTicks = ALARM_TICKS;
			this.setFlag(FLAG_ALARM, true);
		}
	}

	private void stopEngine(@Nullable Component reason) {
		this.setFlag(FLAG_RUNNING, false);
		if (reason != null && this.getControllingPassenger() instanceof Player driver) {
			driver.sendOverlayMessage(reason);
		}
	}

	/** The driver turns the key (or the wires): start the engine, or switch it off. */
	public void toggleEngine(ServerPlayer player) {
		if (!this.type.hasEngine || this.getControllingPassenger() != player || this.actionCooldown > 0) {
			return;
		}

		this.actionCooldown = 15;
		ServerLevel level = (ServerLevel) this.level();
		if (this.isRunning()) {
			this.stopEngine(null);
			return;
		}

		if (CarKeyItem.find(player, this) == null && !this.isKeyInside() && !this.isHotwired()) {
			ItemStack held = player.getMainHandItem();
			if (!held.is(VehicleItems.SCREWDRIVER)) {
				player.sendOverlayMessage(Component.translatable("outbreak.vehicle.no_key"));
				return;
			}

			held.hurtAndBreak(4, player, InteractionHand.MAIN_HAND.asEquipmentSlot());
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.TRIPWIRE_CLICK_ON, SoundSource.NEUTRAL, 1.0F, 1.2F);
			if (this.random.nextFloat() >= 0.4F) {
				player.sendOverlayMessage(Component.translatable("outbreak.vehicle.hotwire_failed"));
				if (this.random.nextFloat() < 0.4F) {
					this.soundAlarm();
				}

				return;
			}

			this.setFlag(FLAG_HOTWIRED, true);
			player.sendOverlayMessage(Component.translatable("outbreak.vehicle.hotwired"));
		}

		ItemStack battery = this.battery();
		if (battery.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("outbreak.vehicle.no_battery"));
			return;
		}

		float charge = CarBatteryItem.charge(battery);
		if (charge < MIN_START_CHARGE) {
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.NEUTRAL, 1.0F, 0.6F);
			player.sendOverlayMessage(Component.translatable("outbreak.vehicle.battery_dead"));
			return;
		}

		CarBatteryItem.setCharge(battery, charge - BATTERY_PER_START);
		this.syncParts();
		NoiseSystem.emit(level, this.position(), this.type.drivingNoise, player);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.MINECART_RIDING, SoundSource.NEUTRAL, 0.8F, 0.5F);
		if (this.engine() <= 0) {
			player.sendOverlayMessage(Component.translatable("outbreak.vehicle.engine_dead"));
			return;
		}

		if (this.fuel() <= 0.0F) {
			player.sendOverlayMessage(Component.translatable("outbreak.vehicle.cranks_no_fuel"));
			return;
		}

		float chance = this.engine() >= 50 ? 1.0F : 0.25F + 0.75F * this.engine() / 50.0F;
		if (charge < 0.25F) {
			chance *= 0.6F;
		}

		if (this.random.nextFloat() < chance) {
			this.setFlag(FLAG_RUNNING, true);
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.PISTON_EXTEND, SoundSource.NEUTRAL, 1.0F, 0.5F);
		} else {
			player.sendOverlayMessage(Component.translatable("outbreak.vehicle.sputters"));
		}
	}

	public void honk(ServerPlayer player) {
		if (this.getControllingPassenger() != player || this.actionCooldown > 0) {
			return;
		}

		this.actionCooldown = 10;
		ServerLevel level = (ServerLevel) this.level();
		if (!this.type.hasEngine) {
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.NEUTRAL, 1.0F, 1.8F);
			NoiseSystem.emit(level, this.position(), 16.0F, player);
		} else if (this.batteryCharge() > 0.05F) {
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), SoundSource.NEUTRAL, 3.0F, 1.2F);
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.NEUTRAL, 3.0F, 1.0F);
			NoiseSystem.emit(level, this.position(), 56.0F, player);
		}
	}

	/** A walker hits a player sitting in this vehicle: the windows take it, as long as there are any. */
	public boolean absorbHit(float damage) {
		if (this.windows() <= 0) {
			return false;
		}

		this.setWindows(this.windows() - Math.max(3, Math.round(damage * 3.0F)));
		SoundEvent sound = this.windows() <= 0 ? SoundEvents.GLASS_BREAK : SoundEvents.GLASS_HIT;
		this.level().playSound(null, this.getX(), this.getY() + 1.0, this.getZ(), sound, SoundSource.NEUTRAL, 1.0F, 1.0F);
		this.soundAlarm();
		return true;
	}

	// --- Interaction ----------------------------------------------------------------------------

	/** Whether this player can get in or open the trunk: unlocked, their key, or a broken window. */
	public boolean canEnter(Player player) {
		return !this.isLocked() || CarKeyItem.find(player, this) != null || (this.type.hasWindows && this.windows() <= 0);
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (player.isSpectator()) {
			return InteractionResult.PASS;
		}

		if (this.level().isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		ServerPlayer serverPlayer = (ServerPlayer) player;
		ItemStack held = player.getItemInHand(hand);
		if (held.getItem() instanceof JerrycanItem) {
			Fuelling.pour(serverPlayer, this, held);
			return InteractionResult.SUCCESS;
		}

		if (held.is(VehicleItems.HOSE)) {
			Fuelling.siphon(serverPlayer, this);
			return InteractionResult.SUCCESS;
		}

		// With its key in hand: a locked car unlocks; sneaking, it locks. Otherwise the key is just in your hand.
		if (CarKeyItem.fits(held, this) && (this.isLocked() || player.isSecondaryUseActive())) {
			this.setLocked(!this.isLocked());
			this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.NEUTRAL, 0.6F, 1.6F);
			player.sendOverlayMessage(Component.translatable(this.isLocked() ? "outbreak.vehicle.locked" : "outbreak.vehicle.unlocked"));
			return InteractionResult.SUCCESS;
		}

		boolean partInHand = held.is(this.tireItem()) || held.is(VehicleItems.CAR_BATTERY) || held.is(VehicleItems.ENGINE_PARTS);
		if (player.isSecondaryUseActive() || partInHand) {
			MechanicsMenu.open(serverPlayer, this);
			return InteractionResult.SUCCESS;
		}

		// The back end of a car is the trunk.
		double along = location.dot(this.forward());
		if (this.trunk != null && along < -this.getBbWidth() * 0.25) {
			if (!this.canEnter(player)) {
				player.sendOverlayMessage(Component.translatable("outbreak.vehicle.is_locked"));
				return InteractionResult.SUCCESS;
			}

			ContainerKind kind = this.type.trunk;
			OutbreakMenus.open(player, kind, this.trunk, kind.title());
			this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.CHEST_OPEN, SoundSource.NEUTRAL, 0.5F, 0.8F);
			return InteractionResult.SUCCESS;
		}

		if (!this.canEnter(player)) {
			player.sendOverlayMessage(Component.translatable("outbreak.vehicle.is_locked"));
			if (this.type.hasWindows) {
				this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.NEUTRAL, 0.4F, 1.6F);
			}

			return InteractionResult.SUCCESS;
		}

		if (player.startRiding(this)) {
			if (this.getControllingPassenger() == player && this.type.hasEngine && !this.isRunning()) {
				player.sendOverlayMessage(Component.translatable("outbreak.vehicle.how_to_start",
					Component.keybind("key.outbreak.engine")));
			}

			return InteractionResult.SUCCESS;
		}

		return InteractionResult.PASS;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (this.isRemoved() || this.isInvulnerableToBase(source)) {
			return false;
		}

		this.setHurtDir(-this.getHurtDir());
		this.setHurtTime(10);
		this.markHurt();
		Entity attacker = source.getEntity();
		if (attacker instanceof Player player && player.getAbilities().instabuild && player.isSecondaryUseActive()) {
			this.discard();
			return true;
		}

		if (attacker instanceof Player player && source.getDirectEntity() == player) {
			if (this.type == VehicleType.BICYCLE) {
				// Punching a bicycle picks it up (onto the ground if your pockets are full).
				ItemStack bicycle = BicycleItem.pickUp(this);
				if (!player.getInventory().add(bicycle)) {
					this.spawnAtLocation(level, bicycle);
				}

				this.discard();
				return true;
			}

			if (this.type.hasWindows && this.windows() > 0) {
				this.setWindows(this.windows() - 34);
				level.playSound(null, this.getX(), this.getY() + 1.0, this.getZ(),
					this.windows() <= 0 ? SoundEvents.GLASS_BREAK : SoundEvents.GLASS_HIT, SoundSource.NEUTRAL, 1.0F, 1.0F);
				NoiseSystem.emit(level, this.position(), 24.0F, player);
				this.soundAlarm();
				return true;
			}
		}

		this.setBody(this.body() - Math.max(1, Math.round(damage / 2.0F)));
		return true;
	}

	@Override
	public void remove(RemovalReason reason) {
		if (!this.level().isClientSide() && reason.shouldDestroy() && this.level() instanceof ServerLevel level
			&& level.getGameRules().get(GameRules.ENTITY_DROPS)) {
			Containers.dropContents(level, this, this.parts);
			if (this.trunk != null) {
				Containers.dropContents(level, this, this.trunk);
			}
		}

		super.remove(reason);
	}

	@Override
	protected Item getDropItem() {
		return Items.AIR;
	}

	@Override
	public ItemStack getPickResult() {
		return new ItemStack(Vehicles.spawnItem(this.type));
	}

	// --- Passengers -----------------------------------------------------------------------------

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return this.getPassengers().size() < this.type.seats.size() && !this.isWreck();
	}

	@Override
	public @Nullable LivingEntity getControllingPassenger() {
		return this.getFirstPassenger() instanceof Player player ? player : null;
	}

	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
		int index = Math.max(0, this.getPassengers().indexOf(passenger));
		Vec3 seat = this.type.seats.get(Math.min(index, this.type.seats.size() - 1));
		return this.right().scale(seat.x).add(this.forward().scale(seat.z)).add(0.0, seat.y, 0.0);
	}

	@Override
	protected void positionRider(Entity passenger, MoveFunction moveFunction) {
		super.positionRider(passenger, moveFunction);
		if (passenger.isLocalInstanceAuthoritative()) {
			passenger.setYRot(passenger.getYRot() + this.deltaRotation);
			passenger.setYHeadRot(passenger.getYHeadRot() + this.deltaRotation);
		}

		this.clampRotation(passenger);
	}

	@Override
	public void onPassengerTurned(Entity passenger) {
		passenger.yRotO += this.clampRotation(passenger);
	}

	private float clampRotation(Entity passenger) {
		passenger.setYBodyRot(this.getYRot());
		float delta = Mth.wrapDegrees(passenger.getYRot() - this.getYRot());
		float clamped = Mth.clamp(delta, -120.0F, 120.0F);
		passenger.setYRot(passenger.getYRot() + clamped - delta);
		return clamped - delta;
	}

	@Override
	protected void addPassenger(Entity passenger) {
		super.addPassenger(passenger);
		// Sit down facing forward.
		passenger.setYRot(this.getYRot());
		passenger.setYHeadRot(this.getYRot());
		passenger.yRotO = this.getYRot();
	}

	@Override
	protected void removePassenger(Entity passenger) {
		super.removePassenger(passenger);
		if (!this.level().isClientSide()) {
			this.speed = this.measuredSpeed;
		}
	}

	@Override
	public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
		// Out of the door on the passenger's side.
		Vec3 seat = this.getPassengerAttachmentPoint(passenger, this.getDimensions(this.getPose()), 1.0F);
		Vec3 side = this.right().scale(Math.signum(seat.dot(this.right())) == 0 ? -1.0 : Math.signum(seat.dot(this.right())));
		for (double distance : new double[] {this.getBbWidth() * 0.5 + 0.6, this.getBbWidth() * 0.5 + 1.2}) {
			for (Vec3 direction : new Vec3[] {side, side.scale(-1.0), this.forward(), this.forward().scale(-1.0)}) {
				Vec3 target = this.position().add(direction.scale(distance));
				BlockPos pos = BlockPos.containing(target.x, this.getBoundingBox().maxY, target.z);
				for (int down = 0; down < 3; down++) {
					BlockPos at = pos.below(down);
					double floor = this.level().getBlockFloorHeight(at);
					if (DismountHelper.isBlockFloorValid(floor)) {
						Vec3 spot = new Vec3(target.x, at.getY() + floor, target.z);
						if (DismountHelper.canDismountTo(this.level(), spot, passenger, Pose.STANDING)) {
							return spot;
						}
					}
				}
			}
		}

		return super.getDismountLocationForPassenger(passenger);
	}

	// --- Physics plumbing -----------------------------------------------------------------------

	@Override
	public float maxUpStep() {
		return 1.0F;
	}

	@Override
	protected InterpolationHandler createInterpolationHandler() {
		return LinearInterpolationHandler.create(this, 3);
	}

	/** Blocked by solid things (other vehicles, boats); people and walkers get run over instead (see hitEntities). */
	@Override
	public boolean canCollideWith(Entity entity) {
		return entity.canBeCollidedWith(this) && !this.isPassengerOfSameVehicle(entity);
	}

	@Override
	public boolean canBeCollidedWith(@Nullable Entity other) {
		return true;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean isPickable() {
		return !this.isRemoved();
	}

	@Override
	public void push(Entity entity) {
		if (!(entity instanceof Vehicle) && !entity.hasPassenger(this) && !this.hasPassenger(entity)) {
			// Bump people out of the way rather than being pushed around by them.
			Vec3 away = entity.position().subtract(this.position()).multiply(1.0, 0.0, 1.0);
			if (away.lengthSqr() > 1.0E-4) {
				entity.push(away.normalize().scale(0.05));
			}
		}
	}

	@Override
	protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {
		if (onGround) {
			this.resetFallDistance();
		}
	}

	@Override
	protected double getDefaultGravity() {
		return 0.08;
	}

	// --- Saving ---------------------------------------------------------------------------------

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.putFloat("Fuel", this.fuel());
		output.putInt("Engine", this.engine());
		output.putInt("Windows", this.entityData.get(DATA_WINDOWS));
		output.putInt("Body", this.body());
		output.putInt("Color", this.color());
		output.putByte("Flags", (byte) (this.entityData.get(DATA_FLAGS) & ~FLAG_ALARM));
		ContainerHelper.saveAllItems(output.child("Parts"), stacks(this.parts), true);
		if (this.trunk != null) {
			ContainerHelper.saveAllItems(output.child("Trunk"), stacks(this.trunk), true);
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		this.setFuel(input.getFloatOr("Fuel", 0.0F));
		this.setEngine(input.getIntOr("Engine", 100));
		this.setWindows(input.getIntOr("Windows", 100));
		this.entityData.set(DATA_BODY, Mth.clamp(input.getIntOr("Body", 100), 0, 100));
		this.setColor(input.getIntOr("Color", 0));
		this.entityData.set(DATA_FLAGS, input.getByteOr("Flags", (byte) 0));
		NonNullList<ItemStack> partStacks = NonNullList.withSize(this.parts.getContainerSize(), ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input.childOrEmpty("Parts"), partStacks);
		for (int i = 0; i < partStacks.size(); i++) {
			this.parts.setItem(i, partStacks.get(i));
		}

		if (this.trunk != null) {
			NonNullList<ItemStack> trunkStacks = NonNullList.withSize(this.trunk.getContainerSize(), ItemStack.EMPTY);
			ContainerHelper.loadAllItems(input.childOrEmpty("Trunk"), trunkStacks);
			for (int i = 0; i < trunkStacks.size(); i++) {
				this.trunk.setItem(i, trunkStacks.get(i));
			}
		}

		this.syncParts();
	}

	private static NonNullList<ItemStack> stacks(SimpleContainer container) {
		NonNullList<ItemStack> stacks = NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
		for (int i = 0; i < stacks.size(); i++) {
			stacks.set(i, container.getItem(i));
		}

		return stacks;
	}

	/** The trunk: open only while you're next to the vehicle. */
	private static final class VehicleContainer extends SimpleContainer {
		private final Vehicle vehicle;

		VehicleContainer(Vehicle vehicle, int size) {
			super(size);
			this.vehicle = vehicle;
		}

		@Override
		public boolean stillValid(Player player) {
			return this.vehicle.isAlive() && player.distanceToSqr(this.vehicle) < 64.0;
		}
	}
}
