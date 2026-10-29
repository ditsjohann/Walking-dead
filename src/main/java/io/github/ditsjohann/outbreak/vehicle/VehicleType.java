package io.github.ditsjohann.outbreak.vehicle;

import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import io.github.ditsjohann.outbreak.inventory.ContainerKind;

import net.minecraft.world.phys.Vec3;

/**
 * Everything that differs between vehicles. Speeds are in blocks per tick (1 block/tick = 72 km/h),
 * fuel in litres, sizes in blocks. Seats are (right, up, forward) from the vehicle's centre; the first
 * seat is the driver's (on the left: the Dutch drive on the right).
 */
public enum VehicleType {
	BICYCLE(new Builder(2, 1.0F, 1.4F).tireItem(TireSize.BIKE).speed(0.36F, 0.010F, 0.05F).turn(7.0F)
		.noise(0.0F, 3.0F).length(1.8F).seats(seat(0, 0.95, -0.15))
		.colors("black", "red", "blue", "green")),
	SCOOTER(new Builder(2, 1.0F, 1.4F).tireItem(TireSize.BIKE).speed(0.55F, 0.014F, 0.05F).turn(5.5F)
		.engine(6.0F, 0.0025F, true).noise(10.0F, 22.0F).length(1.9F).seats(seat(0, 0.75, -0.1), seat(0, 0.8, -0.65))
		.colors("red", "white", "black", "cream")),
	HATCHBACK(new Builder(4, 2.3F, 1.7F).speed(0.85F, 0.011F, 0.045F).turn(4.2F)
		.engine(40.0F, 0.007F, true).windows().trunk(ContainerKind.TRUNK).noise(16.0F, 28.0F).length(3.6F)
		.seats(seat(-0.42, 0.35, 0.25), seat(0.42, 0.35, 0.25), seat(-0.42, 0.35, -0.65), seat(0.42, 0.35, -0.65))
		.colors("red", "blue", "white", "silver", "black", "green")),
	SEDAN(new Builder(4, 2.4F, 1.7F).speed(0.95F, 0.012F, 0.045F).turn(3.8F)
		.engine(50.0F, 0.008F, true).windows().trunk(ContainerKind.LARGE_TRUNK).noise(16.0F, 28.0F).length(4.2F)
		.seats(seat(-0.42, 0.35, 0.35), seat(0.42, 0.35, 0.35), seat(-0.42, 0.35, -0.55), seat(0.42, 0.35, -0.55))
		.colors("silver", "black", "blue", "red", "white", "green")),
	VAN(new Builder(4, 2.6F, 2.2F).speed(0.75F, 0.008F, 0.04F).turn(3.4F)
		.engine(70.0F, 0.011F, true).windows().trunk(ContainerKind.VAN_CARGO).noise(18.0F, 32.0F).length(4.6F)
		.seats(seat(-0.45, 0.6, 1.0), seat(0.45, 0.6, 1.0))
		.colors("white", "silver", "blue", "red"));

	/** Car tires and the small tires of bicycles and scooters are different items. */
	public enum TireSize {
		CAR,
		BIKE
	}

	public final int wheels;
	public final float width;
	public final float height;
	public final float length;
	public final TireSize tireSize;
	public final float maxSpeed;
	public final float acceleration;
	public final float braking;
	public final float turnRate;
	public final boolean hasEngine;
	public final float tankSize;
	/** Litres per block driven. */
	public final float consumption;
	public final boolean needsKey;
	public final boolean hasBattery;
	public final boolean hasWindows;
	public final @Nullable ContainerKind trunk;
	public final float idleNoise;
	public final float drivingNoise;
	public final List<Vec3> seats;
	public final List<String> colors;

	VehicleType(Builder builder) {
		this.wheels = builder.wheels;
		this.width = builder.width;
		this.height = builder.height;
		this.length = builder.length;
		this.tireSize = builder.tireSize;
		this.maxSpeed = builder.maxSpeed;
		this.acceleration = builder.acceleration;
		this.braking = builder.braking;
		this.turnRate = builder.turnRate;
		this.hasEngine = builder.tankSize > 0.0F;
		this.tankSize = builder.tankSize;
		this.consumption = builder.consumption;
		this.needsKey = builder.needsKey;
		this.hasBattery = this.hasEngine;
		this.hasWindows = builder.windows;
		this.trunk = builder.trunk;
		this.idleNoise = builder.idleNoise;
		this.drivingNoise = builder.drivingNoise;
		this.seats = builder.seats;
		this.colors = builder.colors;
	}

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	public boolean isCar() {
		return this.wheels == 4;
	}

	/** Part slots: one per wheel, then the battery. */
	public int partSlots() {
		return this.wheels + (this.hasBattery ? 1 : 0);
	}

	public int batterySlot() {
		return this.hasBattery ? this.wheels : -1;
	}

	private static Vec3 seat(double right, double up, double forward) {
		return new Vec3(right, up, forward);
	}

	private static final class Builder {
		private final int wheels;
		private final float width;
		private final float height;
		private float length;
		private TireSize tireSize = TireSize.CAR;
		private float maxSpeed;
		private float acceleration;
		private float braking;
		private float turnRate;
		private float tankSize;
		private float consumption;
		private boolean needsKey;
		private boolean windows;
		private @Nullable ContainerKind trunk;
		private float idleNoise;
		private float drivingNoise;
		private List<Vec3> seats = List.of();
		private List<String> colors = List.of();

		Builder(int wheels, float width, float height) {
			this.wheels = wheels;
			this.width = width;
			this.height = height;
		}

		Builder tireItem(TireSize size) {
			this.tireSize = size;
			return this;
		}

		Builder speed(float max, float acceleration, float braking) {
			this.maxSpeed = max;
			this.acceleration = acceleration;
			this.braking = braking;
			return this;
		}

		Builder turn(float degreesPerTick) {
			this.turnRate = degreesPerTick;
			return this;
		}

		Builder engine(float tank, float litresPerBlock, boolean needsKey) {
			this.tankSize = tank;
			this.consumption = litresPerBlock;
			this.needsKey = needsKey;
			return this;
		}

		Builder windows() {
			this.windows = true;
			return this;
		}

		Builder trunk(ContainerKind kind) {
			this.trunk = kind;
			return this;
		}

		Builder noise(float idle, float driving) {
			this.idleNoise = idle;
			this.drivingNoise = driving;
			return this;
		}

		Builder length(float length) {
			this.length = length;
			return this;
		}

		Builder seats(Vec3... seats) {
			this.seats = List.of(seats);
			return this;
		}

		Builder colors(String... colors) {
			this.colors = List.of(colors);
			return this;
		}
	}
}
