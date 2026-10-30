package io.github.ditsjohann.outbreak.survivor;

import io.github.ditsjohann.outbreak.character.Perks;
import io.github.ditsjohann.outbreak.entity.Walker;
import io.github.ditsjohann.outbreak.sandbox.Sandbox;
import io.github.ditsjohann.outbreak.survivor.body.BandageType;
import io.github.ditsjohann.outbreak.survivor.body.BodyPart;
import io.github.ditsjohann.outbreak.survivor.body.Wound;
import io.github.ditsjohann.outbreak.survivor.body.WoundType;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

/** Walker attacks leave wounds: mostly scratches, sometimes lacerations, and now and then a bite. */
public final class Injuries {
	private static final float SCRATCH_CHANCE = 0.55F;
	private static final float LACERATION_CHANCE = 0.30F;

	private Injuries() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (entity instanceof ServerPlayer player && damageTaken > 0.0F && !blocked && source.getEntity() instanceof Walker) {
				onWalkerHit(player);
			}
		});
	}

	private static void onWalkerHit(ServerPlayer player) {
		RandomSource random = player.getRandom();
		if (random.nextFloat() < Perks.woundDodge(player)) {
			// Thick skin: it didn't break through.
			return;
		}

		float roll = random.nextFloat();
		WoundType type = roll < SCRATCH_CHANCE ? WoundType.SCRATCH : roll < SCRATCH_CHANCE + LACERATION_CHANCE ? WoundType.LACERATION : WoundType.BITE;
		if (type != WoundType.BITE && random.nextFloat() < Perks.woundWorsen(player)) {
			// Thin skin: it goes deeper.
			type = WoundType.values()[type.ordinal() + 1];
		}

		BodyPart part = randomPart(random);
		inflict(player, part, type);
	}

	public static void inflict(ServerPlayer player, BodyPart part, WoundType type) {
		SurvivorState state = Survivors.get(player);
		Wound existing = state.wound(part);
		if (existing == null || type.ordinal() > existing.type.ordinal()) {
			state.wounds.put(part, Wound.fresh(type));
		} else {
			// Hit on an existing, worse wound: it reopens.
			existing.bleeding = true;
			existing.bleedLeft = existing.type.bleedTicks();
			existing.healLeft = existing.type.healTicks();
			existing.bandage = BandageType.NONE;
			existing.bandageAge = 0;
		}

		if (!state.knox && player.getRandom().nextFloat() < Sandbox.current().infection().chance(type)) {
			state.knox = true;
			state.knoxTicks = 0;
		}

		state.panic = Math.min(1.0F, state.panic + 0.2F);
		Survivors.sync(player);
		player.sendOverlayMessage(Component.translatable("outbreak.message.wounded", Component.translatable(type.translationKey()), Component.translatable(part.translationKey()))
			.withStyle(type == WoundType.BITE ? ChatFormatting.DARK_RED : ChatFormatting.RED));
	}

	private static BodyPart randomPart(RandomSource random) {
		int total = 0;
		for (BodyPart part : BodyPart.values()) {
			total += part.hitWeight();
		}

		int pick = random.nextInt(total);
		for (BodyPart part : BodyPart.values()) {
			pick -= part.hitWeight();
			if (pick < 0) {
				return part;
			}
		}

		return BodyPart.TORSO;
	}
}
