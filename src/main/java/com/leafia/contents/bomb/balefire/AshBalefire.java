package com.leafia.contents.bomb.balefire;

import com.hbm.blocks.ModBlocks;
import com.hbm.items.ModItems;
import com.hbm.potion.NtmEffects;
import com.leafia.client.ResidueClient;
import com.hbm.handler.radiation.ChunkRadiationManager;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Thermonuclear ash: the grey powder balefire burns down to, a snow-like layer that falls, burns
 * whoever walks through it, eats the rock beside it and, now and then, catches fire again.
 *
 * <p>Ported from {@code com.leafia.contents.bomb.balefire.AshBalefire}, a {@code BlockFallingLeafia}
 * (which is {@code BlockFalling} with a hand-rolled fall and a landing hook on its own entity,
 * {@code Bruh}). The ledger's answer was "vanilla {@code FallingBlock} for the falling bases"
 * (D-1093 #6), and that is {@link FallingBlock} here; the one thing upstream's hook did that
 * vanilla's landing does not is merge a landing layer with the layer it lands on, which is
 * {@link #onLand} and {@link #canBeReplaced}. <b>D-1093 #6 also said to skip the ignition until the
 * base {@code BlockBalefire} landed, and it has</b> ({@code com.hbm.blocks.bomb.Balefire}), so the
 * ignition is ported (D-3004).
 *
 * <p>Layers 1 to 8, like snow, with upstream's own shape: the selection box is {@code layers x 2}
 * pixels tall and the collision box one layer less.
 *
 * <h2>The tick</h2>
 *
 * It ticks by itself every 1 to 5 ticks, forever (and on random ticks as well, which join the same
 * chain). Each tick, in upstream's order:
 * <ol>
 * <li>one time in 4,000, it burns: with two layers or fewer it becomes balefire; with more it
 *     loses two layers and carries on. The tick ends there if it became balefire.
 * <li>otherwise, with odds {@code layers / 8}, it picks a block within two of itself on each axis.
 *     If that is not loaded, <b>the tick ends - no radiation, no fall, no further tick</b>; the chain
 *     is only restarted by a neighbour update or a random tick (upstream's early {@code return}).
 *     If the block is air or ash and has a flammable neighbour, balefire is lit there. Otherwise,
 *     if it is stone, cobblestone, baleonite, an infested stone or rock, and is not corium or a
 *     corium ore, it becomes baleonite at level 2, 1 or 0 - <b>by {@code (nextInt(3) + 1) ^ 2}, which
 *     in Java is XOR and not a square</b>: 0 and 1 give level 2 and 3 gives level 1, and level 0
 *     never comes up. Kept as written; see {@link BaleoniteBlock#roll}.
 * <li>it adds 0.8 RAD to its chunk, never past 80;
 * <li>it falls if the block under it is neither a full cube nor sturdy on top;
 * <li>it schedules itself again.
 * </ol>
 *
 * <p>Walking in or through it sets the walker alight for six seconds and gives radiation level 5
 * for twenty-five ticks (upstream's {@code 5 * 5}, which is ticks and not seconds).
 *
 * <h2>What it drops</h2>
 *
 * {@code powder_balefire}, {@code floor(layers / 2) - nextInt(2)} of them, so one layer drops none
 * (loot table, written by a generator script). A layer landing on a full stack of
 * itself and overflowing with nowhere to go drops {@code floor((total - 8) / 2) - nextInt(2)}.
 * <b>Deviation:</b> upstream also turned any ash that could not settle - a pit under it, a liquid -
 * into powder; vanilla's falling entity drops the block's own item instead, and cannot be told
 * otherwise, so in that rare case the player gets an {@code ash_balefire} item rather than powder.
 */
public class AshBalefire extends FallingBlock {

	public static final MapCodec<AshBalefire> CODEC = simpleCodec(AshBalefire::new);
	public static final IntegerProperty LAYERS = BlockStateProperties.LAYERS;

	/** Upstream's {@code SNOW_AABB}: indexed by layers, 0 to 8, two pixels each. */
	protected static final VoxelShape[] SHAPE_BY_LAYER = new VoxelShape[] {
			Shapes.empty(),
			Block.box(0.0, 0.0, 0.0, 16.0, 2.0, 16.0),
			Block.box(0.0, 0.0, 0.0, 16.0, 4.0, 16.0),
			Block.box(0.0, 0.0, 0.0, 16.0, 6.0, 16.0),
			Block.box(0.0, 0.0, 0.0, 16.0, 8.0, 16.0),
			Block.box(0.0, 0.0, 0.0, 16.0, 10.0, 16.0),
			Block.box(0.0, 0.0, 0.0, 16.0, 12.0, 16.0),
			Block.box(0.0, 0.0, 0.0, 16.0, 14.0, 16.0),
			Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 16.0)
	};

	/** {@code tickRate}. */
	public static final int TICK_RATE = 1;
	/** One time in this many ticks, it burns. */
	public static final int BURN_ONE_IN = 4000;
	/** Chunk RAD per tick, and the most it raises a chunk to: {@code incrementRad(world, pos, 80 * 0.01F, 80)}. */
	public static final float RAD = 80 * 0.01F;
	public static final float RAD_CAP = 80F;
	/** {@code setFire(6)}, seconds. */
	public static final int FIRE_SECONDS = 6;
	/** {@code new PotionEffect(radiation, 5 * 5, 4)}. */
	public static final int WALK_TICKS = 5 * 5;
	public static final int WALK_AMPLIFIER = 4;

	public AshBalefire(Properties properties) {
		super(properties);
		registerDefaultState(getStateDefinition().any().setValue(LAYERS, 1));
	}

	@Override
	protected MapCodec<? extends FallingBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(LAYERS);
	}

	@Override
	public int getDustColor(BlockState state, BlockGetter level, BlockPos pos) {
		return state.getMapColor(level, pos).col;
	}

	// ---------------------------------------------------------------- shape

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE_BY_LAYER[state.getValue(LAYERS)];
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE_BY_LAYER[state.getValue(LAYERS) - 1];
	}

	@Override
	protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
		return SHAPE_BY_LAYER[state.getValue(LAYERS)];
	}

	@Override
	protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE_BY_LAYER[state.getValue(LAYERS)];
	}

	@Override
	protected boolean useShapeForLightOcclusion(BlockState state) {
		return true;
	}

	/** {@code isPassable}: walkable below five layers. */
	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType type) {
		return type == PathComputationType.LAND && state.getValue(LAYERS) < 5;
	}

	// ---------------------------------------------------------------- the tick

	@Override
	protected int getDelayAfterPlace() {
		return TICK_RATE;
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		tick(state, level, pos, random);
	}

	/** Upstream's {@code updateTick}. */
	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {

		int layers = state.getValue(LAYERS);

		if(random.nextInt(BURN_ONE_IN) == 0) {
			if(layers <= 2) {
				level.setBlockAndUpdate(pos, ModBlocks.balefire.get().defaultBlockState());
				return;
			}
			level.setBlockAndUpdate(pos, state.setValue(LAYERS, layers - 2));

		} else if(random.nextInt(8) < layers) {

			BlockPos target = pos.offset(random.nextInt(5) - 2, random.nextInt(5) - 2, random.nextInt(5) - 2);
			if(!level.isLoaded(target)) return;

			BlockState there = level.getBlockState(target);
			if(there.isAir() || there.is(this)) {
				if(isSurroundingBlockFlammable(level, target))
					level.setBlockAndUpdate(target, ModBlocks.balefire.get().defaultBlockState());
			} else if(BaleoniteBlock.consumes(there) && !BaleoniteBlock.isCorium(there)) {
				int baleonite = BaleoniteBlock.levelForRoll(BaleoniteBlock.roll(random));
				if(baleonite >= 0)
					level.setBlockAndUpdate(target, com.leafia.contents.LeafiaBlocks.baleonite.get().withMeta(baleonite));
			}
		}

		ChunkRadiationManager.incrementRad(level, pos, RAD, RAD_CAP);
		fallIfUnsupported(level, pos);
		level.scheduleTick(pos, this, TICK_RATE + random.nextInt(5));
	}

	/**
	 * Upstream's {@code isSurroundingBlockFlammable}: any of the six neighbours can burn. 1.12.2 asked
	 * its material ({@code getCanBurn}); the successor 1.21.1 offers is the block's flammability,
	 * which is what the base mod's {@code ExplosionTom} uses for the same question. A neighbour in an
	 * unloaded chunk cannot burn.
	 */
	protected boolean isSurroundingBlockFlammable(Level level, BlockPos pos) {
		for(Direction direction : Direction.values()) {
			BlockPos near = pos.relative(direction);
			if(!level.isLoaded(near)) continue;
			BlockState state = level.getBlockState(near);
			if(state.getFlammability(level, near, Direction.UP) > 0 || state.getFireSpreadSpeed(level, near, Direction.UP) > 0)
				return true;
		}
		return false;
	}

	// ---------------------------------------------------------------- falling

	/**
	 * Upstream's {@code spaceBelow}: nothing solid to rest on - the block under is neither a full
	 * cube nor sturdy on its top. (Air, fire and liquids are neither, which covers upstream's
	 * {@code canFallThrough} as well.)
	 */
	public boolean spaceBelow(Level level, BlockPos pos) {
		BlockPos belowPos = pos.below();
		BlockState below = level.getBlockState(belowPos);
		return !below.isCollisionShapeFullBlock(level, belowPos) && !below.isFaceSturdy(level, belowPos, Direction.UP);
	}

	private void fallIfUnsupported(ServerLevel level, BlockPos pos) {
		if(spaceBelow(level, pos) && pos.getY() >= level.getMinBuildHeight()) {
			FallingBlockEntity entity = FallingBlockEntity.fall(level, pos, level.getBlockState(pos));
			falling(entity);
		}
	}

	/**
	 * Lets a falling layer land on a layer of itself. Vanilla's falling entity asks the landing
	 * block whether it can be replaced with an empty hand and a directional context - which is the
	 * one thing that never otherwise asks. A real player placing ash, or anything else, is not
	 * affected: ash is not replaceable by itself, so it is placed beside, as upstream's was.
	 */
	@Override
	protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
		if(context instanceof DirectionalPlaceContext && context.getItemInHand().isEmpty() && state.getValue(LAYERS) < 8)
			return true;
		return super.canBeReplaced(state, context);
	}

	/**
	 * Upstream's {@code blockLanded}: the falling layers have been placed, over what was there. If
	 * that was ash, the two are added together: the lower block takes up to 8, the rest goes on top
	 * if it fits, and otherwise becomes powder.
	 */
	@Override
	public void onLand(Level level, BlockPos pos, BlockState state, BlockState replaced, FallingBlockEntity entity) {
		if(!replaced.is(this)) return;

		int total = replaced.getValue(LAYERS) + state.getValue(LAYERS);
		level.setBlock(pos, state.setValue(LAYERS, Math.min(total, 8)), Block.UPDATE_ALL);
		if(total <= 8) return;

		BlockPos above = pos.above();
		if(level.getBlockState(above).canBeReplaced()) {
			level.setBlock(above, state.setValue(LAYERS, total - 8), Block.UPDATE_ALL);
		} else {
			int count = drops(total - 8, level.random);
			if(count > 0) Block.popResource(level, pos.above(), new ItemStack(ModItems.powder_balefire.get(), count));
		}
	}

	/** Upstream's {@code drops}: {@code floorDiv(layers, 2) - nextInt(2)}, which can be negative. */
	public static int drops(int layers, RandomSource random) {
		return Math.floorDiv(layers, 2) - random.nextInt(2);
	}

	// ---------------------------------------------------------------- contact

	/** Upstream's {@code onEntityWalk} and {@code onEntityCollision}, which do the same thing. */
	@Override
	public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
		super.stepOn(level, pos, state, entity);
		burn(level, entity);
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
		burn(level, entity);
	}

	private static void burn(Level level, Entity entity) {
		if(level.isClientSide) return;
		entity.igniteForSeconds(FIRE_SECONDS);
		if(entity instanceof LivingEntity living)
			living.addEffect(new MobEffectInstance(NtmEffects.RADIATION, WALK_TICKS, WALK_AMPLIFIER));
	}

	// ---------------------------------------------------------------- client

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		super.animateTick(state, level, pos, random);
		ResidueClient.ashDisplayTick(level, state.getValue(LAYERS), pos, random);
	}
}
