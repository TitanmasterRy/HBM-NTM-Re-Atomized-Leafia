package com.leafia.contents.debug.ff_test.tank;

import java.util.ArrayList;
import java.util.List;

import com.hbm.blocks.BlockContainerBase;
import com.hbm.blocks.ILookOverlay;
import com.leafia.init.LeafiaBlockEntities;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * {@code test_ff_tank}, the debug Forge-fluid tank's block ({@link DebugTankTE}).
 *
 * <p>Upstream: {@code com/leafia/contents/debug/ff_test/tank/DebugTankBlock.java}: as {@code DebugSourceBlock}, with
 * the overlay titled "Test Tank Block" - no harvest level, no creative tab (D-3031).
 */
public class DebugTankBlock extends BlockContainerBase implements ILookOverlay {

	public DebugTankBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new DebugTankTE(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if(level.isClientSide) return null;
		return createTickerHelper(type, LeafiaBlockEntities.FF_TANK.get(), DebugTankTE::tick);
	}

	@Override
	@OnlyIn(Dist.CLIENT)
	public void printHook(GuiGraphics gui, Level world, BlockPos pos) {
		if(world.getBlockEntity(pos) instanceof DebugTankTE tank) {
			List<String> text = new ArrayList<>();
			text.add(tank.zaza.getFluidAmount() + "mB");
			ILookOverlay.printGeneric(gui, "Test Tank Block", 0xFF00FF, 0x400040, text);
		}
	}
}
