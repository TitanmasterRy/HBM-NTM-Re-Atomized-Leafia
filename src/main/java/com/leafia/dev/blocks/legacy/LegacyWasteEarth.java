package com.leafia.dev.blocks.legacy;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Contaminated dirt and scorched grass, in seven levels.
 *
 * <p>Ported from {@code com.leafia.dev.blocks.legacy.LegacyWasteEarth}, which extends NTM-CE's
 * {@code WasteEarth} (read out of the 1.5.1.8 jar with Vineflower). The CE class does four things,
 * and for these two blocks only the first applies:
 *
 * <ul>
 * <li>carries {@code PropertyInteger meta} 0 to 6 - here {@link #META};
 * <li>{@code onEntityWalk}: a potion for {@code waste_mycelium}, {@code frozen_grass} and
 *     {@code burning_earth} by identity - none is this block, so nothing happens;
 * <li>{@code updateTick}: clears {@code waste_earth} and {@code waste_mycelium} to dirt - not
 *     these two, so the random tick the constructor asks for ({@code tick = true}) does nothing and
 *     is not requested here;
 * <li>{@code canEntitySpawn}: only radiation-immune things spawn on it, {@link RadImmuneSpawn};
 * <li>drops: {@code quantityDropped} 1, and the item of the block at <b>damage 0</b>, because CE's
 *     class overrides no {@code damageDropped} - so a level 5 block drops a level 0 one. Kept.
 * </ul>
 *
 * <p>{@code scorched_earth} is built with {@code getBlockState().getBaseState().withProperty(META, 2)}
 * as its default, and its blockstate file names levels 2 to 6 only, so its levels 0 and 1 have
 * no model upstream. They exist as states here too (the property is shared with the dirt), draw as
 * level 2, and have no item: only levels 2 to 6 are ever produced.
 */
public class LegacyWasteEarth extends Block implements MetaBlock {

	public static final EnumProperty<MetaLevel> META = MetaLevel.LEVELS_7;

	public LegacyWasteEarth(Properties properties) {
		this(properties, MetaLevel.L0);
	}

	public LegacyWasteEarth(Properties properties, MetaLevel defaultLevel) {
		super(properties);
		registerDefaultState(getStateDefinition().any().setValue(META, defaultLevel));
	}

	@Override
	public EnumProperty<MetaLevel> getVariantProperty() {
		return META;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(META);
	}

	@Override
	public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
		return getVariantStack(state);
	}
}
