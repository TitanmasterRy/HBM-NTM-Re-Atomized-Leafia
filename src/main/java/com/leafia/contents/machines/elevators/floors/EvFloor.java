package com.leafia.contents.machines.elevators.floors;

import com.hbm.blocks.BlockDummyable;
import com.leafia.contents.machines.elevators.EvPulleyTE;
import com.leafia.dev.math.FiaBB;
import com.leafia.dev.math.FiaMatrix;
import com.leafia.init.LeafiaBlockEntities;
import com.mojang.serialization.MapCodec;

import api.hbm.block.IToolable;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A landing: the doorway a shaft opens onto on one floor, with its call button.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/elevators/floors/EvFloor.java}. State and
 * synchronisation are in {@link EvFloorTE}; this is placement, shape, the button hit region and
 * the redstone hook.
 *
 * <h2>Two blocks tall, and the top one is the doorway</h2>
 *
 * Dimensions {@code {2, 0, 0, 0, 1, 1}} — two up, none down, one each way across the face — and
 * {@link #fillSpace} then adds one <em>extra</em> block directly above the core. That extra is the
 * doorway: {@link #getCollisionShape} returns nothing for it while the doors are open, which is
 * what lets a player walk through, and returns the solid shape again once they shut. Upstream
 * keys that on {@code META >= extra}; here it is {@link Part#EXTRA}.
 *
 * <h2>The button is a hit region, not a block</h2>
 *
 * There is no separate button block. {@link #useWithoutItem} converts the hit vector into the
 * core's object space with {@link FiaMatrix} and asks whether it landed in a 1.5/16 square at
 * {@code (0.125 * 5, 0.125 * 8)}. Both constants are upstream's and are in <em>block</em> units,
 * unlike the elevator panels' mixed sixteenths (D-1034) — so they are kept as the literal
 * {@code 0.125f * 5} rather than folded to {@code 0.625f}, which is the form the original is
 * checkable against.
 *
 * <h2>The paint disguise is stored here and drawn later</h2>
 *
 * Right-clicking a landing with any block item paints it to look like that block, and upstream
 * draws it through 1.12.2's extended block states: a {@code SimpleUnlistedProperty<IBlockState>}
 * read by {@code getExtendedState} and rendered by {@code EvFloorBakedModel}. 1.21.1 has neither —
 * the successor is {@code ModelProperty} plus {@code ModelData} and a dynamic {@code BakedModel},
 * and nothing in this port uses either yet.
 *
 * <p><b>So this batch ports the paint's state and not its appearance.</b> Setting it works, it
 * saves, it synchronises and {@link EvFloorTE#paintBlock} holds it; the landing simply does not
 * look like it yet. That is not a hole this batch opened: {@code EvFloorRender} is unported too,
 * so the doors are equally invisible, and the disguise model and the door renderer want the same
 * work. They land together. D-1039.
 */
public class EvFloor extends BlockDummyable implements IToolable {

	public static final MapCodec<EvFloor> CODEC = simpleCodec(EvFloor::new);

	/** Upstream's button hit region, in block units, relative to the core's object space. */
	private static final float BUTTON_X = 0.125f * 5;
	private static final float BUTTON_Y = 0.125f * 8;
	private static final double BUTTON_SIZE = 1.5 / 16d;

	public EvFloor(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends EvFloor> codec() {
		return CODEC;
	}

	/**
	 * The pulley driving whatever shaft stands above this position, or null.
	 *
	 * <p>Upstream walks up to {@code world.getHeight()} and returns the <em>first</em> pulley it
	 * meets — unlike {@code EvWeightEntity.findPulley}, which keeps going and takes the highest.
	 * The two really do differ; kept.
	 */
	@Nullable
	public static EvPulleyTE getPulley(Level level, BlockPos centerPos) {
		BlockPos pos = centerPos;
		while(pos.getY() < level.getMaxBuildHeight()) {
			if(level.getBlockEntity(pos) instanceof EvPulleyTE pulley) return pulley;
			pos = pos.above();
		}
		return null;
	}

	/**
	 * Which way a landing faces.
	 *
	 * <p>Upstream reads {@code EnumFacing.byIndex(META - 10)} off the core's metadata;
	 * {@link BlockDummyable#DIR} carries the same thing here as a real property. Exposed as a
	 * static because {@link EvFloorTE} needs it and holds a state, not a block.
	 */
	public static Direction facingOf(BlockState state) {
		return state.getValue(DIR);
	}

	/**
	 * Upstream's facing-to-yaw table for the whole elevator family, in degrees.
	 *
	 * <p>The same four-case switch on {@code META - 10} appears in {@code EvFloor},
	 * {@code EvPulleyRender} and {@code EvBufferRender} upstream, identically — and it is
	 * <em>not</em> {@link com.hbm.render.util.ObjUtil#rotateForFacing}'s mapping, which is these
	 * four angles shifted by half a turn (the same trap D-1021 records for the fluid valve). One
	 * copy here, because three copies of a table that decides which way a machine faces is how
	 * two of them come to disagree.
	 */
	public static float yawOf(Direction face) {
		return switch(face) {
			case NORTH -> 180;
			case SOUTH -> 0;
			case WEST -> 270;
			case EAST -> 90;
			// Upstream's switch has no case for UP or DOWN and falls through to the identity.
			default -> 0;
		};
	}

	/** Upstream's {@code getMatrix(int meta)}: the facing as a rotation about Y. */
	public static FiaMatrix getMatrix(Direction face) {
		return new FiaMatrix().rotateY(yawOf(face));
	}

	private FiaMatrix getMatrix(BlockGetter level, BlockPos pos) {
		BlockPos core = findCore(level, pos);
		if(core == null) return new FiaMatrix();
		return getMatrix(facingOf(level.getBlockState(core)));
	}

	/**
	 * The painted surround is a real block model; the core and the doorway are the renderer's.
	 *
	 * <p>{@link BlockDummyable} answers {@code ENTITYBLOCK_ANIMATED} for every part, which is
	 * right for a machine whose whole appearance comes from a {@code BlockEntityRenderer}. A
	 * landing is not quite that: upstream's {@code getRenderType} returns {@code MODEL} while
	 * {@code META < extra} — the <b>dummies</b> — so the surround is chunk-meshed like any wall
	 * and only the core's frame and doors are drawn per-frame. That is what lets a painted
	 * landing be a wall of whatever block you painted it (D-1042), and it is also cheaper: the
	 * surround is meshed once per change rather than drawn every frame.
	 */
	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return state.getValue(PART) == Part.DUMMY ? RenderShape.MODEL : super.getRenderShape(state);
	}

	@Override public int[] getDimensions() { return new int[] { 2, 0, 0, 0, 1, 1 }; }

	@Override public int getOffset() { return 0; }

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		// Upstream: `if (meta >= 12) return new EvFloorTE()`, which in 1.12.2 BlockDummyable
		// metadata is the core and nothing else.
		return state.getValue(PART) == Part.CORE ? new EvFloorTE(pos, state) : null;
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if(state.getValue(PART) != Part.CORE) return null;
		return createTickerHelper(type, LeafiaBlockEntities.EV_FLOOR.get(), EvFloorTE::tick);
	}

	/** The dummies, plus one extra directly above the core: the doorway. */
	@Override
	protected void fillSpace(Level world, BlockPos pos, Direction dir, int o) {
		super.fillSpace(world, pos, dir, o);
		this.makeExtra(world, pos.relative(dir, o).above());
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		FiaBB bb = new FiaBB(
				new FiaMatrix(new Vec3(0.5, 0, 0.5))
						.rotateAlong(getMatrix(level, pos))
						.translate(0, 0, 0.5)
						.rotateY(180),
				-0.5, 0, 0.5, 1, 2 / 16d);
		return Shapes.create(bb.toAABB());
	}

	/**
	 * The doorway has no collision while the doors are open.
	 *
	 * <p>Only the {@link Part#EXTRA} block — upstream's {@code META >= extra} — and only when the
	 * core's {@code open} is above zero, so a door caught mid-animation is already walkable. That
	 * is upstream's behaviour and it is what makes the landing passable at all.
	 */
	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		if(state.getValue(PART) == Part.EXTRA) {
			BlockPos core = findCore(level, pos);
			if(core != null && level.getBlockEntity(core) instanceof EvFloorTE floor && floor.open.cur > 0)
				return Shapes.empty();
		}
		return getShape(state, level, pos, context);
	}

	/**
	 * A screwdriver opens the floor-number screen. Upstream's {@code ItemTooling} branch of
	 * {@code onBlockActivated}.
	 *
	 * <p>Upstream tests {@code item instanceof ItemTooling} and then {@code getType() ==
	 * SCREWDRIVER} inside the block. The base mod inverts that - the tool reports a
	 * {@link ToolType} and the block decides - so this is the same two tests spelled the way
	 * {@code ItemBlowtorch} and the RBMK panels already spell them. <b>Nothing calls it yet:</b>
	 * the screwdriver is a plain {@code Item} in this port because {@code ItemTooling} is not
	 * ported, and its constructor is what registers it as {@link ToolType#SCREWDRIVER}. The day
	 * it is, this works with no change here - and until then a GameTest drives it directly.
	 *
	 * <p>Like upstream it answers true on either side once there is a core to talk to, and only
	 * the server sends the packet; a click on a dummy with no core answers false.
	 */
	@Override
	public boolean onScrew(Level level, Player player, BlockPos pos, Direction side, Vec3 hit, ToolType tool) {

		if(tool != ToolType.SCREWDRIVER) return false;

		BlockPos core = findCore(level, pos);
		if(core == null) return false;

		if(!level.isClientSide && level.getBlockEntity(core) instanceof EvFloorTE floor)
			floor.openGui(player);

		return true;
	}

	/**
	 * Right-click with a block: paint the landing to look like it.
	 *
	 * <p>Upstream paints only when {@code paintBlock == null}, so a landing is painted once and
	 * never repainted or cleared — there is no tool that removes it. Kept; it is a limitation, not
	 * an accident, and changing it would add behaviour the original does not have.
	 *
	 * <p>Upstream's screwdriver branch is not here: it is {@link #onScrew}, because the base mod
	 * dispatches tools through {@link IToolable} rather than an {@code instanceof ItemTooling}.
	 */
	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
			Player player, InteractionHand hand, BlockHitResult hit) {

		BlockPos core = findCore(level, pos);
		if(core == null) return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;

		if(!(stack.getItem() instanceof BlockItem blockItem))
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

		if(!(level.getBlockEntity(core) instanceof EvFloorTE floor))
			return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;

		if(floor.paintBlock != null)
			return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;

		floor.paintBlock = blockItem.getBlock();
		floor.paintMeta = 0;   // 1.21.1 has no block metadata; see EvFloorTE's note.
		floor.syncPaint();
		floor.setChanged();
		level.sendBlockUpdated(pos, state, state, 3);
		return ItemInteractionResult.sidedSuccess(level.isClientSide);
	}

	/** Empty-handed: the call button, if the click landed on it. */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
			Player player, BlockHitResult hit) {

		BlockPos core = findCore(level, pos);
		if(core == null) return InteractionResult.PASS;
		if(!(level.getBlockEntity(core) instanceof EvFloorTE floor)) return InteractionResult.PASS;

		BlockState coreState = level.getBlockState(core);
		FiaMatrix mat = new FiaMatrix(new Vec3(core.getX() + 0.5, core.getY(), core.getZ() + 0.5))
				.rotateAlong(getMatrix(facingOf(coreState)));
		FiaMatrix rel = mat.toObjectSpace(new FiaMatrix(hit.getLocation()));

		double x = rel.position.x;
		double y = rel.position.y;
		boolean onButton = x >= BUTTON_X - 0.5 && x <= BUTTON_X + BUTTON_SIZE
				&& y >= BUTTON_Y - 0.5 && y <= BUTTON_Y + BUTTON_SIZE;
		if(!onButton) return InteractionResult.PASS;

		if(!level.isClientSide) call(level, core, coreState, floor, player, InteractionHand.MAIN_HAND);
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	/**
	 * Ask the cabin to come to this landing.
	 *
	 * <p>Upstream repeats this walk in both the button branch and {@code checc}; hoisted, because
	 * the two were identical and a change to one that missed the other would make a landing
	 * answer a button but not a redstone pulse.
	 */
	private static void call(Level level, BlockPos core, BlockState coreState, EvFloorTE floor,
			@Nullable Player player, @Nullable InteractionHand hand) {
		BlockPos centerPos = core.relative(facingOf(coreState).getOpposite());
		EvPulleyTE pulley = getPulley(level, centerPos);
		if(pulley != null && pulley.elevator != null)
			pulley.elevator.onButtonServer("floor" + floor.floor, player, hand);
	}

	/** Upstream: redstone dust connects to a landing on every side. */
	@Override
	public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
		return true;
	}

	/**
	 * Upstream's {@code checc}: a powered landing calls the cabin.
	 *
	 * <p>It is level-triggered, not edge-triggered — every neighbour change while the block is
	 * powered calls again. Kept.
	 */
	public void checc(Level level, BlockPos pos) {
		BlockPos core = findCore(level, pos);
		if(core == null) return;
		if(!level.hasNeighborSignal(pos)) return;
		if(level.getBlockEntity(core) instanceof EvFloorTE floor)
			call(level, core, level.getBlockState(core), floor, null, null);
	}

	@Override
	protected void neighborChanged(BlockState state, Level world, BlockPos pos, Block block, BlockPos fromPos, boolean movedByPiston) {
		super.neighborChanged(state, world, pos, block, fromPos, movedByPiston);
		checc(world, pos);
	}

	/*
	 * Not ported with this batch, and both for the same reason:
	 *
	 *   - getExtendedState / the PAINT unlisted property -> ModelProperty + ModelData
	 *   - shouldSideBeRendered, which culls every face but the one the landing faces
	 *   - bakeModel, which swaps in EvFloorBakedModel
	 *
	 * All three are the paint disguise's rendering, and they want the dynamic BakedModel this
	 * port has no instance of yet. The state they read is ported and synched; see the class
	 * javadoc and D-1039.
	 */
}
