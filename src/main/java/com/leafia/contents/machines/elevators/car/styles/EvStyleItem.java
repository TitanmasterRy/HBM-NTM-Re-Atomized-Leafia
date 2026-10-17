package com.leafia.contents.machines.elevators.car.styles;

import net.minecraft.world.item.Item;

/**
 * One panel of an elevator cabin, as an item a player slots into the car.
 *
 * <p>A cabin is assembled from six of these — a floor, a ceiling and four walls — and the car
 * stores the {@link #getStyleId() style id} of each in its own synched data, which is what a
 * renderer reads to decide which parts of the cabin model to draw.
 *
 * <p>Upstream derives the style id by chopping {@code "ev_"} off the registry name, and that is
 * kept here rather than passing the id in separately: two fields that must agree are one field
 * too many, and upstream's own ids ({@code s6wall}, {@code skyliftdoor}) are what a player's saved
 * cabin already holds.
 *
 * <h2>These have no texture, on purpose</h2>
 *
 * Every one of the six is drawn by {@code EvStyleItemRender} from the cabin OBJ — it binds a
 * different texture per named part and draws a little 3D preview of the panel. There is no flat
 * item texture anywhere in the clone for any of them, because none is ever used. That renderer is
 * not ported (D-1035: this port has no {@code renderPart}, and the OBJ carries no materials), so
 * these register with a <b>null creative tab</b> until it is. See D-1036.
 *
 * <p>Upstream's {@code AddonItemBase} superclass dissolves — it was translation key, registry
 * name, creative tab and an {@code ALL_ITEMS} list, all of which is {@code DeferredRegister}
 * here (D-1033).
 */
public class EvStyleItem extends Item {

	/** Which face of the cabin this panel goes on. */
	public enum StyleType { FLOOR, CEILING, WALL }

	private final String style;

	public final StyleType type;

	/**
	 * @param name the full registry name, {@code ev_} prefix and all — the style id is derived
	 *             from it exactly as upstream derives it.
	 */
	public EvStyleItem(Properties props, String name, StyleType type) {
		super(props);
		this.type = type;
		this.style = name.substring(3);
	}

	/** The id the car stores for this panel: {@code s6wall}, {@code s6floor}, {@code skyliftdoor}, … */
	public String getStyleId() {
		return style;
	}
}
