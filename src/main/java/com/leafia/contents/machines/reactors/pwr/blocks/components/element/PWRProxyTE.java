package com.leafia.contents.machines.reactors.pwr.blocks.components.element;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * The block entity of a stacked fuel column block: it forwards the item face to the column's top.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/reactors/pwr/blocks/components/element/PWRProxyTE.java},
 * a CE {@code TileEntityProxyInventory} whose {@link #getTE()} walks up the column to the first
 * non-stacked element. The capability forwarding CE's proxy base did is a {@code RegisterCapabilitiesEvent}
 * entry here ({@code LeafiaCapabilities}), which asks {@link #getTE()} for the top's inventory. Upstream's
 * walk stops at y 256; this one stops at the level's height.
 */
public class PWRProxyTE extends BlockEntity {

	public PWRProxyTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Nullable
	public BlockEntity getTE() {
		if(level == null) return null;
		BooleanProperty stacked = PWRElementBlock.stacked;
		for(int i = worldPosition.getY() + 1; i < level.getMaxBuildHeight(); i++) {
			BlockPos newPos = new BlockPos(worldPosition.getX(), i, worldPosition.getZ());
			BlockState state = level.getBlockState(newPos);
			if(state.getBlock() instanceof PWRElementBlock) {
				if(!state.getValue(stacked)) return level.getBlockEntity(newPos);
			} else break;
		}
		return null;
	}
}
