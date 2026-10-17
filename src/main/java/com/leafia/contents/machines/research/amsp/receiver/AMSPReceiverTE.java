package com.leafia.contents.machines.research.amsp.receiver;

import com.leafia.init.LeafiaBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The anti-mass spectrometer's receiver: a block entity that does nothing.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/research/amsp/receiver/AMSPReceiverTE.java}. Its whole
 * {@code update} is commented out: an unfinished meltdown that would, over thirty-two four-second steps,
 * shake the camera, throw {@code shockParticleEditionTrademark} lightning from a point 5.5 blocks above
 * it, and then blow up with 300 glyphids and a 150-block {@code DFCNukeParticle}. What is left is a
 * looped-alarm field that nothing ever sets and a {@code invalidate} that stops it. None of that is
 * ported - there is nothing live to port - and the commented-out code is not either; it is upstream's
 * to finish, and the pieces it would call ({@code shockParticleEditionTrademark},
 * {@code DFCNukeParticle}) are in.
 */
public class AMSPReceiverTE extends BlockEntity {

	public AMSPReceiverTE(BlockPos pos, BlockState state) {
		this(LeafiaBlockEntities.AMSP_RECEIVER.get(), pos, state);
	}

	public AMSPReceiverTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}
}
