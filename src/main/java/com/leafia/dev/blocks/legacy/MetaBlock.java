package com.leafia.dev.blocks.legacy;

import com.hbm.blocks.IBlockMulti;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A residue block whose upstream metadata is a blockstate level (see {@link MetaLevel}).
 *
 * <p>Not an upstream file: it is {@link IBlockMulti} with the two things the residue blocks need
 * that the base interface does not give. Its {@code getVariants()} reads the <em>property's own
 * values</em> rather than the enum class, because baleonite and digammitite have six levels of
 * the enum's seven, and an item registered for a value the property rejects would place a state
 * that does not exist. And it keeps upstream's way of naming a state, {@code getStateFromMeta},
 * as {@link #withMeta}, which the fallout table and the PWR meltdown both use.
 */
public interface MetaBlock extends IBlockMulti<MetaLevel> {

	@Override
	default MetaLevel[] getVariants() {
		return getVariantProperty().getPossibleValues().toArray(new MetaLevel[0]);
	}

	/** Upstream's metadata number of this state. */
	default int meta(BlockState state) {
		return state.getValue(getVariantProperty()).ordinal();
	}

	/**
	 * Upstream's {@code getStateFromMeta}: the block's default state at this level. A level the
	 * block does not have throws, where 1.12.2 would have produced a state with no model; callers
	 * pass numbers they computed from the block's own range.
	 */
	default BlockState withMeta(int meta) {
		return ((Block) this).defaultBlockState().setValue(getVariantProperty(), MetaLevel.of(meta));
	}

	/** {@code new ItemStack(block, 1, meta)}: the item of that level. */
	default ItemStack stackOf(int meta) {
		return getVariantStack(withMeta(meta));
	}
}
