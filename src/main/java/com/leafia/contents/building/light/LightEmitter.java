package com.leafia.contents.building.light;

import com.hbm.blocks.BlockDummyable;
import com.leafia.contents.LeafiaBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The invisible block a lit fluorescent light drops beneath itself so the light actually
 * reaches the floor.
 *
 * <p>Upstream: {@code com/leafia/contents/building/light/LightEmitter.java}, where it extends
 * {@code BlockAir} and overrides eleven methods to be as absent as possible — no collision, no
 * render, no drop, no faces, replaceable. In 1.21.1 all eleven are
 * {@link net.minecraft.world.level.block.state.BlockBehaviour.Properties}, and vanilla's
 * {@link AirBlock} is already the rest of it. That is D-019's shape again: most of an
 * upstream block class turns out to be the platform's job now.
 *
 * <p>Extending {@code AirBlock} rather than {@code Block} is load-bearing and not cosmetic:
 * {@link LightTE#updateLightEmitters} only descends through positions that answer
 * {@code isAir()}, which is upstream's {@code getMaterial() == Material.AIR}, and in 1.12.2 a
 * {@code BlockAir} subclass passed that test. A plain {@code Block} would not, so a column of
 * emitters would refuse to extend past its own first block.
 *
 * <h2>It cleans itself up</h2>
 *
 * An emitter looks up to {@link #LIGHT_RANGE} blocks for a {@link LightBlock}, walks to that
 * multiblock's core, and deletes itself unless the core is lit. That runs on placement and on
 * every neighbour change, which is what removes a column when the fixture above it is broken
 * rather than switched off — the fixture is gone, so nothing is left to run
 * {@code updateLightEmitters}.
 */
public class LightEmitter extends AirBlock {

	/** Upstream's, and the same number {@link LightTE} descends. */
	public static final int LIGHT_RANGE = 8;

	public LightEmitter(Properties properties) {
		super(properties);
	}

	/**
	 * Deletes this emitter unless there is a lit fixture above it.
	 *
	 * <p>Upstream's loop, including its {@code break}s: the search stops at the <em>first</em>
	 * {@link LightBlock} it meets going up, so an emitter under an unlit fixture is removed even
	 * if a lit one stands higher still.
	 */
	public void update(Level level, BlockPos pos) {

		BlockPos original = pos;
		boolean invalid = true;

		for(int i = 0; i < LIGHT_RANGE; i++) {

			pos = pos.above();
			BlockState state = level.getBlockState(pos);

			if(state.getBlock() instanceof LightBlock light) {

				BlockPos core = light.findCore(level, pos);

				if(core != null && level.getBlockState(core).getBlock() instanceof LightBlock
						&& level.getBlockState(core).getValue(LightBlock.LIT)) {
					invalid = false;
				}

				break;
			}
		}

		if(invalid) level.setBlockAndUpdate(original, Blocks.AIR.defaultBlockState());
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		if(!level.isClientSide) update(level, pos);
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
			BlockPos neighborPos, boolean movedByPiston) {

		super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);

		/*
		 * BlockDummyable.safeRem is set while a multiblock is being built or torn down. Without
		 * this guard an emitter would look up mid-teardown, find the fixture already half gone,
		 * and delete itself - the same problem, and the same flag, that the base mod's dummies
		 * use to survive their own construction.
		 */
		if(!level.isClientSide && !BlockDummyable.safeRem) update(level, pos);
	}

	/** True when the block at {@code pos} is this addon's emitter. */
	public static boolean isEmitter(BlockState state) {
		return state.getBlock() instanceof LightEmitter;
	}

	/** The registered instance, or null when the feature is off. */
	public static Block get() {
		return LeafiaBlocks.light_emitter == null ? null : LeafiaBlocks.light_emitter.get();
	}
}
