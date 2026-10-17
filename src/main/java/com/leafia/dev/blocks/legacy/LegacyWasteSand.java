package com.leafia.dev.blocks.legacy;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Contaminated sand, red sand and gravel, in seven levels: sand that falls.
 *
 * <p>Ported from {@code com.leafia.dev.blocks.legacy.LegacyWasteSand}, which extends NTM-CE's
 * {@code WasteSand}, a {@code BlockFalling} (read out of the 1.5.1.8 jar with Vineflower). The base
 * mod has no {@code WasteSand}, and the ledger's answer was "vanilla": 1.21.1's {@link FallingBlock}
 * already does the falling - the two-tick delay after placement and after any neighbour change,
 * and the entity handoff - tick for tick, as the base mod's own {@code BlockNTMSand} relies on.
 * What CE's class adds on top is:
 *
 * <ul>
 * <li>{@code PropertyInteger meta} 0 to 6 - {@link #META};
 * <li>{@code onEntityWalk}: a 49-level radiation potion for {@code waste_trinitite} and
 *     {@code waste_trinitite_red} by identity - neither is a legacy block, so nothing happens;
 * <li>{@code canEntitySpawn}: only radiation-immune things, {@link RadImmuneSpawn};
 * <li>drops: one of the block, at <b>damage 0</b>, because CE overrides no {@code damageDropped}
 *     (a level 4 gravel drops a level 0 gravel). Kept, in the loot table.
 * </ul>
 *
 * <p>The falling dust takes the block's own map colour, the base mod's convention for a falling
 * block upstream gave no dust colour (D-107).
 */
public class LegacyWasteSand extends FallingBlock implements MetaBlock {

	public static final EnumProperty<MetaLevel> META = MetaLevel.LEVELS_7;
	public static final MapCodec<LegacyWasteSand> CODEC = simpleCodec(LegacyWasteSand::new);

	public LegacyWasteSand(Properties properties) {
		super(properties);
		registerDefaultState(getStateDefinition().any().setValue(META, MetaLevel.L0));
	}

	@Override
	protected MapCodec<? extends FallingBlock> codec() {
		return CODEC;
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

	@Override
	public int getDustColor(BlockState state, BlockGetter level, BlockPos pos) {
		return state.getMapColor(level, pos).col;
	}
}
