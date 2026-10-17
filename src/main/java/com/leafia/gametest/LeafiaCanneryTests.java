package com.leafia.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hbm.blocks.ModBlocks;
import com.hbm.wiaj.JarConnections;
import com.hbm.wiaj.JarScene;
import com.hbm.wiaj.JarScript;
import com.hbm.wiaj.WorldInAJar;
import com.hbm.wiaj.actors.ActorTileEntity;
import com.hbm.wiaj.actors.IDrawableActor;
import com.hbm.wiaj.actors.ISpecialActor;
import com.hbm.wiaj.actors.TextMetrics;
import com.hbm.wiaj.actions.ActionWait;
import com.hbm.wiaj.actions.IJarAction;
import com.hbm.wiaj.cannery.CanneryBase;
import com.hbm.wiaj.cannery.Jars;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.LeafiaFluids;
import com.leafia.contents.cannery.AddonJars;
import com.leafia.contents.cannery.actions.ActionOffsetAndZoom;
import com.leafia.contents.cannery.actors.ActorVisBase;
import com.leafia.contents.cannery.actors.ActorVisFrame;
import com.leafia.contents.cannery.actors.ActorVisText;
import com.leafia.contents.cannery.cannery.CanneryLFTR;
import com.leafia.contents.cannery.cannery.CanneryPWR;
import com.leafia.contents.cannery.cannery.CanneryTest;
import com.leafia.contents.machines.processing.mixingvat.MixingVatRender;
import com.leafia.contents.machines.reactors.lftr.components.control.MSRControlBlock;
import com.leafia.contents.machines.reactors.lftr.components.ejector.MSREjectorBlock;
import com.leafia.contents.machines.reactors.lftr.components.plug.MSRPlugBlock;
import com.leafia.contents.machines.reactors.pwr.blocks.components.control.PWRControlBlock;
import com.leafia.contents.machines.reactors.pwr.blocks.components.terminal.PWRTerminalBlock;
import com.leafia.contents.network.ff_duct.FFDuctStandard;
import com.leafia.contents.network.ff_duct.FFDuctTE;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The addon's guide scenes for the World In A Jar screen (D-3029): the PWR and LFTR topics, the test topic, the action and the three
 * actors they add, and how they are put in the base mod's table.
 *
 * <p>The engine is deterministic by design and the tests drive it the way the base mod's own do: fast-forward, which runs ticks with no
 * clock, then read the jar and the actors. {@code forwardOne} ends the scene before and runs one tick into the scene it names, so
 * after {@code k} forwards the jar holds the end of scene {@code k - 1} and whatever scene {@code k} does before its first wait. The scene
 * lengths below are the sum of each upstream script's waits, loops unrolled, counted by hand from the Java and not read back from the port.
 * The drawing is not tested here: it needs a client, and is looked at in a running one.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaCanneryTests {

	private static final String EMPTY = "empty";

	/** Six pixels a character, nine a line: a font the scripts can be built with and measured against, without a client. */
	private static final TextMetrics FIXED = new TextMetrics() {

		@Override public int width(String text) { return text.length() * 6; }
		@Override public int height() { return 9; }

		@Override
		public List<String> autoBreak(String text, int width) {
			List<String> lines = new ArrayList<>();
			String line = "";
			for(String word : text.split(" ")) {
				String next = line.isEmpty() ? word : line + " " + word;
				if(!line.isEmpty() && width(next) > width) {
					lines.add(line);
					line = word;
				} else {
					line = next;
				}
			}
			lines.add(line);
			return lines;
		}
	};

	private interface Body {
		void run(JarScript script, WorldInAJar world);
	}

	/** Builds a topic's script with the fixed font, runs the body, and puts the engine's statics back whatever it does. */
	private static void with(GameTestHelper helper, CanneryBase topic, Body body) {

		var keep = CanneryBase.metricsSource;
		CanneryBase.metricsSource = () -> FIXED;
		int keepTarget = JarScript.ffwTarget;

		try {
			JarScript script = topic.createScript();
			body.run(script, script.world);
		} finally {
			CanneryBase.metricsSource = keep;
			JarScript.ffwTarget = keepTarget;
			JarScript.freeRun = false;
		}
		helper.succeed();
	}

	private static void forward(JarScript script, int scenes) {
		for(int i = 0; i < scenes; i++) script.forwardOne();
	}

	/** {@code n} ticks of the script with no clock. */
	private static void tick(JarScript script, int n) {
		JarScript.freeRun = true;
		try {
			for(int i = 0; i < n; i++) script.run();
		} finally {
			JarScript.freeRun = false;
		}
	}

	private static int duration(JarScene scene) {
		int total = 0;
		for(IJarAction action : scene.actions) total += action.getDuration();
		return total;
	}

	private static void near(GameTestHelper helper, double actual, double expected, String what) {
		helper.assertTrue(Math.abs(actual - expected) <= 1e-9 * Math.max(1, Math.abs(expected)), what + ": " + actual + " vs " + expected);
	}

	private static boolean is(WorldInAJar world, int x, int y, int z, Block block) {
		return world.getBlock(x, y, z).is(block);
	}

	/** Upstream's {@code getHash} for a 9 x 5 x 9 jar. */
	private static int pwrHash(int x, int y, int z) {
		return x + y * 9 + z * 9 * 5 + 300;
	}

	/** Upstream's {@code getHash} for a 9 x 12 x 9 jar. */
	private static int lftrHash(int x, int y, int z) {
		return x + y * 9 + z * 9 * 12 + 300;
	}

	private static boolean pwr() {
		return LeafiaFeatures.pwrReactor();
	}

	private static boolean lftr() {
		return LeafiaFeatures.lftrReactor() && LeafiaFeatures.ffDuct;
	}

	private static void sceneLengths(GameTestHelper helper, JarScript script, int[] expected) {
		helper.assertTrue(script.scenes.size() == expected.length, "scenes: " + script.scenes.size() + " vs " + expected.length);
		for(int i = 0; i < expected.length; i++)
			helper.assertTrue(duration(script.scenes.get(i)) == expected[i], "scene " + i + " lasts " + duration(script.scenes.get(i)) + ", upstream's script waits " + expected[i]);
	}

	// ---- the scripts ----

	@GameTest(template = EMPTY)
	public static void thePwrTopicIsUpstreamsNineScenesInANineByFiveByNineJar(GameTestHelper helper) {
		if(!pwr()) { helper.succeed(); return; }
		CanneryPWR topic = new CanneryPWR();
		helper.assertTrue(topic.getName().equals("cannery.leafia.pwr"), "named by the key the language file defines");
		helper.assertFalse(topic.getIcon().isEmpty(), "the fuel rod is its icon");
		helper.assertTrue(topic.seeAlso().length == 0, "and it points at nothing");
		with(helper, topic, (script, world) -> {
			helper.assertTrue(world.sizeX == 9 && world.sizeY == 5 && world.sizeZ == 9, "the jar is 9 x 5 x 9");
			sceneLengths(helper, script, new int[] { 178, 192, 300, 220, 50, 110, 260, 376, 80 });
		});
	}

	@GameTest(template = EMPTY)
	public static void theLftrTopicIsUpstreamsEightScenesInANineByTwelveByNineJar(GameTestHelper helper) {
		if(!lftr()) { helper.succeed(); return; }
		CanneryLFTR topic = new CanneryLFTR();
		helper.assertTrue(topic.getName().equals("cannery.leafia.lftr"), "named by the key the language file defines");
		helper.assertFalse(topic.getIcon().isEmpty(), "the reaction chamber is its icon");
		with(helper, topic, (script, world) -> {
			helper.assertTrue(world.sizeX == 9 && world.sizeY == 12 && world.sizeZ == 9, "the jar is 9 x 12 x 9");
			sceneLengths(helper, script, new int[] { 278, 740, 302, 100, 330, 280, 200, 332 });
		});
	}

	@GameTest(template = EMPTY)
	public static void theTestTopicLaysAFloorAndShowsAGreenFrameAndTheWordTest(GameTestHelper helper) {
		if(!pwr()) { helper.succeed(); return; }
		with(helper, new CanneryTest(), (script, world) -> {
			helper.assertTrue(world.sizeX == 5 && world.sizeY == 5 && world.sizeZ == 5, "the jar is 5 x 5 x 5");
			sceneLengths(helper, script, new int[] { 50 });
			script.forwardOne();
			for(int x = 0; x < 5; x++)
				for(int z = 0; z < 5; z++)
					helper.assertTrue(is(world, x, 0, z, ModBlocks.concrete_smooth.get()), "the floor is smooth concrete at " + x + ", " + z);
			helper.assertTrue(script.actors.size() == 2, "two actors: " + script.actors.keySet());
			ActorVisFrame frame = (ActorVisFrame) script.actors.get(0);
			helper.assertTrue(frame.cellX() == 1 && frame.cellY() == 1 && frame.cellZ() == 1 && frame.color() == 0x00FF00 && frame.scale() == 1F,
					"actor 0 is a green unit frame in the middle cell");
			ActorVisText text = (ActorVisText) script.actors.get(1);
			helper.assertTrue(text.cellX() == 1 && text.cellY() == 1 && text.cellZ() == 1 && text.color() == 0x00FF00 && text.scale() == 1F
					&& text.forceUnicode() == -1 && text.texts().length == 1 && text.texts()[0].equals("Test"), "actor 1 says Test in green and leaves the font alone");
			near(helper, script.rotationYaw, 0, "the jar has turned 45 degrees from -45");
			near(helper, script.rotationPitch, -90, "and 60 down from -30");
		});
	}

	// ---- the PWR scenes ----

	@GameTest(template = EMPTY)
	public static void thePwrIntroBuildsTheReactorAroundAnEmptyMiddleAndFiveControlRodColumns(GameTestHelper helper) {
		if(!pwr()) { helper.succeed(); return; }
		with(helper, new CanneryPWR(), (script, world) -> {
			forward(script, 1);
			near(helper, script.zoom, 2, "the zoom is set to 2");
			// the ring: ports on the east and west faces' middle, reflectors beside them, the casing between, a channel layer, casing above and below
			helper.assertTrue(is(world, 6, 1, 4, LeafiaBlocks.lwr_port.get()) && is(world, 2, 1, 4, LeafiaBlocks.lwr_port.get()), "a port in the middle of the east and west walls");
			helper.assertTrue(is(world, 6, 1, 3, LeafiaBlocks.lwr_reflector.get()) && is(world, 6, 1, 5, LeafiaBlocks.lwr_reflector.get()), "a reflector either side of it");
			helper.assertTrue(is(world, 4, 1, 2, LeafiaBlocks.lwr_casing.get()) && is(world, 3, 1, 2, LeafiaBlocks.lwr_reflector.get()), "casing in the middle of the north wall, reflectors beside it");
			helper.assertTrue(is(world, 4, 2, 4, LeafiaBlocks.lwr_channel.get()) && is(world, 3, 2, 3, LeafiaBlocks.lwr_channel.get()), "a channel layer inside the walls");
			helper.assertTrue(is(world, 4, 0, 4, LeafiaBlocks.lwr_casing.get()) && is(world, 4, 3, 4, LeafiaBlocks.lwr_casing.get()), "casing under and over the middle");
			helper.assertTrue(is(world, 4, 2, 2, LeafiaBlocks.lwr_terminal.get()), "the terminal is in the north wall at the channel layer");
			helper.assertTrue(world.getBlock(4, 2, 2).getValue(PWRTerminalBlock.FACING) == Direction.NORTH, "facing north, upstream's metadata 2");
			helper.assertTrue(world.getBlock(4, 1, 4).isAir(), "the middle of the fuel layer is empty: upstream's lazy air");
			for(int[] cell : new int[][] { { 5, 1, 4 }, { 3, 1, 4 }, { 4, 1, 5 }, { 4, 1, 3 } })
				helper.assertTrue(is(world, cell[0], cell[1], cell[2], LeafiaBlocks.lwr_element.get()), "a fuel column at " + cell[0] + ", " + cell[2]);
			helper.assertTrue(script.actors.get(0) == null, "the text panel is gone by the end of the scene");
			// five control rod columns, which are actors with no block behind them
			int[][] controls = { { 4, 4 }, { 5, 5 }, { 5, 3 }, { 3, 5 }, { 3, 3 } };
			for(int[] c : controls) {
				ISpecialActor actor = script.actors.get(pwrHash(c[0], 1, c[1]));
				helper.assertTrue(actor instanceof ActorTileEntity, "an actor at " + c[0] + ", " + c[1]);
				CompoundTag data = ((ActorTileEntity) actor).data();
				helper.assertTrue(data.getDouble("x") == c[0] && data.getDouble("y") == 1 && data.getDouble("z") == c[1] && data.getInt("variant") == 0,
						"standing in its cell, of the standard skin: " + data);
			}
			helper.assertTrue(script.actors.size() == 5, "and nothing else: " + script.actors.keySet());
		});
	}

	@GameTest(template = EMPTY)
	public static void thePwrFloorIsPaintedMagentaAtTheCornersAndLimeAlongTheFourLines(GameTestHelper helper) {
		if(!pwr()) { helper.succeed(); return; }
		with(helper, new CanneryPWR(), (script, world) -> {
			forward(script, 3);
			for(int[] c : new int[][] { { 3, 3 }, { 3, 5 }, { 5, 3 }, { 5, 5 } })
				helper.assertTrue(is(world, c[0], 0, c[1], Blocks.MAGENTA_WOOL), "magenta wool under the corner column at " + c[0] + ", " + c[1]);
			helper.assertTrue(is(world, 5, 0, 4, ModBlocks.concrete_smooth.get()), "the lime line has not started");

			forward(script, 1);
			// nearest the middle is concrete, then wool, alternating outwards
			for(int i = 1; i <= 4; i++) {
				Block expected = i % 2 == 0 ? Blocks.LIME_WOOL : Blocks.LIME_CONCRETE;
				helper.assertTrue(is(world, 4 + i, 0, 4, expected) && is(world, 4 - i, 0, 4, expected)
						&& is(world, 4, 0, 4 + i, expected) && is(world, 4, 0, 4 - i, expected), "the line is " + expected + " " + i + " out, in all four directions");
			}
			helper.assertTrue(is(world, 4, 0, 4, ModBlocks.concrete_smooth.get()), "and the middle is still concrete: the skins scene laid the floor over the reactor's casing");
		});
	}

	@GameTest(template = EMPTY)
	public static void thePwrFluxNumbersAreHalvesAtTheCornersAndHalveAgainEveryTwoBlocks(GameTestHelper helper) {
		if(!pwr()) { helper.succeed(); return; }
		with(helper, new CanneryPWR(), (script, world) -> {
			forward(script, 4);
			// scene 4: a half in each of the four corners, in white, at one and a half times the size, in the ordinary font
			int[][] corners = { { 1, 3, 3 }, { 2, 5, 3 }, { 3, 3, 5 }, { 4, 5, 5 } };
			for(int[] c : corners) {
				ActorVisText text = (ActorVisText) script.actors.get(c[0]);
				helper.assertTrue(text.cellX() == c[1] && text.cellY() == 1 && text.cellZ() == c[2] && text.color() == 0xFFFFFF && text.scale() == 1.5F
						&& text.forceUnicode() == 0 && text.texts().length == 1 && text.texts()[0].equals("1/2"), "actor " + c[0] + " is 1/2 at " + c[1] + ", " + c[2]);
			}
			helper.assertTrue(script.actors.size() == 4, "and nothing else: " + script.actors.keySet());

			forward(script, 1);
			// scene 5: forty numbers, two blocks apart along each of the four lines, a power of two each
			helper.assertTrue(script.actors.size() == 40, "forty numbers: " + script.actors.size());
			for(int i = 1; i <= 10; i++) {
				String label = "1/" + (int) Math.pow(2, i);
				int[][] expected = { { i, 4 + i * 2, 4 }, { i + 11, 4 - i * 2, 4 }, { i + 22, 4, 4 + i * 2 }, { i + 33, 4, 4 - i * 2 } };
				for(int[] e : expected) {
					ActorVisText text = (ActorVisText) script.actors.get(e[0]);
					helper.assertTrue(text.cellX() == e[1] && text.cellY() == 1 && text.cellZ() == e[2] && text.texts()[0].equals(label) && text.color() == 0xFFFFFF,
							"actor " + e[0] + " is " + label + " at " + e[1] + ", " + e[2]);
				}
			}
		});
	}

	@GameTest(template = EMPTY)
	public static void thePwrControlRodsAreRemovedAndPutBackInTheClassicSkinAndThenReplacedByChannels(GameTestHelper helper) {
		if(!pwr()) { helper.succeed(); return; }
		with(helper, new CanneryPWR(), (script, world) -> {
			forward(script, 6);
			// the end of the linear flux scene: four standard columns two blocks out
			int[][] arms = { { 6, 4 }, { 2, 4 }, { 4, 6 }, { 4, 2 } };
			for(int[] a : arms) {
				ActorTileEntity actor = (ActorTileEntity) script.actors.get(pwrHash(a[0], 1, a[1]));
				helper.assertTrue(actor != null && actor.data().getInt("variant") == 0, "a standard column at " + a[0] + ", " + a[1]);
			}
			// a hundred and twenty-five ticks into the next scene they have been taken out and put back in the classic skin
			tick(script, 125);
			for(int[] a : arms) {
				ActorTileEntity actor = (ActorTileEntity) script.actors.get(pwrHash(a[0], 1, a[1]));
				helper.assertTrue(actor != null && actor.data().getInt("variant") == 1, "a classic column at " + a[0] + ", " + a[1]);
			}
			near(helper, script.rotationYaw, -45, "the jar is turned back to where it began");
			near(helper, script.rotationPitch, -30, "in both directions");

			forward(script, 1);
			// the end of the control rod scene: the classic skin came and went, and lead channels stand in their place
			for(int[] a : arms) {
				helper.assertTrue(script.actors.get(pwrHash(a[0], 1, a[1])) == null, "no column at " + a[0] + ", " + a[1]);
				helper.assertTrue(is(world, a[0], 1, a[1], LeafiaBlocks.lwr_channel.get()), "a channel instead");
			}
		});
	}

	@GameTest(template = EMPTY)
	public static void thePwrTopicPlaysToTheEndAndLeavesAColumnOfThreeFuelRodsOnAConcreteFloor(GameTestHelper helper) {
		if(!pwr()) { helper.succeed(); return; }
		with(helper, new CanneryPWR(), (script, world) -> {
			forward(script, 9);
			helper.assertTrue(script.currentScene == null, "the last scene finished");
			helper.assertTrue(script.actors.isEmpty(), "with no actor left: " + script.actors.keySet());
			for(int y = 1; y <= 3; y++)
				helper.assertTrue(is(world, 4, y, 4, LeafiaBlocks.lwr_element.get()), "a fuel rod at height " + y);
			for(int x = 0; x < 9; x++)
				for(int z = 0; z < 9; z++)
					helper.assertTrue(is(world, x, 0, z, ModBlocks.concrete_smooth.get()), "the floor is smooth concrete at " + x + ", " + z);
			for(int[] a : new int[][] { { 6, 4 }, { 2, 4 }, { 4, 6 }, { 4, 2 } })
				helper.assertTrue(world.getBlock(a[0], 1, a[1]).isAir(), "the graphite is gone from " + a[0] + ", " + a[1]);
			near(helper, script.rotationYaw, -45, "yaw is as it began");
			near(helper, script.rotationPitch, -30, "pitch is as it began");
		});
	}

	// ---- the LFTR scenes ----

	@GameTest(template = EMPTY)
	public static void theLftrIntroBuildsTheChambersTheRodsAndAColumnOfPipe(GameTestHelper helper) {
		if(!lftr()) { helper.succeed(); return; }
		with(helper, new CanneryLFTR(), (script, world) -> {
			forward(script, 1);
			near(helper, script.zoom, 1.5, "the zoom is set to 1.5");
			Block compound = ModBlocks.brick_compound.get();
			helper.assertTrue(is(world, 3, 2, 3, ModBlocks.deco_pipe_framed.get()) && is(world, 5, 3, 5, ModBlocks.deco_pipe_framed.get()), "framed pipe posts at the corners of the base");
			helper.assertTrue(is(world, 3, 4, 3, compound) && is(world, 4, 8, 3, compound), "brick compound floors at 4 and 8 (the upper one has rods through its middle and corners)");
			helper.assertTrue(world.getBlock(4, 4, 4).getBlock() == LeafiaBlocks.msr_plug.get() && world.getBlock(4, 4, 4).getValue(MSRPlugBlock.FACING) == Direction.NORTH,
					"the fuel plug in the middle of the lower floor, facing north (metadata 2)");
			helper.assertTrue(is(world, 4, 4, 3, LeafiaBlocks.ff_duct_solid_shielded.get()), "and a shielded duct beside it");
			helper.assertTrue(is(world, 4, 5, 2, compound) && is(world, 4, 6, 2, ModBlocks.brick_concrete.get()) && is(world, 4, 7, 2, compound), "the shell is compound, concrete, compound");
			helper.assertTrue(is(world, 4, 5, 4, LeafiaBlocks.msr_element.get()) && is(world, 3, 7, 3, LeafiaBlocks.msr_element.get()), "reaction chambers in the middle and the corners");
			helper.assertTrue(world.getBlock(4, 6, 4).getBlock() == LeafiaBlocks.msr_ejector.get() && world.getBlock(4, 6, 4).getValue(MSREjectorBlock.FACING) == Direction.SOUTH,
					"an ejector in place of the middle chamber of the second layer, facing south (metadata 3)");
			helper.assertTrue(world.getBlock(4, 8, 4).getBlock() == LeafiaBlocks.msr_control.get() && world.getBlock(4, 8, 4).getValue(MSRControlBlock.FACING) == Direction.DOWN
					&& world.getBlock(3, 8, 5).getBlock() == LeafiaBlocks.msr_control.get(), "control rods on the upper floor, pointing down");
			for(int y = 9; y <= 11; y++) {
				BlockState state = world.getBlock(4, y, 4);
				helper.assertTrue(state.getBlock() == LeafiaBlocks.msr_control_extension.get() && state.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.Y, "an upright extension at height " + y);
			}
			// twelve arbitrary blocks, each with its own overlay actor
			int[][] arb = { { 3, 4 }, { 5, 4 }, { 4, 3 }, { 4, 5 } };
			for(int y = 5; y <= 7; y++)
				for(int[] a : arb) {
					helper.assertTrue(is(world, a[0], y, a[1], LeafiaBlocks.msr_arbitrary.get()), "an arbitrary block at " + a[0] + ", " + y + ", " + a[1]);
					helper.assertTrue(script.actors.get(lftrHash(a[0], y, a[1])) instanceof ActorTileEntity, "with an overlay actor");
				}
			helper.assertTrue(script.actors.size() == 12, "and no other actor: " + script.actors.size());
			helper.assertTrue(script.actors.get(0) == null, "the text panel is gone");
		});
	}

	@GameTest(template = EMPTY)
	public static void theLftrDuctColumnTakesTheArmsAndEndsUpstreamsRuleGivesIt(GameTestHelper helper) {
		if(!lftr()) { helper.succeed(); return; }
		with(helper, new CanneryLFTR(), (script, world) -> {
			forward(script, 1);
			// the column (4, 1..3, 4) sits on a shielded duct of the same fluid and under the fuel plug, which is not a machine the jar can see into
			for(int y = 1; y <= 3; y++) {
				BlockState state = drawn(world, 4, y, 4);
				helper.assertTrue(state.getBlock() == LeafiaBlocks.ff_duct.get() && state.getValue(FFDuctStandard.MATERIAL) == com.hbm.blocks.network.FluidDuctStandard.PipeMaterial.COLORED,
						"a coloured special pipe at height " + y + " (metadata 2)");
				helper.assertTrue(world.getBlockEntity(new BlockPos(4, y, 4)) instanceof FFDuctTE duct && duct.getFluidType() == LeafiaFluids.FLUORIDE, "carrying the salt");
				helper.assertTrue(state.getValue(FFDuctStandard.UP) == (y < 3), "up is joined below the top: " + y);
				helper.assertTrue(state.getValue(FFDuctStandard.DOWN), "down is always joined: " + y);
				helper.assertFalse(state.getValue(FFDuctStandard.NORTH) || state.getValue(FFDuctStandard.SOUTH) || state.getValue(FFDuctStandard.EAST) || state.getValue(FFDuctStandard.WEST),
						"and no side is: " + y);
				helper.assertFalse(state.getValue(FFDuctStandard.END_NEG), "the lower end is another duct at height " + y);
				helper.assertTrue(state.getValue(FFDuctStandard.END_POS) == (y == 3), "the upper end is open only at the top: " + y);
			}
			helper.assertTrue(is(world, 4, 0, 4, LeafiaBlocks.ff_duct_solid_shielded.get()) && world.getBlockEntity(new BlockPos(4, 0, 4)) instanceof FFDuctTE, "the shielded duct under the column has a block entity too");
			helper.assertFalse(world.getBlockEntity(new BlockPos(4, 4, 3)) instanceof FFDuctTE, "the one beside the plug has none");
		});
	}

	/**
	 * The state the guide draws for the block at a cell: the base mod's {@code JarConnections.resolve} with the addon's duct resolver
	 * added, as the client's setup adds it (D-3041). The jar's own block is the duct's default state; its arms exist only when drawn.
	 */
	private static BlockState drawn(WorldInAJar world, int x, int y, int z) {
		JarConnections.addResolver(CanneryLFTR.DUCT_RESOLVER);
		try {
			return JarConnections.resolve(world, new BlockPos(x, y, z), world.getBlock(x, y, z));
		} finally {
			JarConnections.removeResolver(CanneryLFTR.DUCT_RESOLVER);
		}
	}

	/** Ticks into the vat scene, well after the pipe is laid and well before it is taken up again. */
	private static final int INSIDE_THE_PIPE_RUN = 278 + 600;

	@GameTest(template = EMPTY)
	public static void theLftrVatScenePipesTheSaltToTheStandInMachineAndTurnsTheJar(GameTestHelper helper) {
		if(!lftr()) { helper.succeed(); return; }
		with(helper, new CanneryLFTR(), (script, world) -> {
			tick(script, INSIDE_THE_PIPE_RUN);
			helper.assertTrue(script.sceneNumber == 1, "in the vat scene: " + script.sceneNumber);
			near(helper, script.offsetY, 3.5, "the camera has risen 3.5");
			near(helper, script.zoom, 2.0, "and zoomed by a half");

			// the vat is an actor at the middle of the jar's north side, turned by the rotation switch's case 2
			ActorTileEntity vat = (ActorTileEntity) script.actors.get(1);
			helper.assertTrue(vat != null && vat.data().getDouble("x") == 4 && vat.data().getDouble("y") == 1 && vat.data().getDouble("z") == 3
					&& vat.data().getInt("meta") == 12 && !vat.data().getBoolean("spin"), "the vat stands at 4, 1, 3 with metadata 12 and is not spinning: " + (vat == null ? null : vat.data()));

			// the pipe: three lengths along the west wall, the first joined to the stand-in machine on its east
			BlockEntity dummy = world.getBlockEntity(new BlockPos(3, 1, 5));
			helper.assertTrue(dummy instanceof CanneryLFTR.FFDummyConnector, "the stand-in machine at 3, 1, 5");
			BlockState first = drawn(world, 2, 1, 5), second = drawn(world, 2, 1, 6), third = drawn(world, 2, 1, 7);
			helper.assertTrue(first.getBlock() == LeafiaBlocks.ff_duct.get() && second.getBlock() == LeafiaBlocks.ff_duct.get() && third.getBlock() == LeafiaBlocks.ff_duct.get(), "three pipes");
			helper.assertTrue(first.getValue(FFDuctStandard.EAST) && first.getValue(FFDuctStandard.SOUTH) && !first.getValue(FFDuctStandard.NORTH) && !first.getValue(FFDuctStandard.WEST)
					&& !first.getValue(FFDuctStandard.UP) && !first.getValue(FFDuctStandard.DOWN), "the first is joined east, to the machine, and south, to the next pipe only");
			helper.assertFalse(first.getValue(FFDuctStandard.END_POS) || first.getValue(FFDuctStandard.END_NEG), "and is a bend, so has no end flags");
			helper.assertTrue(second.getValue(FFDuctStandard.NORTH) && second.getValue(FFDuctStandard.SOUTH) && !second.getValue(FFDuctStandard.EAST) && !second.getValue(FFDuctStandard.WEST),
					"the second is straight along the pipe");
			helper.assertFalse(second.getValue(FFDuctStandard.END_POS) || second.getValue(FFDuctStandard.END_NEG), "with a pipe at both ends");
			helper.assertTrue(third.getValue(FFDuctStandard.NORTH) && !third.getValue(FFDuctStandard.SOUTH), "the third is joined back only");
			helper.assertTrue(third.getValue(FFDuctStandard.END_POS) && !third.getValue(FFDuctStandard.END_NEG), "and is open at its south end");

			// the reactor behind is gone: every cell inside the 5 x 5 column was emptied (the shielded duct and the plug with it)
			helper.assertTrue(world.getBlock(4, 4, 4).isAir() && world.getBlock(4, 2, 4).isAir() && is(world, 4, 0, 4, ModBlocks.concrete_smooth.get()), "the reactor is gone and the floor is concrete");
		});
	}

	@GameTest(template = EMPTY)
	public static void theLftrFluxFramesAreYellowQuartersOnTheSixAxesAndTheTwelveExtraPlaces(GameTestHelper helper) {
		if(!lftr()) { helper.succeed(); return; }
		with(helper, new CanneryLFTR(), (script, world) -> {
			forward(script, 2);
			near(helper, script.offsetY, 2.0, "the camera is 3.5 up and 1.5 back down");
			near(helper, script.zoom, 2.0, "zoomed in by a half");
			helper.assertTrue(is(world, 4, 3, 4, LeafiaBlocks.msr_element.get()), "one chamber stands in the middle of the clean jar");
			helper.assertTrue(script.actors.get(1) == null, "and the vat is gone");

			// scene 1's end: up to four frames out along each of the six axes, none below the floor
			Set<List<Integer>> axes = new HashSet<>();
			for(Direction face : Direction.values())
				for(int i = 1; i <= 4; i++) {
					BlockPos p = new BlockPos(4, 3, 4).relative(face, i);
					if(p.getY() >= 1) axes.add(List.of(p.getX(), p.getY(), p.getZ()));
				}
			helper.assertTrue(axes.size() == 22, "twenty-two cells: " + axes.size());
			helper.assertTrue(frames(script).equals(axes), "frames exactly there: " + frames(script));
			for(ISpecialActor actor : script.actors.values()) {
				ActorVisFrame frame = (ActorVisFrame) actor;
				helper.assertTrue(frame.color() == 0xFFFF00 && frame.scale() == 0.25F, "each is yellow and a quarter of a block");
			}

			// scene 2, a hundred and forty ticks in: the axes are cleared and the twelve extra places are marked
			tick(script, 140);
			helper.assertTrue(script.sceneNumber == 2, "in the flux scene: " + script.sceneNumber);
			Set<List<Integer>> extra = new HashSet<>();
			for(int a : new int[] { 3, 5 })
				for(int b : new int[] { 2, 4 }) {
					extra.add(List.of(a, b, 4));
					extra.add(List.of(4, b, a));
				}
			for(int x : new int[] { 3, 5 })
				for(int z : new int[] { 3, 5 }) extra.add(List.of(x, 3, z));
			helper.assertTrue(extra.size() == 12, "twelve cells: " + extra.size());
			helper.assertTrue(frames(script).equals(extra), "frames exactly there: " + frames(script));
		});
	}

	private static Set<List<Integer>> frames(JarScript script) {
		Set<List<Integer>> cells = new HashSet<>();
		for(ISpecialActor actor : script.actors.values()) {
			if(actor instanceof ActorVisBase vis) cells.add(List.of(vis.cellX(), vis.cellY(), vis.cellZ()));
		}
		return cells;
	}

	@GameTest(template = EMPTY)
	public static void theLftrControlRodsPointAtTheChamberFromThreeSidesWithAnExtensionBehindEach(GameTestHelper helper) {
		if(!lftr()) { helper.succeed(); return; }
		with(helper, new CanneryLFTR(), (script, world) -> {
			// the rod scene is index 4: forwarding to it runs its first actions, up to the first wait
			forward(script, 4);
			BlockState rod = world.getBlock(4, 3, 3);
			helper.assertTrue(rod.getBlock() == LeafiaBlocks.msr_control.get() && rod.getValue(MSRControlBlock.FACING) == Direction.SOUTH, "a rod on the north face, pointing south");
			BlockState ext = world.getBlock(4, 3, 2);
			helper.assertTrue(ext.getBlock() == LeafiaBlocks.msr_control_extension.get() && ext.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.Z, "its extension behind it along z (metadata 8)");

			tick(script, 25);
			helper.assertTrue(world.getBlock(4, 3, 3).isAir() && world.getBlock(4, 3, 2).isAir(), "after twenty ticks the first pair is gone");
			BlockState west = world.getBlock(3, 3, 4);
			helper.assertTrue(west.getBlock() == LeafiaBlocks.msr_control.get() && west.getValue(MSRControlBlock.FACING) == Direction.EAST, "a rod on the west face, pointing east");
			helper.assertTrue(world.getBlock(2, 3, 4).getValue(RotatedPillarBlock.AXIS) == Direction.Axis.X, "its extension along x (metadata 4)");
		});
	}

	// ---- the action and the actors ----

	@GameTest(template = EMPTY)
	public static void offsetAndZoomMovesAndZoomsByWhatItIsGivenOverItsDurationAndSetsAtZero(GameTestHelper helper) {
		var keepTarget = JarScript.ffwTarget;
		try {
			WorldInAJar world = new WorldInAJar(1, 1, 1);
			JarScript script = new JarScript(world);
			JarScene scene = new JarScene(script);
			ActionOffsetAndZoom timed = new ActionOffsetAndZoom(2.1, -4.2, 6.3, 0.5, 20);
			// after a wait, as in every topic: an action that follows another starts in the tick that one ends, and so acts time + 1 times
			scene.add(new ActionWait(2));
			scene.add(timed);
			script.addScene(scene);
			helper.assertTrue(timed.getDuration() == 20, "it lasts as long as it is told");
			script.forwardOne();
			near(helper, script.offsetX, 2.1, "x");
			near(helper, script.offsetY, -4.2, "y");
			near(helper, script.offsetZ, 6.3, "z");
			near(helper, script.zoom, 1.5, "the zoom rises by 0.5 from 1");

			WorldInAJar world2 = new WorldInAJar(1, 1, 1);
			JarScript script2 = new JarScript(world2);
			JarScene scene2 = new JarScene(script2);
			scene2.add(new ActionOffsetAndZoom(1, 2, 3, 4, 0));
			script2.addScene(scene2);
			script2.forwardOne();
			near(helper, script2.offsetX, 1, "an instant action moves x");
			near(helper, script2.lastOffsetX, 1, "and the last-tick copy with it");
			near(helper, script2.lastOffsetZ, 3, "z too");
			near(helper, script2.zoom, 4, "and sets the zoom to it");
			near(helper, script2.lastZoom, 4, "and its last-tick copy");

			// with no zoom a timed action is the base mod's offset, which the LFTR topic now uses itself (D-3041)
			WorldInAJar world3 = new WorldInAJar(1, 1, 1);
			JarScript script3 = new JarScript(world3);
			JarScene scene3 = new JarScene(script3);
			scene3.add(new ActionWait(2));
			scene3.add(new ActionOffsetAndZoom(0, -1.5, 0, 0, 20));
			script3.addScene(scene3);
			script3.forwardOne();
			near(helper, script3.offsetY, -1.5, "a timed offset with no zoom moves the camera by it");
			near(helper, script3.zoom, 1, "and leaves the zoom alone");

			WorldInAJar world4 = new WorldInAJar(1, 1, 1);
			JarScript script4 = new JarScript(world4);
			JarScene scene4 = new JarScene(script4);
			scene4.add(new ActionWait(2));
			scene4.add(new com.hbm.wiaj.actions.ActionOffsetBy(0, -1.5, 0, 20));
			script4.addScene(scene4);
			script4.forwardOne();
			near(helper, script4.offsetY, script3.offsetY, "the base mod's ActionOffsetBy moves the camera exactly as the stand-in did");
			near(helper, script4.zoom, script3.zoom, "and leaves the zoom alone");
		} finally {
			JarScript.ffwTarget = keepTarget;
			JarScript.freeRun = false;
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theVisActorsAreDrawnBothWaysAndIgnoreEveryUpdate(GameTestHelper helper) {
		var keepTarget = JarScript.ffwTarget;
		try {
			WorldInAJar world = new WorldInAJar(3, 3, 3);
			JarScript script = new JarScript(world);
			ActorVisFrame frame = new ActorVisFrame(script, 1, 2, 0, 0x123456, 0.5F);
			ActorVisFrame plain = new ActorVisFrame(script, 0, 0, 0, 0xFFFFFF);
			ActorVisText text = new ActorVisText(script, 2, 1, 0, 0xABCDEF, 2F, true, "a", "b");
			ActorVisText untouched = new ActorVisText(script, 0, 0, 0, 0, 1F, "c");
			helper.assertTrue(frame instanceof IDrawableActor && frame instanceof ISpecialActor && text instanceof IDrawableActor, "each is both halves of the base mod's actor");
			helper.assertTrue(frame.cellX() == 1 && frame.cellY() == 2 && frame.cellZ() == 0 && frame.color() == 0x123456 && frame.scale() == 0.5F, "the frame keeps what it was given");
			helper.assertTrue(plain.scale() == 1F, "a frame given no scale is a whole block");
			helper.assertTrue(text.cellX() == 2 && text.cellY() == 1 && text.color() == 0xABCDEF && text.scale() == 2F && text.forceUnicode() == 1
					&& text.texts().length == 2 && text.texts()[1].equals("b"), "the text keeps what it was given, and true means the unicode font");
			helper.assertTrue(untouched.forceUnicode() == -1, "and a text given no font choice leaves the font alone");
			// upstream's setDataPoint and setActorData do nothing, where the base class would store the value
			JarScene scene = new JarScene(script);
			frame.updateActor(scene);
			frame.setDataPoint("speed", 5F);
			frame.setActorData(new CompoundTag());
			text.setDataPoint("speed", 5F);
			helper.assertTrue(frame.cellX() == 1 && text.cellX() == 2, "an update and a data point change nothing");
		} finally {
			JarScript.ffwTarget = keepTarget;
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theMixingVatBladeTurnsNineDegreesATickWhileSpinningAndWrapsAt360(GameTestHelper helper) {
		CanneryLFTR.ActorMixingVat vat = new CanneryLFTR.ActorMixingVat();
		CompoundTag data = new CompoundTag();
		data.putBoolean("spin", false);
		helper.assertTrue(vat.rotation() == 115F && vat.previousRotation() == 115F, "the blade starts at 115 degrees");
		for(int i = 0; i < 5; i++) vat.updateActor(i, data);
		helper.assertTrue(vat.rotation() == 115F && vat.previousRotation() == 115F, "and does not move while the tag says not to");
		data.putBoolean("spin", true);
		vat.updateActor(5, data);
		helper.assertTrue(vat.rotation() == 124F && vat.previousRotation() == 115F, "nine degrees a tick: " + vat.rotation() + " from " + vat.previousRotation());
		for(int i = 0; i < 26; i++) vat.updateActor(6 + i, data);
		helper.assertTrue(vat.rotation() == 358F, "twenty-seven ticks in it has reached 358: " + vat.rotation());
		vat.updateActor(32, data);
		helper.assertTrue(vat.rotation() == 7F && vat.previousRotation() == -2F, "the next wraps it to 7, and its last tick's copy with it to -2: " + vat.rotation() + " / " + vat.previousRotation());
		vat.updateActor(0, new CompoundTag());
		helper.assertTrue(vat.rotation() == 115F && vat.previousRotation() == 115F, "a rewound scene starts the blade again at 115 (D-3076): " + vat.rotation());
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theStandInMachineJoinsAnyPipeAndHoldsNothing(GameTestHelper helper) {
		if(!lftr()) { helper.succeed(); return; }
		CanneryLFTR.FFDummyConnector dummy = new CanneryLFTR.FFDummyConnector();
		for(Direction dir : Direction.values())
			helper.assertTrue(dummy.canConnect(null, dir), "it takes a pipe from " + dir);
		helper.assertTrue(dummy.getTanks() == 0 && dummy.drain(100, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE).isEmpty()
				&& dummy.fill(net.neoforged.neoforge.fluids.FluidStack.EMPTY, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE) == 0, "and has no tank to fill or drain");
		helper.succeed();
	}

	// ---- registration, language, models ----

	@GameTest(template = EMPTY)
	public static void theTopicsAreInTheBaseModsTableUnderTheirOwnItemsAndOneTopicEach(GameTestHelper helper) {
		Map<Item, CanneryBase> keep = new HashMap<>(Jars.canneries);
		try {
			Jars.canneries.clear();
			AddonJars.initJars();

			if(pwr()) {
				CanneryBase topic = Jars.canneries.get(LeafiaBlocks.lwr_element.get().asItem());
				helper.assertTrue(topic instanceof CanneryPWR, "the PWR topic opens from the fuel rod");
				List<DeferredBlock<? extends Block>> others = List.of(LeafiaBlocks.reactor_element, LeafiaBlocks.reactor_element_blank, LeafiaBlocks.lwr_channel,
						LeafiaBlocks.lwr_control, LeafiaBlocks.reactor_control);
				for(var block : others)
					helper.assertTrue(Jars.canneries.get(block.get().asItem()) == topic, block.getId() + " opens the same topic");
			} else {
				helper.assertFalse(Jars.canneries.values().stream().anyMatch(t -> t instanceof CanneryPWR), "no PWR topic with the PWR off");
			}

			if(lftr()) {
				CanneryBase topic = Jars.canneries.get(LeafiaBlocks.msr_element.get().asItem());
				helper.assertTrue(topic instanceof CanneryLFTR, "the LFTR topic opens from the reaction chamber");
				List<DeferredBlock<? extends Block>> others = List.of(LeafiaBlocks.msr_control, LeafiaBlocks.msr_plug, LeafiaBlocks.msr_arbitrary);
				for(var block : others)
					helper.assertTrue(Jars.canneries.get(block.get().asItem()) == topic, block.getId() + " opens the same topic");
			} else {
				helper.assertFalse(Jars.canneries.values().stream().anyMatch(t -> t instanceof CanneryLFTR), "no LFTR topic with it off");
			}

			// the debug source that opens the test topic (D-3031): it registers with its switch, and the topic with it
			boolean hasSource = net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(LeafiaRef.rl("test_ff_source"));
			helper.assertTrue(hasSource == com.leafia.config.LeafiaFeatures.debugFFBlocks(), "the source is registered exactly when its switch is on: " + hasSource);
			helper.assertTrue(Jars.canneries.values().stream().anyMatch(t -> t instanceof CanneryTest) == hasSource, "the test topic is there exactly when its block is");
			if(hasSource)
				helper.assertTrue(Jars.canneries.get(com.leafia.contents.LeafiaBlocks.test_ff_source.get().asItem()) instanceof CanneryTest, "and the source opens it");
			helper.assertTrue(Jars.canneries.size() == (pwr() ? 6 : 0) + (lftr() ? 4 : 0) + (hasSource ? 1 : 0), "and nothing else: " + Jars.canneries.size());
		} finally {
			Jars.canneries.clear();
			Jars.canneries.putAll(keep);
		}
		helper.succeed();
	}

	private static String resource(String path) {
		try(InputStream in = LeafiaCanneryTests.class.getResourceAsStream(path)) {
			if(in == null) throw new IllegalStateException("missing resource " + path);
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch(IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@GameTest(template = EMPTY)
	public static void everyLineTheTopicsReadIsInTheLanguageFileInUpstreamsWords(GameTestHelper helper) {
		JsonObject lang = JsonParser.parseString(resource("/assets/leafia/lang/en_us.json")).getAsJsonObject();
		List<String> keys = new ArrayList<>(List.of("cannery.leafia.pwr", "cannery.leafia.lftr"));
		for(char c : "0123456789abcde".toCharArray()) keys.add("cannery.leafia.pwr." + c);
		for(char c : "0123456789abcdefgh".toCharArray()) keys.add("cannery.leafia.lftr." + c);
		helper.assertTrue(keys.size() == 35, "thirty-five lines");
		for(String key : keys)
			helper.assertTrue(lang.has(key) && !lang.get(key).getAsString().isEmpty(), key + " is defined");
		helper.assertTrue(lang.get("cannery.leafia.pwr").getAsString().equals("Pressurized Water Reactor"), "the PWR's title");
		helper.assertTrue(lang.get("cannery.leafia.lftr.9").getAsString().equals("If one exceeds 6000°C, the component will be destroyed."), "the LFTR's heat limit");
		helper.assertTrue(lang.get("cannery.leafia.pwr.7").getAsString().equals("PWR Fuel Rods receives 1/2 flux from the §dcorners§r.."), "the corner line keeps upstream's colour codes");
		helper.assertTrue(lang.get("cannery.leafia.lftr.h").getAsString().startsWith("Keep in mind that the drain will not work"), "and the last line");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theOlderVatIsSixPartModelsOfOneObjOnTwoTextures(GameTestHelper helper) {
		helper.assertTrue(MixingVatRender.PARTS.length == 6, "six parts");
		helper.assertTrue(String.join(",", MixingVatRender.PARTS).equals("Base,MixingBlade,VatGlass,Vat,Tanks,VatLiquid"), "in upstream's draw order");
		String obj = resource("/assets/leafia/models/blocks/mixingvat_old.obj");
		String mtl = resource("/assets/leafia/models/blocks/mixingvat_old.mtl");
		helper.assertTrue(mtl.contains("newmtl Vat") && mtl.contains("newmtl Fluid"), "two materials");
		int objects = 0, usemtl = 0;
		for(String line : obj.split("\n")) {
			if(line.startsWith("o ")) {
				objects++;
				helper.assertTrue(List.of(MixingVatRender.PARTS).contains(line.substring(2).trim()), "object " + line + " is a part");
			}
			if(line.startsWith("usemtl ")) usemtl++;
			if(line.startsWith("vt ")) {
				String[] uv = line.split(" ");
				float u = Float.parseFloat(uv[1]), v = Float.parseFloat(uv[2]);
				helper.assertTrue(u >= 0 && u <= 1 && v >= 0 && v <= 1, "a texture coordinate outside the atlas sprite: " + line);
			}
		}
		helper.assertTrue(objects == 6 && usemtl == 6, "six objects each with a material: " + objects + " / " + usemtl);
		for(String part : MixingVatRender.PARTS) {
			JsonObject model = JsonParser.parseString(resource("/assets/leafia/" + "models/" + MixingVatRender.partModel(part) + ".json")).getAsJsonObject();
			helper.assertTrue(model.get("model").getAsString().equals(MixingVatRender.mdl.toString()), part + " is a view of the OBJ");
			JsonObject hidden = model.getAsJsonObject("visibility");
			helper.assertTrue(hidden.size() == 5 && !hidden.has(part), part + " hides the five others and not itself: " + hidden);
		}
		helper.assertTrue(MixingVatRender.tex.getPath().equals("block/mixingvat_old") && MixingVatRender.rsc.getPath().equals("block/mixingvat_old_fluid"), "and the two textures");
		helper.assertTrue(LeafiaCanneryTests.class.getResource("/assets/leafia/textures/block/mixingvat_old.png") != null
				&& LeafiaCanneryTests.class.getResource("/assets/leafia/textures/block/mixingvat_old_fluid.png") != null, "which are there");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theControlRodRendererDrawsThePartsByTheNamesUpstreamDrew(GameTestHelper helper) {
		String[] parts = PWRControlBlock.PARTS;
		helper.assertTrue(parts[com.leafia.contents.cannery.CanneryActorRenders.FRAME_SIDE].equals("FrameSide") && parts[com.leafia.contents.cannery.CanneryActorRenders.FRAME_END].equals("FrameEnd")
				&& parts[com.leafia.contents.cannery.CanneryActorRenders.FRAME_END_TOP].equals("FrameEndTop") && parts[com.leafia.contents.cannery.CanneryActorRenders.FRAME_END_BTM].equals("FrameEndBtm")
				&& parts[com.leafia.contents.cannery.CanneryActorRenders.RODS_SIDE].equals("RodsSide") && parts[com.leafia.contents.cannery.CanneryActorRenders.RODS_END].equals("RodsEnd")
				&& parts[com.leafia.contents.cannery.CanneryActorRenders.RODS_END_TOP].equals("RodsEndTop") && parts[com.leafia.contents.cannery.CanneryActorRenders.RODS_END_BTM].equals("RodsEndBtm"),
				"the indices the jar draws by are the names upstream's renderPart calls took");
		helper.succeed();
	}
}
