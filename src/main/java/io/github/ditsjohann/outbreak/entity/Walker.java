package io.github.ditsjohann.outbreak.entity;

import java.util.Optional;
import java.util.UUID;

import io.github.ditsjohann.outbreak.character.Characters;
import io.github.ditsjohann.outbreak.character.Perks;
import io.github.ditsjohann.outbreak.character.Skill;
import io.github.ditsjohann.outbreak.combat.GunItem;
import io.github.ditsjohann.outbreak.combat.WeaponStyle;
import io.github.ditsjohann.outbreak.entity.ai.BreakObstacleGoal;
import io.github.ditsjohann.outbreak.entity.ai.FollowHerdLeaderGoal;
import io.github.ditsjohann.outbreak.entity.ai.InvestigateNoiseGoal;
import io.github.ditsjohann.outbreak.entity.ai.MigrateGoal;
import io.github.ditsjohann.outbreak.gore.Blood;
import io.github.ditsjohann.outbreak.gore.BodyPart;
import io.github.ditsjohann.outbreak.gore.Limb;
import io.github.ditsjohann.outbreak.item.OutbreakItems;
import io.github.ditsjohann.outbreak.sandbox.Sandbox;
import io.github.ditsjohann.outbreak.sandbox.SandboxSettings;
import io.github.ditsjohann.outbreak.survivor.OutbreakDamageTypes;
import io.github.ditsjohann.outbreak.vehicle.Vehicle;
import io.github.ditsjohann.outbreak.world.Camouflage;
import io.github.ditsjohann.outbreak.world.Places;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreakDoorGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.ZombieAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A walker: slow, doesn't burn, only dies from a head hit, hunts by sound first and sight second,
 * and drifts around in herds. Extends the vanilla zombie so it can reuse the zombie model and
 * sounds until it gets its own.
 */
public class Walker extends Zombie {
	/** Fraction of the walker's height above which a hit counts as a headshot. */
	public static final double HEAD_START = 0.78;
	private static final double VEHICLE_REACH = 0.6;
	/** A crawler's hitbox: as wide as a standing walker (so changing to it never pushes anything up), but low. */
	private static final EntityDimensions CRAWLING = EntityDimensions.scalable(0.6F, 0.6F).withEyeHeight(0.4F);
	private static final float HEADSHOT_MULTIPLIER = 4.0F;
	/** Skins cycle through the outfits (tools/walkers/make_walkers.py): which outfit is which. */
	private static final int OUTFITS = 16;
	private static final int OUTFIT_POLICE = 3;
	private static final int OUTFIT_NURSE = 4;
	private static final int OUTFIT_DOCTOR = 5;
	private static final int OUTFIT_CONSTRUCTION = 6;
	private static final int OUTFIT_STUDENT = 7;
	private static final int OUTFIT_SOLDIER = 11;
	private static final int OUTFIT_CHEF = 15;
	/** Chance a walker killed with a blade leaves its guts behind. */
	private static final float GUTS_CHANCE = 0.4F;
	private static final float BODY_MULTIPLIER = 0.5F;
	/** Cosine of the half-angle of a walker's field of view (~65 degrees). */
	private static final double VIEW_CONE_COS = 0.42;
	/** How long a heard noise stays interesting, in ticks. */
	private static final int NOISE_MEMORY = 20 * 30;

	/** How many skins there are (tools/walkers/make_walkers.py). */
	public static final int SKINS = 24;
	public static final float CRAWLER_CHANCE = 0.08F;
	/** A corpse lies where it fell for two to three minutes, then it's gone. */
	public static final int CORPSE_TICKS = 20 * 120;
	private static final int CORPSE_EXTRA_TICKS = 20 * 60;
	public static final int FALL_TICKS = 10;
	public static final int GET_UP_TICKS = 24;

	private static final EntityDataAccessor<Integer> DATA_VARIANT = SynchedEntityData.defineId(Walker.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_CRAWLER = SynchedEntityData.defineId(Walker.class, EntityDataSerializers.BOOLEAN);
	/** {@link Posture} ordinal. */
	private static final EntityDataAccessor<Byte> DATA_POSTURE = SynchedEntityData.defineId(Walker.class, EntityDataSerializers.BYTE);
	/** Game time the posture last changed: the client times falls and getting up from it. */
	private static final EntityDataAccessor<Integer> DATA_POSTURE_SINCE = SynchedEntityData.defineId(Walker.class, EntityDataSerializers.INT);
	/** World yaw the walker fell (or is falling) towards. */
	private static final EntityDataAccessor<Float> DATA_FALL_YAW = SynchedEntityData.defineId(Walker.class, EntityDataSerializers.FLOAT);
	/** The last hit, packed: see {@link Hit}. A new one has a new sequence number. */
	private static final EntityDataAccessor<Integer> DATA_HIT = SynchedEntityData.defineId(Walker.class, EntityDataSerializers.INT);
	/** Bits of the {@link Limb}s it has lost. */
	private static final EntityDataAccessor<Integer> DATA_LIMBS = SynchedEntityData.defineId(Walker.class, EntityDataSerializers.INT);
	private float trailDistance;
	private static final Identifier CRAWLER_SPEED = Identifier.fromNamespaceAndPath("outbreak", "crawler");
	/** The world's sandbox walker speed, on top of the shambler's base speed. */
	private static final Identifier SANDBOX_SPEED = Identifier.fromNamespaceAndPath("outbreak", "sandbox_speed");

	/** Standing, knocked down (falling, then lying there), or getting back up. */
	public enum Posture {
		STANDING,
		DOWN,
		GETTING_UP
	}

	/** A hit as the client sees it: which way it pushed (world yaw), how hard (0-15), and whether it hit the head. */
	public record Hit(int sequence, float yaw, int strength, boolean head) {
		static int pack(int sequence, float yaw, int strength, boolean head) {
			int yawByte = Math.floorMod(Math.round(yaw / 360.0F * 256.0F), 256);
			return (sequence & 0xFF) | (Math.clamp(strength, 0, 15) << 8) | ((head ? 1 : 0) << 12) | (yawByte << 16);
		}

		public static Hit unpack(int packed) {
			return new Hit(packed & 0xFF, ((packed >>> 16) & 0xFF) * 360.0F / 256.0F, (packed >>> 8) & 0xF, ((packed >>> 12) & 1) == 1);
		}
	}

	private int staggerTicks;
	private int downTicks;
	private int hitSequence;

	private @Nullable Vec3 noiseTarget;
	private float noiseLoudness;
	private long noiseHeardAt;
	private int noiseVersion;
	private @Nullable UUID leaderId;
	/** Where its herd is heading (only for leaders and loners), and until when it keeps going. */
	private @Nullable BlockPos migration;
	private long migrationUntil;

	public Walker(EntityType<? extends Walker> type, Level level) {
		super(type, level);
		this.getNavigation().setCanOpenDoors(true);
		this.xpReward = 0;
		this.entityData.set(DATA_VARIANT, this.random.nextInt(SKINS));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_VARIANT, 0);
		builder.define(DATA_CRAWLER, false);
		builder.define(DATA_POSTURE, (byte) 0);
		builder.define(DATA_POSTURE_SINCE, 0);
		builder.define(DATA_FALL_YAW, 0.0F);
		builder.define(DATA_HIT, 0);
		builder.define(DATA_LIMBS, 0);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, 20.0)
			.add(Attributes.FOLLOW_RANGE, 32.0)
			.add(Attributes.MOVEMENT_SPEED, 0.18F)
			.add(Attributes.ATTACK_DAMAGE, 3.0)
			.add(Attributes.ARMOR, 0.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 0.45)
			.add(Attributes.SPAWN_REINFORCEMENTS_CHANCE, 0.0);
	}

	public static boolean checkWalkerSpawnRules(EntityType<? extends Mob> type, LevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
		// Unlike vanilla monsters, walkers don't care about light: they're out in broad daylight too.
		if (reason == EntitySpawnReason.NATURAL && Sandbox.current().population() == SandboxSettings.Population.NONE) {
			return false;
		}

		if (level.getDifficulty() == Difficulty.PEACEFUL || !Mob.checkMobSpawnRules(type, level, reason, pos, random)) {
			return false;
		}

		// Natural spawns: out of sight, a few at a time, not on roofs, thicker where it matters.
		return reason != EntitySpawnReason.NATURAL || !(level instanceof ServerLevel server) || WalkerSpawning.allow(server, pos, random);
	}

	@Override
	public float getWalkTargetValue(BlockPos pos, LevelReader level) {
		// Monsters prefer (and only spawn in) the dark. Walkers don't care.
		return 0.0F;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(1, new BreakDoorGoal(this, difficulty -> true) {
			@Override
			public boolean canUse() {
				return Walker.this.canBreakThings() && super.canUse();
			}
		});
		this.goalSelector.addGoal(1, new BreakObstacleGoal(this));
		this.goalSelector.addGoal(2, new ZombieAttackGoal(this, 1.15, false));
		this.goalSelector.addGoal(4, new InvestigateNoiseGoal(this, 1.0));
		this.goalSelector.addGoal(5, new FollowHerdLeaderGoal(this, 0.9));
		this.goalSelector.addGoal(6, new MigrateGoal(this, 0.75));
		this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.7));
		this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers(Walker.class));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 5, true, false, (target, level) -> this.canSpot(target)));
	}

	/** Sight check: range depends on how bright it is where the target stands, crouching halves it, and walkers only see what's in front of them. */
	private boolean canSpot(LivingEntity target) {
		if (target instanceof Player player && (!Characters.hasCharacter(player) || Camouflage.isCovered(player))) {
			// Still making their survivor, or smelling like one of us.
			return false;
		}

		double range = 6.0 + this.level().getMaxLocalRawBrightness(target.blockPosition());
		if (target.isCrouching()) {
			range *= 0.5;
		}

		if (target instanceof Player player) {
			range *= Perks.visibility(player);
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

	// --- Damage: only the head counts. Everything else knocks them about. ---

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		Entity direct = source.getDirectEntity();
		boolean isAttack = direct != null && direct != this && !source.is(DamageTypeTags.IS_EXPLOSION);
		boolean runOver = source.is(OutbreakDamageTypes.RUN_OVER);
		boolean head = false;
		// Run over by a vehicle: crushed, head or not.
		if (isAttack && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !runOver) {
			if (direct instanceof Player player && !(player.getMainHandItem().getItem() instanceof GunItem)) {
				damage *= Perks.meleeDamage(player);
			}

			// A walker on the ground can't protect its head: every hit is a head hit.
			head = this.posture() != Posture.STANDING || this.isCrawler() || this.isHeadshot(direct);
			if (head) {
				damage *= HEADSHOT_MULTIPLIER;
			} else {
				// Body hits stagger but can never finish a walker off.
				damage = Math.min(damage * BODY_MULTIPLIER, this.getHealth() - 1.0F);
				if (damage <= 0.0F) {
					damage = 0.01F;
				}
			}
		}

		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt && direct instanceof ServerPlayer player && Camouflage.isCovered(player)) {
			// Attacking one of them gives you away.
			Camouflage.wash(player, "outbreak.guts.attacked");
		}

		if (hurt && this.isDeadOrDying() && direct instanceof Player player && WeaponStyle.isSharp(player.getMainHandItem())
			&& this.random.nextFloat() < GUTS_CHANCE) {
			// Opened up: its guts, for smearing on.
			this.spawnAtLocation(level, new ItemStack(OutbreakItems.WALKER_GUTS));
		}

		if (hurt && source.is(DamageTypeTags.IS_EXPLOSION)) {
			this.blownApart(level, source);
		}

		if (hurt && isAttack) {
			this.knockAbout(level, source, direct, head, runOver);
			if (direct instanceof ServerPlayer player && !runOver) {
				Characters.addXp(player, Skill.FIGHTING, head ? 3.0F : 2.0F);
				Characters.addXp(player, Skill.STRENGTH, 0.5F);
			}
		}

		return hurt;
	}

	/** The physics of a hit: which way it throws the walker, a stagger, and maybe a fall. */
	private void knockAbout(ServerLevel level, DamageSource source, Entity direct, boolean head, boolean runOver) {
		Entity attacker = source.getEntity() != null ? source.getEntity() : direct;
		WeaponStyle style = attacker instanceof LivingEntity living && direct == attacker ? WeaponStyle.of(living.getMainHandItem()) : WeaponStyle.OTHER;
		Vec3 away = this.position().subtract(direct.position()).multiply(1.0, 0.0, 1.0);
		if (direct instanceof Projectile || runOver) {
			away = direct.getDeltaMovement().multiply(1.0, 0.0, 1.0);
		}

		if (away.lengthSqr() < 1.0E-6) {
			away = this.getLookAngle().scale(-1.0);
		}

		// Yaw the blow pushes towards; a sideways swing comes across towards the attacker's left.
		float yaw = (float) Math.toDegrees(Math.atan2(-away.x, away.z)) - (runOver ? 0.0F : style.lateral);
		float strength = runOver ? 1.6F : style.strength * (head ? 1.3F : 1.0F);
		if (!runOver && attacker instanceof Player player) {
			strength *= Perks.push(player);
		}

		this.hitSequence++;
		this.entityData.set(DATA_HIT, Hit.pack(this.hitSequence, yaw, Math.round(strength * 8.0F), head));
		ItemStack weapon = attacker instanceof LivingEntity living && direct == attacker ? living.getMainHandItem() : ItemStack.EMPTY;
		this.bleed(level, yaw, strength, head, runOver, WeaponStyle.isSharp(weapon), runOver ? null : this.hitPoint(direct), away);
		if (this.isDeadOrDying()) {
			this.entityData.set(DATA_FALL_YAW, yaw);
			return;
		}

		float rad = yaw * Mth.DEG_TO_RAD;
		// Sideways, not up: vanilla's knockback already gives the little hop.
		float push = 0.15F + strength * 0.25F;
		this.push(-Mth.sin(rad) * push, 0.02 + strength * 0.02, Mth.cos(rad) * push);
		this.markHurt();
		this.staggerTicks = Math.max(this.staggerTicks, Math.round(6 + strength * 14));

		float chance = runOver ? 1.0F : head ? style.headKnockdown : style.knockdown;
		if (style == WeaponStyle.FIST && this.staggerTicks > 12) {
			// Shoving a walker that's already reeling.
			chance += 0.25F;
		}

		if (!this.isCrawler() && this.posture() == Posture.STANDING && this.random.nextFloat() < chance) {
			this.knockDown(yaw, 50 + this.random.nextInt(60));
			// A hard enough hit (or a car) breaks the legs: it'll crawl from now on.
			if (runOver || (style == WeaponStyle.SWING && !head && this.random.nextFloat() < 0.15F)) {
				this.setCrawler(true);
			}
		}

		level.playSound(null, this.getX(), this.getY(), this.getZ(), head ? SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR : SoundEvents.PLAYER_ATTACK_KNOCKBACK,
			SoundSource.HOSTILE, 0.6F + strength * 0.3F, head ? 0.7F : 0.9F);
	}

	// --- Gore: blood, and coming apart ---

	public int lostLimbs() {
		return this.entityData.get(DATA_LIMBS);
	}

	public boolean hasLost(Limb limb) {
		return limb.isLost(this.lostLimbs());
	}

	/** Without hands it can still bite, but can't batter doors and barricades down. */
	public boolean canBreakThings() {
		return !(this.hasLost(Limb.RIGHT_FOREARM) && this.hasLost(Limb.LEFT_FOREARM));
	}

	/**
	 * A hit draws blood: a spray the way the blow went, splatter on the ground under and behind it (lots:
	 * a head hit more), and drops where the spray comes down. A sharp blade can take off the limb it
	 * hit (see {@link #limbAt}); under a car, legs. Walls behind it get splashed, mostly straight on
	 * from the blow ({@code blow}: from the attacker through the walker), sometimes the way the swing went.
	 */
	private void bleed(ServerLevel level, float yaw, float strength, boolean head, boolean runOver, boolean sharp, @Nullable Vec3 hit, Vec3 blow) {
		Vec3 fling = new Vec3(-Mth.sin(yaw * Mth.DEG_TO_RAD), 0.35, Mth.cos(yaw * Mth.DEG_TO_RAD));
		Vec3 wound = hit != null ? hit : head ? this.getEyePosition() : this.position().add(0.0, this.getBbHeight() * 0.55, 0.0);
		Blood.spray(level, wound, fling, head ? 16 : 10, 0.2F + 0.2F * strength);
		// Splatter right under the wound, and where the spray lands behind it.
		Blood.splat(level, this.position(), this.random.nextFloat() < (head ? 0.5F : 0.25F) ? 1 : 0);
		int drops = (head ? 3 : 2) + this.random.nextInt(2) + (sharp ? 1 : 0);
		for (int i = 0; i < drops; i++) {
			double reach = 0.6 + this.random.nextDouble() * (1.0 + strength * 1.5);
			double spread = (this.random.nextDouble() - 0.5) * 1.2;
			Vec3 at = this.position().add(fling.x * reach - fling.z * spread, 0.0, fling.z * reach + fling.x * spread);
			Blood.splat(level, at, this.random.nextFloat() < 0.2F ? 1 : 0);
		}

		Blood.wall(level, wound, fling, head ? 3.5 : 3.0);
		for (int i = 0; i < (head ? 3 : 2); i++) {
			Blood.wall(level, wound, blow, head ? 3.5 : 3.0);
		}
		if (runOver) {
			// Under the wheels: legs crushed off, sometimes both.
			if (this.random.nextFloat() < 0.45F) {
				this.sever(level, this.randomLeg(), fling);
				if (this.random.nextFloat() < 0.25F) {
					this.sever(level, this.randomLeg(), fling);
				}
			}

			Blood.pool(level, this.position(), 3);
			return;
		}

		if (!sharp) {
			return;
		}

		if (head) {
			// A blade through the neck of one that's already finished: the head comes off.
			if (this.isDeadOrDying() && this.random.nextFloat() < 0.45F) {
				this.sever(level, Limb.HEAD, fling);
			}

			return;
		}

		Limb limb = this.limbAt(hit);
		if (limb != null && this.random.nextFloat() < (limb.isArm() ? 0.35F : 0.28F)) {
			this.sever(level, limb, fling);
		}
	}

	/**
	 * Where on the body a blow landed: where the attacker's line of sight meets the walker (or the
	 * projectile's path), or null if it can't be worked out.
	 */
	private @Nullable Vec3 hitPoint(Entity direct) {
		Vec3 from;
		Vec3 to;
		if (direct instanceof LivingEntity attacker) {
			from = attacker.getEyePosition();
			to = from.add(attacker.getViewVector(1.0F).scale(8.0));
		} else {
			from = direct.position();
			to = from.add(direct.getDeltaMovement().scale(4.0));
		}

		return this.getBoundingBox().inflate(0.15).clip(from, to).orElse(null);
	}

	/**
	 * The limb at a point on the body (standing): below the knee a shin, the hips to the knee a leg,
	 * an arm from the shoulder (its forearm below the elbow), by which side of the body it's on. A
	 * limb already gone leaves what's above it (the arm, once the forearm is off).
	 */
	private @Nullable Limb limbAt(@Nullable Vec3 hit) {
		if (hit == null || this.isCrawler() || this.posture() != Posture.STANDING) {
			// Lying or crawling: whatever's nearest the blade.
			return this.random.nextFloat() < 0.6F ? (this.random.nextBoolean() ? Limb.RIGHT_FOREARM : Limb.LEFT_FOREARM) : this.randomLeg();
		}

		float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
		Vec3 left = new Vec3(Mth.cos(yaw), 0.0, Mth.sin(yaw));
		Vec3 local = hit.subtract(this.position());
		double side = local.dot(left);
		double up = local.y;
		// Right in the middle of the chest: the nearer arm (the attacker's side), at random.
		boolean onLeft = Math.abs(side) > 0.08 ? side > 0.0 : this.random.nextBoolean();
		Limb limb;
		if (up < 0.4) {
			limb = onLeft ? Limb.LEFT_SHIN : Limb.RIGHT_SHIN;
		} else if (up < 0.78) {
			limb = onLeft ? Limb.LEFT_LEG : Limb.RIGHT_LEG;
		} else if (up < 1.1) {
			limb = onLeft ? Limb.LEFT_FOREARM : Limb.RIGHT_FOREARM;
		} else if (up < 1.52) {
			limb = onLeft ? Limb.LEFT_ARM : Limb.RIGHT_ARM;
		} else {
			return null;
		}

		// The forearm is gone already: the rest of that arm (and the same for a shin).
		if (this.hasLost(limb)) {
			for (Limb whole : Limb.values()) {
				if (whole.lower == limb) {
					return this.hasLost(whole) ? null : whole;
				}
			}

			return null;
		}

		return limb;
	}

	private Limb randomLeg() {
		boolean right = this.random.nextBoolean();
		return this.random.nextBoolean() ? (right ? Limb.RIGHT_SHIN : Limb.LEFT_SHIN) : (right ? Limb.RIGHT_LEG : Limb.LEFT_LEG);
	}

	/** Caught in a blast: limbs torn off, the head too if it's dead. */
	private void blownApart(ServerLevel level, DamageSource source) {
		Vec3 from = source.getSourcePosition() != null ? source.getSourcePosition() : this.position().subtract(0.0, 1.0, 0.0);
		Vec3 fling = this.position().subtract(from).normalize().add(0.0, 0.5, 0.0);
		for (Limb limb : new Limb[] {Limb.RIGHT_ARM, Limb.LEFT_ARM, Limb.RIGHT_LEG, Limb.LEFT_LEG}) {
			if (this.random.nextFloat() < 0.3F) {
				this.sever(level, limb, fling);
			}
		}

		if (this.isDeadOrDying() && this.random.nextFloat() < 0.5F) {
			this.sever(level, Limb.HEAD, fling);
		}

		Blood.pool(level, this.position(), 4);
	}

	/**
	 * Takes a limb off: it flies away with the blow (as a {@link BodyPart} in this walker's skin),
	 * blood sprays from the stump, and without a leg it can only crawl. Returns false if it's
	 * already gone.
	 */
	public boolean sever(ServerLevel level, Limb limb, Vec3 fling) {
		if (this.hasLost(limb)) {
			return false;
		}

		this.entityData.set(DATA_LIMBS, this.lostLimbs() | limb.bit());
		Vec3 where = this.bodyPoint(limb.offset);
		Vec3 velocity = fling.normalize().scale(0.25 + this.random.nextDouble() * 0.15).add(0.0, 0.2, 0.0)
			.add(this.random.nextGaussian() * 0.04, 0.0, this.random.nextGaussian() * 0.04);
		BodyPart.spawn(level, OutbreakEntities.BODY_PART, limb, this.skin(), where, velocity, this.yBodyRot + this.random.nextFloat() * 40.0F - 20.0F);
		Blood.spray(level, where, fling, limb == Limb.HEAD ? 26 : 16, 0.4F);
		Blood.splat(level, this.position(), 1);
		level.playSound(null, where.x, where.y, where.z, SoundEvents.HONEY_BLOCK_BREAK, SoundSource.HOSTILE, 1.2F, 0.55F);
		level.playSound(null, where.x, where.y, where.z, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.HOSTILE, 0.5F, 0.5F);
		if (limb.isLeg() && !this.isCrawler()) {
			this.setCrawler(true);
			if (this.isAlive() && this.posture() == Posture.STANDING) {
				this.knockDown((float) Math.toDegrees(Math.atan2(-fling.x, fling.z)), 40 + this.random.nextInt(40));
			}
		}

		return true;
	}

	/** A point on the standing body, x as in the model (negative is its right), y up and z forward, in blocks from the feet. */
	private Vec3 bodyPoint(Vec3 offset) {
		float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
		Vec3 forward = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
		Vec3 left = new Vec3(Mth.cos(yaw), 0.0, Mth.sin(yaw));
		double up = this.isCrawler() || this.posture() != Posture.STANDING ? offset.y * 0.25 : offset.y;
		return this.position().add(left.scale(offset.x)).add(forward.scale(offset.z)).add(0.0, up, 0.0);
	}

	/** Crawlers smear a trail behind them, and stumps drip. */
	private void trail(ServerLevel level) {
		// Crawlers, stumps and the badly hurt leave blood behind them.
		boolean hurt = this.getHealth() < this.getMaxHealth() * 0.6F;
		if (!this.isCrawler() && this.lostLimbs() == 0 && !hurt) {
			return;
		}

		this.trailDistance += (float) Math.hypot(this.getX() - this.xo, this.getZ() - this.zo);
		if (this.trailDistance > (this.isCrawler() ? 1.1F : this.lostLimbs() != 0 ? 2.5F : 3.5F)) {
			this.trailDistance = 0.0F;
			if (this.random.nextFloat() < 0.6F) {
				Blood.splat(level, this.position(), 0);
			}
		}
	}

	// --- Posture: stagger, knocked down, getting up, crawling ---

	public int skin() {
		return Math.floorMod(this.entityData.get(DATA_VARIANT), SKINS);
	}

	public void setSkin(int skin) {
		this.entityData.set(DATA_VARIANT, Math.floorMod(skin, SKINS));
	}

	public boolean isCrawler() {
		return this.entityData.get(DATA_CRAWLER);
	}

	public void setCrawler(boolean crawler) {
		this.entityData.set(DATA_CRAWLER, crawler);
		var speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(CRAWLER_SPEED);
			if (crawler) {
				speed.addPermanentModifier(new AttributeModifier(CRAWLER_SPEED, -0.6, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
		}
	}

	public Posture posture() {
		return Posture.values()[Math.clamp(this.entityData.get(DATA_POSTURE), 0, Posture.values().length - 1)];
	}

	/** Game time the current posture started. */
	public int postureSince() {
		return this.entityData.get(DATA_POSTURE_SINCE);
	}

	public float fallYaw() {
		return this.entityData.get(DATA_FALL_YAW);
	}

	public Hit lastHit() {
		return Hit.unpack(this.entityData.get(DATA_HIT));
	}

	private void setPosture(Posture posture) {
		this.entityData.set(DATA_POSTURE, (byte) posture.ordinal());
		this.entityData.set(DATA_POSTURE_SINCE, (int) this.level().getGameTime());
	}

	/** Falls over towards yaw and lies there for a while. */
	public void knockDown(float yaw, int ticks) {
		this.entityData.set(DATA_FALL_YAW, yaw);
		this.setPosture(Posture.DOWN);
		this.downTicks = ticks;
		this.getNavigation().stop();
	}

	/** Back on its feet at once (for tests). */
	public void standUp() {
		this.setPosture(Posture.STANDING);
		this.downTicks = 0;
		this.staggerTicks = 0;
	}

	/** A hit for show (and for tests): push, stagger and maybe fall, as if hit from that side. */
	public void react(float yaw, float strength, boolean head) {
		this.hitSequence++;
		this.entityData.set(DATA_HIT, Hit.pack(this.hitSequence, yaw, Math.round(strength * 8.0F), head));
		this.staggerTicks = Math.max(this.staggerTicks, Math.round(6 + strength * 14));
	}

	/** Staggering, down or getting up: no walking, no biting. */
	public boolean isHelpless() {
		return this.staggerTicks > 0 || this.posture() != Posture.STANDING;
	}

	@Override
	protected boolean isImmobile() {
		return super.isImmobile() || this.posture() != Posture.STANDING;
	}

	@Override
	public void aiStep() {
		// Here rather than in customServerAiStep: that doesn't run while the walker is down (immobile).
		if (!this.level().isClientSide() && this.isAlive()) {
			if (this.tickCount % 40 == 1) {
				this.applySandboxSpeed();
			}

			if (this.staggerTicks > 0) {
				this.staggerTicks--;
			}

			if (this.posture() == Posture.DOWN && --this.downTicks <= 0) {
				this.setPosture(Posture.GETTING_UP);
				this.downTicks = GET_UP_TICKS;
			} else if (this.posture() == Posture.GETTING_UP && --this.downTicks <= 0) {
				this.setPosture(Posture.STANDING);
			}

			this.trail((ServerLevel) this.level());
			// Covered in guts: it loses interest, unless they've just hit it.
			if (this.getTarget() instanceof Player player && Camouflage.isCovered(player) && this.getLastHurtByMob() != player) {
				this.setTarget(null);
			}
		}

		super.aiStep();
	}

	/** Shamblers, fast shamblers or sprinters, as the world's sandbox settings say (they can change any time). */
	private void applySandboxSpeed() {
		double bonus = Sandbox.current().walkerSpeed().speed - SandboxSettings.WalkerSpeed.SHAMBLERS.speed;
		var speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}

		AttributeModifier current = speed.getModifier(SANDBOX_SPEED);
		if (current == null ? bonus != 0.0 : current.amount() != bonus) {
			speed.removeModifier(SANDBOX_SPEED);
			if (bonus != 0.0) {
				speed.addTransientModifier(new AttributeModifier(SANDBOX_SPEED, bonus, AttributeModifier.Operation.ADD_VALUE));
			}
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		boolean helpless = this.isHelpless();
		for (Goal.Flag flag : new Goal.Flag[] {Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP}) {
			if (helpless) {
				this.goalSelector.disableControlFlag(flag);
			} else {
				this.goalSelector.enableControlFlag(flag);
			}
		}

		if (helpless) {
			this.getNavigation().stop();
		}

		super.customServerAiStep(level);
	}

	/** Crawlers are low. A knocked-down walker keeps its standing hitbox: it's back up in a few seconds. */
	@Override
	public EntityDimensions getDefaultDimensions(Pose pose) {
		if (this.isCrawler()) {
			return CRAWLING;
		}

		return super.getDefaultDimensions(pose);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (DATA_CRAWLER.equals(accessor)) {
			this.refreshDimensions();
		}
	}

	/** How long this corpse lies there (the same on the server and every client: it goes by its UUID). */
	public int corpseTicks() {
		return CORPSE_TICKS + Math.floorMod(this.getUUID().hashCode(), CORPSE_EXTRA_TICKS);
	}

	/** Corpses stay a while instead of vanishing in a puff. */
	@Override
	protected void tickDeath() {
		this.deathTime++;
		if (this.level() instanceof ServerLevel level) {
			if (this.deathTime == 14) {
				// The pool spreads out from where the body came down.
				float yaw = this.entityData.get(DATA_FALL_YAW) * Mth.DEG_TO_RAD;
				Blood.pool(level, this.position().add(-Mth.sin(yaw) * 0.8, 0.0, Mth.cos(yaw) * 0.8), this.hasLost(Limb.HEAD) ? 6 : 4);
			} else if (this.hasLost(Limb.HEAD) && this.deathTime < 12 && this.deathTime % 3 == 0) {
				// Pumping out of the neck as it goes down.
				Blood.spray(level, this.getEyePosition().subtract(0.0, 0.3, 0.0), new Vec3(0.0, 1.0, 0.0), 3, 0.15F);
			}
		}

		if (this.deathTime >= this.corpseTicks() && !this.level().isClientSide() && !this.isRemoved()) {
			this.level().broadcastEntityEvent(this, (byte) 60);
			this.remove(RemovalReason.KILLED);
		}
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

	public @Nullable BlockPos getMigration() {
		if (this.migration != null && this.level().getGameTime() > this.migrationUntil) {
			this.migration = null;
		}

		return this.migration;
	}

	/** Sets off with its herd towards somewhere, for a while (see Migration). */
	public void migrate(BlockPos to, long ticks) {
		this.migration = to;
		this.migrationUntil = this.level().getGameTime() + ticks;
	}

	public void clearMigration() {
		this.migration = null;
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

	/** Someone sitting in a vehicle is reached through it: the walker claws at the windows first (see Vehicle#absorbHit). */
	@Override
	public boolean isWithinMeleeAttackRange(LivingEntity target) {
		if (target.getVehicle() instanceof Vehicle vehicle) {
			return this.getAttackBoundingBox(VEHICLE_REACH).intersects(vehicle.getBoundingBox());
		}

		return super.isWithinMeleeAttackRange(target);
	}

	// --- Vanilla zombie behaviour we don't want ---

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData) {
		// Passing our own group data skips the zombie's baby, chicken-jockey and equipment logic.
		HerdData herd = groupData instanceof HerdData data ? data : new HerdData();
		super.finalizeSpawn(level, difficulty, spawnReason, herd);
		this.setCanPickUpLoot(false);
		if (this.random.nextFloat() < CRAWLER_CHANCE) {
			this.setCrawler(true);
		}

		// Dressed for where they were when it happened: scrubs in the hospital, uniforms at the police station.
		Places.Kind place = Places.at(this.blockPosition());
		if ((spawnReason == EntitySpawnReason.NATURAL || spawnReason == EntitySpawnReason.CHUNK_GENERATION)
			&& place != null && place.skins.length > 0 && this.random.nextFloat() < 0.65F) {
			this.setSkin(place.skins[this.random.nextInt(place.skins.length)]);
		}

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

	/** What they had on them, by what they were: a police officer's ammunition, a nurse's bandages. */
	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		switch (Math.floorMod(this.skin(), OUTFITS)) {
			case OUTFIT_POLICE -> {
				this.maybeDrop(level, OutbreakItems.AMMO_9MM, 0.3F, 2, 8);
				this.maybeDrop(level, OutbreakItems.BATON, 0.06F, 1, 1);
				if (this.random.nextFloat() < 0.02F) {
					ItemStack pistol = new ItemStack(OutbreakItems.PISTOL);
					GunItem.setRounds(pistol, this.random.nextInt(8));
					pistol.setDamageValue(this.random.nextInt(pistol.getMaxDamage() / 2));
					this.spawnAtLocation(level, pistol);
				}
			}
			case OUTFIT_SOLDIER -> {
				this.maybeDrop(level, OutbreakItems.RIFLE_ROUNDS, 0.3F, 2, 6);
				this.maybeDrop(level, OutbreakItems.AMMO_9MM, 0.15F, 3, 10);
				this.maybeDrop(level, OutbreakItems.BANDAGE, 0.2F, 1, 2);
			}
			case OUTFIT_NURSE, OUTFIT_DOCTOR -> {
				this.maybeDrop(level, OutbreakItems.BANDAGE, 0.35F, 1, 2);
				this.maybeDrop(level, OutbreakItems.DISINFECTANT, 0.15F, 1, 1);
			}
			case OUTFIT_CONSTRUCTION -> {
				this.maybeDrop(level, OutbreakItems.NAILS, 0.3F, 2, 6);
				this.maybeDrop(level, OutbreakItems.HAMMER, 0.04F, 1, 1);
			}
			case OUTFIT_CHEF -> this.maybeDrop(level, OutbreakItems.KITCHEN_KNIFE, 0.1F, 1, 1);
			case OUTFIT_STUDENT -> this.maybeDrop(level, OutbreakItems.SCHOOL_BAG, 0.05F, 1, 1);
			default -> {
			}
		}
	}

	private void maybeDrop(ServerLevel level, net.minecraft.world.item.Item item, float chance, int min, int max) {
		if (this.random.nextFloat() < chance) {
			this.spawnAtLocation(level, new ItemStack(item, min + this.random.nextInt(max - min + 1)));
		}
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

		output.putInt("skin", this.skin());
		output.putBoolean("crawler", this.isCrawler());
		output.putInt("limbs", this.lostLimbs());
		if (this.getMigration() != null) {
			output.store("migration", BlockPos.CODEC, this.migration);
			output.putLong("migration_until", this.migrationUntil);
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.leaderId = input.read("herd_leader", UUIDUtil.CODEC).orElse(null);
		input.getInt("skin").ifPresent(this::setSkin);
		this.entityData.set(DATA_CRAWLER, input.getBooleanOr("crawler", false));
		this.entityData.set(DATA_LIMBS, input.getIntOr("limbs", 0));
		this.migration = input.read("migration", BlockPos.CODEC).orElse(null);
		this.migrationUntil = input.getLongOr("migration_until", 0L);
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
