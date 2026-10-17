package com.leafia.contents.machines.processing.mixingvat;

import java.util.List;

import com.hbm.blocks.BlockDummyable;
import com.leafia.contents.machines.processing.mixingvat.proxy.MixingVatProxy;
import com.leafia.dev.machine.MachineTooltip;
import com.leafia.init.LeafiaBlockEntities;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * `mixingvat`: the mixing vat's block - two wide, three deep and two high, with three port blocks on its ground floor.
 *
 * <p>Upstream: {@code .../mixingvat/MixingVatBlock.java}, on {@code AddonBlockDummyable}, which D-1028 found is
 * registration bookkeeping over {@link BlockDummyable}; this block is the one place its {@code getRotationMat} was
 * ever used, and that lands as {@link MixingVatTE#getFacingForward} and the proxy's two distances, derived from the
 * core's facing (D-3016).
 *
 * <h2>The shape</h2>
 *
 * Upstream's {@code {1, 0, 2, 0, 0, 1}} with an offset of <b>zero</b>: the core is the machine's back left corner, and the
 * box runs one up, two toward the front and one to the right. With {@code front} the way the machine faces
 * (the opposite of the stored direction) and {@code right} its clockwise turn, the extras are {@code core + right},
 * {@code core + 2 front} and {@code core + 2 front + right} - the back row's second block and the whole front row,
 * which is where salt goes in and out. The vat's own ports (where a duct or cable stands to reach it) are one block
 * further: behind both back blocks, and beside the front row's outer sides ({@code MixingVatTE.inputPorts}/{@code outputPorts}).
 *
 * <h2>The tooltip</h2>
 *
 * Upstream's "unfinished / extremely buggy" badge, and nothing else; kept.
 */
public class MixingVatBlock extends BlockDummyable {

	public static final MapCodec<MixingVatBlock> CODEC = simpleCodec(MixingVatBlock::new);

	public MixingVatBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends MixingVatBlock> codec() {
		return CODEC;
	}

	@Override
	public int[] getDimensions() {
		return new int[] { 1, 0, 2, 0, 0, 1 };
	}

	@Override
	public int getOffset() {
		return 0;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return switch(state.getValue(PART)) {
			case CORE -> new MixingVatTE(pos, state);
			case EXTRA -> new MixingVatProxy(LeafiaBlockEntities.MIXING_VAT_DUMMY.get(), pos, state);
			case DUMMY -> null;
		};
	}

	/** Ticks on both sides: the server runs the machine and the client turns the blade. */
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if(state.getValue(PART) != Part.CORE) return null;
		return createTickerHelper(type, LeafiaBlockEntities.MIXING_VAT.get(), MixingVatTE::tick);
	}

	@Override
	protected void fillSpace(Level world, BlockPos pos, Direction back, int o) {

		super.fillSpace(world, pos, back, o);

		BlockPos core = pos.relative(back, o);
		Direction front = back.getOpposite();
		Direction right = front.getClockWise();

		this.makeExtra(world, core.relative(right));
		this.makeExtra(world, core.relative(front, 2));
		this.makeExtra(world, core.relative(front, 2).relative(right));
	}

	/** Upstream's {@code standardOpenBehavior}: any part opens the core's screen unless the player is sneaking. */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
			Player player, BlockHitResult hit) {

		if(level.isClientSide) return player.isShiftKeyDown() ? InteractionResult.PASS : InteractionResult.SUCCESS;
		if(player.isShiftKeyDown()) return InteractionResult.PASS;

		BlockPos core = findCore(level, pos);
		if(core == null) return InteractionResult.FAIL;

		if(level.getBlockEntity(core) instanceof MixingVatTE vat && player instanceof ServerPlayer serverPlayer)
			serverPlayer.openMenu(vat, core);

		return InteractionResult.SUCCESS;
	}

	/** Drawn entirely by {@link MixingVatRenderNeo}. */
	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.ENTITYBLOCK_ANIMATED;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		MachineTooltip.addWIP(tooltip);
		super.appendHoverText(stack, context, tooltip, flag);
	}
}
