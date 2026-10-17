package com.leafia.contents.machines.research.amsp.analyzer;

import java.util.List;

import com.hbm.blocks.BlockDummyable;
import com.hbm.handler.MultiblockHandlerXR;
import com.leafia.dev.machine.MachineTooltip;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * `amsp_analyzer`: a tall tower hung below its core, for a spectrometer that does not exist yet.
 *
 * <p>Upstream: {@code .../research/amsp/analyzer/AMSPAnalyzerBlock.java}, on {@code AddonBlockDummyable},
 * with a work-in-progress badge in its tooltip. {@code {0, 3, 3, 3, 2, 2}} at offset 0 is the top slab,
 * three deep and seven across; {@link #fillSpace} adds three more boxes (two arms the other way round, a
 * 5x5 layer four down and a 3x3 pillar to six down), and the model is drawn from there.
 *
 * <p><b>An upstream slip, kept ({@code D-021}): the extra boxes are checked from the core, and the
 * main box is checked from the core again.</b> Upstream moves {@code x} and {@code z} to the core in
 * place and then hands them to {@code super.checkRequirement}, which moves by the offset a second time.
 * For the analyzer, whose offset is 0, that is the same place. The receiver's is 3 (see there).
 */
public class AMSPAnalyzerBlock extends BlockDummyable {

	public static final MapCodec<AMSPAnalyzerBlock> CODEC = simpleCodec(AMSPAnalyzerBlock::new);

	/** Upstream's three extra boxes, in its order, then the pillar. */
	static final int[][] EXTRA = { { 0, 3, 2, 2, 3, -3 }, { 0, 3, 2, 2, -3, 3 }, { -4, 4, 2, 2, 2, 2 }, { -5, 6, 1, 1, 1, 1 } };

	public AMSPAnalyzerBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends AMSPAnalyzerBlock> codec() {
		return CODEC;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		MachineTooltip.addWIP(tooltip);
		super.appendHoverText(stack, context, tooltip, flag);
	}

	@Override
	public int[] getDimensions() {
		return new int[] { 0, 3, 3, 3, 2, 2 };
	}

	@Override
	public int getOffset() {
		return 0;
	}

	@Override
	protected void fillSpace(Level world, BlockPos pos, Direction dir, int o) {
		super.fillSpace(world, pos, dir, o);
		BlockPos core = pos.relative(dir, o);
		for(int[] box : EXTRA) MultiblockHandlerXR.fillSpace(world, core, box, this, dir);
	}

	@Override
	protected boolean checkRequirement(Level world, BlockPos pos, Direction dir, int o) {
		BlockPos core = pos.relative(dir, o);
		for(int[] box : EXTRA)
			if(!MultiblockHandlerXR.checkSpace(world, core, box, core, dir)) return false;
		return super.checkRequirement(world, core, dir, o);
	}

	/** Upstream makes a block entity for the core only ({@code meta >= 12}). */
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return state.getValue(PART) == Part.CORE ? new AMSPAnalyzerTE(pos, state) : null;
	}
}
