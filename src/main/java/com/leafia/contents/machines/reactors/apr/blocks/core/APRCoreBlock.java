package com.leafia.contents.machines.reactors.apr.blocks.core;

import java.util.List;
import java.util.Map;

import com.hbm.blockentity.BlockEntityProxyCombo;
import com.hbm.blocks.BlockDummyable;
import com.hbm.handler.MultiblockHandlerXR;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.machines.reactors.apr.blocks.APRComponentBlock;
import com.leafia.contents.machines.reactors.apr.blocks.APRComponentBlock.APRComponentType;
import com.leafia.dev.machine.MachineTooltip;
import com.leafia.dev.math.FiaMatrix;
import com.leafia.dev.math.FiaMatrix.RotationOrder;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * {@code apr_core}: the absorption particle reactor's core, a plus-shaped machine five blocks across with a block
 * above it, around which the player builds a disc and up to however many torus chambers they choose.
 *
 * <p>Upstream: {@code .../apr/blocks/core/APRCoreBlock.java}, on {@code AddonBlockDummyable} (D-1028).
 * {@code {0, 0, 2, 2, 1, 1}} with an offset of 2, then {@link #fillSpace} adds a block two out on each side of the
 * short axis ({@code {0, 0, 1, 1, 2, -2}} and {@code {0, 0, 1, 1, -2, 2}}) and the block above the core, which is
 * an {@code EXTRA} with a fluid-only proxy (upstream's {@code TileEntityProxyCombo(false, false, true)}): the
 * liquid oxygen leaves through it.
 *
 * <p>A right click (not sneaking) on any of it: while the reactor is not assembled, the server assembles it if the
 * structure is complete, and the client, if it is not, opens the chamber editor ({@link
 * com.leafia.contents.machines.reactors.apr.blocks.core.container.APRMBUI}); once assembled, the server opens the
 * reactor's screen. Breaking or blowing up any block of it disassembles the reactor first, which with damage on
 * it and particles in it is the endothermic blast ({@link APRCoreTE#disassemble}).
 *
 * <p>{@link #generateTorus} and {@link #generateCircle} are upstream's shape of a chamber, unchanged: four rings
 * 3 deep at {@code r + 0.5}, {@code r - 0.5}, {@code r + 1}, {@code r - 1} one below the core, and the inner ring
 * at {@code r} at the core's height, 5 deep, whose middle layers are turbine blades. A ring is
 * {@code floor(2 pi r / 4)} points over a quarter turn, each point the cell a matrix at {@code (0.5, 0.5, 0.5)}
 * turned by that fraction of 90 degrees and moved {@code -r + 0.25} along its front lands in, mirrored into all
 * four quarters; a point on an axis is a support, the rest assembled hull.
 */
public class APRCoreBlock extends BlockDummyable {

	public static final MapCodec<APRCoreBlock> CODEC = simpleCodec(APRCoreBlock::new);

	public APRCoreBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends APRCoreBlock> codec() {
		return CODEC;
	}

	public static void generateTorus(BlockPos pos, double radius, Map<BlockPos, BlockState> poses) {
		pos = pos.below();
		generateCircle(pos, radius + .5, poses, 3, false);
		generateCircle(pos, radius - .5, poses, 3, false);
		generateCircle(pos, radius + 1, poses, 3, false);
		generateCircle(pos, radius - 1, poses, 3, false);
		generateCircle(pos.above(), radius, poses, 5, true);
	}

	public static void generateCircle(BlockPos center, double radius, Map<BlockPos, BlockState> poses, int depth, boolean isInner) {
		double subdivs = Math.floor(radius * 2 * Math.PI / 4);
		FiaMatrix mat = new FiaMatrix(new Vec3(0.5, 0.5, 0.5));
		APRComponentBlock apr = LeafiaBlocks.apr_component.get();
		BlockState hull = apr.state(APRComponentType.HULL_ASSEMBLED);
		BlockState support = apr.state(APRComponentType.SUPPORT);
		BlockState inner = apr.state(APRComponentType.BLADES);
		for(int i = 0; i <= subdivs; i++) {
			double ratio = i / subdivs;
			FiaMatrix rot = mat.rotate(RotationOrder.XYZ, 0, ratio * 90, 0);
			BlockPos offset = BlockPos.containing(rot.translate(0, 0, -radius + 0.25).position);
			BlockState state = hull;
			if(offset.getX() == 0 || offset.getZ() == 0)
				state = support;
			for(int y = 0; y >= -depth; y--) {
				BlockState layer = (isInner && y < 0 && y > -depth) ? inner : state;
				poses.put(center.offset(offset.getX(), y, offset.getZ()), layer);
				poses.put(center.offset(-offset.getX(), y, offset.getZ()), layer);
				poses.put(center.offset(offset.getX(), y, -offset.getZ()), layer);
				poses.put(center.offset(-offset.getX(), y, -offset.getZ()), layer);
			}
		}
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		MachineTooltip.addMultiblock(tooltip);
		MachineTooltip.addCore(tooltip);
		super.appendHoverText(stack, context, tooltip, flag);
	}

	@Override
	public int[] getDimensions() {
		return new int[] { 0, 0, 2, 2, 1, 1 };
	}

	@Override
	public int getOffset() {
		return 2;
	}

	/** Upstream's three extra regions; the core is {@code pos} moved {@code o} along the facing. */
	private static final int[][] EXTRA_REGIONS = {
			{ 0, 0, 1, 1, 2, -2 },
			{ 0, 0, 1, 1, -2, 2 },
			{ 1, -1, 0, 0, 0, 0 },
	};

	@Override
	protected void fillSpace(Level world, BlockPos pos, Direction dir, int o) {
		super.fillSpace(world, pos, dir, o);
		BlockPos core = pos.relative(dir, o);
		for(int[] region : EXTRA_REGIONS)
			MultiblockHandlerXR.fillSpace(world, core, region, this, dir);
		makeExtra(world, core.above());
	}

	@Override
	protected boolean checkRequirement(Level world, BlockPos pos, Direction dir, int o) {
		BlockPos core = pos.relative(dir, o);
		for(int[] region : EXTRA_REGIONS) {
			if(!MultiblockHandlerXR.checkSpace(world, core, region, core, dir))
				return false;
		}
		return MultiblockHandlerXR.checkSpace(world, core, getDimensions(), core, dir);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return switch(state.getValue(PART)) {
			case CORE -> new APRCoreTE(pos, state);
			case EXTRA -> new BlockEntityProxyCombo(LeafiaBlockEntities.APR_CORE_DUMMY.get(), pos, state).fluid();
			case DUMMY -> null;
		};
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if(state.getValue(PART) != Part.CORE) return null;
		return createTickerHelper(type, LeafiaBlockEntities.APR_CORE.get(), (lvl, pos, st, be) -> be.update());
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if(player.isShiftKeyDown()) return InteractionResult.SUCCESS;
		BlockPos core = findCore(level, pos);
		if(core != null && level.getBlockEntity(core) instanceof APRCoreTE te) {
			boolean assembled = te.checkAssembled();
			if(level.isClientSide) {
				if(!te.assembled && !assembled)
					APRClient.openAssembly(te);
				return InteractionResult.SUCCESS;
			} else {
				if(!te.assembled) {
					te.assembled = assembled;
					if(assembled) te.onAssemble();
					return InteractionResult.SUCCESS;
				}
				if(player instanceof ServerPlayer serverPlayer)
					serverPlayer.openMenu(te, core);
				return InteractionResult.SUCCESS;
			}
		}
		return InteractionResult.PASS;
	}

	/** Upstream's {@code breakBlock} and {@code onBlockExploded}: whatever part goes, the reactor comes apart first. */
	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if(!state.is(newState.getBlock()) && !level.isClientSide) {
			BlockPos core = findCore(level, pos);
			if(core != null && level.getBlockEntity(core) instanceof APRCoreTE te)
				te.disassemble(core);
			if(state.getValue(PART) == Part.CORE)
				com.hbm.util.ItemStackUtil.spillItems(level, pos, state.getBlock(), level.getRandom());
		}
		super.onRemove(state, level, pos, newState, movedByPiston);
	}
}
