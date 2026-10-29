package io.github.ditsjohann.outbreak.entity;

import java.util.Optional;
import java.util.UUID;

import io.github.ditsjohann.outbreak.entity.ai.FollowHerdLeaderGoal;
import io.github.ditsjohann.outbreak.entity.ai.InvestigateNoiseGoal;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreakDoorGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.ZombieAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.tags.DamageTypeTags;

/**
 * A walker: slow, doesn't burn, only dies from a head hit, hunts by sound first and sight second,
 * and drifts around in herds. Extends the vanilla zombie so it can reuse the zombie model and
 * sounds until it gets its own.
 */
public class Walker extends Zombie {
	/** Fraction of the walker's height above which a hit counts as a headshot. */
	private static final double HEAD_START = 0.78;
	private static final float HEADSHOT_MULTIPLIER = 4.0F;
	private static final float BODY_MULTIPLIER = 0.5F;
	/** Cosine of the half-angle of a walker's field of view (~65 degrees). */
	private static final double VIEW_CONE_COS = 0.42;
	/** How long a heard noise stays interesting, in ticks. */
	private static final int NOISE_MEMORY = 20 * 30;

	private @Nullable Vec3 noiseTarget;
	private float noiseLoudness;
	private long noiseHeardAt;
	private int noiseVersion;
	private @Nullable UUID leaderId;

	public Walker(EntityType<? extends Walker> type, Level level) {
		super(type, level);
		this.getNavigation().setCanOpenDoors(true);
		this.xpReward = 0;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, 20.0)
			.add(Attributes.FOLLOW_RANGE, 32.0)
			.add(Attributes.MOVEMENT_SPEED, 0.18F)
			.add(Attributes.ATTACK_DAMAGE, 3.0)
			.add(Attributes.ARMOR, 0.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 0.1)
			.add(Attributes.SPAWN_REINFORCEMENTS_CHANCE, 0.0);
	}

	public static boolean checkWalkerSpawnRules(EntityType<? extends Mob> type, LevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
		// Unlike vanilla monsters, walkers don't care about light: they're out in broad daylight too.
		return level.getDifficulty() != Difficulty.PEACEFUL && Mob.checkMobSpawnRules(type, level, reason, pos, random);
	}

	@Override
	public float getWalkTargetValue(BlockPos pos, LevelReader level) {
		// Monsters prefer (and only spawn in) the dark. Walkers don't care.
		return 0.0F;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(1, new BreakDoorGoal(this, difficulty -> true));
		this.goalSelector.addGoal(2, new ZombieAttackGoal(this, 1.15, false));
		this.goalSelector.addGoal(4, new InvestigateNoiseGoal(this, 1.0));
		this.goalSelector.addGoal(5, new FollowHerdLeaderGoal(this, 0.9));
		this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.7));
		this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers(Walker.class));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 5, true, false, (target, level) -> this.canSpot(target)));
	}

	/** Sight check: range depends on how bright it is where the target stands, crouching halves it, and walkers only see what's in front of them. */
	private boolean canSpot(LivingEntity target) {
		double range = 6.0 + this.level().getMaxLocalRawBrightness(target.blockPosition());
		if (target.isCrouching()) {
			range *= 0.5;
		}

		double distSqr = this.distanceToSqr(target);
		if (distSqr > range * range) {
			return false;
		}

		if (distSqr < 9.0) {
			return true;
		}

		Vec3 toTarget = target.getEyePosition().subtract(this.getEyePosition()).normalize();
		return this.getViewVector(1.0F).dot(toTarget) >= VIEW_CONE_COS;
	}

	// --- Damage: only the head counts ---

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		Entity direct = source.getDirectEntity();
		boolean isAttack = direct != null && direct != this && !source.is(DamageTypeTags.IS_EXPLOSION);
		if (isAttack && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			if (this.isHeadshot(direct)) {
				damage *= HEADSHOT_MULTIPLIER;
			} else {
				// Body hits stagger but can never finish a walker off.
				damage = Math.min(damage * BODY_MULTIPLIER, this.getHealth() - 1.0F);
				this.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 1));
				if (damage <= 0.0F) {
					damage = 0.01F;
				}
			}
		}

		return super.hurtServer(level, source, damage);
	}

	private boolean isHeadshot(Entity direct) {
		double headY = this.getY() + this.getBbHeight() * HEAD_START;
		if (direct instanceof Projectile) {
			return direct.getBoundingBox().getCenter().y >= headY;
		}

		if (direct instanceof LivingEntity attacker) {
			Vec3 eye = attacker.getEyePosition();
			Vec3 end = eye.add(attacker.getViewVector(1.0F).scale(8.0));
			Optional<Vec3> hit = this.getBoundingBox().inflate(0.1).clip(eye, end);
			return hit.isPresent() && hit.get().y >= headY;
		}

		return false;
	}

	// --- Hearing ---

	/** Called by the noise system when this walker hears something. Louder or newer noises win. */
	public void hearNoise(Vec3 pos, float loudness) {
		LivingEntity target = this.getTarget();
		if (target != null && target.isAlive() && this.hasLineOfSight(target)) {
			return;
		}

		long now = this.level().getGameTime();
		boolean stale = this.noiseTarget == null || now - this.noiseHeardAt > 20 * 5;
		if (stale || loudness >= this.noiseLoudness) {
			this.noiseTarget = pos;
			this.noiseLoudness = loudness;
			this.noiseHeardAt = now;
			this.noiseVersion++;
		}
	}

	public @Nullable Vec3 getNoiseTarget() {
		if (this.noiseTarget != null && this.level().getGameTime() - this.noiseHeardAt > NOISE_MEMORY) {
			this.noiseTarget = null;
		}

		return this.noiseTarget;
	}

	public int getNoiseVersion() {
		return this.noiseVersion;
	}

	public void clearNoise() {
		this.noiseTarget = null;
		this.noiseLoudness = 0.0F;
	}

	// --- Herds ---

	public @Nullable Walker getLeader() {
		if (this.leaderId == null || !(this.level() instanceof ServerLevel level)) {
			return null;
		}

		if (level.getEntity(this.leaderId) instanceof Walker leader && leader.isAlive()) {
			return leader;
		}

		return null;
	}

	public void setLeader(@Nullable Walker leader) {
		this.leaderId = leader == null || leader == this ? null : leader.getUUID();
	}

	/** Herd-forming for walkers that spawn on their own (commands, turning) rather than in a natural pack. */
	private void joinNearbyHerd() {
		AABB area = this.getBoundingBox().inflate(16.0, 6.0, 16.0);
		for (Walker other : this.level().getEntitiesOfClass(Walker.class, area, w -> w != this && w.leaderId == null)) {
			this.setLeader(other);
			return;
		}
	}

	// --- Vanilla zombie behaviour we don't want ---

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData) {
		// Passing our own group data skips the zombie's baby, chicken-jockey and equipment logic.
		HerdData herd = groupData instanceof HerdData data ? data : new HerdData();
		super.finalizeSpawn(level, difficulty, spawnReason, herd);
		this.setCanPickUpLoot(false);
		if (herd.leader == null) {
			herd.leader = this;
			if (spawnReason != EntitySpawnReason.NATURAL && spawnReason != EntitySpawnReason.CHUNK_GENERATION) {
				this.joinNearbyHerd();
			}
		} else {
			this.setLeader(herd.leader);
		}

		return herd;
	}

	@Override
	protected void handleAttributes(float difficultyModifier, EntitySpawnReason spawnReason) {
		// No random leader bonuses, no reinforcements.
	}

	@Override
	protected void randomizeReinforcementsChance() {
	}

	@Override
	public void setBaby(boolean baby) {
		super.setBaby(false);
	}

	@Override
	protected boolean isSunSensitive() {
		return false;
	}

	@Override
	protected boolean convertsInWater() {
		// Walkers just walk along the canal bottom.
		return false;
	}

	@Override
	protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
	}

	// --- Saving ---

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		if (this.leaderId != null) {
			output.store("herd_leader", UUIDUtil.CODEC, this.leaderId);
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.leaderId = input.read("herd_leader", UUIDUtil.CODEC).orElse(null);
	}

	/** Shared between all walkers of one naturally spawned pack: the first one becomes the leader. */
	static final class HerdData implements SpawnGroupData {
		@Nullable Walker leader;
	}

	/** Simple helper for goals: horizontal distance squared to a position. */
	public double horizontalDistanceSqr(Vec3 pos) {
		return Mth.square(this.getX() - pos.x) + Mth.square(this.getZ() - pos.z);
	}
}
