package io.github.ditsjohann.outbreak.loot;

import io.github.ditsjohann.outbreak.Outbreak;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Scavenging. The first time anyone opens a chest or barrel that a player didn't place, it gets
 * loot that fits the room it's in, judged from the blocks around it. Loot never comes back.
 */
public final class ContainerLoot {
	/** Set on containers a player placed: those are never filled. */
	public static final AttachmentType<Boolean> PLAYER_PLACED = AttachmentRegistry.createPersistent(Outbreak.id("player_placed"), Codec.BOOL);
	/** Set once a container has had its turn at loot. */
	public static final AttachmentType<Boolean> SCAVENGED = AttachmentRegistry.createPersistent(Outbreak.id("scavenged"), Codec.BOOL);

	private static final int SCAN_RADIUS = 4;

	public enum Room {
		HOUSEHOLD,
		KITCHEN,
		BEDROOM,
		WORKSHOP,
		STUDY;

		public ResourceKey<LootTable> lootTable() {
			return ResourceKey.create(Registries.LOOT_TABLE, Outbreak.id("containers/" + this.name().toLowerCase(java.util.Locale.ROOT)));
		}
	}

	private ContainerLoot() {
	}

	public static void register() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level instanceof ServerLevel serverLevel && !player.isSpectator()) {
				BlockPos pos = hit.getBlockPos();
				prepare(serverLevel, pos);
				BlockState state = level.getBlockState(pos);
				if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
					prepare(serverLevel, ChestBlock.getConnectedBlockPos(pos, state));
				}
			}

			return InteractionResult.PASS;
		});
	}

	/** Give the container at pos its loot table, if it's due one. Vanilla fills it as it opens. */
	public static void prepare(ServerLevel level, BlockPos pos) {
		BlockEntity blockEntity = level.getBlockEntity(pos);
		if (!(blockEntity instanceof ChestBlockEntity || blockEntity instanceof BarrelBlockEntity)
			|| !(blockEntity instanceof RandomizableContainer container)
			|| blockEntity.hasAttached(PLAYER_PLACED)
			|| blockEntity.hasAttached(SCAVENGED)) {
			return;
		}

		blockEntity.setAttached(SCAVENGED, true);
		ResourceKey<LootTable> current = container.getLootTable();
		boolean vanillaLoot = current != null && !current.identifier().getNamespace().equals(Outbreak.MOD_ID);
		if (vanillaLoot || (current == null && container.isEmpty())) {
			container.setLootTable(roomAt(level, pos).lootTable(), level.getRandom().nextLong());
		}

		blockEntity.setChanged();
	}

	/** Guess the room from what's nearby: the kind of block that turns up most decides. */
	public static Room roomAt(ServerLevel level, BlockPos center) {
		int[] score = new int[Room.values().length];
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-SCAN_RADIUS, -1, -SCAN_RADIUS), center.offset(SCAN_RADIUS, 2, SCAN_RADIUS))) {
			BlockState state = level.getBlockState(pos);
			if (state.isAir()) {
				continue;
			}

			if (state.is(BlockTags.BEDS)) {
				score[Room.BEDROOM.ordinal()] += 2;
			} else if (state.is(Blocks.FURNACE) || state.is(Blocks.SMOKER) || state.is(BlockTags.CAULDRONS) || state.is(BlockTags.CAMPFIRES)) {
				score[Room.KITCHEN.ordinal()] += 2;
			} else if (state.is(Blocks.CRAFTING_TABLE) || state.is(BlockTags.ANVIL) || state.is(Blocks.GRINDSTONE)
				|| state.is(Blocks.SMITHING_TABLE) || state.is(Blocks.STONECUTTER) || state.is(Blocks.BLAST_FURNACE)) {
				score[Room.WORKSHOP.ordinal()] += 2;
			} else if (state.is(Blocks.BOOKSHELF) || state.is(Blocks.CHISELED_BOOKSHELF) || state.is(Blocks.LECTERN)) {
				score[Room.STUDY.ordinal()] += 1;
			}
		}

		Room best = Room.HOUSEHOLD;
		int bestScore = 1;
		for (Room room : Room.values()) {
			if (score[room.ordinal()] > bestScore) {
				bestScore = score[room.ordinal()];
				best = room;
			}
		}

		return best;
	}
}
