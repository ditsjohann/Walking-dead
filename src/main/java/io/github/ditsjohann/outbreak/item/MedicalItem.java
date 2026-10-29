package io.github.ditsjohann.outbreak.item;

import io.github.ditsjohann.outbreak.survivor.Survivors;
import io.github.ditsjohann.outbreak.survivor.Treatment;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Rags, bandages and disinfectant: hold use for a moment to treat your worst wound. */
public class MedicalItem extends Item {
	private final Treatment.Kind kind;

	public MedicalItem(Treatment.Kind kind, Properties properties) {
		super(properties);
		this.kind = kind;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (Treatment.target(Survivors.get(player), this.kind) == null) {
			if (!level.isClientSide()) {
				player.sendOverlayMessage(Component.translatable("outbreak.message.nothing_to_treat"));
			}

			return InteractionResult.FAIL;
		}

		return super.use(level, player, hand);
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
		if (user instanceof ServerPlayer player && Treatment.apply(player, this.kind) && !player.hasInfiniteMaterials()) {
			stack.shrink(1);
		}

		return stack;
	}
}
