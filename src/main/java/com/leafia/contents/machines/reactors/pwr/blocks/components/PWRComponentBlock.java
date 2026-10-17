package com.leafia.contents.machines.reactors.pwr.blocks.components;

import javax.annotation.Nullable;

import com.leafia.contents.machines.reactors.pwr.PWRDiagnosis;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * A block that is part of an addon PWR: what the diagnosis flood-fill walks over.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/reactors/pwr/blocks/components/PWRComponentBlock.java},
 * line for line with {@code World} as {@link Level}. Upstream's {@code LeafiaDebug.debugLog} line on a
 * cancelled scan is a developer log behind a debug wand and is not carried.
 */
public interface PWRComponentBlock {

	default boolean shouldRenderOnGUI() {
		return false;
	}

	/**
	 * Only control rods and elements may return false. Basically if the TE is stacked or not
	 * <p>Returning false will make it be completely ignored by diagnosis
	 * <p>For whether this block should be assigned as core or not, see PWRComponentEntity$canAssignCore
	 */
	boolean tileEntityShouldCreate(Level world, BlockPos pos);

	/** Get this TileEntity as PWR component. */
	@Nullable
	default PWRComponentEntity getPWR(Level world, BlockPos pos) {
		if(!tileEntityShouldCreate(world, pos)) return null;
		BlockEntity entity = world.getBlockEntity(pos);
		if(entity instanceof PWRComponentEntity component) return component;
		return null;
	}

	/** getPWR but as TileEntity (is this even necessary) */
	@Nullable
	@Deprecated
	default BlockEntity getEntity(Level world, BlockPos pos) {
		if(!tileEntityShouldCreate(world, pos)) return null;
		BlockEntity entity = world.getBlockEntity(pos);
		if(entity instanceof PWRComponentEntity) return entity;
		return null;
	}

	/** Start diagnosis (DOES NOT WORK ON CLIENT) */
	default void beginDiagnosis(Level world, BlockPos pos, BlockPos trigger) {
		if(world.isClientSide) return;
		PWRDiagnosis.cleanup();
		if(PWRDiagnosis.preventScan.contains(pos)) return; // "Cancelled possible duplicate diagnosis"
		PWRDiagnosis diagnosis = new PWRDiagnosis(world, trigger);
		diagnosis.addPosition(pos);
	}
}
