package com.leafia.contents.building.storage.broof;

import java.util.List;

import com.hbm.blocks.BlockDummyable;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A green canopy you can walk through and store fifty-four stacks in.
 *
 * <p>Earlier batches called it a flat rug, and nothing had drawn it; it is a dome two blocks
 * high over a 3x3 footprint, and upstream draws its OBJ unscaled. Corrected in D-1065.
 *
 * <p>Upstream: {@code .../building/storage/broof/BroofBlock.java}, and its tooltip explains
 * itself better than any summary would: <i>"To celebrate the fact this mod was used in osmp
 * server"</i>, <i>"BROOF!"</i>.
 *
 * <h2>It is a 3x3 multiblock with no collision</h2>
 *
 * {@code getDimensions()} is {@code {1, 0, 1, 1, 1, 1}} — one block up, none down, one in each
 * horizontal direction — so a broof is 3x3x2 with its core in the middle, and
 * {@code getOffset()} of 1 places that core one block in front of where the player clicked.
 *
 * <p>Upstream overrides {@code addCollisionBoxToList} to add <b>nothing</b>, so every one of
 * those nine positions is walked straight through: the canopy's two blocks of height are
 * something you see and cannot feel. Here that is
 * {@link #getCollisionShape} returning {@link Shapes#empty()}. The <em>outline</em> is a full
 * cube, because upstream leaves {@code getBoundingBox} alone and a block you cannot select is a
 * block you cannot break.
 *
 * <h2>The first thing in this addon with a screen</h2>
 *
 * Every GUI the addon has reached so far was blocked at the far end — the elevator's three are
 * all waiting on {@code ItemTooling} (D-1045), the radio's on the client audio proxy. This one
 * opens from a bare right click, so it is what stands up {@code LeafiaMenuTypes} and the
 * menu/screen pattern the rest will reuse. See D-1049.
 */
public class BroofBlock extends BlockDummyable {

	public static final MapCodec<BroofBlock> CODEC = simpleCodec(BroofBlock::new);

	public BroofBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends BroofBlock> codec() {
		return CODEC;
	}

	/** Upstream's: one up, none down, one each way horizontally — a 3x3 footprint, two tall. */
	@Override public int[] getDimensions() { return new int[] { 1, 0, 1, 1, 1, 1 }; }

	@Override public int getOffset() { return 1; }

	/**
	 * Upstream's {@code createNewTileEntity} answers only for {@code meta >= 12}, which is its
	 * core range; {@code PART} says the same thing without arithmetic.
	 */
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return switch(state.getValue(PART)) {
			case CORE -> new BroofTE(pos, state);
			case EXTRA, DUMMY -> null;
		};
	}

	/** Upstream's empty {@code addCollisionBoxToList}: you walk over a rug, not into it. */
	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.empty();
	}

	/**
	 * Upstream's {@code standardOpenBehavior(world, pos, player, 0)}, which is three things:
	 * do nothing on the client, do nothing while sneaking, and otherwise open the <b>core's</b>
	 * GUI rather than the clicked block's.
	 *
	 * <p>The sneak clause is why you can place a block on top of a broof. All three are kept.
	 */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
			Player player, BlockHitResult hit) {

		if(level.isClientSide) return InteractionResult.SUCCESS;
		if(player.isShiftKeyDown()) return InteractionResult.SUCCESS;

		BlockPos core = findCore(level, pos);
		if(core == null) return InteractionResult.FAIL;

		if(level.getBlockEntity(core) instanceof BroofTE broof && player instanceof ServerPlayer serverPlayer)
			serverPlayer.openMenu(broof, core);

		return InteractionResult.SUCCESS;
	}

	/**
	 * Drawn entirely by {@link BroofRender} — upstream's renderer draws the OBJ and the block
	 * itself has no model.
	 */
	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.ENTITYBLOCK_ANIMATED;
	}

	/** Upstream's two tooltip lines, verbatim. They are the block's entire reason for existing. */
	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context,
			List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.literal("To celebrate the fact this mod was used in osmp server"));
		tooltip.add(Component.literal("\"BROOF!\""));
	}
}
