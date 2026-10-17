package com.leafia.contents.machines.powercores.dfc.components;

import javax.annotation.Nullable;

import com.custom_hbm.sound.LCEAudioProxy;
import com.custom_hbm.sound.LCEAudioWrapper;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.leafia.contents.LeafiaFluids;
import com.leafia.contents.fluids.LeafiaLiquids;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What the overhauled DFC components share: the coolant rule that ends in a puddle of molten
 * component, the spent-coolant tank's default fluid, NTM-CE's tank fill, and the laser hum.
 *
 * <p>Not an upstream file: upstream repeats each of these inline in every mixin. Every number here is
 * upstream's, from {@code MixinTileEntityCoreEmitter}, {@code MixinTileEntityCoreReceiver} and
 * {@code CoreExchangerTE}.
 */
public final class DFCParts {

	private DFCParts() {}

	/** Cryogel per tick while a part works: 20 mB, cooled into 20 mB of pyrogel. */
	public static final int CRYOGEL_PER_TICK = 20;

	/** Cold perfluoromethyl per tick: a hundred times the cryogel, 2,000 mB, into hot perfluoromethyl. */
	public static final int PERFLUOROMETHYL_PER_TICK = 2000;

	/** The spent-coolant tank both beam parts carry: 64,000 mB of pyrogel. */
	public static final int OUTPUT_TANK = 64000;

	/**
	 * The fluid a fresh spent-coolant tank holds: pyrogel, the addon's, or nothing when
	 * {@code features.addonFluids} is off and pyrogel does not exist.
	 */
	public static FluidType pyrogel() {
		return LeafiaFluids.PYROGEL != null ? LeafiaFluids.PYROGEL : Fluids.NONE;
	}

	/**
	 * What a part that runs dry turns into: upstream's {@code AddonBlocks.fluid_osmiridium}, the molten
	 * DFC component, a liquid that burns and radiates digamma (D-1079). When
	 * {@code features.worldFluids} is off that liquid does not exist, and the part becomes lava, which is
	 * what the base mod's components do.
	 */
	public static BlockState meltdownState() {
		if(LeafiaLiquids.osmiridium != null && LeafiaLiquids.osmiridium.block != null)
			return LeafiaLiquids.osmiridium.block.get().defaultBlockState();
		return Blocks.LAVA.defaultBlockState();
	}

	/**
	 * Upstream's coolant branch, the same in the emitter and the receiver: a tick of cryogel or of cold
	 * perfluoromethyl from {@code tank} into {@code output} (capped at its size, the excess lost), or, with
	 * neither, the block becomes {@link #meltdownState()}.
	 *
	 * @return true when the part has melted and the caller must stop its tick
	 */
	public static boolean coolOrMelt(Level level, BlockPos pos, FluidTank tank, FluidTank output) {
		if(tank.getTankType() == Fluids.CRYOGEL && tank.getFill() >= CRYOGEL_PER_TICK) {
			tank.setFill(tank.getFill() - CRYOGEL_PER_TICK);
			output.setFill(Math.min(output.getMaxFill(), output.getFill() + CRYOGEL_PER_TICK));
			return false;
		} else if(tank.getTankType() == Fluids.PERFLUOROMETHYL_COLD && tank.getFill() >= PERFLUOROMETHYL_PER_TICK) {
			tank.setFill(tank.getFill() - PERFLUOROMETHYL_PER_TICK);
			output.setFill(Math.min(output.getMaxFill(), output.getFill() + PERFLUOROMETHYL_PER_TICK));
			return false;
		}
		level.setBlockAndUpdate(pos, meltdownState());
		return true;
	}

	/**
	 * NTM-CE's {@code FluidTankNTM.fill(type, amount, doFill)}, read out of the CE jar: nothing for no
	 * fluid or a non-positive amount; an untyped tank takes the type and up to its size; a tank of
	 * another type takes nothing; otherwise up to the room left. Answers what was (or would be) taken.
	 */
	public static int fill(FluidTank tank, @Nullable FluidType type, int amount, boolean doFill) {
		if(type == null || type == Fluids.NONE || amount <= 0) return 0;
		if(tank.getTankType() == Fluids.NONE) {
			int taken = Math.min(tank.getMaxFill(), amount);
			if(doFill) {
				tank.setTankType(type);
				tank.setFill(taken);
			}
			return taken;
		}
		if(!tank.getTankType().equals(type)) return 0;
		int taken = Math.min(tank.getMaxFill() - tank.getFill(), amount);
		if(doFill && taken > 0) tank.setFill(tank.getFill() + taken);
		return taken;
	}

	/**
	 * Upstream's laser loop: start, loop and stop sounds at the block's centre, heard to fifty blocks on
	 * {@code (1 - d/50)^6 / 4}. Client only; on a server the proxy hands back a silent wrapper.
	 */
	public static LCEAudioWrapper laserLoop(Level level, BlockPos pos, SoundEvent loop, SoundEvent start, SoundEvent stop) {
		return LCEAudioProxy.getLoopedSoundStartStop(level, loop, start, stop, SoundSource.BLOCKS,
						pos.getX() + 0.5f, pos.getY() + 0.5f, pos.getZ() + 0.5f, 1, 1)
				.setCustomAttenuation((intended, distance) -> Math.pow(Math.max(0, 1 - distance / 50), 6) / 4);
	}

	/**
	 * Upstream's {@code leafia$sound}/{@code leafia$isPlaying}/{@code leafia$playSound}/{@code leafia$stopSound}
	 * quartet, which every component repeats: made on first use, started once, stopped once, and released
	 * when the block entity goes (upstream's {@code invalidate} and {@code onChunkUnload}).
	 */
	public static final class Hum {
		private final boolean laser1;
		@Nullable
		private LCEAudioWrapper sound;
		private boolean playing = false;

		/** @param laser1 the emitter's {@code laser1}; every other part hums {@code laser2} */
		public Hum(boolean laser1) {
			this.laser1 = laser1;
		}

		public void play(Level level, BlockPos pos) {
			if(sound == null) {
				sound = laser1
						? laserLoop(level, pos, com.leafia.init.LeafiaSounds.laser1loop.get(), com.leafia.init.LeafiaSounds.laser1start.get(), com.leafia.init.LeafiaSounds.laser1stop.get())
						: laserLoop(level, pos, com.leafia.init.LeafiaSounds.laser2loop.get(), com.leafia.init.LeafiaSounds.laser2start.get(), com.leafia.init.LeafiaSounds.laser2stop.get());
			}
			if(!playing) sound.startSound();
			playing = true;
		}

		public void stop() {
			if(sound == null) return;
			if(playing) sound.stopSound();
			playing = false;
		}

		public void release() {
			if(sound != null) {
				sound.stopSound();
				sound = null;
			}
			playing = false;
		}

		public boolean isPlaying() {
			return playing;
		}
	}
}
