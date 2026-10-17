package com.leafia.overwrite_contents.welding;

import com.custom_hbm.sound.LCEAudioProxy;
import com.custom_hbm.sound.LCEAudioWrapper;
import com.hbm.blockentity.machine.BlockEntityMachineSolderingStation;
import com.leafia.contents.machines.powercores.dfc.IDFCClientTicked;
import com.leafia.init.LeafiaBlockEntities;
import com.leafia.init.LeafiaSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The soldering station overhaul: the base mod's station, with the addon's sparks and sound in place of its
 * particles.
 *
 * <p>Upstream: {@code MixinTileEntityMachineSolderingStation} - a {@code @Redirect} that drops the base station's
 * particle packet and a client-side tail. A subclass the base {@code MachineSolderingStation} makes through
 * {@code BlockEntityOverrides} while {@code overhauls.solderingOverhaul} is on (D-1002, off). The server tick is the
 * base mod's own, with the packet suppressed through D-2206's {@code spawnWorkParticles()} seam (D-3031); the base
 * tick also sets the protected {@code recipe} its sync reads, so the bench shows the output it is making again with
 * the overhaul on (the tick D-3027 copied kept it in a local).
 *
 * <p>The client, while it works and one tick after: {@code crafting_tech1_part} looping at volume 1 (no start or stop
 * sound), and a spark of 1 to 3 on one tick in four on average, from the front-right corner of the bench 1.125 up.
 */
public class LeafiaSolderingStationTE extends BlockEntityMachineSolderingStation implements IDFCClientTicked {

	LCEAudioWrapper clientSfx = null;
	boolean clientLooptimer = false;

	public LeafiaSolderingStationTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state, 11);
	}

	public LeafiaSolderingStationTE(BlockPos pos, BlockState state) {
		this(LeafiaBlockEntities.SOLDERING_STATION.get(), pos, state);
	}

	@Override
	public void onLoad() {
		super.onLoad();
		if(level != null && level.isClientSide) LeafiaWeldingClient.track(this);
	}

	/** Upstream's {@code @Redirect}: the base station's tau particle packet is not sent; the client draws the addon's sparks. */
	@Override
	protected void spawnWorkParticles() { }

	/** The facing the core was placed with (the base's {@code dir()} is private). */
	private net.minecraft.core.Direction facingDir() {
		return getBlockState().getValue(com.hbm.blocks.BlockDummyable.DIR);
	}

	/** The front-right corner of the bench, 1.125 up. */
	public Vec3 sparkPoint() {
		Direction dir = facingDir();
		Direction rot = dir.getClockWise();
		return new Vec3(worldPosition.getX() + 0.5 - dir.getStepX() * 0.5 + rot.getStepX() * 0.5, worldPosition.getY() + 1.125,
				worldPosition.getZ() + 0.5 - dir.getStepZ() * 0.5 + rot.getStepZ() * 0.5);
	}

	@Override
	public void leafia$clientTick() {
		if(progress > 0 || clientLooptimer) {
			if(clientSfx == null) {
				clientSfx = LCEAudioProxy.getLoopedSoundStartStop(level, LeafiaSounds.crafting_tech1_part.get(), null, null, SoundSource.BLOCKS,
						worldPosition.getX() + 0.5f, worldPosition.getY() + 0.5f, worldPosition.getZ() + 0.5f, 1, 1);
				clientSfx.startSound();
			}
			LeafiaWeldingClient.solderingParticles(this);
		} else if(clientSfx != null) {
			clientSfx.stopSound();
			clientSfx = null;
		}
		clientLooptimer = progress > 0;
	}

	private void stopSound() {
		if(clientSfx != null) {
			clientSfx.stopSound();
			clientSfx = null;
		}
	}

	@Override
	public void setRemoved() {
		stopSound();
		super.setRemoved();
	}

	@Override
	public void onChunkUnloaded() {
		stopSound();
		super.onChunkUnloaded();
	}
}
