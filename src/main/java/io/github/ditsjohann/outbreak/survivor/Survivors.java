package io.github.ditsjohann.outbreak.survivor;

import io.github.ditsjohann.outbreak.Outbreak;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

import net.minecraft.world.entity.player.Player;

/** Access to a player's {@link SurvivorState}. Not copied on death: a new survivor starts healthy. */
public final class Survivors {
	public static final AttachmentType<SurvivorState> STATE = AttachmentRegistry.create(Outbreak.id("survivor"), builder -> builder
		.initializer(SurvivorState::new)
		.persistent(SurvivorState.CODEC)
		.syncWith(SurvivorState.CLIENT_STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

	private Survivors() {
	}

	public static void register() {
		// Loads the class so the attachment type is registered at startup.
	}

	/** The live state. On the server, call {@link #sync} after changing it. */
	public static SurvivorState get(Player player) {
		return player.getAttachedOrCreate(STATE);
	}

	/** Push the current state to the player's client. */
	public static void sync(Player player) {
		player.setAttached(STATE, get(player).copy());
	}
}
