package com.leafia.contents.machines.processing.solblaster;

import com.hbm.blockentity.BlockEntityProxyCombo;
import com.hbm.blocks.BlockDummyable;
import com.hbm.blocks.ITooltipProvider;
import com.hbm.handler.MultiblockHandlerXR;
import com.hbm.util.ItemStackUtil;
import com.leafia.contents.machines.processing.solblaster.recipes.SolBlasterRecipes;
import com.leafia.init.LeafiaBlockEntities;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * `sol_blaster`, the Neutron Blaster: a 3x3x3 machine with a two-block tower on top.
 *
 * <p>Upstream: {@code .../solblaster/SolBlasterBlock.java}, on {@code AddonBlockDummyable}, which
 * D-1028 found is registration bookkeeping over {@link BlockDummyable} (plus "no mob spawns on a
 * large machine", which is {@code noSpawns} on the block's properties here).
 *
 * <h2>The shape</h2>
 *
 * Upstream's {@code {2, 0, 1, 1, 1, 1}} with an offset of one is the 3x3x3 whose core is the
 * middle of the bottom layer. {@link #fillSpace} adds a second box, {@code {4, -3, 0, 0, 0, 0}}:
 * a down-extent of <em>minus</em> three, so the fill runs from the core's y+3 to y+4 and is one
 * block wide, the tower that the model's chimney stands in. Then the four corners of the bottom layer are
 * upgraded to {@code EXTRA}, with an inventory-only proxy each, which is how a hopper beside the
 * machine reaches the slots from the corners.
 *
 * <h2>Two upstream slips, fixed in D-3075</h2>
 *
 * Upstream shifted {@code x} and {@code z} to the core in place and passed them to {@code checkSpace} as the
 * "just placed block, which counts as free" origin, so the core's own cell was never checked and a solid block
 * there was replaced. The placed block is the free origin now, as in the base class. And the block implemented
 * {@code ITooltipProvider} without ever showing its lang text; it shows it now.
 *
 * <h2>What opens it, and what a break costs</h2>
 *
 * <ul>
 * <li>A click from any block of it opens the screen unless the player is sneaking.</li>
 * <li>Breaking the core scatters every item inside: upstream's {@code BlockDummyable.breakBlock}
 *     does, and this port's {@code BlockDummyable} has a TODO where that goes.</li>
 * </ul>
 */
public class SolBlasterBlock extends BlockDummyable implements ITooltipProvider {

	public static final MapCodec<SolBlasterBlock> CODEC = simpleCodec(SolBlasterBlock::new);

	/** Upstream's tower: from the core's y+3 up to y+4, one block wide. */
	static final int[] TOWER = { 4, -3, 0, 0, 0, 0 };

	public SolBlasterBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends SolBlasterBlock> codec() {
		return CODEC;
	}

	@Override
	public void appendHoverText(net.minecraft.world.item.ItemStack stack, net.minecraft.world.item.Item.TooltipContext context,
			java.util.List<net.minecraft.network.chat.Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
		addStandardInfo("tile.sol_blaster.desc", tooltip); // D-3075: upstream implemented ITooltipProvider and never showed it
		super.appendHoverText(stack, context, tooltip, flag);
	}

	@Override
	public int[] getDimensions() {
		return new int[] { 2, 0, 1, 1, 1, 1 };
	}

	@Override
	public int getOffset() {
		return 1;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return switch(state.getValue(PART)) {
			case CORE -> new SolBlasterTE(pos, state);
			// Upstream's TileEntityProxyCombo(true, false, false): inventory only.
			case EXTRA -> new BlockEntityProxyCombo(LeafiaBlockEntities.SOL_BLASTER_DUMMY.get(), pos, state).inventory();
			case DUMMY -> null;
		};
	}

	/** Ticks on both sides: the server runs the machine and the client eases the door. */
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if(state.getValue(PART) != Part.CORE) return null;
		return createTickerHelper(type, LeafiaBlockEntities.SOL_BLASTER.get(), SolBlasterTE::tick);
	}

	@Override
	protected void fillSpace(Level world, BlockPos pos, Direction dir, int o) {

		super.fillSpace(world, pos, dir, o);

		BlockPos core = pos.relative(dir, o);

		MultiblockHandlerXR.fillSpace(world, core, TOWER, this, dir);

		makeExtra(world, core.offset(1, 0, 1));
		makeExtra(world, core.offset(1, 0, -1));
		makeExtra(world, core.offset(-1, 0, 1));
		makeExtra(world, core.offset(-1, 0, -1));
	}

	/** The tower, then the box, with the placed block as the free origin of both (D-3075). */
	@Override
	protected boolean checkRequirement(Level world, BlockPos pos, Direction dir, int o) {

		BlockPos core = pos.relative(dir, o);

		// D-3075: the free origin is the placed block (as the base class passes it), not the core's own cell, which
		// upstream left unchecked so a solid block there was silently replaced.
		if(!MultiblockHandlerXR.checkSpace(world, core, TOWER, pos, dir)) return false;
		return MultiblockHandlerXR.checkSpace(world, core, getDimensions(), pos, dir);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
			Player player, BlockHitResult hit) {

		if(level.isClientSide || player.isShiftKeyDown()) return InteractionResult.sidedSuccess(level.isClientSide);

		BlockPos core = findCore(level, pos);
		if(core == null) return InteractionResult.PASS;

		if(level.getBlockEntity(core) instanceof SolBlasterTE blaster && player instanceof ServerPlayer serverPlayer)
			serverPlayer.openMenu(blaster, core);

		return InteractionResult.SUCCESS;
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {

		if(!state.is(newState.getBlock()) && !level.isClientSide && state.getValue(PART) == Part.CORE)
			ItemStackUtil.spillItems(level, pos, state.getBlock(), level.getRandom());

		super.onRemove(state, level, pos, newState, movedByPiston);
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	/** The number of input slots holding something the blaster has a recipe for. */
	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {

		BlockPos core = findCore(level, pos);
		if(core == null || !(level.getBlockEntity(core) instanceof SolBlasterTE blaster)) return 0;

		int power = 0;
		for(int i = SolBlasterTE.FIRST_INPUT; i < SolBlasterTE.SLOTS; i++)
			if(SolBlasterRecipes.isValidInput(blaster.slots[i])) power++;

		return power;
	}

	/** Every block of the machine reports its own neighbours' redstone to the core. */
	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos,
			boolean movedByPiston) {

		if(!level.isClientSide) {
			BlockPos core = findCore(level, pos);
			if(core != null && level.getBlockEntity(core) instanceof SolBlasterTE blaster)
				blaster.updateRedstonePower(pos);
		}

		super.neighborChanged(state, level, pos, block, fromPos, movedByPiston);
	}
}
