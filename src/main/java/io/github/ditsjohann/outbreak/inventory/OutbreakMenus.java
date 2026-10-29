package io.github.ditsjohann.outbreak.inventory;

import java.util.EnumMap;
import java.util.Map;

import io.github.ditsjohann.outbreak.Outbreak;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

/** Menu types for every container kind, and opening chests and barrels in our screens instead of vanilla's. */
public final class OutbreakMenus {
	private static final Map<ContainerKind, MenuType<OutbreakMenu>> TYPES = new EnumMap<>(ContainerKind.class);

	static {
		for (ContainerKind kind : ContainerKind.values()) {
			register(kind);
		}
	}

	private OutbreakMenus() {
	}

	public static void register() {
		// Chests and barrels: same blocks and contents as vanilla, our screens. Runs after the loot hook.
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!(level instanceof ServerLevel) || player.isSpectator()
				|| (player.isSecondaryUseActive() && (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty()))) {
				return InteractionResult.PASS;
			}

			BlockPos pos = hit.getBlockPos();
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof ChestBlock chest) {
				Container container = ChestBlock.getContainer(chest, state, level, pos, false);
				if (container != null) {
					unpackLoot(level.getBlockEntity(pos), player);
					if (state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
						unpackLoot(level.getBlockEntity(ChestBlock.getConnectedBlockPos(pos, state)), player);
					}

					ContainerKind kind = container.getContainerSize() > ContainerKind.CUPBOARD.size() ? ContainerKind.WARDROBE : ContainerKind.CUPBOARD;
					open(player, kind, container, kind.title());
					player.awardStat(Stats.OPEN_CHEST);
				}

				return InteractionResult.SUCCESS;
			}

			if (state.getBlock() instanceof BarrelBlock && level.getBlockEntity(pos) instanceof Container barrel) {
				unpackLoot(level.getBlockEntity(pos), player);
				open(player, ContainerKind.CRATE, barrel, ContainerKind.CRATE.title());
				player.awardStat(Stats.OPEN_BARREL);
				return InteractionResult.SUCCESS;
			}

			return InteractionResult.PASS;
		});
	}

	private static void register(ContainerKind kind) {
		MenuType<OutbreakMenu>[] holder = new MenuType[1];
		holder[0] = Registry.register(BuiltInRegistries.MENU, Outbreak.id(kind.id()),
			new MenuType<>((id, inventory) -> new OutbreakMenu(holder[0], id, inventory, new SimpleContainer(kind.size()), kind), FeatureFlags.VANILLA_SET));
		TYPES.put(kind, holder[0]);
	}

	public static MenuType<OutbreakMenu> type(ContainerKind kind) {
		return TYPES.get(kind);
	}

	public static void open(Player player, ContainerKind kind, Container container, Component title) {
		player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new OutbreakMenu(type(kind), id, inventory, container, kind), title));
	}

	private static void unpackLoot(BlockEntity blockEntity, Player player) {
		if (blockEntity instanceof RandomizableContainerBlockEntity container) {
			container.unpackLootTable(player);
		}
	}
}
