package io.github.ditsjohann.outbreak.gore;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * A severed arm, leg or head, in the walker's own skin: thrown off by the blow, it tumbles, lands
 * (leaving blood where it does) and lies there for five minutes. Hitting it knocks it about.
 */
public class BodyPart extends Entity {
	private static final EntityDataAccessor<Integer> DATA_LIMB = SynchedEntityData.defineId(BodyPart.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_SKIN = SynchedEntityData.defineId(BodyPart.class, EntityDataSerializers.INT);
	private static final int LIFETIME = 20 * 60 * 5;

	private int age;
	private boolean landed;
	/** Client side: how far it has turned over in the air (degrees), for the tumble. */
	public float tumble;
	public float tumbleO;

	public BodyPart(EntityType<? extends BodyPart> type, Level level) {
		super(type, level);
	}

	public static void spawn(ServerLevel level, EntityType<BodyPart> type, Limb limb, int skin, Vec3 pos, Vec3 velocity, float yaw) {
		BodyPart part = type.create(level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
		if (part == null) {
			return;
		}

		part.entityData.set(DATA_LIMB, limb.ordinal());
		part.entityData.set(DATA_SKIN, skin);
		part.snapTo(pos.x, pos.y, pos.z, yaw, 0.0F);
		part.setDeltaMovement(velocity);
		level.addFreshEntity(part);
	}

	public Limb limb() {
		return Limb.byOrdinal(this.entityData.get(DATA_LIMB));
	}

	public int skin() {
		return this.entityData.get(DATA_SKIN);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_LIMB, 0);
		builder.define(DATA_SKIN, 0);
	}

	@Override
	public void tick() {
		super.tick();
		Vec3 motion = this.getDeltaMovement();
		if (!this.onGround()) {
			motion = motion.add(0.0, -0.045, 0.0);
		}

		this.move(MoverType.SELF, motion);
		motion = this.getDeltaMovement();
		if (this.onGround()) {
			motion = motion.multiply(0.55, 0.0, 0.55);
			if (!this.landed && this.level() instanceof ServerLevel level) {
				this.landed = true;
				Blood.splat(level, this.position(), this.limb() == Limb.HEAD || this.limb().lower != null ? 1 : 0);
			}
		} else {
			motion = motion.scale(0.98);
		}

		this.setDeltaMovement(motion);
		if (this.level().isClientSide()) {
			this.tumbleO = this.tumble;
			if (!this.onGround()) {
				this.tumble += (float) motion.length() * 120.0F;
			} else {
				// Comes to rest on its side.
				this.tumble = Math.round(this.tumble / 180.0F) * 180.0F + 90.0F * (float) Math.signum(Math.sin(Math.toRadians(this.tumble)));
			}
		} else if (++this.age > LIFETIME) {
			this.discard();
		}
	}

	@Override
	public boolean isPickable() {
		return this.isAlive();
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		Entity attacker = source.getDirectEntity();
		if (attacker != null) {
			// Kicked about.
			Vec3 away = this.position().subtract(attacker.position()).multiply(1.0, 0.0, 1.0).normalize();
			this.setDeltaMovement(away.scale(0.35).add(0.0, 0.2, 0.0));
			this.landed = false;
			Blood.spray(level, this.position().add(0.0, 0.1, 0.0), away, 4, 0.2F);
		}

		return true;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.putInt("limb", this.entityData.get(DATA_LIMB));
		output.putInt("skin", this.skin());
		output.putInt("age", this.age);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		this.entityData.set(DATA_LIMB, input.getIntOr("limb", 0));
		this.entityData.set(DATA_SKIN, input.getIntOr("skin", 0));
		this.age = input.getIntOr("age", 0);
		this.landed = true;
	}
}
