package com.leafia.contents.machines.reactors.pwr.blocks.wreckage;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.custom_hbm.effectNT.EffectNT;
import com.leafia.contents.LeafiaItems;
import com.leafia.contents.machines.reactors.pwr.debris.PWRDebrisItem;
import com.llib.exceptions.LeafiaDevFlaw;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * What a level-3 PWR meltdown leaves of the vessel's walls: the block that stood there, drawn on one of a
 * handful of broken meshes, scorched by how close it was to the core.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/reactors/pwr/blocks/wreckage/PWRMeshedWreck.java}. The
 * block is invisible ({@link RenderShape#INVISIBLE}) and {@link RenderPWRMeshedWreck} draws it; the block
 * entity ({@link PWRMeshedWreckEntity}) remembers the block it replaced, its {@link Erosion}, which mesh of
 * that erosion it shows, and its scorch (0 to 7). {@link #FACING} is the way the broken face points.
 *
 * <h2>Kept as upstream wrote it</h2>
 *
 * <ul>
 * <li>{@link VariationGroupConstructor}, {@link VariationGroup}, {@link Variation} and {@link WreckBound}
 *     line for line: the variation bits ({@code index << 5 | erosion << 3}), the eight-per-erosion limit,
 *     a {@code null} hitbox list meaning the whole block, an empty one meaning none, and the two
 *     hitbox-building conventions in upstream's ASCII-art comments.</li>
 * <li>The collision boxes are the variation's hitboxes turned by the facing exactly as upstream turns
 *     them ({@link #collisionBoxes}): pointing up, as built; down, {@code rotateX(-180)}; sideways,
 *     {@code rotateY(180 + yaw)} then {@code rotateX(90)}. The outline stays the whole block, because
 *     upstream overrides only {@code addCollisionBoxToList}.</li>
 * <li>Breaking it drops three to five debris items ({@code for(i = rand(3); i < 5 + fortune; i++)}) of the
 *     block it replaced: shards for anything whose name contains "glass", otherwise the block's own kind
 *     ({@link #getDebrisItem}). Walking on one scorched past 4 sets you alight for four seconds.</li>
 * <li>Above scorch 3 it occasionally flames ({@code rbmkflame}, 300 ticks) and crackles, one chance in
 *     {@code 45 + 40 x (7 - scorch)} a display tick.</li>
 * </ul>
 *
 * <h2>Changed shape</h2>
 *
 * <ul>
 * <li><b>The mesh is a name.</b> Upstream holds an {@code HFRWavefrontObject} per variation and draws its
 *     named groups; 1.21.1 bakes models, so a {@link Variation} carries the mesh's name and the renderer
 *     draws the baked (mesh, object) models a generator script generates.</li>
 * <li><b>The replaced block is a {@link BlockState}</b> rather than a registry name and a metadata.
 *     Its registry name is still what the drops carry as {@code block}.</li>
 * <li><b>Drops read the block entity off the loot context</b>, so upstream's {@code rmCache} - a map that
 *     kept the block entity alive between {@code onBlockHarvested} and {@code getDrops}, and was never
 *     emptied - has no successor.</li>
 * </ul>
 */
public abstract class PWRMeshedWreck extends Block implements EntityBlock {

	public static final DirectionProperty FACING = DirectionalBlock.FACING;

	/**
	 * Every wreck mesh's object names, as a generator script baked them (one model per
	 * pair) - the renderer's table, kept here so a test on the server can check the models exist.
	 */
	public static final Map<String, String[]> MESH_OBJECTS = new java.util.LinkedHashMap<>();

	static {
		MESH_OBJECTS.put("intact", new String[] {"TOP", "BACK", "RIGHT", "FRONT", "LEFT", "BOTTOM"});
		MESH_OBJECTS.put("metal_slight_0", new String[] {"Cube.002"});
		MESH_OBJECTS.put("metal_slight_1", new String[] {"Cube"});
		MESH_OBJECTS.put("metal_wreck_0", new String[] {"Cube"});
		MESH_OBJECTS.put("metal_rubble_0", new String[] {"Cube"});
		MESH_OBJECTS.put("stone_slight_0", new String[] {"Cube"});
		MESH_OBJECTS.put("stone_slight_1", new String[] {"Cube"});
		MESH_OBJECTS.put("stone_slight_2", new String[] {"Cube"});
		MESH_OBJECTS.put("wreck_stone", new String[] {"Cube"});
		MESH_OBJECTS.put("wreck_stone_3", new String[] {"Cube"});
		MESH_OBJECTS.put("stone_rubble_0_flip", new String[] {"stone_rubble_0"});
		MESH_OBJECTS.put("stone_rubble_1", new String[] {"Cube"});
		MESH_OBJECTS.put("stone_rubble_2", new String[] {"Cube"});
		MESH_OBJECTS.put("stone_rubble_3", new String[] {"Cube"});
	}

	/** Upstream's {@code setHardness(5)}; the resistance is the 1.12.2 default that leaves, 5 (D-012). */
	public static final float HARDNESS = 5F;

	protected PWRMeshedWreck(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.UP));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	/** Upstream's {@code onBlockPlacedBy}: faces the placer, all six ways ({@code getDirectionFromEntityLiving}). */
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new PWRMeshedWreckEntity(pos, state);
	}

	/** What a non-glass wreck breaks into. */
	public abstract Item getDebrisItem();

	public abstract VariationGroup getVariations();

	/**
	 * Turns this block into a wreck of {@code state}: upstream's {@code create}. The variation is a random
	 * one of the erosion's list ({@code rand.nextInt(list.length)}).
	 */
	public void create(Level world, BlockPos pos, Direction face, BlockState state, Erosion erosion, int heat) {
		create(world, pos, face, state, erosion, heat, world.random);
	}

	public void create(Level world, BlockPos pos, Direction face, BlockState state, Erosion erosion, int heat, RandomSource rand) {
		world.setBlock(pos, this.defaultBlockState().setValue(FACING, face), Block.UPDATE_ALL);
		if(world.getBlockEntity(pos) instanceof PWRMeshedWreckEntity entity) {
			entity.display = state;
			entity.erosion = erosion;
			entity.scorch = heat;
			entity.variation = rand.nextInt(this.getVariations().list(erosion).length);
			entity.sync();
		}
	}

	// ---------------------------------------------------------------------------------- drops

	/**
	 * Upstream's {@code getDrops}: three to five debris items naming the replaced block, more with fortune.
	 * A wreck with no replaced block drops nothing (upstream would throw on its null name).
	 */
	@Override
	protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
		BlockEntity be = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
		if(!(be instanceof PWRMeshedWreckEntity wreck) || wreck.display == null) return List.of();
		ItemStack tool = params.getOptionalParameter(LootContextParams.TOOL);
		int fortune = tool == null || tool.isEmpty() ? 0 : EnchantmentHelper.getItemEnchantmentLevel(
				params.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE), tool);
		return drops(wreck.display, fortune, params.getLevel().random);
	}

	/** The drop list: {@code for(int i = rand.nextInt(3); i < 5 + fortune; i++)} copies of one stack. */
	public List<ItemStack> drops(BlockState display, int fortune, RandomSource rand) {
		Item item = PWRDebrisItem.blockName(display).contains("glass") ? LeafiaItems.lwr_shard.get() : getDebrisItem();
		ItemStack stack = PWRDebrisItem.of(item, display);
		List<ItemStack> drops = new ArrayList<>();
		for(int i = rand.nextInt(3); i < 5 + fortune; i++)
			drops.add(stack.copy());
		return drops;
	}

	/** Upstream's {@code onEntityWalk}: scorched past 4, it sets you alight ({@code setFire(4)}). */
	@Override
	public void stepOn(Level world, BlockPos pos, BlockState state, Entity entity) {
		if(world.getBlockEntity(pos) instanceof PWRMeshedWreckEntity wreck && wreck.scorch > 4)
			entity.igniteForSeconds(4);
		super.stepOn(world, pos, state, entity);
	}

	// ------------------------------------------------------------------------------ the shape

	/** Upstream's {@code addCollisionBoxToList}: the variation's hitboxes, turned by the facing. None without the block entity. */
	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
		if(!(world.getBlockEntity(pos) instanceof PWRMeshedWreckEntity wreck)) return Shapes.empty();
		VoxelShape shape = Shapes.empty();
		for(AABB box : collisionBoxes(getVariations().get(wreck.erosion, wreck.variation), state.getValue(FACING)))
			shape = Shapes.or(shape, Shapes.create(box));
		return shape;
	}

	/** The boxes, in block space, a variation collides with when facing {@code face}. Rotation parity with the renderer. */
	public static List<AABB> collisionBoxes(Variation variation, Direction face) {
		List<AABB> out = new ArrayList<>();
		for(WreckBound hitbox : variation.hitboxes) {
			WreckBound bound = hitbox.reflect();
			if(face.getStepY() != 0) {
				if(face == Direction.DOWN) bound = bound.rotateX(-180);
			} else {
				bound = bound.rotateY(180 + face.toYRot()).rotateX(90);
			}
			out.add(bound.compile());
		}
		return out;
	}

	@Override
	protected boolean propagatesSkylightDown(BlockState state, BlockGetter world, BlockPos pos) {
		return true;
	}

	// ------------------------------------------------------------------------------- the client

	/** Upstream's {@code randomDisplayTick}: a scorched wreck flames now and then. */
	@Override
	public void animateTick(BlockState state, Level world, BlockPos pos, RandomSource rand) {
		super.animateTick(state, world, pos, rand);
		if(world.getBlockEntity(pos) instanceof PWRMeshedWreckEntity wreck) {
			int heat = wreck.scorch;
			if(heat >= 4 && rand.nextInt(45 + 40 * (7 - heat)) == 0) {
				CompoundTag data = new CompoundTag();
				data.putString("type", "rbmkflame");
				data.putInt("maxAge", 300);
				data.putDouble("posX", pos.getX() + 0.5);
				data.putDouble("posY", pos.getY() + 1.75 + 0.75 * (heat - 4) / 3);
				data.putDouble("posZ", pos.getZ() + 0.5);
				EffectNT.effectNT(data);
				world.playLocalSound(pos.getX() + 0.5F, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS,
						1.0F + rand.nextFloat(), rand.nextFloat() * 0.7F + 0.3F, false);
			}
		}
	}

	// ------------------------------------------------------------------------- the variations

	public enum Erosion {
		NONE, SLIGHT, NORMAL, RUBBLE;

		public int getBits() {
			return this.ordinal() << 3;
		}
	}

	public static class VariationGroupConstructor {

		Erosion writeMode = null;
		final Map<Erosion, List<Variation>> map = new EnumMap<>(Erosion.class);

		public VariationGroupConstructor() {
			for(Erosion value : Erosion.values()) map.put(value, new ArrayList<>());
		}

		public VariationGroupConstructor setErosion(Erosion erosion) {
			writeMode = erosion;
			return this;
		}

		public VariationGroupConstructor addVariation(String meshName, @Nullable WreckBound... hitboxes) {
			if(writeMode == null) throw new LeafiaDevFlaw("setErosion must be called before using addVariation!");
			List<Variation> list = map.get(writeMode);
			if(list.size() >= 8) throw new LeafiaDevFlaw("Variations can only be added nax 8 for each Erosion level!");
			list.add(new Variation((list.size() << 5) + writeMode.getBits(), meshName, hitboxes));
			return this;
		}

		public VariationGroup compile() {
			return new VariationGroup(
					map.get(Erosion.NONE).toArray(new Variation[0]),
					map.get(Erosion.SLIGHT).toArray(new Variation[0]),
					map.get(Erosion.NORMAL).toArray(new Variation[0]),
					map.get(Erosion.RUBBLE).toArray(new Variation[0]));
		}
	}

	public static class VariationGroup {

		public final Variation[] NONE;
		public final Variation[] SLIGHT;
		public final Variation[] NORMAL;
		public final Variation[] RUBBLE;

		private VariationGroup(Variation[] none, Variation[] slight, Variation[] normal, Variation[] rubble) {
			this.NONE = none;
			this.SLIGHT = slight;
			this.NORMAL = normal;
			this.RUBBLE = rubble;
		}

		public Variation[] list(Erosion erosion) {
			return switch(erosion) {
				case NONE -> NONE;
				case SLIGHT -> SLIGHT;
				case NORMAL -> NORMAL;
				case RUBBLE -> RUBBLE;
			};
		}

		public Variation fromBits(int bits) {
			return list(Erosion.values()[(bits >> 3) & 0b11])[(bits >> 5) & 0b111];
		}

		public Variation get(Erosion erosion, int variation) {
			return list(erosion)[variation];
		}
	}

	/** How the renderer textures a mesh, decided from its object names the first time it is drawn. */
	public enum RuntimeRenderType {
		PARTICLE,
		ALL_SIX,
		SIDE_TOP,
		SIDE_TOP_BOTTOM
	}

	public static class Variation {

		/** The mesh: {@code models/leafia/pwrwreck/<meshName>.obj} upstream, its baked objects here. */
		public final String meshName;
		public final WreckBound[] hitboxes;
		public final boolean collision;
		public final int bits;
		/** Set by the renderer, as upstream's. */
		public RuntimeRenderType renderType = null;

		Variation(int bits, String meshName, @Nullable WreckBound[] hitboxes) {
			this.bits = bits;
			this.meshName = meshName;
			if(hitboxes == null) {
				this.collision = true;
				this.hitboxes = new WreckBound[] {new WreckBound(0, 0, 2, 2, 0, 2)};
			} else {
				this.collision = hitboxes.length > 0;
				this.hitboxes = hitboxes;
			}
		}
	}

	/**
	 * Utility class for making bounding boxes, set in the way shown on upstream's diagram, but internally
	 * converted to -0.5 ~ 0.5 with the centre as its pivot, which makes rotations easy.
	 */
	public static class WreckBound {

		double front0;
		double front1;
		double up0;
		double up1;
		double right0;
		double right1;
		Vec3 lookVector = new Vec3(0, 0, 1);
		Vec3 upVector = new Vec3(0, 1, 0);
		Vec3 rightVector = new Vec3(1, 0, 0);

		private WreckBound() {}

		/**
		 * x, f: -1 ~ 0 ~ 1 (sideways and front-to-back); y, width, thickness, height: 0 ~ 2.
		 */
		public WreckBound(double x, double f, double width, double thickness, double y, double height) {
			front0 = (f - thickness / 2) / 2;
			right0 = (x - width / 2) / 2;
			up0 = (y - 1) / 2;
			front1 = (f + thickness / 2) / 2;
			right1 = (x + width / 2) / 2;
			up1 = (y - 1 + height) / 2;
		}

		/** x0, x1, f0, f1: -5 ~ 0 ~ 5; y, height: 0 ~ 10. */
		public static WreckBound blend(double x0, double f0, double x1, double f1, double y, double height) {
			return new WreckBound((x0 + x1) / 10, -(f0 + f1) / 10, Math.abs(x1 - x0) / 5, Math.abs(f1 - f0) / 5, y / 5, height / 5);
		}

		public WreckBound reflect() {
			WreckBound bound = new WreckBound();
			bound.front0 = this.front0;
			bound.front1 = this.front1;
			bound.up0 = this.up0;
			bound.up1 = this.up1;
			bound.right0 = this.right0;
			bound.right1 = this.right1;
			bound.lookVector = this.lookVector;
			bound.upVector = this.upVector;
			bound.rightVector = this.rightVector;
			return bound;
		}

		public WreckBound rotateY(double angle) {
			WreckBound bound = reflect();
			double cos = Math.cos(angle / 180 * Math.PI);
			double sin = Math.sin(angle / 180 * Math.PI);
			bound.rightVector = rightVector.scale(cos).add(lookVector.scale(sin));
			bound.lookVector = lookVector.scale(cos).subtract(rightVector.scale(sin));
			return bound;
		}

		public WreckBound rotateZ(double angle) {
			WreckBound bound = reflect();
			double cos = Math.cos(angle / 180 * Math.PI);
			double sin = Math.sin(angle / 180 * Math.PI);
			bound.rightVector = rightVector.scale(cos).add(upVector.scale(sin));
			bound.upVector = upVector.scale(cos).subtract(rightVector.scale(sin));
			return bound;
		}

		public WreckBound rotateX(double angle) {
			WreckBound bound = reflect();
			double cos = Math.cos(angle / 180 * Math.PI);
			double sin = Math.sin(angle / 180 * Math.PI);
			bound.lookVector = lookVector.scale(cos).add(upVector.scale(sin));
			bound.upVector = upVector.scale(cos).subtract(lookVector.scale(sin));
			return bound;
		}

		public AABB compile() {
			Vec3 vec0 = new Vec3(0.5, 0.5, 0.5)
					.add(rightVector.scale(right0))
					.add(upVector.scale(up0))
					.add(lookVector.scale(-front1));
			Vec3 vec1 = new Vec3(0.5, 0.5, 0.5)
					.add(rightVector.scale(right1))
					.add(upVector.scale(up1))
					.add(lookVector.scale(-front0));
			return new AABB(
					Math.min(vec0.x, vec1.x), Math.min(vec0.y, vec1.y), Math.min(vec0.z, vec1.z),
					Math.max(vec0.x, vec1.x), Math.max(vec0.y, vec1.y), Math.max(vec0.z, vec1.z));
		}
	}

	/** Upstream's {@code onBlockPlacedBy} default for a hand-placed wreck; see the subclasses. */
	protected static void placedByHand(Level world, BlockPos pos, Block shown, int variations) {
		if(world.getBlockEntity(pos) instanceof PWRMeshedWreckEntity entity) {
			entity.display = shown.defaultBlockState();
			entity.variation = world.random.nextInt(variations);
			entity.scorch = world.random.nextInt(8);
			entity.sync();
		}
	}
}
