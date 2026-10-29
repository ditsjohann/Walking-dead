package io.github.ditsjohann.outbreak.vehicle;

import org.jspecify.annotations.Nullable;

import io.github.ditsjohann.outbreak.Outbreak;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Working on a vehicle (sneak-right-click it): its tires and battery as slots on a sketch of the
 * vehicle, the state of everything else, and repairs. Changing parts takes tools: a jack and a
 * wrench for car tires, a wrench for scooter tires and batteries; bicycle tires come off by hand.
 */
public class MechanicsMenu extends AbstractContainerMenu {
	public static final ExtendedMenuType<MechanicsMenu, Integer> TYPE = Registry.register(BuiltInRegistries.MENU, Outbreak.id("mechanics"),
		new ExtendedMenuType<>(MechanicsMenu::new, ByteBufCodecs.VAR_INT));

	public static final int BUTTON_REPAIR_ENGINE = 0;
	public static final int BUTTON_FIX_WINDOWS = 1;
	public static final int ENGINE_PARTS_REPAIR = 15;
	public static final int GLASS_PER_REPAIR = 2;
	public static final int WINDOWS_PER_REPAIR = 50;

	public static final int SCREEN_WIDTH = 176;
	public static final int POCKETS_TOP = 124;
	public static final int HOTBAR_TOP = POCKETS_TOP + 22;
	public static final int SCREEN_HEIGHT = HOTBAR_TOP + 26;

	private final @Nullable Vehicle vehicle;
	private final Container parts;
	private final int partSlots;

	private MechanicsMenu(int containerId, Inventory inventory, Integer vehicleId) {
		this(containerId, inventory, inventory.player.level().getEntity(vehicleId) instanceof Vehicle vehicle ? vehicle : null);
	}

	private MechanicsMenu(int containerId, Inventory inventory, @Nullable Vehicle vehicle) {
		super(TYPE, containerId);
		this.vehicle = vehicle;
		this.parts = vehicle != null ? vehicle.parts() : new SimpleContainer(0);
		this.partSlots = this.parts.getContainerSize();
		Player player = inventory.player;
		if (vehicle != null) {
			VehicleType type = vehicle.vehicleType();
			int[][] positions = slotPositions(type);
			for (int i = 0; i < this.partSlots; i++) {
				boolean battery = i == type.batterySlot();
				Item item = battery ? VehicleItems.CAR_BATTERY : vehicle.tireItem();
				this.addSlot(new PartSlot(this.parts, i, positions[i][0], positions[i][1], item, player, type, battery));
			}
		}

		for (int i = 0; i < 9; i++) {
			this.addSlot(new Slot(inventory, 9 + i, 8 + i * 18, POCKETS_TOP));
		}

		for (int i = 0; i < 9; i++) {
			this.addSlot(new Slot(inventory, i, 8 + i * 18, HOTBAR_TOP));
		}
	}

	public static void open(ServerPlayer player, Vehicle vehicle) {
		player.openMenu(new ExtendedMenuProvider<Integer>() {
			@Override
			public Integer getScreenOpeningData(ServerPlayer serverPlayer) {
				return vehicle.getId();
			}

			@Override
			public Component getDisplayName() {
				return vehicle.describe();
			}

			@Override
			public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player menuPlayer) {
				return new MechanicsMenu(containerId, inventory, vehicle);
			}
		});
	}

	/** Where each part slot sits on the sketch: front of the vehicle at the top. */
	public static int[][] slotPositions(VehicleType type) {
		if (type.isCar()) {
			return new int[][] {{18, 34}, {66, 34}, {18, 92}, {66, 92}, {42, 22}};
		}

		return new int[][] {{42, 20}, {42, 98}, {42, 59}};
	}

	public @Nullable Vehicle vehicle() {
		return this.vehicle;
	}

	/** What the player still needs to take off or put on a part in this slot, or null if they have it all. */
	public static @Nullable Component missingTools(Player player, VehicleType type, boolean battery) {
		boolean wrench = has(player, VehicleItems.WRENCH);
		boolean jack = has(player, VehicleItems.JACK);
		if (battery) {
			return wrench ? null : Component.translatable("outbreak.mechanics.needs_wrench");
		}

		return switch (type) {
			case BICYCLE -> null;
			case SCOOTER -> wrench ? null : Component.translatable("outbreak.mechanics.needs_wrench");
			default -> wrench && jack ? null : Component.translatable("outbreak.mechanics.needs_jack_and_wrench");
		};
	}

	private static boolean has(Player player, Item item) {
		return player.getAbilities().instabuild || player.getInventory().hasAnyMatching(stack -> stack.is(item));
	}

	private static @Nullable ItemStack find(Player player, Item item) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(item)) {
				return stack;
			}
		}

		return null;
	}

	@Override
	public boolean clickMenuButton(Player player, int id) {
		Vehicle vehicle = this.vehicle;
		if (vehicle == null || !(player instanceof ServerPlayer serverPlayer)) {
			return false;
		}

		ItemStack wrench = find(player, VehicleItems.WRENCH);
		ItemStack screwdriver = find(player, VehicleItems.SCREWDRIVER);
		switch (id) {
			case BUTTON_REPAIR_ENGINE -> {
				ItemStack engineParts = find(player, VehicleItems.ENGINE_PARTS);
				if (!vehicle.vehicleType().hasEngine || vehicle.engine() >= 100) {
					return false;
				}

				if (vehicle.isWreck()) {
					serverPlayer.sendOverlayMessage(Component.translatable("outbreak.mechanics.burnt_out"));
					return false;
				}

				if (wrench == null || engineParts == null) {
					serverPlayer.sendOverlayMessage(Component.translatable("outbreak.mechanics.needs_engine_parts"));
					return false;
				}

				engineParts.shrink(1);
				wrench.hurtAndBreak(2, serverPlayer, InteractionHand.MAIN_HAND.asEquipmentSlot());
				vehicle.setEngine(vehicle.engine() + ENGINE_PARTS_REPAIR);
				player.level().playSound(null, vehicle.getX(), vehicle.getY(), vehicle.getZ(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.5F, 1.4F);
				return true;
			}
			case BUTTON_FIX_WINDOWS -> {
				if (!vehicle.vehicleType().hasWindows || vehicle.windows() >= 100) {
					return false;
				}

				if (screwdriver == null || player.getInventory().countItem(Items.GLASS_PANE) < GLASS_PER_REPAIR) {
					serverPlayer.sendOverlayMessage(Component.translatable("outbreak.mechanics.needs_glass"));
					return false;
				}

				int left = GLASS_PER_REPAIR;
				for (int i = 0; i < player.getInventory().getContainerSize() && left > 0; i++) {
					ItemStack stack = player.getInventory().getItem(i);
					if (stack.is(Items.GLASS_PANE)) {
						int take = Math.min(left, stack.getCount());
						stack.shrink(take);
						left -= take;
					}
				}

				screwdriver.hurtAndBreak(2, serverPlayer, InteractionHand.MAIN_HAND.asEquipmentSlot());
				vehicle.setWindows(vehicle.windows() + WINDOWS_PER_REPAIR);
				player.level().playSound(null, vehicle.getX(), vehicle.getY(), vehicle.getZ(), SoundEvents.GLASS_PLACE, SoundSource.PLAYERS, 1.0F, 1.0F);
				return true;
			}
			default -> {
				return false;
			}
		}
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		ItemStack clicked = ItemStack.EMPTY;
		Slot slot = this.slots.get(slotIndex);
		if (slot.hasItem()) {
			ItemStack stack = slot.getItem();
			clicked = stack.copy();
			if (slotIndex < this.partSlots) {
				if (!this.moveItemStackTo(stack, this.partSlots, this.slots.size(), true)) {
					return ItemStack.EMPTY;
				}
			} else if (!this.moveItemStackTo(stack, 0, this.partSlots, false)) {
				return ItemStack.EMPTY;
			}

			if (stack.isEmpty()) {
				slot.setByPlayer(ItemStack.EMPTY);
			} else {
				slot.setChanged();
			}
		}

		return clicked;
	}

	@Override
	public boolean stillValid(Player player) {
		return this.vehicle != null && this.vehicle.isAlive() && player.distanceToSqr(this.vehicle) < 64.0;
	}

	/** A tire or battery slot. Only takes the right part, and only with the right tools in your inventory. */
	public static final class PartSlot extends Slot {
		private final Item item;
		private final Player player;
		private final VehicleType type;
		private final boolean battery;

		PartSlot(Container container, int index, int x, int y, Item item, Player player, VehicleType type, boolean battery) {
			super(container, index, x, y);
			this.item = item;
			this.player = player;
			this.type = type;
			this.battery = battery;
		}

		public boolean isBattery() {
			return this.battery;
		}

		public @Nullable Component missingTools() {
			return MechanicsMenu.missingTools(this.player, this.type, this.battery);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return stack.is(this.item) && this.missingTools() == null;
		}

		@Override
		public boolean mayPickup(Player player) {
			return this.missingTools() == null;
		}

		@Override
		public int getMaxStackSize() {
			return 1;
		}
	}
}
