package com.leafia.gametest;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.imageio.ImageIO;

import com.custom_hbm.util.LCEColor;
import com.hbm.blocks.BlockDummyable;
import com.hbm.blocks.BlockDummyable.Part;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.machines.powercores.dfc.render.DFCLayout;
import com.leafia.contents.machines.powercores.dfc.render.DFCObj;
import com.leafia.contents.machines.processing.solblaster.SolBlasterObj.Mesh;
import com.leafia.contents.machines.research.amsp.analyzer.AMSPAnalyzerBlock;
import com.leafia.contents.machines.research.amsp.analyzer.AMSPAnalyzerTE;
import com.leafia.contents.machines.research.amsp.receiver.AMSPReceiverBlock;
import com.leafia.contents.machines.research.amsp.receiver.AMSPReceiverTE;
import com.leafia.init.LeafiaBlockEntities;
import com.leafia.lib.LeafiaRef;
import com.leafia.overwrite_contents.interfaces.IMixinTileEntityCore;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The overhauled DFC's renderers, the spectrometer's blocks and the sparks (DFC-4, D-3017), as far as a
 * dedicated server can see them: the meshes the renderers name, the layout arithmetic they run on, the
 * one-layer beam colour, the two particle kinds, the shock path, and the spectrometer's blocks. What the
 * renderers draw is looked at in a running client (the {@code dfc3} snapshot scene).
 *
 * <p>The meshes' numbers were counted off the clone's OBJs with a script, not out of the loader under
 * test; the layout's by hand from upstream's expressions.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaDFC4Tests {

	private static final String EMPTY = "empty";
	private static final String BIG = "empty16";

	// ---- the meshes ------------------------------------------------------------------------

	private static int faces(String mesh, String part) {
		Mesh m = DFCObj.load(mesh).get(part);
		return m == null ? -1 : m.faces();
	}

	/** Every part of every component mesh, counted off upstream's files: a copy that lost one is red. */
	@GameTest(template = EMPTY)
	public static void theComponentMeshesAreUpstreamsToTheFace(GameTestHelper helper) {
		Object[][] table = {
				{ DFCObj.BOOSTER, DFCObj.CORE, 202 }, { DFCObj.BOOSTER, DFCObj.FRAME, 58 },
				{ DFCObj.ABSORBER, DFCObj.CORE, 188 }, { DFCObj.ABSORBER, DFCObj.FRAME, 32 }, { DFCObj.ABSORBER, DFCObj.ARM, 26 },
				{ DFCObj.REINFORCED, DFCObj.CORE, 2866 }, { DFCObj.REINFORCED, DFCObj.FRAME, 32 }, { DFCObj.REINFORCED, DFCObj.ARM, 26 },
				{ DFCObj.REINFORCED, DFCObj.FAN, 4052 },
				{ DFCObj.INJECTOR, DFCObj.CORE, 98 }, { DFCObj.INJECTOR, DFCObj.FRAME, 50 }, { DFCObj.INJECTOR, DFCObj.ARM, 44 },
				{ DFCObj.STABILIZER, DFCObj.CORE, 3568 }, { DFCObj.STABILIZER, DFCObj.FRAME, 50 }, { DFCObj.STABILIZER, DFCObj.ARM, 8 },
				{ DFCObj.STABILIZER, DFCObj.LENS, 16 }, { DFCObj.STABILIZER, DFCObj.GLASS, 16 },
				{ DFCObj.EXCHANGER, DFCObj.CORE, 296 }, { DFCObj.EXCHANGER, DFCObj.FRAME, 58 },
				{ DFCObj.PULSER, DFCObj.CORE, 794 }, { DFCObj.PULSER, DFCObj.FRAME, 32 }, { DFCObj.PULSER, DFCObj.NEON, 48 },
				{ DFCObj.PULSER, DFCObj.ARM_LASER, 8 }, { DFCObj.PULSER, DFCObj.RING_OUTER, 32 }, { DFCObj.PULSER, DFCObj.RING_INNER, 32 },
				{ DFCObj.PULSER, DFCObj.ARM, 26 },
		};
		for(Object[] row : table) {
			int got = faces((String) row[0], (String) row[1]);
			helper.assertTrue(got == (int) row[2], row[0] + "/" + row[1] + " has " + got + " faces, upstream's has " + row[2]);
		}
		helper.succeed();
	}

	/**
	 * A part the mesh lacks draws nothing and says nothing, so the renderer's names are pinned against the
	 * files: it asks for every object each file has, and the only name it asks for that a file lacks is
	 * the {@code Arm} of the booster and the exchanger (upstream's shared loop asks every part).
	 */
	@GameTest(template = EMPTY)
	public static void theRendererAsksForEveryObjectAFileHasAndOnlyTheArmItMayLack(GameTestHelper helper) {
		for(Map.Entry<String, Set<String>> entry : DFCObj.DRAWN.entrySet()) {
			Set<String> file = new TreeSet<>(DFCObj.load(entry.getKey()).keySet());
			Set<String> asked = new TreeSet<>(entry.getValue());
			Set<String> unasked = new TreeSet<>(file);
			unasked.removeAll(asked);
			helper.assertTrue(unasked.isEmpty(), entry.getKey() + " has objects the renderer never draws: " + unasked);
			Set<String> missing = new TreeSet<>(asked);
			missing.removeAll(file);
			boolean armless = entry.getKey().equals(DFCObj.BOOSTER) || entry.getKey().equals(DFCObj.EXCHANGER);
			helper.assertTrue(armless ? missing.equals(Set.of(DFCObj.ARM)) : missing.isEmpty(),
					entry.getKey() + " is asked for " + missing + " which it lacks");
		}
		helper.assertTrue(DFCObj.DRAWN.size() == 7, "seven component meshes");
		helper.succeed();
	}

	/** Eight floats a vertex, four to a face, {@code v} flipped: the first vertex of two parts, read off the files. */
	@GameTest(template = EMPTY)
	public static void aMeshKeepsItsTextureCoordinatesWithTheVFlipped(GameTestHelper helper) {
		float[] core = DFCObj.load(DFCObj.BOOSTER).get(DFCObj.CORE).data();
		helper.assertTrue(core.length == 202 * 4 * 8, "the booster's core is " + core.length + " floats");
		helper.assertTrue(core[3] == 0.78125F && core[4] == 1F - 0.125F, "booster Core first uv " + core[3] + ", " + core[4]);
		float[] neon = DFCObj.load(DFCObj.PULSER).get(DFCObj.NEON).data();
		helper.assertTrue(neon[3] == 0.010417F && neon[4] == 1F - 0.8625F, "pulser Neon first uv " + neon[3] + ", " + neon[4]);
		helper.succeed();
	}

	/** The core's own meshes: ten deformed spheres of eighty faces, and the ring's two bands. */
	@GameTest(template = EMPTY)
	public static void theCoresSpheresAndRingAreUpstreams(GameTestHelper helper) {
		for(int i = 0; i < DFCObj.DEFORMED; i++) {
			Map<String, Mesh> sphere = DFCObj.load("deform" + i);
			helper.assertTrue(sphere.size() == 1 && sphere.containsKey("Icosphere") && sphere.get("Icosphere").faces() == 80,
					"deform" + i + " is one 80-face icosphere: " + sphere.keySet());
			helper.assertTrue(DFCObj.merged("deform" + i).faces() == 80, "merged");
		}
		Map<String, Mesh> ring = DFCObj.load(DFCObj.RING);
		helper.assertTrue(ring.keySet().equals(Set.of(DFCObj.INNER_BAND, DFCObj.OUTER_BAND)), "the ring's bands: " + ring.keySet());
		helper.assertTrue(ring.get(DFCObj.INNER_BAND).faces() == 3395 && ring.get(DFCObj.OUTER_BAND).faces() == 3395, "3,395 faces a band");
		helper.succeed();
	}

	/** The nine textures, standalone, at upstream's sizes (the shader-fix white pixel is one by one). */
	@GameTest(template = EMPTY)
	public static void theTexturesAreThereAtUpstreamsSizes(GameTestHelper helper) throws IOException {
		Object[][] table = { { "core_emitter", 32 }, { "core_cemitter", 32 }, { "core_receiver", 38 }, { "core_injector", 32 },
				{ "core_exchanger", 32 }, { "core_pulser", 32 }, { "test_texture64", 64 }, { "null-noisemap", 1000 }, { "solid", 1 } };
		for(Object[] row : table) {
			String path = "/assets/leafia/textures/entity/dfc/" + row[0] + ".png";
			try(InputStream in = LeafiaDFC4Tests.class.getResourceAsStream(path)) {
				helper.assertTrue(in != null, "missing " + path);
				BufferedImage image = ImageIO.read(in);
				helper.assertTrue(image.getWidth() == (int) row[1] && image.getHeight() == (int) row[1],
						path + " is " + image.getWidth() + "x" + image.getHeight());
			}
		}
		helper.succeed();
	}

	// ---- the layout -------------------------------------------------------------------------

	private static boolean near(double a, double b) {
		return Math.abs(a - b) < 1e-9;
	}

	/** A part's front is -Z: a target north needs no turn, east turns -90 about the vertical, and up pitches 90. */
	@GameTest(template = EMPTY)
	public static void aPartTurnsItsFrontToItsTarget(GameTestHelper helper) {
		helper.assertTrue(near(DFCLayout.yaw(new Vec3i(0, 0, -5)), 0) && near(DFCLayout.pitch(new Vec3i(0, 0, -5)), 0), "north is neither");
		helper.assertTrue(near(DFCLayout.yaw(new Vec3i(7, 0, 0)), -90), "east is -90: " + DFCLayout.yaw(new Vec3i(7, 0, 0)));
		helper.assertTrue(near(DFCLayout.yaw(new Vec3i(-7, 0, 0)), 90), "west is 90");
		helper.assertTrue(near(Math.abs(DFCLayout.yaw(new Vec3i(0, 0, 3))), 180), "south is half a turn");
		helper.assertTrue(near(DFCLayout.pitch(new Vec3i(0, 4, 0)), 90) && near(DFCLayout.pitch(new Vec3i(0, -4, 0)), -90), "up and down pitch a quarter");
		helper.assertTrue(near(DFCLayout.pitch(new Vec3i(3, 3, 0)), 45), "diagonally up is 45");
		helper.assertTrue(DFCLayout.unit(new Vec3i(0, 0, 0)).equals(Vec3.ZERO), "its own cell is no direction");
		helper.succeed();
	}

	/** A face counts as pointed at when more than 0.15 of the direction lies along it. */
	@GameTest(template = EMPTY)
	public static void aFaceIsPointedAtPastAHundredFiftyThousandths(GameTestHelper helper) {
		helper.assertTrue(DFCLayout.isFace(Direction.EAST, new Vec3(0.1500001, 0, 0)), "just over");
		helper.assertTrue(!DFCLayout.isFace(Direction.EAST, new Vec3(0.15, 0, 0)), "0.15 itself is not past it");
		helper.assertTrue(!DFCLayout.isFace(Direction.WEST, new Vec3(0.9, 0, 0)), "the far side is not");
		helper.assertTrue(DFCLayout.isFace(Direction.WEST, new Vec3(-0.9, 0, 0)), "the negative side, negated");
		helper.assertTrue(DFCLayout.isFace(Direction.UP, new Vec3(0, 0.2, 0)) && DFCLayout.isFace(Direction.DOWN, new Vec3(0, -0.2, 0)), "up and down");
		helper.assertTrue(DFCLayout.isFace(Direction.SOUTH, new Vec3(0, 0, 0.2)) && DFCLayout.isFace(Direction.NORTH, new Vec3(0, 0, -0.2)), "south and north");
		helper.succeed();
	}

	/**
	 * Aimed along an axis, a part has five frames (all but the one on the face behind its target's front)
	 * and the front's four arms; aimed between two axes, both fronts lose the arm toward each other.
	 */
	@GameTest(template = EMPTY)
	public static void theFramesAndArmsFollowTheTarget(GameTestHelper helper) {

		Vec3 east = DFCLayout.unit(new Vec3i(5, 0, 0));
		helper.assertTrue(DFCLayout.frames(east).size() == 5 && !DFCLayout.frames(east).contains(Direction.WEST),
				"aimed east, every frame but the west's: " + DFCLayout.frames(east));
		int fronts = 0;
		for(Direction face : Direction.values()) if(DFCLayout.isFront(face, east)) fronts++;
		helper.assertTrue(fronts == 1 && DFCLayout.isFront(Direction.EAST, east), "one front, east");
		boolean[] arms = DFCLayout.arms(Direction.EAST, east);
		helper.assertTrue(arms[0] && arms[1] && arms[2] && arms[3], "four arms on a lone front");

		// Up-and-east, exactly: two components tie to the last bit, so two fronts, and three arms on each.
		Vec3 both = DFCLayout.unit(new Vec3i(4, 4, 0));
		helper.assertTrue(DFCLayout.isFront(Direction.EAST, both) && DFCLayout.isFront(Direction.UP, both), "two fronts");
		helper.assertTrue(!DFCLayout.isFront(Direction.DOWN, both) && !DFCLayout.isFront(Direction.WEST, both), "and no others");
		helper.assertTrue(DFCLayout.frames(both).size() == 4 && !DFCLayout.frames(both).contains(Direction.WEST) && !DFCLayout.frames(both).contains(Direction.DOWN),
				"four frames: " + DFCLayout.frames(both));
		helper.assertTrue(count(DFCLayout.arms(Direction.EAST, both)) == 3 && !DFCLayout.arms(Direction.EAST, both)[0],
				"the east front loses the arm toward up (its edge 0)");
		helper.assertTrue(count(DFCLayout.arms(Direction.UP, both)) == 3, "and the up front loses one too: " + java.util.Arrays.toString(DFCLayout.arms(Direction.UP, both)));
		helper.succeed();
	}

	private static int count(boolean[] flags) {
		int n = 0;
		for(boolean b : flags) if(b) n++;
		return n;
	}

	/** The orb's size is {@code ln(T / 50 + 1) + 0.5}, and it shrinks to nothing in the last 3% of a collapse. */
	@GameTest(template = EMPTY)
	public static void theOrbIsUpstreamsSize(GameTestHelper helper) {
		helper.assertTrue(DFCLayout.orbScale(1400, 0) == (float) Math.log(1400 / 50d + 1) + 0.5F, "1,400 degrees: ln 29 + 0.5");
		helper.assertTrue(near(DFCLayout.orbScale(1400, 0), 3.8672, 1e-3), "which is 3.8672: " + DFCLayout.orbScale(1400, 0));
		helper.assertTrue(DFCLayout.orbScale(1400, 0.97) == DFCLayout.orbScale(1400, 0), "the 97% mark is still full size");
		helper.assertTrue(Math.abs(DFCLayout.orbScale(1400, 1.0)) < 1e-9, "a finished collapse is nothing (to the ease's rounding, -2.4e-14): " + DFCLayout.orbScale(1400, 1.0));
		float mid = DFCLayout.orbScale(1400, 0.985);
		helper.assertTrue(mid > 0 && mid < DFCLayout.orbScale(1400, 0), "and in between, in between: " + mid);
		helper.assertTrue(DFCLayout.orbSpin(0.5) == 0 && DFCLayout.orbSpin(0.97) == 0, "no turn before the last 3%");
		helper.assertTrue(near(DFCLayout.orbSpin(1.0), 1000), "a full 1,000 degrees at the end: " + DFCLayout.orbSpin(1.0));
		helper.assertTrue(DFCLayout.standbyBrightness(100) == 1F && DFCLayout.standbyBrightness(0) == 0F, "the standby ball is dark at zero and white at 100");
		helper.assertTrue(near(DFCLayout.standbyBrightness(25), 0.125), "(25/100)^1.5 is an eighth: " + DFCLayout.standbyBrightness(25));
		helper.succeed();
	}

	private static boolean near(double a, double b, double eps) {
		return Math.abs(a - b) < eps;
	}

	/**
	 * A one-layer beam is its outer colour, opaque. The shells of a deeper one blend from the inner colour at
	 * the first to the outer at the last. The alpha byte of the bare colour is zero, which the beam's
	 * {@code SRC_ALPHA, ONE} blend draws as nothing - the sparks and the field disturber's lines are
	 * one layer.
	 */
	@GameTest(template = EMPTY)
	public static void aOneLayerBeamIsItsOuterColourAndOpaque(GameTestHelper helper) {
		helper.assertTrue(LCEColor.layerColor(0x7F7F7F, 0x5B1D00, 1, 1) == 0xFF5B1D00, "one layer: " + Integer.toHexString(LCEColor.layerColor(0x7F7F7F, 0x5B1D00, 1, 1)));
		helper.assertTrue(LCEColor.layerColor(0x7F7F7F, 0x5B1D00, 2, 1) == 0xFF7F7F7F, "two layers, the inner shell is the inner colour");
		helper.assertTrue(LCEColor.layerColor(0x7F7F7F, 0x5B1D00, 2, 2) == 0xFF5B1D00, "and the last the outer");
		int middle = LCEColor.layerColor(0x000000, 0x80FF40, 3, 2);
		helper.assertTrue(middle == 0xFF407F20, "three layers, the middle shell is half way: " + Integer.toHexString(middle));
		helper.succeed();
	}

	// ---- the shock --------------------------------------------------------------------------

	/**
	 * The lightning path starts at the core, its first hop is half a block to one and a half, and it is
	 * never longer than the 25 hops after it (27 points, the last of them the hit's if it hit).
	 */
	@GameTest(template = EMPTY)
	public static void aShockPathStartsAtTheCoreAndHopsHalfToOneAndAHalf(GameTestHelper helper) {
		Vec3 core = helper.absoluteVec(new Vec3(2.5, 2.5, 2.5));
		for(int i = 0; i < 40; i++) {
			List<Vec3> path = IMixinTileEntityCore.shockPath(helper.getLevel(), core, false);
			helper.assertTrue(path.size() >= 3 && path.size() <= 27, "a path of " + path.size() + " points");
			helper.assertTrue(path.get(0).equals(core), "it starts at the core");
			double first = path.get(1).distanceTo(core);
			helper.assertTrue(first >= 0.5 - 1e-9 && first <= 1.5 + 1e-9, "the first hop is " + first);
			for(Vec3 p : path) helper.assertTrue(Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z), "a point is not a number");
		}
		helper.succeed();
	}

	// ---- the spectrometer --------------------------------------------------------------------

	private static ServerPlayer mockPlayer(GameTestHelper helper) {
		@SuppressWarnings("deprecation")
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			var field = net.minecraft.server.players.PlayerList.class.getDeclaredField("players");
			field.setAccessible(true);
			((java.util.List<?>) field.get(helper.getLevel().getServer().getPlayerList())).remove(player);
		} catch(ReflectiveOperationException e) {
			throw new IllegalStateException("could not detach the mock player", e);
		}
		return player;
	}

	private static float yawFor(Direction looking) {
		return switch(looking) {
			case SOUTH -> 0F;
			case WEST -> 90F;
			case NORTH -> 180F;
			case EAST -> 270F;
			default -> throw new IllegalArgumentException(looking.toString());
		};
	}

	/** Places a machine the way a player does (the AMC tests' rig); true if its core stands where it should. */
	private static boolean put(GameTestHelper helper, Block block, BlockPos core, Direction dir, int offset) {
		ServerPlayer player = mockPlayer(helper);
		BlockPos at = core.relative(dir, offset);
		player.setYRot(yawFor(dir.getOpposite()));
		player.setXRot(0F);
		helper.setBlock(at, block.defaultBlockState());
		block.setPlacedBy(helper.getLevel(), helper.absolutePos(at), block.defaultBlockState(), player, new ItemStack(block));
		BlockState state = helper.getLevel().getBlockState(helper.absolutePos(core));
		return state.getBlock() == block && state.getValue(BlockDummyable.PART) == Part.CORE && state.getValue(BlockDummyable.DIR) == dir;
	}

	private static boolean dummy(GameTestHelper helper, Block block, BlockPos rel) {
		BlockState state = helper.getLevel().getBlockState(helper.absolutePos(rel));
		return state.getBlock() == block && state.getValue(BlockDummyable.PART) != Part.CORE;
	}

	/** The switch and the registry agree: with `amsp` on both blocks and both block entity types exist. */
	@GameTest(template = EMPTY)
	public static void theSpectrometerBlocksAreRegisteredBehindTheirSwitch(GameTestHelper helper) {
		helper.assertTrue(LeafiaFeatures.amsp, "amsp defaults on");
		boolean hasBlocks = BuiltInRegistries.BLOCK.containsKey(LeafiaRef.rl("amsp_analyzer")) && BuiltInRegistries.BLOCK.containsKey(LeafiaRef.rl("amsp_receiver"));
		boolean hasTypes = BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(LeafiaRef.rl("amsp_analyzer")) && BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(LeafiaRef.rl("amsp_receiver"));
		helper.assertTrue(hasBlocks == LeafiaFeatures.amsp && hasTypes == LeafiaFeatures.amsp,
				"blocks " + hasBlocks + ", types " + hasTypes + " against the switch " + LeafiaFeatures.amsp);
		helper.assertTrue((LeafiaBlocks.amsp_analyzer != null) == LeafiaFeatures.amsp && (LeafiaBlocks.amsp_receiver != null) == LeafiaFeatures.amsp
				&& (LeafiaBlockEntities.AMSP_ANALYZER != null) == LeafiaFeatures.amsp && (LeafiaBlockEntities.AMSP_RECEIVER != null) == LeafiaFeatures.amsp,
				"and so do the holders");
		helper.succeed();
	}

	/** Hardness 25, resistance 60 x 0.6 (D-012), the dimensions and offsets upstream declares. */
	@GameTest(template = EMPTY)
	public static void theSpectrometerBlocksAreUpstreamsInStrengthAndShape(GameTestHelper helper) {
		AMSPAnalyzerBlock analyzer = LeafiaBlocks.amsp_analyzer.get();
		AMSPReceiverBlock receiver = LeafiaBlocks.amsp_receiver.get();
		for(Block block : new Block[] { analyzer, receiver }) {
			float hardness = block.defaultBlockState().getDestroySpeed(helper.getLevel(), BlockPos.ZERO);
			helper.assertTrue(hardness == 25F, block + " hardness " + hardness);
			helper.assertTrue(block.getExplosionResistance() == 36F, block + " resistance " + block.getExplosionResistance());
		}
		helper.assertTrue(java.util.Arrays.equals(analyzer.getDimensions(), new int[] { 0, 3, 3, 3, 2, 2 }) && analyzer.getOffset() == 0,
				"the analyzer's dimensions");
		helper.assertTrue(java.util.Arrays.equals(receiver.getDimensions(), new int[] { 1, 0, 3, 3, 3, 3 }) && receiver.getOffset() == 3,
				"the receiver's dimensions");
		helper.succeed();
	}

	/**
	 * The analyzer hangs six blocks below its core: a 3x3 pillar to the bottom, a 5x5 layer above it and
	 * the slab on top.
	 */
	@GameTest(template = BIG)
	public static void theAnalyzerHangsSixBlocksBelowItsCore(GameTestHelper helper) {
		Block block = LeafiaBlocks.amsp_analyzer.get();
		BlockPos core = new BlockPos(8, 8, 8);

		helper.assertTrue(put(helper, block, core, Direction.NORTH, 0), "the analyzer was not placed");
		BlockEntity be = helper.getLevel().getBlockEntity(helper.absolutePos(core));
		helper.assertTrue(be instanceof AMSPAnalyzerTE, "its core has " + be);
		helper.assertTrue(helper.getLevel().getBlockEntity(helper.absolutePos(core.below())) == null, "a dummy has none");

		helper.assertTrue(dummy(helper, block, core.below(6)) && dummy(helper, block, core.below(6).east().south()), "the pillar to six below, 3x3");
		helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(core.below(7))).isAir(), "and no deeper");
		helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(core.below(5).east(2))).isAir(), "the pillar is 3 wide, not 5");
		helper.assertTrue(dummy(helper, block, core.below(4).east(2).north(2)) && dummy(helper, block, core.below(4).west(2).south(2)), "the layer at four below is 5x5");
		helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(core.below(4).east(3))).isAir(), "and no wider");
		helper.succeed();
	}

	/**
	 * The pillar and the layer are checked from the core by the extra boxes alone, the main box never
	 * reaches them: a stone in the pillar refuses the placement, and without it the same placement works.
	 */
	@GameTest(template = BIG)
	public static void aStoneInTheAnalyzersPillarRefusesIt(GameTestHelper helper) {
		Block block = LeafiaBlocks.amsp_analyzer.get();
		BlockPos core = new BlockPos(8, 8, 8);
		BlockPos stone = core.below(5).east();
		helper.setBlock(stone, Blocks.STONE);
		helper.assertTrue(!put(helper, block, core, Direction.NORTH, 0), "a stone in the pillar must refuse the placement");
		helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(stone)).is(Blocks.STONE), "and be left where it was");
		helper.setBlock(stone, Blocks.AIR);
		helper.assertTrue(put(helper, block, core, Direction.NORTH, 0), "the same placement without it works");
		helper.succeed();
	}

	/**
	 * The receiver's offset is 3, and upstream's {@code checkRequirement} moves to the core and then lets the
	 * base move by the offset again: the main box is checked three blocks behind the core (D-021, kept). A
	 * stone at the real box's far edge is not noticed, and the machine overwrites it.
	 */
	@GameTest(template = BIG)
	public static void theReceiverDoesNotNoticeAStoneAtItsFarEdge(GameTestHelper helper) {
		Block block = LeafiaBlocks.amsp_receiver.get();

		// Core (8, 2, 8) facing north: the real box is x 5..11, z 5..11 (y 2..3); the checked one z 8..14.
		BlockPos core = new BlockPos(8, 2, 8);
		BlockPos farEdge = new BlockPos(6, 2, 5);
		helper.setBlock(farEdge, Blocks.STONE);
		helper.assertTrue(put(helper, block, core, Direction.NORTH, 3), "a stone at the real box's far edge is not noticed");
		BlockEntity be = helper.getLevel().getBlockEntity(helper.absolutePos(core));
		helper.assertTrue(be instanceof AMSPReceiverTE, "its core has " + be);
		helper.assertTrue(dummy(helper, block, farEdge), "and the machine overwrote it");
		helper.assertTrue(dummy(helper, block, core.east(3).north(3).above()) && dummy(helper, block, core.west(3).south(3)), "the base is 7x7, two tall");
		helper.succeed();
	}

	/** ...and one three blocks behind the real box, inside the shifted check, refuses; without it the machine goes in. */
	@GameTest(template = BIG)
	public static void aStoneThreeBlocksBehindTheReceiverRefusesIt(GameTestHelper helper) {
		Block block = LeafiaBlocks.amsp_receiver.get();
		BlockPos core = new BlockPos(8, 2, 8);
		BlockPos stone = new BlockPos(8, 2, 14);
		helper.setBlock(stone, Blocks.STONE);
		helper.assertTrue(!put(helper, block, core, Direction.NORTH, 3), "a stone in the shifted box must refuse the placement");
		helper.setBlock(stone, Blocks.AIR);
		helper.assertTrue(put(helper, block, core, Direction.NORTH, 3), "the same placement without it works");
		helper.succeed();
	}
}
