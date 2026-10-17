package com.leafia.contents.minerals.corium;

import com.leafia.contents.minerals.AddonOreBase;
import com.hbm.handler.radiation.ChunkRadiationManager;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ported from {@code com.leafia.contents.minerals.corium.CoriumOreBase}: chernobylite and
 * zetalite ore, which leak radiation into the world for as long as they stand.
 *
 * <p>Every time it ticks it adds {@code rads / 10} to the chunk's radiation, up to a ceiling of
 * {@code rads} ({@link #rads}, 450) - so one ore alone fills its chunk to 450 in ten ticks and
 * then holds it there, and a second ore does nothing more. The ceiling is NTM-CE's four-argument
 * {@code incrementRad}, the base mod's own since D-2199.
 *
 * <p><b>It ticks twice over, and both are kept.</b> Upstream sets {@code setTickRandomly(true)}
 * and also schedules an update from {@code onBlockAdded} that reschedules itself, every
 * {@code 60 + rand(500)} ticks - the random tick runs the same method, so a placed ore ticks on
 * both clocks. In 1.21.1 a random tick calls {@code tick}, as {@code updateTick} did, so
 * overriding {@link #tick} covers both; the reschedule is idempotent (a block cannot hold two
 * pending ticks at one position), as it was upstream.
 *
 * <p><b>Upstream's other half is not here.</b> {@code AddonBase.fMLLoadCompleteEvent} calls
 * {@code ((BlockHazard) ModBlocks.block_corium).addRadiation(CoriumOreBase.rads)}, giving the
 * base mod's corium block the same leak. This port's {@code block_corium} is a plain block (its
 * registration says {@code // BlockHazard}) and the addon cannot reach into it (D-1001): a
 * base-mod hand-off in D-1076.
 */
public class CoriumOreBase extends AddonOreBase implements ICoriumOre {

	/** Upstream's {@code rads}: the ceiling, and ten times the step. Public and mutable, as upstream's. */
	public static int rads = 450;

	public CoriumOreBase(Properties properties) {
		super(properties);
	}

	/** Upstream's {@code tickRate}: 60 to 559 ticks, drawn fresh at every call. */
	public static int tickRate(RandomSource random) {
		return 60 + random.nextInt(500);
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		if(!level.isClientSide)
			level.scheduleTick(pos, this, tickRate(level.random));
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		ChunkRadiationManager.incrementRad(level, pos, rads / 10f, rads);
		level.scheduleTick(pos, this, tickRate(level.random));
	}
}
