package com.leafia.contents.parts.depleteds;

import com.hbm.items.machine.ItemDepletedFuel;

/**
 * A depleted fuel the addon adds beside the base mod's: the waste a bred, mid-enriched, high-enriched,
 * pure or solinium Schrabidium rod leaves, which the base mod's sixteen have no entry for.
 *
 * <p>Upstream: {@code com/leafia/contents/parts/depleteds/AddonDepletedFuelItem.java}. Upstream's class
 * is a {@code ItemDepletedFuel} that files itself in the addon's item list and bakes a model by hand
 * from a texture path and a domain, because a 1.12.2 item cannot say "draw {@code hbm:waste_uranium}"
 * without a model bake. Both halves are gone here: registration is {@code LeafiaRods}'s, and the model
 * is an {@code item/generated} JSON naming the base mod's texture.
 * What is left is the base class, which is the whole behaviour: a stack with {@code cooling} on its
 * {@code custom_data} reads tan with a "still cooling" line, and the item replaces its dropped entity
 * with one that fire and lava cannot destroy ({@code ItemNuclearWaste}).
 *
 * <p>The tint that makes the cooling stack tan is a client colour handler, registered by
 * {@code LeafiaRodRender} for these five exactly as the base mod does for its own sixteen.
 */
public class AddonDepletedFuelItem extends ItemDepletedFuel {

	/** The texture this fuel is drawn with, {@code namespace:path} under {@code textures/item/}. */
	public final String texture;

	public AddonDepletedFuelItem(Properties properties, String namespace, String texturePath) {
		super(properties);
		this.texture = namespace + ":item/" + texturePath;
	}
}
