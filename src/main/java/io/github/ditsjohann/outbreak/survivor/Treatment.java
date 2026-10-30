package io.github.ditsjohann.outbreak.survivor;

import java.util.Map;

import io.github.ditsjohann.outbreak.character.Characters;
import io.github.ditsjohann.outbreak.character.Perks;
import io.github.ditsjohann.outbreak.character.Skill;
import io.github.ditsjohann.outbreak.survivor.body.BandageType;
import io.github.ditsjohann.outbreak.survivor.body.BodyPart;
import io.github.ditsjohann.outbreak.survivor.body.Wound;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * First aid. Instead of a body-part picker, treatment goes to the wound that needs it most:
 * bleeding first, then dirty bandages, then anything still unbandaged.
 */
public final class Treatment {
	private Treatment() {
	}

	public enum Kind {
		RAG,
		BANDAGE,
		DISINFECTANT
	}

	/** Which body part this treatment would go to, or null if it would do nothing. Safe on both sides. */
	public static @Nullable BodyPart target(SurvivorState state, Kind kind) {
		BodyPart best = null;
		int bestScore = 0;
		for (Map.Entry<BodyPart, Wound> entry : state.wounds.entrySet()) {
			int score = score(entry.getValue(), kind);
			if (score > bestScore) {
				bestScore = score;
				best = entry.getKey();
			}
		}

		return best;
	}

	private static int score(Wound wound, Kind kind) {
		if (kind == Kind.DISINFECTANT) {
			if (wound.infected) {
				return 3;
			}

			return wound.disinfected ? 0 : 1 + wound.type.ordinal();
		}

		BandageType bandage = kind == Kind.RAG ? BandageType.RAG : BandageType.BANDAGE;
		int severity = 1 + wound.type.ordinal();
		if (wound.bleeding) {
			return 30 + severity;
		}

		if (wound.isBandageDirty()) {
			return 20 + severity;
		}

		if (!wound.isBandaged()) {
			return 10 + severity;
		}

		// Swapping a rag for a proper bandage is still worth it.
		return wound.bandage.ordinal() < bandage.ordinal() ? severity : 0;
	}

	/** Apply on the server. Returns false if there was nothing to treat. */
	public static boolean apply(ServerPlayer player, Kind kind) {
		SurvivorState state = Survivors.get(player);
		BodyPart part = target(state, kind);
		if (part == null) {
			return false;
		}

		Wound wound = state.wounds.get(part);
		String messageKey;
		if (kind == Kind.DISINFECTANT) {
			wound.infected = false;
			wound.disinfected = true;
			messageKey = "outbreak.message.disinfected";
		} else {
			BandageType bandage = kind == Kind.RAG ? BandageType.RAG : BandageType.BANDAGE;
			wound.bandage = bandage;
			wound.bandageAge = 0;
			wound.bleeding = false;
			if (!wound.disinfected && player.getRandom().nextFloat() < bandage.infectionChance() * Perks.woundInfectionMultiplier(player)) {
				wound.infected = true;
			}

			messageKey = "outbreak.message.bandaged";
		}

		Survivors.sync(player);
		Characters.addXp(player, Skill.FIRST_AID, 8.0F);
		player.sendOverlayMessage(Component.translatable(messageKey, Component.translatable(part.translationKey())));
		return true;
	}
}
