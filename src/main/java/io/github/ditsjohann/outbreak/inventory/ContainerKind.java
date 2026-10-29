package io.github.ditsjohann.outbreak.inventory;

import java.util.Locale;

import net.minecraft.network.chat.Component;

/**
 * Every kind of container has its own screen: its own size, layout and look. The layout is shared by
 * the menu (both sides) and the screen; the look is a texture per kind (tools/textures/hudart.py).
 */
public enum ContainerKind {
	SCHOOL_BAG(9, 2),
	HIKING_BAG(9, 3),
	/** A vanilla chest. */
	CUPBOARD(9, 3),
	/** A vanilla double chest. */
	WARDROBE(9, 6),
	/** A vanilla barrel. */
	CRATE(9, 3),
	FRIDGE(6, 3),
	KITCHEN_CUPBOARD(6, 2),
	TOOLBOX(5, 2),
	METAL_CABINET(9, 3),
	/** A hatchback's trunk. */
	TRUNK(9, 2),
	/** A sedan's trunk. */
	LARGE_TRUNK(9, 3),
	/** The back of a van. */
	VAN_CARGO(9, 6);

	public static final int SCREEN_WIDTH = 176;
	private static final int CONTAINER_TOP = 18;

	private final int columns;
	private final int rows;

	ContainerKind(int columns, int rows) {
		this.columns = columns;
		this.rows = rows;
	}

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	public int columns() {
		return this.columns;
	}

	public int rows() {
		return this.rows;
	}

	public int size() {
		return this.columns * this.rows;
	}

	public boolean isBag() {
		return this == SCHOOL_BAG || this == HIKING_BAG;
	}

	public Component title() {
		return Component.translatable("container.outbreak." + this.id());
	}

	/** X of the first container slot: the grid is centred. */
	public int containerLeft() {
		return SCREEN_WIDTH / 2 - this.columns * 9 + 1;
	}

	public int containerTop() {
		return CONTAINER_TOP;
	}

	public int pocketsTop() {
		return CONTAINER_TOP + this.rows * 18 + 16;
	}

	public int hotbarTop() {
		return this.pocketsTop() + 22;
	}

	public int screenHeight() {
		return this.hotbarTop() + 18 + 8;
	}
}
