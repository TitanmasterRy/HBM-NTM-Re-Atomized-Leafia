package com.leafia.dev.blocks.legacy;

import com.hbm.hazard.HazardSystem;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * A hazard block with metadata support: the contamination blocks fallout leaves behind, in seven
 * levels - terracotta, sandstone, red sandstone and the snow block.
 *
 * <p>Ported from {@code com.leafia.dev.blocks.legacy.LegacyBlockHazardMeta}, a {@code BlockMeta}.
 * Upstream baked its models and registered its sprites from a {@code BlockBakeFrame} per level;
 * 1.21.1 needs neither - the blockstate names a model per {@code meta}, and
 * a generator script writes them (D-3004).
 *
 * <p>Walking on it, or standing in it, applies the hazards registered for the block's item
 * ({@code HazardSystem.applyHazards(this, entity)}, which makes a stack of the block at metadata
 * 0). <b>Upstream registers none for any of these blocks</b> - not in {@code AddonHazards}, and
 * NTM-CE's 1.5.1.8 {@code ModBlocks} no longer has them - so today the call finds nothing to apply.
 * It is kept exactly, because it is the hook a hazard registered on the level-0 item would use.
 */
public class LegacyBlockHazardMeta extends Block implements MetaBlock {

	public static final EnumProperty<MetaLevel> META = MetaLevel.LEVELS_7;

	public LegacyBlockHazardMeta(Properties properties) {
		super(properties);
		registerDefaultState(getStateDefinition().any().setValue(META, MetaLevel.L0));
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
	public ItemStack getCloneItemStack(net.minecraft.world.level.LevelReader level, BlockPos pos, BlockState state) {
		return getVariantStack(state);
	}

	/** {@code onEntityWalk}. */
	@Override
	public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
		super.stepOn(level, pos, state, entity);
		applyHazards(level, entity);
	}

	/** {@code onEntityCollision}. */
	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
		applyHazards(level, entity);
	}

	private void applyHazards(Level level, Entity entity) {
		if(level.isClientSide || !(entity instanceof LivingEntity living)) return;
		HazardSystem.applyHazards(stackOf(0), living);
	}
}
