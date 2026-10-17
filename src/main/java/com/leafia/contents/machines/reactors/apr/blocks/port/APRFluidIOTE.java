package com.leafia.contents.machines.reactors.apr.blocks.port;

import javax.annotation.Nullable;

import com.hbm.blockentity.BlockEntityLoadedBase;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.util.DirPos;
import com.leafia.contents.machines.reactors.apr.blocks.core.APRCoreTE;
import com.leafia.contents.machines.reactors.apr.blocks.core.APRCoreTE.APRChamber;
import com.leafia.init.LeafiaBlockEntities;

import api.hbm.fluidmk2.IFluidStandardTransceiverMK2;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The APR's fluid port: it takes coolant into the core and fluid into the chamber it stands nearest to, and gives
 * that chamber's cooled output back.
 *
 * <p>Upstream: {@code .../apr/blocks/port/APRFluidIOTE.java}. The core finds a port while it checks the structure
 * (a port is accepted wherever a ring wants a support) and writes its own position into {@link #targetPos}; the
 * port then picks its chamber as the one whose radius is closest to its own distance from the core
 * (upstream's {@code @Spaghetti("this sucks")}). Every tick, to all six neighbours: subscribe the core's coolant and
 * the chamber's input, offer the chamber's output - three calls per side, as upstream, not {@code autoPort}: the
 * offer is the transfer (D-969).
 *
 * <p>It receives into the chamber's input <em>and</em> the core's coolant tank, and sends only the chamber's output;
 * spent coolant (liquid oxygen) leaves through the core's own top. Upstream keeps a stale core in {@link #target}
 * until the core is removed.
 */
public class APRFluidIOTE extends BlockEntityLoadedBase implements IFluidStandardTransceiverMK2 {

	@Nullable public BlockPos targetPos = null;
	@Nullable public APRCoreTE target = null;

	public APRFluidIOTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public APRFluidIOTE(BlockPos pos, BlockState state) {
		this(LeafiaBlockEntities.APR_PORT.get(), pos, state);
	}

	/** The chamber whose radius is nearest this port's distance from the core; ties to the inner one. */
	public int getChamberIndex() {
		double distance = Math.sqrt(worldPosition.distSqr(target.getBlockPos()));
		int closestIndex = 0;
		double closestDistance = Double.MAX_VALUE;
		for(int i = 0; i < target.chambers.size(); i++) {
			double dist = Math.abs(distance - target.chambers.get(i));
			if(dist < closestDistance) {
				closestDistance = dist;
				closestIndex = i;
			}
		}
		return closestIndex;
	}

	public boolean validateTarget() {
		if(targetPos == null)
			return false;
		if(level.getBlockEntity(targetPos) instanceof APRCoreTE apr) {
			target = apr;
			return true;
		}
		target = null;
		return false;
	}

	@Override
	public FluidTank[] getReceivingTanks() {
		if(validateTarget()) {
			APRChamber chamber = target.getChamberSafely(getChamberIndex());
			if(chamber == null) return new FluidTank[0];
			return new FluidTank[] { chamber.input, target.oxygen };
		}
		return new FluidTank[0];
	}

	@Override
	public FluidTank[] getSendingTanks() {
		if(validateTarget()) {
			APRChamber chamber = target.getChamberSafely(getChamberIndex());
			if(chamber == null) return new FluidTank[0];
			return new FluidTank[] { chamber.output };
		}
		return new FluidTank[0];
	}

	@Override
	public FluidTank[] getAllTanks() {
		if(validateTarget())
			return target.getAllTanks();
		return new FluidTank[0];
	}

	@Override
	protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.loadAdditional(nbt, registries);
		if(nbt.contains("targetX"))
			targetPos = new BlockPos(nbt.getInt("targetX"), nbt.getInt("targetY"), nbt.getInt("targetZ"));
	}

	@Override
	protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		if(targetPos != null) {
			nbt.putInt("targetX", targetPos.getX());
			nbt.putInt("targetY", targetPos.getY());
			nbt.putInt("targetZ", targetPos.getZ());
		}
		super.saveAdditional(nbt, registries);
	}

	public void update() {
		if(level.isClientSide) return;
		if(targetPos != null) {
			if(level.getBlockEntity(targetPos) instanceof APRCoreTE core)
				target = core;
		}
		if(target != null && target.isRemoved())
			target = null;
		if(target != null) {
			APRChamber chamber = target.getChamberSafely(getChamberIndex());
			if(chamber == null) return;
			for(Direction value : Direction.values()) {
				DirPos dp = new DirPos(worldPosition.relative(value), value);
				trySubscribe(target.oxygen.getTankType(), level, dp);
				trySubscribe(chamber.input.getTankType(), level, dp);
				tryProvide(chamber.output.getTankType(), level, dp);
			}
		}
	}
}
