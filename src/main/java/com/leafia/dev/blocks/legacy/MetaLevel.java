package com.leafia.dev.blocks.legacy;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Upstream's metadata byte for the residue blocks, as a blockstate value.
 *
 * <p>Not an upstream file. NTM-CE's {@code BlockMeta} and the {@code WasteEarth}/{@code WasteSand}
 * family carry a {@code PropertyInteger "meta"} of 0 to 6 (0 to 15 on {@code BlockMeta} itself,
 * narrowed by each block's {@code META_COUNT}), and 1.21.1 has no metadata. The base mod's answer
 * for a block with a handful of finishes is {@code IBlockMulti}: an enum property and one item per
 * value, which is what these blocks use. The property keeps upstream's name, {@code meta}, so a
 * blockstate file reads {@code "meta=3"} exactly as upstream's did, and the serialised names are
 * the numbers.
 *
 * <p>Two value sets are shared: {@link #LEVELS_6} for baleonite and digammitite
 * ({@code LEVELS = 6}) and {@link #LEVELS_7} for everything else (the contamination blocks'
 * {@code META_COUNT} of 7). Both must be {@code static final} constants rather than per-block
 * fields, because a block's state definition is built inside {@code Block}'s constructor, before
 * any field of a subclass is assigned.
 */
public enum MetaLevel implements StringRepresentable {
	L0, L1, L2, L3, L4, L5, L6;

	public static final EnumProperty<MetaLevel> LEVELS_6 = EnumProperty.create("meta", MetaLevel.class, level -> level.ordinal() <= 5);
	public static final EnumProperty<MetaLevel> LEVELS_7 = EnumProperty.create("meta", MetaLevel.class);

	@Override
	public String getSerializedName() {
		return Integer.toString(ordinal());
	}

	/** The level with this metadata number. */
	public static MetaLevel of(int meta) {
		return values()[meta];
	}
}
