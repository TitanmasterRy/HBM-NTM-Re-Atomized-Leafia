package com.leafia.contents.machines.reactors.pwr;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.hbm.blocks.ModBlocks;
import com.hbm.blocks.fluid.CoriumFinite;
import com.hbm.blocks.generic.BlockSellafieldSlaked;
import com.hbm.explosion.ExplosionNT;
import com.hbm.explosion.ExplosionNT.ExAttrib;
import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.toclient.AuxParticlePacketNT;
import com.hbm.sound.NtmSounds;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.machines.reactors.pwr.PWRData.PWRExplosion;
import com.leafia.contents.machines.reactors.pwr.blocks.components.PWRComponentBlock;
import com.leafia.contents.machines.reactors.pwr.blocks.components.control.PWRControlBlock;
import com.leafia.contents.machines.reactors.pwr.blocks.components.element.PWRElementBlock;
import com.leafia.contents.machines.reactors.pwr.blocks.wreckage.PWRMeshedWreck;
import com.leafia.contents.machines.reactors.pwr.blocks.wreckage.PWRMeshedWreck.Erosion;
import com.leafia.contents.machines.reactors.pwr.debris.PWRDebrisEntity;
import com.leafia.dev.LeafiaUtil;
import com.leafia.dev.blocks.legacy.MetaBlock;
import com.leafia.passive.effects.LeafiaShakecam;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.DeferredBlock;

/**
 * The level-3 PWR meltdown: the vessel blows outward, the core's own blocks vaporise into radon and fly as
 * debris, the walls that hold are left as scorched wreckage facing the blast, and the ground around is
 * cooked into waste.
 *
 * <p>Upstream: {@code PWRData.PWRExplosion.explodeLv3()} in
 * {@code com/leafia/contents/machines/reactors/pwr/PWRData.java} - the one call D-3006 left as a hook,
 * {@link PWRData#LV3_MELTDOWN}, which this class fills ({@link #explodeLv3}). It is upstream's body in
 * upstream's order, split into named steps so the parts a test can reach are reachable; every constant is
 * upstream's.
 *
 * <ol>
 * <li><b>Reach.</b> The reactor's size is the mean of its three spans, each padded by 1 + 2 + 4
 *     ({@link #reactorSize}). The members grow four blocks outward on every face.</li>
 * <li><b>Who goes</b> ({@link #plan}). For each member in turn: a non-solid one pushes the blast its way
 *     ({@code pressure += ray x 2 / n}); fire and corium are skipped; a fuel column becomes its rod's
 *     corium, or air. Anything else casts eleven samples out along its ray from the centre, at
 *     {@code centre + ray x (s^1.5 + 1)} for s = 0, 0.2, ... 2, and counts the ones that are out of the
 *     world, air, or another member. At {@code 7 + rand(3)} it is vaporised ({@code pressure += ray / n});
 *     short of that it remains ({@code pressure -= ray / n}) if it is a solid visible cube or a component.</li>
 * <li><b>Vaporised</b> blocks beside a remaining one become wreck (if solid or a component); the rest
 *     throw {@link #debrisPerBlock} pieces each if solid and hard (hardness at least 1), and become dense
 *     radon. A piece flies at {@code ray / size x (1 + 4 rand) + signedPow(pressure, 0.8) / 2} per axis.</li>
 * <li><b>The blast.</b> Remaining blocks are swapped for bedrock so it cannot take them, then an
 *     {@link ExplosionNT} of {@code 24 x toughness / 15,000} with fire and no drops at resolution 32, and two
 *     camera shakes ({@code PWR_NEAR} within {@code 4.25 x size}, {@code PWR_FAR} within {@code 9.25 x size},
 *     intensity {@code toughness / 10,000}).</li>
 * <li><b>The wreckage</b> ({@link #wreckOne}). Each remaining block that is not buried, and each
 *     vaporised one turned to wreck, takes a heat of {@code clamp((1 - |ray| / (size / 2))^0.45 x 8, 0, 7)},
 *     floored, rounded or ceiled on a third each. A control rod becomes electrical scrap. A wreck faces the
 *     open side with the most open space beside it (starting from the side facing the core), and is eroded
 *     SLIGHT with two firm neighbours, RUBBLE when not boxed in, NORMAL otherwise - stone wreck for an
 *     iron-material block that sounds like stone, metal wreck for any other iron. A block that is not iron,
 *     or not a wreck, and is hot at all is cooked ({@link #wasteFor}) or else becomes an intact stone wreck.</li>
 * <li>The rest of the remaining blocks get themselves back, the debris is thrown, and an {@code rbmkmush}
 *     cloud of the reactor's size goes up with the RBMK's explosion sound at volume 50.</li>
 * </ol>
 *
 * <h2>What changed shape</h2>
 *
 * <ul>
 * <li><b>The rays read through {@link #rays}</b>, a world view the caller chooses: the world itself in
 *     play, and a bounded one in a test, where the samples would otherwise reach the GameTest world's
 *     neighbouring structures. The blast is {@link #BLAST}, for the same reason - a real one is a crater
 *     of radius 25 or more.</li>
 * <li><b>{@code Material}</b> is {@link PWRMaterial#isIron}; {@code isFullBlock} is
 *     {@code isSolidRender}; {@code isPassable} is "does not block motion"; upstream's {@code IFluidBlock}
 *     test is "not a liquid block".</li>
 * <li>Every player within 100 is granted {@code nukebwr} (ultra-hot steam hot coolant) or {@code nukepwr} (any
 *     liquid hot coolant), upstream's {@code AdvancementManager} calls, by {@link #advancementFor} (D-3040). The debug
 *     wand's chat lines are not carried.</li>
 * <li>Two base-mod gaps, handed off: the base mod's {@code waste_earth} and {@code waste_mycelium} have no
 *     levels (NTM-CE's have sixteen), so cooked grass and mycelium take the default; and with the addon's
 *     {@code legacyWaste} switch off its eight waste blocks do not exist, and what they would have been
 *     becomes intact stone wreck instead.</li>
 * </ul>
 */
public final class PWRMeltdown {

	private PWRMeltdown() {}

	/** Whether a ray sample counts toward vaporising: out of the world, or air. Members count separately. */
	@FunctionalInterface
	public interface RayLookup {
		boolean open(BlockPos pos);
	}

	/** The blast, swappable so a test can build a meltdown without one. */
	@FunctionalInterface
	public interface Blast {
		void blast(Level world, Vec3 at, float strength);
	}

	/** Upstream's: {@code ExplosionNT} with fire and no drops, at resolution 32. */
	public static Blast BLAST = (world, at, strength) -> {
		ExplosionNT nt = new ExplosionNT(world, null, at.x, at.y, at.z, strength);
		nt.addAttrib(ExAttrib.FIRE);
		nt.addAttrib(ExAttrib.NODROP);
		nt.overrideResolution(32);
		nt.explode();
	};

	/** The hook's implementation (PWRData.LV3_MELTDOWN): upstream's {@code explodeLv3}. */
	public static void explodeLv3(PWRData data, PWRExplosion boom) {
		Level world = boom.world;
		run(data, boom, world.random, p -> !world.isInWorldBounds(p) || world.getBlockState(p).isAir(), BLAST);
	}

	// ----------------------------------------------------------------------------- the numbers

	/** The mean of the three spans, each padded by {@code 1 + 2 + 4}. */
	public static double reactorSize(PWRExplosion b) {
		return (b.maxX - b.minX + 1 + 2 + 4 + b.maxY - b.minY + 1 + 2 + 4 + b.maxZ - b.minZ + 1 + 2 + 4) / 3d;
	}

	/** Pieces per vaporised block: 5, or 4 past size 30, 3 past 40, 2 past 50, 1 past 70. */
	public static int debrisPerBlock(double reactorSize) {
		int debrisPerBlock = 5;
		if(reactorSize > 30) debrisPerBlock = 4;
		if(reactorSize > 40) debrisPerBlock = 3;
		if(reactorSize > 50) debrisPerBlock = 2;
		if(reactorSize > 70) debrisPerBlock = 1;
		return debrisPerBlock;
	}

	/** {@code 24 x (toughness / 15,000)}. */
	public static float blastStrength(int toughness) {
		return 24 * (toughness / 15_000f);
	}

	/** The heat before rounding: {@code clamp((clamp(1 - |ray| / (size / 2), 0, 1))^0.45 x 8, 0, 7)}. */
	public static double heatBase(double rayLength, double reactorSize) {
		return Mth.clamp(Math.pow(Mth.clamp(1 - rayLength / (reactorSize / 2), 0, 1), 0.45) * 8, 0, 7);
	}

	/** Floored, rounded (roll 1) or ceiled (roll 2): upstream's {@code heatRand}. */
	public static int heat(double heatBase, int heatRand) {
		int heat = (int) heatBase;
		if(heatRand == 1) heat = (int) Math.round(heatBase);
		else if(heatRand == 2) heat = (int) Math.ceil(heatBase);
		return heat;
	}

	/** The side a wreck faces first: away from the core along the ray's strongest axis, UP on a tie. */
	public static Direction outward(Vec3 ray) {
		double absX = Math.abs(ray.x);
		double absY = Math.abs(ray.y);
		double absZ = Math.abs(ray.z);
		if((absX > absY) && (absX > absZ)) return (ray.x > 0) ? Direction.WEST : Direction.EAST;
		else if((absY > absX) && (absY > absZ)) return (ray.y > 0) ? Direction.DOWN : Direction.UP;
		else if((absZ > absX) && (absZ > absY)) return (ray.z > 0) ? Direction.NORTH : Direction.SOUTH;
		return Direction.UP;
	}

	/** SLIGHT with two firm neighbours, else RUBBLE when not boxed in, else NORMAL. */
	public static Erosion erosion(int reliableSurround, boolean surrounded) {
		Erosion erosion = Erosion.NORMAL;
		if(reliableSurround >= 2) erosion = Erosion.SLIGHT;
		else if(!surrounded) erosion = Erosion.RUBBLE;
		return erosion;
	}

	/**
	 * What heat cooks a block into, or null for "becomes an intact stone wreck". Levels are
	 * {@code min(heat, 6)}. Natural stone and cobblestone are level 0 and slaked sellafield its own level:
	 * either becomes slaked sellafield if the heat reaches that level. Upstream's classes, by their 1.21.1
	 * blocks: grass, gravel, dirt (and coarse dirt, podzol, farmland), snow layer, snow block, mycelium,
	 * sandstone (plain, chiselled, cut - not red), terracotta of any colour, sand and red sand.
	 */
	@Nullable
	public static BlockState wasteFor(BlockState state, int heat) {
		int lv = Math.min(heat, 6);
		Block block = state.getBlock();
		if(block == Blocks.GRASS_BLOCK) return ModBlocks.waste_earth.get().defaultBlockState();
		if(block == Blocks.GRAVEL) return legacy(LeafiaBlocks.waste_gravel, lv);
		if(block == Blocks.DIRT || block == Blocks.COARSE_DIRT || block == Blocks.PODZOL || block == Blocks.FARMLAND)
			return legacy(LeafiaBlocks.waste_dirt, lv);
		if(block == Blocks.SNOW) return legacy(LeafiaBlocks.waste_snow, lv);
		if(block == Blocks.SNOW_BLOCK) return legacy(LeafiaBlocks.waste_snow_block, lv);
		if(block == Blocks.MYCELIUM) return ModBlocks.waste_mycelium.get().defaultBlockState();
		if(block == Blocks.SANDSTONE || block == Blocks.CHISELED_SANDSTONE || block == Blocks.CUT_SANDSTONE)
			return legacy(LeafiaBlocks.waste_sandstone, lv);
		if(state.is(BlockTags.TERRACOTTA)) return legacy(LeafiaBlocks.waste_terracotta, lv);
		if(block == Blocks.SAND) return legacy(LeafiaBlocks.waste_sand, lv);
		if(block == Blocks.RED_SAND) return legacy(LeafiaBlocks.waste_sand_red, lv);
		int level = -1;
		if(block == Blocks.COBBLESTONE || block == Blocks.STONE || block == Blocks.GRANITE || block == Blocks.POLISHED_GRANITE
				|| block == Blocks.DIORITE || block == Blocks.POLISHED_DIORITE || block == Blocks.ANDESITE || block == Blocks.POLISHED_ANDESITE)
			level = 0;
		else if(block == ModBlocks.sellafield_slaked.get())
			level = state.getValue(BlockSellafieldSlaked.LEVEL);
		if((level >= 0) && (heat >= level))
			return ModBlocks.sellafield_slaked.get().defaultBlockState().setValue(BlockSellafieldSlaked.LEVEL, lv);
		return null;
	}

	@Nullable
	private static <T extends Block & MetaBlock> BlockState legacy(@Nullable DeferredBlock<T> holder, int level) {
		return holder == null ? null : holder.get().withMeta(level);
	}

	// ------------------------------------------------------------------------------- the plan

	/** Who goes (step 2): the grown members in order, which vaporise, which remain, and the pressure. */
	public static final class Plan {
		public final List<BlockPos> members = new ArrayList<>();
		public final List<BlockPos> vaporized = new ArrayList<>();
		public final List<BlockPos> remains = new ArrayList<>();
		/** Fuel columns, in order, and the corium each becomes (null: air). */
		public final Map<BlockPos, Block> elements = new LinkedHashMap<>();
		public Vec3 pressure = Vec3.ZERO;
	}

	/**
	 * Step 2, upstream's first member loop, without touching the world: {@code data.members} must already be
	 * grown. One {@code rand.nextInt(3)} per member that casts rays, in member order.
	 */
	public static Plan plan(PWRData data, PWRExplosion boom, RayLookup rays, RandomSource rand) {
		Level world = boom.world;
		Vec3 centerPoint = boom.centerPoint;
		Plan plan = new Plan();
		plan.members.addAll(data.members);
		Set<BlockPos> memberSet = new HashSet<>(plan.members);
		int n = plan.members.size();
		Vec3 pressure = Vec3.ZERO;
		for(BlockPos member : plan.members) {
			Vec3 ray = Vec3.atLowerCornerOf(member).add(0.5, 0.5, 0.5).subtract(centerPoint);
			BlockState state = world.getBlockState(member);
			if(!state.isSolid()) {
				pressure = pressure.add(ray.scale(2d / n));
				continue;
			}
			Block block = state.getBlock();
			if(block instanceof BaseFireBlock) continue;
			if(block instanceof CoriumFinite) continue;
			if(block instanceof PWRElementBlock) {
				plan.elements.put(member, data.coriumTypes.get(member.asLong()));
				continue;
			}
			boolean destroyed = false;
			int counter = 0;
			int threshold = 7 + rand.nextInt(3);
			for(double s = 0; s <= 2; s += 0.2) {
				BlockPos rayHit = BlockPos.containing(centerPoint.add(ray.scale(Math.pow(s, 1.5) + 1)));
				if(rays.open(rayHit) || memberSet.contains(rayHit)) {
					if(++counter >= threshold) {
						plan.vaporized.add(member);
						destroyed = true;
						pressure = pressure.add(ray.scale(1d / n));
						break;
					}
				}
			}
			if(!destroyed) {
				if(LeafiaUtil.isSolidVisibleCube(state, world, member) || block instanceof PWRComponentBlock)
					plan.remains.add(member);
				pressure = pressure.subtract(ray.scale(1d / n));
			}
		}
		plan.pressure = pressure;
		return plan;
	}

	// -------------------------------------------------------------------------------- the run

	/** What a run did, for tests. */
	public static final class Result {
		public Plan plan;
		public double reactorSize;
		public final List<BlockPos> placeWrecks = new ArrayList<>();
		public final List<BlockPos> allDebris = new ArrayList<>();
		public final List<PWRDebrisEntity> debris = new ArrayList<>();
	}

	/**
	 * Which advancement a level-3 meltdown grants, by the reactor's hot coolant (its second tank): {@code nukebwr} for
	 * ultra-hot steam, else {@code nukepwr} for any liquid, else none - upstream's two-armed test, in its order.
	 */
	public static String advancementFor(com.hbm.inventory.fluid.FluidType hot) {
		if(hot == null) return null;
		if(hot.equals(com.hbm.inventory.fluid.Fluids.ULTRAHOTSTEAM)) return com.leafia.init.LeafiaAdvancements.NUKE_BWR;
		if(hot.hasTrait(com.hbm.inventory.fluid.trait.FluidTraitSimple.FT_Liquid.class)) return com.leafia.init.LeafiaAdvancements.NUKE_PWR;
		return null;
	}

	/** Upstream's {@code explodeLv3}, with the rays and the blast chosen by the caller (class note). */
	public static Result run(PWRData data, PWRExplosion boom, RandomSource rand, RayLookup rays, Blast blast) {
		Level world = boom.world;
		Vec3 centerPoint = boom.centerPoint;
		Result result = new Result();
		double reactorSize = reactorSize(boom);
		result.reactorSize = reactorSize;

		// nukebwr / nukepwr for every player within 100 of the centre block (class note)
		String advancement = advancementFor(data.tankTypes[1]);
		if(advancement != null)
			com.leafia.init.LeafiaAdvancements.grantWithin(world, new net.minecraft.world.phys.AABB(BlockPos.containing(centerPoint)).inflate(100), advancement);

		// mmm this is gonna be crispy computer
		data.growMembers(data.growMembers(data.growMembers(data.growMembers(data.members))));

		Plan plan = plan(data, boom, rays, rand);
		result.plan = plan;
		for(Map.Entry<BlockPos, Block> element : plan.elements.entrySet()) {
			if(element.getValue() != null) world.setBlockAndUpdate(element.getKey(), element.getValue().defaultBlockState());
			else world.removeBlock(element.getKey(), false);
		}
		Vec3 pressure = plan.pressure;

		Map<BlockPos, BlockState> placeMap = new LinkedHashMap<>();
		Set<BlockPos> antiPlaceSet = new HashSet<>();
		Set<BlockPos> remains = new HashSet<>(plan.remains);
		Set<BlockPos> vaporized = new HashSet<>(plan.vaporized);
		List<BlockPos> placeWrecks = result.placeWrecks;
		Set<BlockPos> placeWreckSet = new HashSet<>();
		List<BlockPos> allDebris = result.allDebris;
		int debrisPerBlock = debrisPerBlock(reactorSize);

		for(BlockPos pos : plan.vaporized) {
			boolean converted = false;
			BlockState state = world.getBlockState(pos);
			for(Direction face : Direction.values()) {
				if(remains.contains(pos.relative(face))) {
					if(LeafiaUtil.isSolidVisibleCube(state, world, pos) || state.getBlock() instanceof PWRComponentBlock) {
						placeWrecks.add(pos);
						placeWreckSet.add(pos);
						allDebris.add(pos);
						placeMap.put(pos, state);
						converted = true;
					}
					break;
				}
			}
			if(!converted) {
				if(!(state.getBlock() instanceof LiquidBlock) && LeafiaUtil.isSolidVisibleCube(state, world, pos)) {
					if(state.getDestroySpeed(world, pos) >= 1) {
						for(int i = 0; i < debrisPerBlock; i++) {
							Vec3 ray = Vec3.atLowerCornerOf(pos).add(0.5, 0.5, 0.5).subtract(centerPoint);
							PWRDebrisEntity debris = new PWRDebrisEntity(world, pos.getX() + 0.5D, pos.getY() + 0.5, pos.getZ() + 0.5D, state);
							double mx = PWRData.signedPow(ray.x, 1) / reactorSize * (1 + rand.nextDouble() * 4) + PWRData.signedPow(pressure.x, 0.8) / 2;
							double my = PWRData.signedPow(ray.y, 1) / reactorSize * (1 + rand.nextDouble() * 4) + PWRData.signedPow(pressure.y, 0.8) / 2;
							double mz = PWRData.signedPow(ray.z, 1) / reactorSize * (1 + rand.nextDouble() * 4) + PWRData.signedPow(pressure.z, 0.8) / 2;
							debris.setDeltaMovement(mx, my, mz);
							result.debris.add(debris);
						}
					}
				}
				world.setBlockAndUpdate(pos, ModBlocks.gas_radon_dense.get().defaultBlockState());
			}
		}

		for(BlockPos pos : plan.remains) {
			placeMap.put(pos, world.getBlockState(pos));
			world.setBlockAndUpdate(pos, Blocks.BEDROCK.defaultBlockState());
		}
		blast.blast(world, centerPoint.add(0.5, 0.5, 0.5), blastStrength(data.toughness));
		shake(world, centerPoint, reactorSize, data.toughness);

		for(BlockPos pos : plan.remains) {
			boolean buried = true;
			for(Direction face : Direction.values()) {
				BlockPos side = pos.relative(face);
				if(vaporized.contains(side) || !LeafiaUtil.isSolidVisibleCube(lv3(side, placeMap, world), world, side)) {
					buried = false;
					break;
				}
			}
			if(!buried) allDebris.add(pos);
		}
		for(BlockPos member : allDebris)
			wreckOne(world, member, centerPoint, reactorSize, placeMap, placeWreckSet, antiPlaceSet, rand);

		for(Map.Entry<BlockPos, BlockState> entry : placeMap.entrySet()) {
			if(!antiPlaceSet.contains(entry.getKey())) world.setBlockAndUpdate(entry.getKey(), entry.getValue());
		}
		for(PWRDebrisEntity debris : result.debris) world.addFreshEntity(debris);

		CompoundTag particle = new CompoundTag();
		particle.putString("type", "rbmkmush");
		particle.putFloat("scale", (float) reactorSize);
		if(world instanceof ServerLevel server) {
			PacketDispatcher.sendToPlayersNear(server, centerPoint.x + 0.5, centerPoint.y + 0.5, centerPoint.z + 0.5, 250,
					new AuxParticlePacketNT(particle, centerPoint.x + 0.5, centerPoint.y + 0.5, centerPoint.z + 0.5));
		}
		world.playSound(null, centerPoint.x + 0.5, centerPoint.y + 0.5, centerPoint.z + 0.5,
				NtmSounds.BLOCK_RBMK_EXPLOSION.get(), SoundSource.BLOCKS, 50.0F, 1.0F);
		return result;
	}

	/** The two shakes (step 4). Upstream's parameter strings, to the players upstream's target points reach. */
	public static String[][] shakeParams(double reactorSize, int toughness) {
		double shakeIntensity = toughness / 10_000d;
		return new String[][] {
				{"type=smooth", "preset=PWR_NEAR", "bloomDulling*" + (0.8125 - Math.pow(shakeIntensity, 0.5) / 20) / 0.75,
						"duration*" + Math.pow(shakeIntensity, 0.5), "range=" + reactorSize * 4},
				{"type=smooth", "preset=PWR_FAR", "duration*" + Math.pow(shakeIntensity, 0.5), "range=" + reactorSize * 9}};
	}

	private static void shake(Level world, Vec3 centerPoint, double reactorSize, int toughness) {
		if(!(world instanceof ServerLevel server)) return;
		String[][] params = shakeParams(reactorSize, toughness);
		BlockPos at = BlockPos.containing(centerPoint);
		LeafiaShakecam.sendNear(server, at, reactorSize * 4.25, params[0]);
		LeafiaShakecam.sendNear(server, at, reactorSize * 9.25, params[1]);
	}

	/** Upstream's {@code getBlockLv3}: what was there before the blast, if it was put aside, else what is there. */
	static BlockState lv3(BlockPos pos, Map<BlockPos, BlockState> map, Level world) {
		BlockState state = map.get(pos);
		return state != null ? state : world.getBlockState(pos);
	}

	private static final Direction[][] SIDES = {
			{Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH}, // X
			{Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST}, // Y: 1.12.2's EnumFacing.HORIZONTALS
			{Direction.UP, Direction.DOWN, Direction.EAST, Direction.WEST}}; // Z

	/** Step 5 for one block: upstream's {@code allDebris} loop body. One {@code rand.nextInt(3)}, then a wreck's own roll. */
	public static void wreckOne(Level world, BlockPos member, Vec3 centerPoint, double reactorSize, Map<BlockPos, BlockState> placeMap,
			Set<BlockPos> placeWrecks, Set<BlockPos> antiPlaceSet, RandomSource rand) {
		BlockState state = lv3(member, placeMap, world);
		Block block = state.getBlock();
		Vec3 ray = Vec3.atLowerCornerOf(member).add(0.5, 0.5, 0.5).subtract(centerPoint);

		if(block instanceof PWRControlBlock) {
			world.setBlockAndUpdate(member, ModBlocks.block_electrical_scrap.get().defaultBlockState());
			antiPlaceSet.add(member);
			state = ModBlocks.block_electrical_scrap.get().defaultBlockState();
			block = state.getBlock();
		}
		SoundType soundType = state.getSoundType();
		boolean iron = PWRMaterial.isIron(state);
		int heat = heat(heatBase(ray.length(), reactorSize), rand.nextInt(3));
		boolean noErosion = true;
		if(placeWrecks.contains(member)) {
			noErosion = false;
			Direction face = outward(ray);
			Direction most = null;
			int spaces = -1;
			int reliableSurround = 0;
			boolean surrounded = false;
			for(int i = 0; i < 7; i++) {
				Direction curFace = (i > 0) ? Direction.values()[i - 1] : face;
				if(i > 0 && curFace == face) continue; // skip if duplicate
				BlockPos behind = member.relative(curFace, -1);
				BlockPos front = member.relative(curFace);
				if(!lv3(behind, placeMap, world).isSolidRender(world, behind)) continue;
				if(placeWrecks.contains(behind)) continue;
				if(lv3(front, placeMap, world).blocksMotion()) continue;
				if(placeWrecks.contains(front)) continue;
				int mySpaces = 0;
				int surround = 0;
				int reliable = 0;
				for(Direction side : SIDES[curFace.getAxis().ordinal()]) {
					BlockPos beside = member.relative(side);
					if(placeWrecks.contains(beside))
						surround++;
					else if(lv3(beside, placeMap, world).isSolidRender(world, beside)) {
						surround++;
						reliable++;
					}
					BlockPos diagonal = front.relative(side);
					if(LeafiaUtil.isSolidVisibleCube(lv3(diagonal, placeMap, world), world, diagonal)) continue;
					if(placeWrecks.contains(diagonal)) continue;
					mySpaces++;
				}
				if(mySpaces > spaces) {
					spaces = mySpaces;
					most = curFace;
					surrounded = surround >= 4;
					reliableSurround = reliable;
				}
			}
			if(most != null) {
				Erosion erosion = erosion(reliableSurround, surrounded);
				if(iron) {
					PWRMeshedWreck wreck = soundType == SoundType.STONE ? LeafiaBlocks.lwrwreck_stone.get() : LeafiaBlocks.lwrwreck_metal.get();
					wreck.create(world, member, most, state, erosion, heat, rand);
					antiPlaceSet.add(member);
				} else
					noErosion = true;
			}
		}
		if(noErosion && heat > 0) {
			BlockState waste = wasteFor(state, heat);
			if(waste != null) world.setBlockAndUpdate(member, waste);
			else LeafiaBlocks.lwrwreck_stone.get().create(world, member, Direction.UP, state, Erosion.NONE, heat, rand);
			antiPlaceSet.add(member);
		}
	}
}
