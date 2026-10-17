package com.leafia.dev.blocks;

import net.minecraft.world.item.BlockItem;

/**
 * Ported from `com.leafia.dev.blocks.ICustomItemBlockProvider`.
 *
 * <p>A block that supplies its own item form rather than taking a plain one. {@code ItemBlock}
 * is {@link BlockItem} in 1.21.1; the implementor now has to supply {@code Item.Properties}
 * too, which is a detail on their side rather than a change to this contract.
 */
public interface ICustomItemBlockProvider {
	BlockItem provideItem();
}
