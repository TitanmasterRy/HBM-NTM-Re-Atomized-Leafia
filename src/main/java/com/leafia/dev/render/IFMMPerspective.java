package com.leafia.dev.render;

import net.minecraft.world.item.Item;

/**
 * Ported from `com.leafia.dev.render.IFMMPerspective`.
 *
 * <p>Asks whether an item should use the addon's "FMM" held-item perspective rather than the
 * default one. Named by five upstream renderers.
 */
public interface IFMMPerspective {
	boolean useFMMPerspective(Item item);
}
