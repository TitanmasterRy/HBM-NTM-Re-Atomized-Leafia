package com.leafia.contents.machines.misc.modular_turbine;

import com.hbm.blocks.BlockDummyable;
import com.leafia.contents.machines.misc.modular_turbine.ModularTurbineBlockBase.TurbineComponentType;
import com.leafia.contents.machines.misc.modular_turbine.core.MTCoreTE;
import com.leafia.contents.machines.misc.modular_turbine.core.MTCoreTE.TurbineAssembly;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * The block entity of every turbine part that is not the core: blades, separators, ports,
 * flywheels, the generator. It holds two references and nothing else — the core that assembled it
 * and the steam stage it belongs to, both null until a core claims it.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/misc/modular_turbine/ModularTurbineComponentTE.java}.
 *
 * <h2>What changed</h2>
 *
 * <ul>
 * <li>{@code setBlockType} is gone. It existed because a 1.12.2 tile entity cached its block and
 *     upstream needed to set it early; a 1.21.1 block entity reads its block off its state, so
 *     {@link #getBlockState()} already answers.</li>
 * <li>Upstream's facing is {@code EnumFacing.byIndex(getBlockMetadata() - 10).getOpposite()}. The
 *     metadata minus ten <em>is</em> {@link BlockDummyable#DIR} here, so the turbine's axis is
 *     {@code DIR.getOpposite()} — the way the player was looking when they placed it.</li>
 * <li>{@code invalidate} becomes {@link #setRemoved}, and that needed a guard upstream never had
 *     to write. See below.</li>
 * </ul>
 *
 * <h2>Unloading is not removal</h2>
 *
 * Upstream disassembles the turbine from {@code invalidate}, which 1.12.2 calls when a block is
 * <em>broken</em>. 1.21.1 calls {@link #setRemoved} for that <em>and</em> when the chunk unloads,
 * so a literal port would take a turbine apart every time a player walked away from half of it —
 * and a turbine is up to 300 blocks long, so it routinely straddles a chunk border. NeoForge calls
 * {@link #onChunkUnloaded} first on the unload path, so that sets a flag and {@code setRemoved}
 * checks it. The core rebuilds its own assembly from what it saved when its chunk comes back.
 */
public class ModularTurbineComponentTE extends BlockEntity implements IMTStageUpgradeContributor, IMTMachineUpgradeContributor {

	public MTCoreTE core;
	public TurbineAssembly assembly;

	/** Set by {@link #onChunkUnloaded}, so {@link #setRemoved} can tell an unload from a break. */
	protected boolean unloading = false;

	public ModularTurbineComponentTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	/** The turbine's axis: the way the player faced when placing it. Upstream's {@code meta - 10}, opposite. */
	public Direction getAssemblyFacing() {
		return getBlockState().getValue(BlockDummyable.DIR).getOpposite();
	}

	/**
	 * The part whose shaft passes through {@code p}, if it is a turbine part lying on {@code myAxis}.
	 *
	 * <p>Used by the renderer to find the next blade stage along the shaft, which is why it checks
	 * the axis: two turbines crossing at right angles must not see each other's blades.
	 */
	protected ModularTurbineComponentTE getTEFromShaftPos(BlockPos p, Axis myAxis) {
		if(level.getBlockState(p).getBlock() instanceof ModularTurbineBlockBase other) {
			BlockPos core = other.findCore(level, p);
			if(core != null && level.getBlockEntity(core) instanceof ModularTurbineComponentTE mte) {
				Direction facing = level.getBlockState(core).getValue(BlockDummyable.DIR).getOpposite();
				if(facing.getAxis().equals(myAxis))
					return mte;
			}
		}
		return null;
	}

	AABB local$renderAABB;

	/**
	 * Upstream's fixed box, five blocks either side and ten up, because the largest part (9x9)
	 * reaches that far from its core.
	 *
	 * <p>Upstream overrides this on the tile entity; in NeoForge 21.1 the hook is
	 * {@code BlockEntityRenderer.getRenderBoundingBox(T)}, so the component renderer (T6) returns
	 * this from there. Kept here so the box is defined beside the part it bounds.
	 */
	public AABB getRenderBoundingBox() {
		if(local$renderAABB == null) {
			local$renderAABB = new AABB(
					worldPosition.getX() - 5, worldPosition.getY() - 1, worldPosition.getZ() - 5,
					worldPosition.getX() + 5, worldPosition.getY() + 10, worldPosition.getZ() + 5
			);
		}
		return local$renderAABB;
	}

	public boolean local$firstRender = true;
	public ModularTurbineComponentTE local$nextBlade;

	/**
	 * Finds the blade stage one block further along the shaft, for the renderer to animate the
	 * steam between them. Client-side state only; upstream's commented-out {@code prevBlade} half
	 * is left out rather than carried as a comment.
	 */
	public void local$checkForBlades(boolean rebuild) {
		Direction face = getAssemblyFacing();
		boolean valid = false;
		if(getBlockState().getBlock() instanceof ModularTurbineBlockBase base) {
			valid = true;
			if(local$nextBlade != null && local$nextBlade.isRemoved()) { local$nextBlade = null; rebuild = true; }
			if(rebuild && local$nextBlade == null) {
				BlockPos shaft = worldPosition.above(base.shaftHeight());
				ModularTurbineComponentTE te = getTEFromShaftPos(shaft.relative(face), face.getAxis());
				if(te != null) {
					if(te.getBlockState().getBlock() instanceof ModularTurbineBlockBase other) {
						if(other.componentType() == TurbineComponentType.BLADES)
							local$nextBlade = te;
					}
				}
			}
		}
		if(!valid) {
			local$nextBlade = null;
		}
	}

	@Override
	public void onChunkUnloaded() {
		unloading = true;
		super.onChunkUnloaded();
	}

	/** Upstream's {@code invalidate}: breaking any part takes the whole assembly down. Not on unload. */
	@Override
	public void setRemoved() {
		if(!unloading && core != null)
			core.disassemble();
		super.setRemoved();
	}
}
