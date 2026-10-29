package io.github.ditsjohann.outbreak.survivor;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Temporary: tools can't be crafted any more and there's no loot system yet, so every new survivor
 * starts with a worn axe and pickaxe. Replaced by professions and scavenging in later phases.
 */
public final class StarterKit {
	private static final String RECEIVED_TAG = "outbreak.starter_kit";

	private StarterKit() {
	}

	public static void register() {
		ServerPlayerEvents.JOIN.register(player -> {
			if (player.addTag(RECEIVED_TAG)) {
				give(player);
			}
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (!alive) {
				newPlayer.addTag(RECEIVED_TAG);
				give(newPlayer);
			}
		});
	}

	private static void give(ServerPlayer player) {
		player.getInventory().add(worn(Items.IRON_AXE, 0.4F));
		player.getInventory().add(worn(Items.STONE_PICKAXE, 0.5F));
		player.getInventory().add(new ItemStack(Items.BREAD, 3));
		player.getInventory().add(new ItemStack(Items.TORCH, 6));
	}

	private static ItemStack worn(net.minecraft.world.item.Item item, float wear) {
		ItemStack stack = new ItemStack(item);
		stack.setDamageValue((int) (stack.getMaxDamage() * wear));
		return stack;
	}
}
