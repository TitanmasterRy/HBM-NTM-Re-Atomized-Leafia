package com.leafia.contents.machines.reactors.pwr.blocks.components;

import javax.annotation.Nullable;

import com.leafia.contents.machines.reactors.pwr.PWRData;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * A block entity in an addon PWR: it knows where the reactor's core is, and one of them holds the
 * reactor's {@link PWRData}.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/reactors/pwr/blocks/components/PWRComponentEntity.java},
 * line for line with {@code World} as {@link Level}.
 */
public interface PWRComponentEntity {

	@Nullable
	static PWRData getCoreFromPos(Level world, @Nullable BlockPos corePos) {
		if(corePos != null && world != null) {
			if(world.getBlockState(corePos).getBlock() instanceof PWRComponentBlock block) {
				if(block.tileEntityShouldCreate(world, corePos)) {
					BlockEntity entity = world.getBlockEntity(corePos);
					if(entity instanceof PWRComponentEntity component)
						return component.getCore();
				}
			}
		}
		return null;
	}

	void setCoreLink(@Nullable BlockPos pos);

	@Nullable
	PWRData getCoreByCorePos();

	void assignCore(@Nullable PWRData data);

	PWRData getCore();

	default void onDiagnosis() {}

	default boolean canAssignCore() {
		return false;
	}
}
