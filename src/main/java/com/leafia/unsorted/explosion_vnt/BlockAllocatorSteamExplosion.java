package com.leafia.unsorted.explosion_vnt;

import java.util.HashSet;
import java.util.Set;

import com.hbm.blocks.fluid.CoriumFinite;
import com.hbm.explosion.vanillant.ExplosionVNT;
import com.hbm.explosion.vanillant.interfaces.IBlockAllocator;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * The block pass of a steam explosion: the blast corium makes when it meets water ({@link
 * com.leafia.overwrite_contents.LeafiaCoriumBehavior}).
 *
 * <p>Upstream: {@code com/leafia/unsorted/explosion_vnt/BlockAllocatorSteamExplosion.java}. It is the base
 * mod's {@code BlockAllocatorStandard} - rays from every surface cell of a {@code resolution}-cube, each
 * starting at {@code size x (0.7 + 0.6 x rand)} - with its numbers changed so that it reaches far but
 * barely bites:
 *
 * <ul>
 * <li>a ray loses {@code 2.75 x step} a step, not {@code 0.75 x step}, so it runs about a quarter as far
 *     through air;</li>
 * <li>a block costs {@code (resistance / 10 + 0.03) x step}, not {@code (resistance + 0.3) x step}, so it
 *     goes through ten times as much rock;</li>
 * <li>air, water and corium cost nothing at all - the ray passes through the pool that caused it.</li>
 * </ul>
 *
 * <p>Every block a ray reaches with power left is taken, liquids and air included (the processor decides
 * what breaking air means), as upstream's does. 1.12.2's {@code Material.WATER} is a water block here: a
 * {@link LiquidBlock} whose fluid is water, which is what that material held - a waterlogged stair is not
 * one. Corium's material ({@code ModBlocks.fluidcorium}) is any {@link CoriumFinite}, balefire corium
 * among them. The resistance is the block's (or, with an exploder, the exploder's say), as in the base
 * allocator; 1.21.1's numbers are 1.12.2's {@code getExplosionResistance} already (D-012).
 */
public class BlockAllocatorSteamExplosion implements IBlockAllocator {

	/** The base allocator's step, unchanged. */
	public static final float STEP_SIZE = 0.3F;
	/** Air loss a step, per unit of step: the base's is 0.75. */
	public static final float AIR_LOSS = 2.75F;
	/** Blocks cost {@code resistance / RESISTANCE_DIVISOR + RESISTANCE_FLOOR} a unit of step. */
	public static final float RESISTANCE_DIVISOR = 10F;
	public static final float RESISTANCE_FLOOR = 0.03F;

	protected int resolution;

	public BlockAllocatorSteamExplosion() {
		this(16);
	}

	public BlockAllocatorSteamExplosion(int resolution) {
		this.resolution = resolution;
	}

	/** 1.12.2's {@code Material.WATER}: a water block. */
	public static boolean isWater(BlockState state) {
		return state.getBlock() instanceof LiquidBlock && state.getFluidState().is(FluidTags.WATER);
	}

	/** What a ray passes for free: air, water and corium. */
	public static boolean isFree(BlockState state) {
		return state.isAir() || isWater(state) || state.getBlock() instanceof CoriumFinite;
	}

	@Override
	public Set<BlockPos> allocate(ExplosionVNT explosion, Level world, double x, double y, double z, float size) {

		Set<BlockPos> affectedBlocks = new HashSet<>();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

		for(int i = 0; i < this.resolution; ++i) {
			for(int j = 0; j < this.resolution; ++j) {
				for(int k = 0; k < this.resolution; ++k) {

					if(i == 0 || i == this.resolution - 1 || j == 0 || j == this.resolution - 1 || k == 0 || k == this.resolution - 1) {

						double d0 = (double) ((float) i / ((float) this.resolution - 1.0F) * 2.0F - 1.0F);
						double d1 = (double) ((float) j / ((float) this.resolution - 1.0F) * 2.0F - 1.0F);
						double d2 = (double) ((float) k / ((float) this.resolution - 1.0F) * 2.0F - 1.0F);
						double d3 = Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2);

						d0 /= d3;
						d1 /= d3;
						d2 /= d3;

						float powerRemaining = size * (0.7F + world.random.nextFloat() * 0.6F);
						double currentX = x;
						double currentY = y;
						double currentZ = z;

						for(float stepSize = STEP_SIZE; powerRemaining > 0.0F; powerRemaining -= stepSize * AIR_LOSS) {

							cursor.set(Mth.floor(currentX), Mth.floor(currentY), Mth.floor(currentZ));
							BlockState state = world.getBlockState(cursor);

							if(!isFree(state)) {
								FluidState fluid = world.getFluidState(cursor);
								float blockResistance = explosion.exploder != null
										? explosion.exploder.getBlockExplosionResistance(explosion.compat, world, cursor, state, fluid, powerRemaining)
										: Math.max(state.getExplosionResistance(world, cursor, explosion.compat),
												fluid.getExplosionResistance(world, cursor, explosion.compat));
								powerRemaining -= (blockResistance / RESISTANCE_DIVISOR + RESISTANCE_FLOOR) * stepSize;
							}

							if(powerRemaining > 0.0F && (explosion.exploder == null
									|| explosion.exploder.shouldBlockExplode(explosion.compat, world, cursor, state, powerRemaining))) {
								affectedBlocks.add(cursor.immutable());
							}

							currentX += d0 * (double) stepSize;
							currentY += d1 * (double) stepSize;
							currentZ += d2 * (double) stepSize;
						}
					}
				}
			}
		}

		return affectedBlocks;
	}
}
