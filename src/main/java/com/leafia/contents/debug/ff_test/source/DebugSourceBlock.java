package com.leafia.contents.debug.ff_test.source;

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
 * {@code test_ff_source}, the debug Forge-fluid source's block ({@link DebugSourceTE}).
 *
 * <p>Upstream: {@code com/leafia/contents/debug/ff_test/source/DebugSourceBlock.java}, an {@code AddonBlockContainer}
 * of {@code Material.ANVIL} drawn as a model, with a look overlay titled "Test Source Block" in magenta reading the
 * tank's fill. {@code AddonBlockContainer}, unlike {@code AddonBlockBase}, sets no harvest level and no creative tab, so
 * this block breaks instantly, drops without a tool, and is reached with {@code /give} only - kept (D-3031).
 */
public class DebugSourceBlock extends BlockContainerBase implements ILookOverlay {

	public DebugSourceBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new DebugSourceTE(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if(level.isClientSide) return null;
		return createTickerHelper(type, LeafiaBlockEntities.FF_SOURCE.get(), DebugSourceTE::tick);
	}

	@Override
	@OnlyIn(Dist.CLIENT)
	public void printHook(GuiGraphics gui, Level world, BlockPos pos) {
		if(world.getBlockEntity(pos) instanceof DebugSourceTE tank) {
			List<String> text = new ArrayList<>();
			text.add(tank.zaza.getFluidAmount() + "mB");
			ILookOverlay.printGeneric(gui, "Test Source Block", 0xFF00FF, 0x400040, text);
		}
	}
}
