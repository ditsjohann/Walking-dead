package io.github.ditsjohann.outbreak.combat;

import java.util.function.Consumer;

import com.mojang.serialization.Codec;

import io.github.ditsjohann.outbreak.Outbreak;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * A gun. Hold right-click to aim (tighter, a closer look, the gun up at your shoulder); left-click
 * to fire; R to reload from the ammo in your pockets. How many rounds are in it is kept on the gun.
 */
public class GunItem extends Item {
	public static final DataComponentType<Integer> ROUNDS = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Outbreak.id("rounds"),
		DataComponentType.<Integer>builder().persistent(Codec.intRange(0, 64)).networkSynchronized(ByteBufCodecs.VAR_INT).build());

	public final GunType type;

	public GunItem(GunType type, Properties properties) {
		super(properties);
		this.type = type;
	}

	public static int rounds(ItemStack stack) {
		return stack.getOrDefault(ROUNDS, 0);
	}

	public static void setRounds(ItemStack stack, int rounds) {
		stack.set(ROUNDS, rounds);
	}

	/** Aiming: held as long as right-click is (see Guns for the shot itself). */
	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return 72000;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.BOW;
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag) {
		lines.accept(Component.translatable("outbreak.gun.rounds", rounds(stack), this.type.capacity).withStyle(ChatFormatting.GRAY));
		lines.accept(Component.translatable("outbreak.gun.ammo", this.type.ammo().getDefaultInstance().getHoverName()).withStyle(ChatFormatting.DARK_GRAY));
	}
}
