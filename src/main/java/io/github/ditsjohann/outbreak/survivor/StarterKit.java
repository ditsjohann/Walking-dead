package io.github.ditsjohann.outbreak.survivor;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * What test players (Carpet's fake players, the client game tests) start with, since they get a
 * plain survivor instead of making one: a worn axe and pickaxe, bread and torches. Real players get
 * their profession's things (see Profession#kit).
 */
public final class StarterKit {
	private StarterKit() {
	}

	public static void giveTestKit(ServerPlayer player) {
		player.getInventory().add(worn(Items.IRON_AXE, 0.4F));
		player.getInventory().add(worn(Items.STONE_PICKAXE, 0.5F));
		player.getInventory().add(new ItemStack(Items.BREAD, 3));
		player.getInventory().add(new ItemStack(Items.TORCH, 6));
	}

	private static ItemStack worn(Item item, float wear) {
		ItemStack stack = new ItemStack(item);
		stack.setDamageValue((int) (stack.getMaxDamage() * wear));
		return stack;
	}
}
