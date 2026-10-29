package io.github.ditsjohann.outbreak.inventory;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * A survivor has 18 slots on their body: the 9-slot hotbar and 9 pockets (inventory slots 0-17).
 * The rest of vanilla's inventory (slots 18-35) is locked: everything else goes in bags and containers.
 */
public final class Pockets {
	public static final int SLOTS = 18;
	private static final int VANILLA_MAIN_END = Inventory.INVENTORY_SIZE;

	private Pockets() {
	}

	public static void register() {
		// Once a second is enough to catch anything that ended up in a locked slot (old worlds, commands).
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 == 0) {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					if (!player.isCreative() && !player.isSpectator()) {
						emptyLockedSlots(player);
					}
				}
			}
		});
	}

	/** Inventory slots that don't exist for a survivor. */
	public static boolean isLocked(int slot) {
		return slot >= SLOTS && slot < VANILLA_MAIN_END;
	}

	/** Anything in a locked slot moves to a free pocket, or drops at your feet. */
	public static void emptyLockedSlots(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		for (int slot = SLOTS; slot < VANILLA_MAIN_END; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}

			inventory.setItem(slot, ItemStack.EMPTY);
			if (!inventory.add(stack)) {
				player.drop(stack, false, Prediction.SERVER_ONLY);
			}
		}
	}
}
