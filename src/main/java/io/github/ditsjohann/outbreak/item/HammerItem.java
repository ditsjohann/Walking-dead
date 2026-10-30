package io.github.ditsjohann.outbreak.item;

import io.github.ditsjohann.outbreak.block.BarricadeBlock;
import io.github.ditsjohann.outbreak.block.Barricades;
import io.github.ditsjohann.outbreak.block.OutbreakBlocks;
import io.github.ditsjohann.outbreak.character.Characters;
import io.github.ditsjohann.outbreak.character.Perks;
import io.github.ditsjohann.outbreak.character.Skill;
import io.github.ditsjohann.outbreak.noise.NoiseSystem;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A hammer: a weapon, and the tool for barricades. Right-click a window or door to nail a plank in
 * front of it (1 plank + 2 nails, up to 4 planks). Sneak-right-click a barricade to pry a plank off.
 * Hammering is loud.
 */
public class HammerItem extends Item {
	public static final int NAILS_PER_PLANK = 2;
	private static final float HAMMERING_NOISE = 20.0F;

	public HammerItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		Player player = context.getPlayer();
		if (player == null) {
			return InteractionResult.PASS;
		}

		BlockPos clicked = context.getClickedPos();
		BlockState clickedState = level.getBlockState(clicked);
		boolean onBarricade = clickedState.is(OutbreakBlocks.BARRICADE);

		if (onBarricade && player.isSecondaryUseActive()) {
			if (level instanceof ServerLevel serverLevel) {
				Barricades.removePlank(serverLevel, clicked, clickedState, true);
				hammered(serverLevel, clicked, player, context);
				if (player instanceof ServerPlayer serverPlayer) {
					Characters.addXp(serverPlayer, Skill.CARPENTRY, 2.0F);
				}
			}

			return InteractionResult.SUCCESS;
		}

		BlockPos target;
		BlockState newState;
		if (onBarricade) {
			int planks = clickedState.getValue(BarricadeBlock.PLANKS);
			if (planks >= BarricadeBlock.MAX_PLANKS) {
				return InteractionResult.FAIL;
			}

			target = clicked;
			newState = clickedState.setValue(BarricadeBlock.PLANKS, planks + 1);
		} else {
			Direction face = context.getClickedFace();
			if (face.getAxis().isVertical()) {
				return InteractionResult.PASS;
			}

			target = clicked.relative(face);
			if (!level.getBlockState(target).canBeReplaced()) {
				return InteractionResult.FAIL;
			}

			newState = OutbreakBlocks.BARRICADE.defaultBlockState().setValue(BarricadeBlock.FACING, face);
		}

		if (!player.hasInfiniteMaterials() && !hasMaterials(player.getInventory())) {
			if (!level.isClientSide()) {
				player.sendOverlayMessage(Component.translatable("outbreak.message.need_planks_and_nails"));
			}

			return InteractionResult.FAIL;
		}

		if (level instanceof ServerLevel serverLevel) {
			// A good carpenter sometimes gets it up with the scraps (no plank or nails used).
			if (!player.hasInfiniteMaterials() && player.getRandom().nextFloat() >= Perks.sparedMaterials(player)) {
				consumeMaterials(player.getInventory());
			}

			serverLevel.setBlock(target, newState, Block.UPDATE_ALL);
			hammered(serverLevel, target, player, context);
			if (player instanceof ServerPlayer serverPlayer) {
				Characters.addXp(serverPlayer, Skill.CARPENTRY, 6.0F);
			}
		}

		return InteractionResult.SUCCESS;
	}

	private static void hammered(ServerLevel level, BlockPos pos, Player player, UseOnContext context) {
		level.playSound(null, pos, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.PLAYERS, 0.8F, 1.3F);
		NoiseSystem.emit(level, Vec3.atCenterOf(pos), HAMMERING_NOISE);
		ItemStack hammer = context.getItemInHand();
		hammer.hurtAndBreak(1, player, context.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
	}

	private static boolean hasMaterials(Inventory inventory) {
		return count(inventory, true) >= 1 && count(inventory, false) >= NAILS_PER_PLANK;
	}

	private static int count(Inventory inventory, boolean planks) {
		int count = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (planks ? stack.is(ItemTags.PLANKS) : stack.is(OutbreakItems.NAILS)) {
				count += stack.getCount();
			}
		}

		return count;
	}

	private static void consumeMaterials(Inventory inventory) {
		int planks = 1;
		int nails = NAILS_PER_PLANK;
		for (int i = 0; i < inventory.getContainerSize() && (planks > 0 || nails > 0); i++) {
			ItemStack stack = inventory.getItem(i);
			if (planks > 0 && stack.is(ItemTags.PLANKS)) {
				int take = Math.min(planks, stack.getCount());
				stack.shrink(take);
				planks -= take;
			} else if (nails > 0 && stack.is(OutbreakItems.NAILS)) {
				int take = Math.min(nails, stack.getCount());
				stack.shrink(take);
				nails -= take;
			}
		}
	}
}
