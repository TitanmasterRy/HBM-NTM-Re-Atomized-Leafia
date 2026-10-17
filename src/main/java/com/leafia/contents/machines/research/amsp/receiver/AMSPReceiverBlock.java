package com.leafia.contents.machines.research.amsp.receiver;

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
 * `amsp_receiver`: a 7x7 base two blocks tall with a collar above it, for the same unfinished spectrometer.
 *
 * <p>Upstream: {@code .../research/amsp/receiver/AMSPReceiverBlock.java}. {@code {1, 0, 3, 3, 3, 3}} at
 * offset 3 puts the core three blocks in from the edge the machine is placed against;
 * {@link #fillSpace} adds four boxes at {@code y + 2}, the ring of the collar.
 *
 * <p><b>An upstream slip, kept ({@code D-021}).</b> Upstream moves {@code x} and {@code z} to the core in
 * place and then passes them to {@code super.checkRequirement}, which moves by the offset <em>again</em>:
 * with an offset of 3 the main box is checked three blocks further along the facing than the core, and
 * the placement's own clicked cell is not the "free" one. The four collar boxes are checked from the
 * core, as intended. Reproduced exactly (the same call, the same arguments).
 */
public class AMSPReceiverBlock extends BlockDummyable {

	public static final MapCodec<AMSPReceiverBlock> CODEC = simpleCodec(AMSPReceiverBlock::new);

	/** The collar: four boxes at one layer, two ways round and two narrower. */
	static final int[][] EXTRA = { { 2, -2, 3, -2, 3, 3 }, { 2, -2, -2, 3, 3, 3 }, { 2, -2, 1, 1, 3, -2 }, { 2, -2, 1, 1, -2, 3 } };

	public AMSPReceiverBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends AMSPReceiverBlock> codec() {
		return CODEC;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		MachineTooltip.addWIP(tooltip);
		super.appendHoverText(stack, context, tooltip, flag);
	}

	@Override
	public int[] getDimensions() {
		return new int[] { 1, 0, 3, 3, 3, 3 };
	}

	@Override
	public int getOffset() {
		return 3;
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
		return state.getValue(PART) == Part.CORE ? new AMSPReceiverTE(pos, state) : null;
	}
}
