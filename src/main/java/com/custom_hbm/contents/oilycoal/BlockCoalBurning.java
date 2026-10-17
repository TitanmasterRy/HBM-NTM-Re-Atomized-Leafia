package com.custom_hbm.contents.oilycoal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Burning oily coal: lit, smoking, and permanent.
 *
 * <p>Upstream: {@code com.custom_hbm.contents.oilycoal.BlockCoalBurning}. Flames and smoke on
 * every face but the bottom that has air beside it, three seconds of fire for anything that
 * walks on it, and fire left behind whenever it stops being this block.
 *
 * <h2>It never drops anything, even with silk touch — upstream's behaviour, kept</h2>
 *
 * Upstream's {@code breakBlock} sets fire at its own position. 1.12.2's
 * {@code Chunk.setBlockState} calls {@code breakBlock} and then checks that the block it was
 * asked to place is still there; the fire has replaced it, so the call reports failure, and the
 * player's {@code removeBlock} takes that as "not removed" and skips {@code harvestBlock}. The
 * silk-touch drop its {@code canSilkHarvest} would allow is therefore never reached. 1.21.1's
 * {@code LevelChunk.setBlockState} has the same check after {@link #onRemove}, so the same
 * nested write gives the same result here with no special-casing. That is why the block has
 * {@code noLootTable()} rather than a table nothing can reach. The same write runs when a piston
 * moves it, which leaves fire behind it too, as upstream's did (D-1063).
 */
public class BlockCoalBurning extends Block {

	public BlockCoalBurning(Properties properties) {
		super(properties);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource rand) {
		super.animateTick(state, level, pos, rand);
		for(Direction dir : Direction.values()) {

			if(dir == Direction.DOWN)
				continue;

			if(level.getBlockState(pos.relative(dir)).isAir()) {

				double ix = pos.getX() + 0.5F + dir.getStepX() + rand.nextDouble() - 0.5D;
				double iy = pos.getY() + 0.5F + dir.getStepY() + rand.nextDouble() - 0.5D;
				double iz = pos.getZ() + 0.5F + dir.getStepZ() + rand.nextDouble() - 0.5D;

				if(dir.getStepX() != 0)
					ix = pos.getX() + 0.5F + dir.getStepX() * 0.5 + rand.nextDouble() * 0.125 * dir.getStepX();
				if(dir.getStepY() != 0)
					iy = pos.getY() + 0.5F + dir.getStepY() * 0.5 + rand.nextDouble() * 0.125 * dir.getStepY();
				if(dir.getStepZ() != 0)
					iz = pos.getZ() + 0.5F + dir.getStepZ() * 0.5 + rand.nextDouble() * 0.125 * dir.getStepZ();

				level.addParticle(ParticleTypes.FLAME, ix, iy, iz, 0.0, 0.0, 0.0);
				level.addParticle(ParticleTypes.SMOKE, ix, iy, iz, 0.0, 0.0, 0.0);
				level.addParticle(ParticleTypes.SMOKE, ix, iy, iz, 0.0, 0.1, 0.0);
			}
		}
	}

	/**
	 * Upstream's {@code breakBlock}. Server side only, as 1.12.2's was: its {@code Chunk} calls
	 * {@code breakBlock} only {@code if(!world.isRemote)}.
	 */
	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if(!level.isClientSide && !state.is(newState.getBlock()))
			level.setBlock(pos, Blocks.FIRE.defaultBlockState(), Block.UPDATE_ALL);
		super.onRemove(state, level, pos, newState, movedByPiston);
	}

	/** Upstream's {@code onEntityWalk}: {@code setFire(3)}, which is three seconds. */
	@Override
	public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
		entity.igniteForSeconds(3);
	}
}
