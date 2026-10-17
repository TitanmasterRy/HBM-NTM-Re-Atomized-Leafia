package com.leafia.contents.building.storage.rack;

import javax.annotation.Nullable;

import com.hbm.blockentity.machine.storage.BlockEntityCrateBase;
import com.hbm.blocks.BlockDummyable;
import com.leafia.dev.math.FiaMatrix;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The storage rack: a 3 wide, 2 deep, 3 tall shelf with four places for a block, opened from the shelf.
 *
 * <p>Upstream: {@code .../building/storage/rack/RackBlock.java}, an {@code AddonBlockDummyable} (registration
 * bookkeeping over the base multiblock, D-1028) with dimensions {@code {2, 0, 1, 0, 1, 1}} and offset 0; only
 * the core has a block entity.
 *
 * <h2>Which place was clicked</h2>
 *
 * Upstream finds the core, builds a matrix at the core's centre rotated by the core's facing
 * ({@code NORTH} 180, {@code SOUTH} 0, {@code WEST} 270, {@code EAST} 90), and puts the click in that matrix's
 * space: <b>left</b> is {@code x <= 0}, <b>upper</b> is {@code y >= 1.5} (from the core's floor), and the place is
 * {@code (left ? 0 : 1) + (upper ? 2 : 0)}. The matrix is the addon's {@link FiaMatrix}, ported with its tests;
 * the arithmetic is kept as written.
 *
 * <h2>What a click does</h2>
 *
 * <ul>
 * <li>On a place with a block: <b>sneaking with an empty hand</b> takes it off the rack into the hand's slot (the
 *     crate's contents with it); otherwise it <b>opens</b> the crate - nothing happens for a block that is not one
 *     of the base mod's crates. <b>No lock is asked about</b>, so a locked crate opens from a rack, as upstream's.</li>
 * <li>On an empty place with a block item in hand: puts one on the rack, if its model is a model.</li>
 * <li>Every other click on the rack is swallowed, so a block cannot be placed against it without sneaking.</li>
 * </ul>
 *
 * Breaking the rack drops every stored block, each with what it held.
 */
public class RackBlock extends BlockDummyable {

	public static final MapCodec<RackBlock> CODEC = simpleCodec(RackBlock::new);

	public RackBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends RackBlock> codec() {
		return CODEC;
	}

	@Override
	public int[] getDimensions() {
		return new int[] { 2, 0, 1, 0, 1, 1 };
	}

	@Override
	public int getOffset() {
		return 0;
	}

	/** The same table as {@code SolBlasterRender.facingDegrees}: the renderer's turn and the click matrix's. */
	public static float facingDegrees(Direction dir) {
		return switch(dir) {
			case NORTH -> 180F;
			case SOUTH -> 0F;
			case WEST -> 270F;
			case EAST -> 90F;
			default -> 0F;
		};
	}

	/** Upstream's {@code getMatrix(meta)}: a quarter turn per facing; the same table as the renderer's. */
	public static FiaMatrix getMatrix(Direction facing) {
		FiaMatrix mat = new FiaMatrix();
		switch(facing) {
			case NORTH -> mat = mat.rotateY(180);
			case SOUTH -> mat = mat.rotateY(0);
			case WEST -> mat = mat.rotateY(270);
			case EAST -> mat = mat.rotateY(90);
			default -> { }
		}
		return mat;
	}

	/** The place a click at {@code hit} lands on, for a rack whose core is at {@code core}. */
	public static int placeOf(BlockPos core, Direction facing, Vec3 hit) {
		FiaMatrix mat = new FiaMatrix(new Vec3(core.getX() + 0.5, core.getY(), core.getZ() + 0.5)).rotateAlong(getMatrix(facing));
		FiaMatrix rel = mat.toObjectSpace(new FiaMatrix(hit));
		boolean isLeft = rel.getX() <= 0;
		boolean isUpper = rel.getY() >= 1.5;
		return (isLeft ? 0 : 1) + (isUpper ? 2 : 0);
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return switch(state.getValue(PART)) {
			case CORE -> new RackTE(pos, state);
			case EXTRA, DUMMY -> null;
		};
	}

	/**
	 * Upstream's {@code onBlockActivated}, all of it: with or without an item, from either side. It is here and not
	 * in {@code useWithoutItem} because 1.21.1 asks the item's side first even for an empty hand, and a sneaking
	 * empty hand is the one click that takes a crate off.
	 */
	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
			Player player, InteractionHand hand, BlockHitResult hit) {

		if(!level.isClientSide) interact(level, pos, player, stack, hit.getLocation());
		return ItemInteractionResult.sidedSuccess(level.isClientSide);
	}

	/** The server's half of a click, public for the tests. */
	public void interact(Level level, BlockPos pos, Player player, ItemStack stack, Vec3 hit) {

		BlockPos core = findCore(level, pos);
		if(core == null) return;
		if(!(level.getBlockEntity(core) instanceof RackTE rack)) return;

		int index = placeOf(core, level.getBlockState(core).getValue(DIR), hit);

		if(rack.has(index)) {
			if(player.isShiftKeyDown() && stack.isEmpty()) {
				ItemStack box = rack.removeCrate(index, true);
				if(!box.isEmpty()) give(player, box);
			} else {
				BlockEntityCrateBase crate = rack.crate(index);
				if(crate != null && player instanceof ServerPlayer server) {
					crate.setRemoteAccess(true);
					server.openMenu(crate, core);
				}
			}
		} else if(!stack.isEmpty()) {
			if(rack.storeCrate(stack, index, true)) stack.shrink(1);
		}
	}

	/** Upstream's {@code player.inventory.add(currentItem, box)}; the box lands on the floor if the hand's slot is taken. */
	private static void give(Player player, ItemStack box) {
		Inventory inventory = player.getInventory();
		if(!inventory.add(inventory.selected, box)) player.drop(box, false);
	}

	/** Upstream's {@code breakBlock}: each stored block is spat out where the core was. */
	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if(!state.is(newState.getBlock()) && !level.isClientSide && state.getValue(PART) == Part.CORE
				&& level.getBlockEntity(pos) instanceof RackTE rack) {
			for(int i = 0; i < RackTE.PLACES; i++) {
				ItemStack stack = rack.removeCrate(i, false);
				if(!stack.isEmpty()) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
			}
		}
		super.onRemove(state, level, pos, newState, movedByPiston);
	}

	/** Drawn entirely by {@link RackRender}. */
	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.ENTITYBLOCK_ANIMATED;
	}
}
