package com.leafia.dev.blocks.legacy;

import com.hbm.hazard.HazardSystem;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Contaminated ice.
 *
 * <p>Ported from {@code com.leafia.dev.blocks.legacy.LegacyWasteIce}, a {@code BlockIce}. Everything
 * ice does - melting under light, turning to water when broken, giving nothing without silk touch,
 * the slippery floor - is vanilla's {@link IceBlock}, which is where upstream got it too; this adds
 * only the two hazard hooks. As with {@link LegacyBlockHazardMeta}, the hazards applied are those
 * registered for the block's item, which upstream registers none of.
 *
 * <p>Upstream's {@code setHarvestLevel("pickaxe", -1)} is "any tool, or none" - the block is
 * hand-mineable, so it is in no tool tag.
 */
public class LegacyWasteIce extends IceBlock {

	public static final MapCodec<LegacyWasteIce> CODEC = simpleCodec(LegacyWasteIce::new);

	public LegacyWasteIce(Properties properties) {
		super(properties);
	}

	@Override
	public MapCodec<? extends IceBlock> codec() {
		return CODEC;
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
		HazardSystem.applyHazards(new ItemStack(this), living);
	}
}
