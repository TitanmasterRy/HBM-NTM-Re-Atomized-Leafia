package com.custom_hbm.contents.oilycoal;

import com.hbm.blocks.ModBlocks;
import com.leafia.contents.LeafiaBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Oily coal ore: a coal ore that catches.
 *
 * <p>Upstream: {@code com.custom_hbm.contents.oilycoal.BlockCoalOil}, one of the addon's
 * "legacy blocks" — mainline NTM removed it (hbm-src migrates {@code ore_coal_oil} to plain
 * {@code coal_ore}) and the addon put it back. When a neighbour changes and any of the six
 * blocks beside it is burning oily coal, the base mod's balefire, fire, or lava, it schedules
 * itself 2 to 21 ticks out and then becomes {@link BlockCoalBurning} — unconditionally, whether
 * the fire is still there or not. Blown up, it leaves fire where it stood.
 *
 * <p>Its drops — two or three coal, itself with silk touch — are the loot table, read off
 * upstream's {@code getItemDropped}/{@code quantityDropped}. Upstream overrides Forge's
 * three-argument {@code quantityDropped}, which skips {@code BlockOre}'s fortune bonus, so the
 * table has none either (D-1063).
 */
public class BlockCoalOil extends Block {

	public BlockCoalOil(Properties properties) {
		super(properties);
	}

	/** Whether a neighbour is one upstream's {@code neighborChanged} counts as a light. */
	public static boolean ignites(BlockState neighbour) {
		Block n = neighbour.getBlock();
		return (LeafiaBlocks.ore_coal_oil_burning != null && n == LeafiaBlocks.ore_coal_oil_burning.get())
				|| n == ModBlocks.balefire.get()
				|| n == Blocks.FIRE
				|| neighbour.getFluidState().is(FluidTags.LAVA);
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbourBlock,
			BlockPos neighbourPos, boolean movedByPiston) {
		for(Direction dir : Direction.values()) {
			if(ignites(level.getBlockState(pos.relative(dir))))
				level.scheduleTick(pos, this, level.random.nextInt(20) + 2);
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		level.setBlock(pos, LeafiaBlocks.ore_coal_oil_burning.get().defaultBlockState(), Block.UPDATE_ALL);
	}

	/** Upstream's {@code onExplosionDestroy}: the ore is gone and fire takes its place. */
	@Override
	public void wasExploded(Level level, BlockPos pos, Explosion explosion) {
		if(!level.isClientSide)
			level.setBlock(pos, Blocks.FIRE.defaultBlockState(), Block.UPDATE_ALL);
	}
}
