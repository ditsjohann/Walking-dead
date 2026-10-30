package io.github.ditsjohann.outbreak.loot;

import io.github.ditsjohann.outbreak.Outbreak;
import io.github.ditsjohann.outbreak.block.StorageBlockEntity;
import io.github.ditsjohann.outbreak.sandbox.Sandbox;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import java.util.function.ToDoubleFunction;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * Scavenging. The first time anyone opens a container that a player didn't place, it gets loot:
 * chests and barrels by the room they're in (judged from the blocks around them), our own containers
 * by what they are (a fridge has food). Loot never comes back.
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
				prepare(serverLevel, pos, player);
				BlockState state = level.getBlockState(pos);
				if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
					prepare(serverLevel, ChestBlock.getConnectedBlockPos(pos, state), player);
				}
			}

			return InteractionResult.PASS;
		});
	}

	/**
	 * Fill the container at pos with loot, if it's due some: its loot table, then more or less of it
	 * as the sandbox loot setting (and the scenario, and the player's luck) says.
	 */
	public static void prepare(ServerLevel level, BlockPos pos, Player player) {
		BlockEntity blockEntity = level.getBlockEntity(pos);
		if (!(blockEntity instanceof ChestBlockEntity || blockEntity instanceof BarrelBlockEntity || blockEntity instanceof StorageBlockEntity)
			|| !(blockEntity instanceof RandomizableContainer container)
			|| blockEntity.hasAttached(PLAYER_PLACED)
			|| blockEntity.hasAttached(SCAVENGED)) {
			return;
		}

		blockEntity.setAttached(SCAVENGED, true);
		ResourceKey<LootTable> current = container.getLootTable();
		boolean vanillaLoot = current != null && !current.identifier().getNamespace().equals(Outbreak.MOD_ID);
		if (vanillaLoot || (current == null && container.isEmpty())) {
			// Our own containers have their own loot (a fridge has food); chests and barrels go by the room.
			ResourceKey<LootTable> table = blockEntity instanceof StorageBlockEntity storage
				? ResourceKey.create(Registries.LOOT_TABLE, Outbreak.id("containers/" + storage.kind().id()))
				: roomAt(level, pos).lootTable();
			container.setLootTable(table, level.getRandom().nextLong());
			container.unpackLootTable(player);
			scale(level, pos, container, table, player, Sandbox.current().lootMultiplier() * lootLuck.applyAsDouble(player));
		}

		blockEntity.setChanged();
	}

	/** How lucky a player is when scavenging (1 = normal). Set by the character system (the Lucky and Unlucky traits). */
	public static ToDoubleFunction<Player> lootLuck = player -> 1.0;

	/** Below 1, each stack has that chance to stay; above 1, the table is rolled again for the extra into the empty slots. */
	private static void scale(ServerLevel level, BlockPos pos, Container container, ResourceKey<LootTable> table, Player player, double multiplier) {
		RandomSource random = level.getRandom();
		if (multiplier < 1.0) {
			for (int slot = 0; slot < container.getContainerSize(); slot++) {
				if (!container.getItem(slot).isEmpty() && random.nextDouble() >= multiplier) {
					container.setItem(slot, ItemStack.EMPTY);
				}
			}

			return;
		}

		LootTable loot = level.getServer().reloadableRegistries().getLootTable(table);
		for (double extra = multiplier - 1.0; extra > 0.0; extra -= 1.0) {
			if (extra >= 1.0 || random.nextDouble() < extra) {
				LootParams params = new LootParams.Builder(level)
					.withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
					.withParameter(LootContextParams.THIS_ENTITY, player)
					.withLuck(player.getLuck())
					.create(LootContextParamSets.CHEST);
				loot.fill(container, params, random.nextLong());
			}
		}
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
