package com.leafia.contents.building.catwalk.railing;

import java.util.Set;

import com.leafia.dev.LeafiaUtil;
import com.leafia.dev.blocks.ICustomItemBlockProvider;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A catwalk railing: up to four thin arms along the edges of one block, joined into runs and
 * corners by whatever its neighbours have.
 *
 * <p>Upstream: {@code .../building/catwalk/railing/CatwalkRailingBase.java}. Abstract there and
 * abstract here; {@code AddonBlocks.Railings} registers exactly one subclass,
 * {@link com.leafia.contents.building.catwalk.railing.types.CatwalkRailingCurved}.
 *
 * <h2>The render mask, which is the whole design</h2>
 *
 * What a railing <em>looks</em> like is not what its blockstate says. Four bits say which arms
 * this block has; eight more say, for each end of each arm, whether a neighbouring railing
 * continues it — and those decide whether that end is drawn as a straight join, a corner curve
 * or a capped end. Sixteen bits in all, of which only four are blockstate.
 *
 * <p>So the block cannot pick a model, and {@link #getStateMapperVariant()} records that
 * upstream reached the same conclusion: its {@code getStateMapper} maps <b>every</b> state to
 * the single {@code normal} variant, and the appearance is decided further down. Here that is
 * {@code CatwalkRailingBakedModel}, which asks {@link #renderMask} at meshing time.
 *
 * <h2>Three things dissolve, and one narrows</h2>
 *
 * Upstream implements {@code ICustomBlockHighlight} and overrides {@code collisionRayTrace} and
 * {@code addCollisionBoxToList}, three separate hand-rolled answers to "this block is not a
 * cube". In 1.21.1 all three read a {@link VoxelShape} off the block, so
 * {@link #getShape} and {@link #getCollisionShape} replace the lot and the custom highlight
 * interface is not needed at all — vanilla draws one outline per box in the shape, which is
 * exactly what upstream's {@code drawHighlight} loop drew by hand.
 *
 * <p><b>The one narrowing is in the item</b>, and it is called out on
 * {@link CatwalkRailingItem#useOn}.
 *
 * <h2>Upstream's whole render-mask cache dissolves too</h2>
 *
 * Upstream keeps a static {@code Long2ObjectOpenHashMap} of chunk-to-position-to-mask, guarded
 * by a monitor, with a generation counter, an {@code IWorldEventListener} that invalidates a
 * 3x3 around every railing change, and three hooks in {@code LeafiaClientListener} for world
 * load and chunk load/unload. All of it exists because 1.12.2's {@code getExtendedState} is
 * called per railing per re-mesh on the chunk batcher threads and reads eight neighbours.
 *
 * <p>None of it is ported, and not because it looked obsolete — see standing rule 5. In 1.21.1
 * the mask is computed in the baked model's {@code getModelData}, which vanilla calls while
 * meshing a section and not otherwise; a section re-meshes when a block in it or beside it
 * changes, which is precisely when the cache would have been invalidated. The cache's whole job
 * is done by when the meshing happens. Its three {@code LeafiaClientListener} hooks therefore
 * need nothing when that file lands (D-1029).
 */
public abstract class CatwalkRailingBase extends Block implements ICustomItemBlockProvider {

	public static final BooleanProperty POS_X = BooleanProperty.create("pos_x");
	public static final BooleanProperty POS_Z = BooleanProperty.create("pos_z");
	public static final BooleanProperty NEG_X = BooleanProperty.create("neg_x");
	public static final BooleanProperty NEG_Z = BooleanProperty.create("neg_z");

	/** The four arm bits — upstream's, and the low four bits of the render mask. */
	public static final int MASK_POS_X = 0b1;
	public static final int MASK_POS_Z = 0b10;
	public static final int MASK_NEG_X = 0b100;
	public static final int MASK_NEG_Z = 0b1000;

	/**
	 * Upstream's sloped-railing bits. <b>Nothing sets either of them.</b>
	 *
	 * <p>The mask is only ever built from the four arm bits and the eight diagonal bits, so
	 * {@code MASK_SLOPED} is never on and the baked model's whole {@code stair_*} branch is
	 * unreachable — as are the eight {@code stair_*} objects the .obj declares for it. Carried
	 * rather than dropped, because the geometry is there and a sloped variant is evidently what
	 * it was cut for; dropping the constants would make that branch impossible to re-reach
	 * rather than merely unused.
	 */
	public static final int MASK_S_DIR_SHIFT = 4;
	public static final int MASK_SLOPED = 0b100_0000;

	/**
	 * The eight join bits, one per arm end: is there a railing arm continuing this one?
	 *
	 * <p>Read {@code MASK_POS_X_NZ} as "the +X arm, at its −Z end". Two neighbours can satisfy
	 * it — the block at −Z having a +X arm, or the block diagonally at +X−Z having a −X arm —
	 * which is what makes a run of railings join across a corner.
	 */
	public static final int MASK_POS_X_NZ = 0b1_00000000;
	public static final int MASK_POS_X_PZ = 0b10_00000000;
	public static final int MASK_POS_Z_NX = 0b100_00000000;
	public static final int MASK_POS_Z_PX = 0b1000_00000000;
	public static final int MASK_NEG_X_NZ = 0b10000_00000000;
	public static final int MASK_NEG_X_PZ = 0b100000_00000000;
	public static final int MASK_NEG_Z_NX = 0b1000000_00000000;
	public static final int MASK_NEG_Z_PX = 0b10000000_00000000;

	/** Upstream's arm: half a sixteenth thick, the full height of the block. */
	private static final double THICKNESS = 0.5 / 16;

	/** Upstream's {@code basePath}/{@code spritePath}/{@code modelPath}, kept as documentation. */
	public final String modelPath;

	protected CatwalkRailingBase(Properties properties, String modelPath) {
		super(properties);
		this.modelPath = modelPath;
		registerDefaultState(stateDefinition.any()
				.setValue(POS_X, false)
				.setValue(POS_Z, false)
				.setValue(NEG_X, false)
				.setValue(NEG_Z, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(POS_X, POS_Z, NEG_X, NEG_Z);
	}

	/**
	 * The single blockstate variant every state maps to.
	 *
	 * <p>Not a hook — 1.21.1 has no {@code StateMapper}, a blockstate JSON does the job. This
	 * exists so the fact is testable: the generated blockstate has exactly this one variant, and
	 * a test asserts it, because a railing that grew a per-state variant would silently stop
	 * drawing its joins.
	 */
	public static String getStateMapperVariant() {
		return "";
	}

	// ------------------------------------------------------------------ the arms

	/** Which arm a horizontal direction is, as a property. */
	public static BooleanProperty armFor(Direction face) {
		return switch(face) {
			case NORTH -> NEG_Z;
			case SOUTH -> POS_Z;
			case WEST -> NEG_X;
			case EAST -> POS_X;
			default -> null;
		};
	}

	/** Upstream's {@code getMetaFromState}: the four arm bits. */
	public static int armMask(BlockState state) {
		int mask = 0;
		if(state.getValue(POS_X)) mask |= MASK_POS_X;
		if(state.getValue(POS_Z)) mask |= MASK_POS_Z;
		if(state.getValue(NEG_X)) mask |= MASK_NEG_X;
		if(state.getValue(NEG_Z)) mask |= MASK_NEG_Z;
		return mask;
	}

	private static boolean hasArm(BlockGetter level, BlockPos pos, BooleanProperty arm) {
		BlockState s = level.getBlockState(pos);
		if(!(s.getBlock() instanceof CatwalkRailingBase)) return false;
		return s.getValue(arm);
	}

	/**
	 * Upstream's {@code getDiagonalMask}, offset for offset.
	 *
	 * <p>Two of the sixteen offsets look like typos and are <b>not corrected</b>:
	 * {@code MASK_POS_Z_PX} tests {@code (+1,0,+1)} where the symmetry of the other seven pairs
	 * would want {@code (+1,0,0)} paired with {@code (+1,0,+1)}, and reads the same block twice
	 * for two different bits. Changing it would change which corners draw as curves, which is a
	 * visual change to a shipped block (D-021). Flagged, not fixed.
	 */
	public static int diagonalMask(BlockGetter level, BlockPos pos) {
		int mask = 0;
		if(hasArm(level, pos.offset(0, 0, -1), POS_X) || hasArm(level, pos.offset(1, 0, -1), NEG_X)) mask |= MASK_POS_X_NZ;
		if(hasArm(level, pos.offset(0, 0, 1), POS_X) || hasArm(level, pos.offset(1, 0, 1), NEG_X)) mask |= MASK_POS_X_PZ;
		if(hasArm(level, pos.offset(-1, 0, 0), POS_Z) || hasArm(level, pos.offset(-1, 0, 1), NEG_Z)) mask |= MASK_POS_Z_NX;
		if(hasArm(level, pos.offset(1, 0, 0), POS_Z) || hasArm(level, pos.offset(1, 0, 1), NEG_Z)) mask |= MASK_POS_Z_PX;
		if(hasArm(level, pos.offset(0, 0, -1), NEG_X) || hasArm(level, pos.offset(-1, 0, -1), POS_X)) mask |= MASK_NEG_X_NZ;
		if(hasArm(level, pos.offset(0, 0, 1), NEG_X) || hasArm(level, pos.offset(-1, 0, 1), POS_X)) mask |= MASK_NEG_X_PZ;
		if(hasArm(level, pos.offset(-1, 0, 0), NEG_Z) || hasArm(level, pos.offset(-1, 0, -1), POS_Z)) mask |= MASK_NEG_Z_NX;
		if(hasArm(level, pos.offset(1, 0, 0), NEG_Z) || hasArm(level, pos.offset(1, 0, -1), POS_Z)) mask |= MASK_NEG_Z_PX;
		return mask;
	}

	/**
	 * The full sixteen-bit mask the baked model draws from — upstream's {@code getExtendedState}
	 * body, minus the cache.
	 */
	public static int renderMask(BlockGetter level, BlockPos pos, BlockState state) {
		return armMask(state) | diagonalMask(level, pos);
	}

	private static boolean has(int mask, int bit) {
		return (mask & bit) != 0;
	}

	private static Direction slopeDirection(int mask) {
		return Direction.from2DDataValue((mask >> MASK_S_DIR_SHIFT) & 0b11);
	}

	/**
	 * Upstream's part selection, branch for branch.
	 *
	 * <p><b>On the block rather than on the baked model, which is where upstream has it.</b>
	 * The model is {@code @OnlyIn(Dist.CLIENT)} and a dedicated server cannot load the class at
	 * all, so leaving the one piece of real arithmetic in the renderer would put it permanently
	 * out of reach of a GameTest — the trap D-1040 recorded for {@code rotateForFacing}. Hoisted
	 * for the same reason {@code EvBufferTE.squash} was.
	 *
	 * <p>Each arm contributes its post and then, at each of its two ends, one of three pieces:
	 * a {@code straight_} when a neighbouring railing continues it, a {@code curve_} when this
	 * block's own perpendicular arm turns the corner, and an {@code end_} cap otherwise. That
	 * three-way choice, eight times, is the whole of what the twelve non-arm mask bits buy.
	 *
	 * <p><b>The {@code stair_} branch is unreachable</b> — nothing ever sets
	 * {@code MASK_SLOPED}. Kept for the reason given on that constant.
	 */
	public static Set<String> partsFor(int mask) {

		Set<String> parts = new java.util.TreeSet<>();

		if(has(mask, MASK_SLOPED)) {
			Direction face = slopeDirection(mask);
			switch(face) {
				case NORTH -> {
					if(has(mask, MASK_POS_X)) parts.add("stair_pXnZ");
					if(has(mask, MASK_NEG_X)) parts.add("stair_nXnZ");
				}
				case SOUTH -> {
					if(has(mask, MASK_POS_X)) parts.add("stair_pXpZ");
					if(has(mask, MASK_NEG_X)) parts.add("stair_nXpZ");
				}
				case WEST -> {
					if(has(mask, MASK_POS_Z)) parts.add("stair_nXpZ");
					if(has(mask, MASK_NEG_Z)) parts.add("stair_nXnZ");
				}
				case EAST -> {
					if(has(mask, MASK_POS_Z)) parts.add("stair_pXpZ");
					if(has(mask, MASK_NEG_Z)) parts.add("stair_pXnZ");
				}
				default -> { }
			}
			return parts;
		}

		if(has(mask, MASK_POS_X)) {
			parts.add("post_pX");
			parts.add(has(mask, MASK_POS_X_PZ) ? "straight_pXpZ"
					: has(mask, MASK_POS_Z) ? "curve_pXpZ" : "end_pXpZ");
			parts.add(has(mask, MASK_POS_X_NZ) ? "straight_pXnZ"
					: has(mask, MASK_NEG_Z) ? "curve_pXnZ" : "end_pXnZ");
		}
		if(has(mask, MASK_NEG_X)) {
			parts.add("post_nX");
			parts.add(has(mask, MASK_NEG_X_PZ) ? "straight_nXpZ"
					: has(mask, MASK_POS_Z) ? "curve_nXpZ" : "end_nXpZ");
			parts.add(has(mask, MASK_NEG_X_NZ) ? "straight_nXnZ"
					: has(mask, MASK_NEG_Z) ? "curve_nXnZ" : "end_nXnZ");
		}
		if(has(mask, MASK_POS_Z)) {
			parts.add("post_pZ");
			parts.add(has(mask, MASK_POS_Z_PX) ? "straight_pZpX"
					: has(mask, MASK_POS_X) ? "curve_pXpZ" : "end_pZpX");
			parts.add(has(mask, MASK_POS_Z_NX) ? "straight_pZnX"
					: has(mask, MASK_NEG_X) ? "curve_nXpZ" : "end_pZnX");
		}
		if(has(mask, MASK_NEG_Z)) {
			parts.add("post_nZ");
			parts.add(has(mask, MASK_NEG_Z_PX) ? "straight_nZpX"
					: has(mask, MASK_POS_X) ? "curve_pXnZ" : "end_nZpX");
			parts.add(has(mask, MASK_NEG_Z_NX) ? "straight_nZnX"
					: has(mask, MASK_NEG_X) ? "curve_nXnZ" : "end_nZnX");
		}
		return parts;
	}

	// ------------------------------------------------------------------ shape

	/** The union of this state's arms, or nothing at all when it has none. */
	private static VoxelShape armShape(BlockState state) {
		VoxelShape shape = Shapes.empty();
		for(Direction face : Direction.Plane.HORIZONTAL) {
			if(state.getValue(armFor(face)))
				shape = Shapes.or(shape, LeafiaUtil.createAABBLayer(face, THICKNESS, 0, 1));
		}
		return shape;
	}

	/**
	 * Upstream's {@code collisionRayTrace} and {@code shouldDrawHighlight}, together.
	 *
	 * <p>Both return nothing for a railing with no arms — {@code shouldDrawHighlight} tests
	 * {@code getMetaFromState(state) == 0} and the ray trace finds no box — so an armless
	 * railing cannot be looked at, outlined or broken. Kept, and it is not reachable in play:
	 * {@link #getStateForPlacement} gives every placed railing all four arms and nothing ever
	 * removes one.
	 */
	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return armShape(state);
	}

	/**
	 * Upstream's {@code addCollisionBoxToList}: the arms, <b>plus a full cube when there are
	 * none</b>.
	 *
	 * <p>That second clause is upstream's {@code if (getMetaFromState(state) == 0) super...},
	 * and it makes an armless railing a solid invisible block you cannot see or break. Carried
	 * because it is upstream's, and unreachable for the same reason the note on {@link #getShape}
	 * gives.
	 */
	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		if(armMask(state) == 0) return Shapes.block();
		return armShape(state);
	}

	/** Upstream's: a railing placed fresh has all four arms, and loses them to nothing. */
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState()
				.setValue(POS_X, true)
				.setValue(POS_Z, true)
				.setValue(NEG_X, true)
				.setValue(NEG_Z, true);
	}

	/**
	 * Adds one arm to an existing railing, chosen by where on it the player clicked.
	 *
	 * <p>Upstream's {@code tryAddRailing}: the larger of the two offsets from the block centre
	 * picks the axis, its sign picks the side, and an arm already there means the click does
	 * nothing and the item is not consumed.
	 *
	 * @param hitX where in the block the click landed along X, 0 to 1
	 * @param hitZ the same along Z
	 * @return true when an arm was added
	 */
	public boolean tryAddRailing(Level level, BlockPos pos, double hitX, double hitZ) {

		BlockState state = level.getBlockState(pos);
		if(!(state.getBlock() instanceof CatwalkRailingBase)) return false;

		hitX -= 0.5;
		hitZ -= 0.5;

		BooleanProperty arm;
		if(Math.abs(hitX) >= Math.abs(hitZ)) arm = hitX >= 0 ? POS_X : NEG_X;
		else arm = hitZ >= 0 ? POS_Z : NEG_Z;

		if(state.getValue(arm)) return false;
		level.setBlock(pos, state.setValue(arm, true), 3);
		return true;
	}

	// ------------------------------------------------------------------ the item

	@Override
	public BlockItem provideItem() {
		return new CatwalkRailingItem(this, new Item.Properties());
	}

	/**
	 * The railing's item: clicking an existing railing's arm adds another arm rather than
	 * placing a second block.
	 *
	 * <p>Upstream nests this inside the block, and it stays nested — it reads the block's own
	 * {@link #tryAddRailing} and has no other consumer.
	 */
	public static class CatwalkRailingItem extends BlockItem {

		public CatwalkRailingItem(Block block, Properties properties) {
			super(block, properties);
		}

		/**
		 * Upstream's {@code onItemUse}, with one narrowing that is worth stating plainly.
		 *
		 * <p>Upstream guards on {@code player.capabilities.allowEdit && stack.canEditBlocks()}.
		 * The first is {@link net.minecraft.world.entity.player.Abilities#mayBuild}; the second
		 * is 1.12.2's adventure-mode "can this stack be placed on that block" check, which
		 * 1.21.1 performs inside {@code BlockItem.place} and exposes nowhere useful for a path
		 * that is not placing a block. So <b>an adventure-mode player who may build can add an
		 * arm to a railing they could not have placed</b>. Narrower than upstream in exactly
		 * one direction, and only in adventure mode.
		 *
		 * <h4>Why the hit coordinates are ever strictly inside the block</h4>
		 *
		 * The guard wants {@code hitX} and {@code hitZ} both strictly between 0 and 1, which a
		 * click on a block <em>face</em> never gives — one of them is exactly 0 or 1. It is
		 * reachable because the railing's shape is its arms, not its block: clicking the inner
		 * face of the north arm reports {@code hitZ = 0.03125}. So this branch fires only when
		 * the player aimed at a railing arm, which is the intent.
		 */
		@Override
		public InteractionResult useOn(UseOnContext context) {

			Level level = context.getLevel();
			BlockPos pos = context.getClickedPos();
			BlockState state = level.getBlockState(pos);
			Direction face = context.getClickedFace();
			Vec3 hit = context.getClickLocation();

			double hitX = hit.x - pos.getX();
			double hitZ = hit.z - pos.getZ();

			if(!(state.getBlock() instanceof CatwalkRailingBase railing)
					|| !(hitX > 0 && hitX < 1 && hitZ > 0 && hitZ < 1)
					|| !face.getAxis().isHorizontal())
				return super.useOn(context);

			Player player = context.getPlayer();
			ItemStack stack = context.getItemInHand();
			if(stack.isEmpty() || player == null || !player.getAbilities().mayBuild)
				return InteractionResult.FAIL;

			// Upstream's mirroring: the click is on the *inside* of an arm, so the arm being
			// asked for is the one across the block. Clicking away from an arm's midline along
			// the other axis flattens this axis to the centre, which hands the choice to the
			// perpendicular arm in tryAddRailing.
			if(face.getAxis() == Direction.Axis.X) {
				hitX = 1 - hitX;
				if(Math.abs(hitZ - 0.5) > 0.15) hitX = 0.5;
			} else if(face.getAxis() == Direction.Axis.Z) {
				hitZ = 1 - hitZ;
				if(Math.abs(hitX - 0.5) > 0.15) hitZ = 0.5;
			}

			if(!railing.tryAddRailing(level, pos, hitX, hitZ)) return InteractionResult.FAIL;

			stack.shrink(1);
			SoundType sound = state.getSoundType();
			level.playSound(player, pos, sound.getPlaceSound(), SoundSource.BLOCKS,
					(sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
			return InteractionResult.sidedSuccess(level.isClientSide);
		}
	}
}
