package com.leafia.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.joml.Matrix4d;
import org.joml.Vector3d;

import com.custom_hbm.contents.torex.LCETorex;
import com.custom_hbm.contents.torex.LCETorex.Cloudlet;
import com.custom_hbm.contents.torex.LCETorex.TorexFinishPacket;
import com.custom_hbm.contents.torex.LCETorex.TorexPacket;
import com.custom_hbm.contents.torex.LCETorex.TorexType;
import com.custom_hbm.contents.torex.TorexMath;
import com.leafia.CommandLeaf;
import com.leafia.config.LeafiaFeatures;
import com.leafia.dev.LeafiaUtil;
import com.leafia.dev.custompacket.LeafiaCustomPacket;
import com.leafia.dev.custompacket.LeafiaPacketTypes;
import com.leafia.dev.optimization.bitbyte.LeafiaBuf;
import com.leafia.lib.LeafiaRef;
import com.leafia.passive.effects.LeafiaShakecam;
import com.leafia.passive.effects.LeafiaShakecam.Parsed;
import com.leafia.passive.effects.LeafiaShakecam.Preset;
import com.leafia.passive.effects.LeafiaShakecam.ShakeFrame;
import com.leafia.passive.effects.LeafiaShakecam.ShakecamPacket;
import com.leafia.passive.effects.LeafiaShakecam.shakeSimple;
import com.leafia.passive.effects.LeafiaShakecam.shakeSmooth;
import com.llib.technical.LeafiaEase;
import com.llib.technical.LeafiaEase.Direction;
import com.llib.technical.LeafiaEase.Ease;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import io.netty.buffer.Unpooled;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The torex and the camera shake (D-1089).
 *
 * <p>Almost everything here runs on a server, because the torex's numbers do: {@link TorexMath}
 * is pure, {@link LCETorex}'s client simulation needs only a level and a random source, and the
 * shake's instances write into a matrix instead of calling GL. What a server cannot do is draw, and
 * that was checked with a client run and photographs, not here.
 *
 * <p>Following the project's rule on timing, nothing asserts a number a few
 * ticks after a live object has been changing it: the simulation tests are built so the quantity
 * under test is either a pure function, a count that does not depend on the dice (a spawn loop
 * whose bound is 1 always spawns), or a relationship the passage of time leaves alone.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaTorexTests {

	private static final String EMPTY = "empty";

	private static void assertClose(GameTestHelper helper, double expected, double actual, double eps, String what) {
		helper.assertTrue(Math.abs(expected - actual) <= eps, what + ": expected " + expected + ", was " + actual);
	}

	// ------------------------------------------------------------------------------ constants

	/** Upstream's {@code public static final} numbers, and the three arithmetic one-liners every spawn goes through. */
	@GameTest(template = EMPTY)
	public static void theConstantsAreUpstreams(GameTestHelper helper) {
		helper.assertTrue(TorexMath.ANIMATION_SPEED_SHK == 6F, "shock speed");
		helper.assertTrue(TorexMath.ANIMATION_SPEED_GENERAL == 3F, "general speed");
		helper.assertTrue(TorexMath.FIRST_CONDENSE_HEIGHT == 130 && TorexMath.SECOND_CONDENSE_HEIGHT == 170, "condensation heights");
		helper.assertTrue(TorexMath.MAX_CLOUDLETS == 20_000, "the cap constant, which upstream reads nowhere");
		helper.assertTrue(TorexMath.FLASH_BASE_DURATION == 15 && TorexMath.FLARE_BASE_DURATION == 600, "flash and flare durations");

		// the maximum age is forty-five seconds per unit of scale
		helper.assertTrue(TorexMath.maxAge(1F) == 900, "scale 1 lives 900 ticks");
		helper.assertTrue(TorexMath.maxAge(0.25F) == 225, "scale a quarter lives 225");
		helper.assertTrue(TorexMath.maxAge(5F) == 4500, "scale 5 lives 4500");

		// the command's number is a hundredth, held between a quarter and five
		assertClose(helper, 1.0, TorexMath.scaleOf(100F), 1e-6, "100 is a scale of 1");
		assertClose(helper, 0.25, TorexMath.scaleOf(1F), 1e-6, "1 is floored at a quarter");
		assertClose(helper, 5.0, TorexMath.scaleOf(100000F), 1e-6, "a huge one is capped at five");
		// and the nuke palette bends it first: a 0.9 power above ten, never above the input
		assertClose(helper, 79.4328, TorexMath.nukeInput(100F), 1e-3, "statFac bends 100 to 79.43");
		assertClose(helper, 5.0, TorexMath.nukeInput(5F), 1e-4, "below 10 it is the identity, as the power would raise it");
		helper.assertTrue(TorexMath.nukeInput(1000F) < 1000F, "and it only ever lowers");
		assertClose(helper, 0.794328, TorexMath.scaleOf(TorexMath.nukeInput(100F)), 1e-5, "so statFac(100) is a scale of 0.79");

		// the packet's range: 200 + amp + amp^0.8 * 16
		assertClose(helper, 936.9714728855957, TorexMath.packetRange(1.0), 1e-9, "range at scale 1 is about 940 blocks");
		assertClose(helper, 3008.3198494515423, TorexMath.packetRange(5.0), 1e-9, "range at the largest scale is about 3,000 blocks");
		helper.succeed();
	}

	/** The four palettes at both ends and the hot end's twice-over, and anything else being the balefire. */
	@GameTest(template = EMPTY)
	public static void thePalettes(GameTestHelper helper) {
		double[] c = new double[3];

		TorexMath.color(TorexMath.TYPE_NUKE, 0, c);
		helper.assertTrue(c[0] == 0.1 && c[1] == 0.075 && c[2] == 0.05, "nuke, cool end");
		TorexMath.color(TorexMath.TYPE_NUKE, 1, c);
		assertClose(helper, 2.5, c[0], 1e-12, "nuke red at 1");
		assertClose(helper, 1.3, c[1], 1e-12, "nuke green at 1");
		assertClose(helper, 0.4, c[2], 1e-12, "nuke blue at 1");
		TorexMath.color(TorexMath.TYPE_NUKE, 2, c);
		assertClose(helper, 0.1 + 2.4 * 2, c[0], 1e-12, "the hot end is twice past 1, not clamped");

		TorexMath.color(TorexMath.TYPE_BALEFIRE, 1, c);
		assertClose(helper, 1.0, c[0], 1e-12, "balefire red");
		assertClose(helper, 2.0, c[1], 1e-12, "balefire green is brightest");
		assertClose(helper, 0.5, c[2], 1e-12, "balefire blue");
		TorexMath.color(TorexMath.TYPE_BALEFIRE, 0, c);
		helper.assertTrue(c[0] == 0.1 && c[1] == 0.1 && c[2] == 0.1, "balefire, cool end, grey");

		// digamma reads its channels off the balefire's green and blue: red, a little green and blue
		TorexMath.color(TorexMath.TYPE_DIGAMMA, 1, c);
		assertClose(helper, 2.0, c[0], 1e-12, "digamma red is the balefire's green");
		assertClose(helper, 0.5, c[1], 1e-12, "digamma green is the balefire's blue");
		assertClose(helper, 0.5, c[2], 1e-12, "digamma blue is the balefire's blue");

		// endothermic is the nuke with red and blue swapped
		TorexMath.color(TorexMath.TYPE_ENDOTHERMIC, 1, c);
		assertClose(helper, 0.4, c[0], 1e-12, "endo red is the nuke's blue");
		assertClose(helper, 1.3, c[1], 1e-12, "endo green is the nuke's green");
		assertClose(helper, 2.5, c[2], 1e-12, "endo blue is the nuke's red");

		TorexMath.color(7, 1, c);
		assertClose(helper, 2.0, c[1], 1e-12, "an unknown palette is the balefire");

		// how hot a puff is: 2 at the core, falling with the square of the distance
		assertClose(helper, 2.0, TorexMath.heatInterp(0, 50, false, 1), 1e-12, "at the core it is 2");
		// distSq 25 over heat 50 = 0.5; div = 2.5 - min(1, 1/5)*1.5 = 2.2; 0.5/2.2 < 1, so still 2
		assertClose(helper, 2.0, TorexMath.heatInterp(25, 50, false, 1), 1e-12, "close in it stays 2");
		// distSq 5000 / 50 = 100 ; /2.2 = 45.45 ; 2/45.45
		assertClose(helper, 2.0 / (100 / 2.2), TorexMath.heatInterp(5000, 50, false, 1), 1e-12, "far out it falls off");
		// a shock puff divides by three times the heat: three times the distance squared is the same colour
		assertClose(helper, TorexMath.heatInterp(5000, 50, false, 1), TorexMath.heatInterp(15000, 50, true, 1), 1e-12, "shock puffs divide by heat times three");
		helper.succeed();
	}

	// ------------------------------------------------------------------------------ the clock

	/** The simulation clock at both ends of both curves, for a small cloud and the largest one. */
	@GameTest(template = EMPTY)
	public static void theSimulationClock(GameTestHelper helper) {
		// scale 1: boost from 4/3 down to 1 over ten seconds, flat to thirty, then down to a quarter, then the wind-down
		assertClose(helper, 4.0 / 3, TorexMath.simulationSpeed(0, 0, 900, 1), 1e-12, "t=0 is the boost's full height");
		assertClose(helper, 7.0 / 6, TorexMath.simulationSpeed(100, 100, 900, 1), 1e-12, "t=100 is half way down it");
		assertClose(helper, 1.0, TorexMath.simulationSpeed(200, 200, 900, 1), 1e-12, "t=200 is 1");
		assertClose(helper, 1.0, TorexMath.simulationSpeed(201, 201, 900, 1), 1e-12, "and stays 1");
		assertClose(helper, 1.0, TorexMath.simulationSpeed(600, 600, 900, 1), 1e-12, "until thirty seconds");
		// t=700, life=700: slow tick is 675, so (25/225) down, and (100/3600) of the way to a quarter
		assertClose(helper, (1 - 25.0 / 225) * (1 - 0.75 * 100 / 3600), TorexMath.simulationSpeed(700, 700, 900, 1), 1e-6, "both curves at once");
		assertClose(helper, 0.0, TorexMath.simulationSpeed(900, 900, 900, 1), 1e-12, "and it is stopped at the end of its life");

		// the biggest cloud boosts to twice: (5 - 0.5) * 2 / 3 + 1 = 4, clamped to 2
		assertClose(helper, 2.0, TorexMath.simulationSpeed(0, 0, 4500, 5), 1e-12, "scale 5 starts at 2");
		assertClose(helper, 1.5, TorexMath.simulationSpeed(100, 100, 4500, 5), 1e-12, "and is at 1.5 after 100");
		assertClose(helper, (1 - 625.0 / 1125) * (1 - 0.75 * 3400 / 3600), TorexMath.simulationSpeed(4000, 4000, 4500, 5), 1e-6, "winding down at 4000");

		helper.assertTrue(TorexMath.pauseTick(900) == 225 && TorexMath.slowTick(900) == 675, "a quarter and three quarters of 900");
		helper.succeed();
	}

	/** The cloud's life: the puff lifetime curve, the fade, the spawn bound and limit. */
	@GameTest(template = EMPTY)
	public static void lifetimesFadeAndSpawnCounts(GameTestHelper helper) {
		// a puff lives its age squared plus 200, up to the cloud's own end
		helper.assertTrue(TorexMath.cloudletLifetime(1, 900, 0) == 201, "tick 1");
		helper.assertTrue(TorexMath.cloudletLifetime(10, 900, 0) == 300, "tick 10");
		helper.assertTrue(TorexMath.cloudletLifetime(30, 900, 0) == 1070, "tick 30: 900 + 200 = 1100 is past the cloud's 1070 left");
		helper.assertTrue(TorexMath.cloudletLifetime(30, 900, 100) == 1100, "banked time lengthens what is left");
		helper.assertTrue(TorexMath.cloudletLifetime(1200, 4500, 0) == 3500, "the square stops growing at 1200 but the end is further off");
		helper.assertTrue(TorexMath.cloudletLifetime(2000, 4500, 0) == 2700, "late, the cloud's end is what limits it");

		helper.assertTrue(TorexMath.alpha(900, 675, 0) == 1.0F, "no fade through three quarters");
		assertClose(helper, 0.9955555, TorexMath.alpha(900, 676, 0), 1e-6, "one tick into the fade");
		assertClose(helper, 0.4977778, TorexMath.alpha(900, 788, 0), 1e-6, "half way through it");
		helper.assertTrue(TorexMath.alpha(900, 900, 0) == 0.0F, "gone at the end");
		assertClose(helper, 0.8888889, TorexMath.alpha(900, 800, 100), 1e-6, "banked time moves the fade later");

		// the bound of the draw: a spawn happens every tick above 0.8 speed, one in two near a half, and rarely when slow
		int[][] bounds = {{2, 1}, {1, 1}, {0, 0}};
		helper.assertTrue(TorexMath.spawnBound(2) == 1 && TorexMath.spawnBound(1.5) == 1 && TorexMath.spawnBound(1) == 1, "a fast cloud always spawns");
		helper.assertTrue(TorexMath.spawnBound(0.5) == 2, "at a half, one tick in two");
		helper.assertTrue(TorexMath.spawnBound(0.25) == 4, "at a quarter, one in four");
		helper.assertTrue(TorexMath.spawnBound(0.12) == 9, "at 0.12, one in nine");
		helper.assertTrue(TorexMath.spawnBound(0.001) == 1000 && TorexMath.spawnBound(0) == 1000, "and the floor of the speed is a thousandth");
		// the number per spawn
		helper.assertTrue(TorexMath.spawnLimit(true, 2, 1) == 4, "boosted: four puffs");
		helper.assertTrue(TorexMath.spawnLimit(true, 1.5, 1) == 4, "up to 1.1 the ceiling is 2, above it 2 again: 1.5 still two pairs");
		helper.assertTrue(TorexMath.spawnLimit(true, 1.0, 1) == 2, "full speed: two");
		helper.assertTrue(TorexMath.spawnLimit(true, 0.5, 1) == 2, "half speed: still two");
		helper.assertTrue(TorexMath.spawnLimit(true, 0.05, 1) == 0 && TorexMath.spawnLimit(true, 0.1, 1) == 0, "below a tenth: none");
		helper.assertTrue(TorexMath.spawnLimit(true, 1.0, 5) == 10 && TorexMath.spawnLimit(true, 1.0, 6) == 12, "the endothermic palette's multipliers");
		helper.assertTrue(TorexMath.spawnLimit(false, 2, 1) == 0, "a skipped tick spawns nothing");
		helper.succeed();
	}

	/** The shock ring: when it is made, how many, how long they live, how fast they go. */
	@GameTest(template = EMPTY)
	public static void theShockRing(GameTestHelper helper) {
		assertClose(helper, 54.75, TorexMath.tickOffset(1), 1e-9, "scale 1 starts its ring at tick 54.75");
		assertClose(helper, 273.75, TorexMath.tickOffset(5), 1e-9, "scale 5 at 273.75");
		helper.assertTrue(!TorexMath.shockActive(54, 54.75) && TorexMath.shockActive(55, 54.75), "it starts the tick after the offset");
		helper.assertTrue(TorexMath.shockActive(79, 54.75) && !TorexMath.shockActive(80, 54.75), "and stops 25 ticks later: 150/6");
		// at 0.5 ticks into it
		helper.assertTrue(TorexMath.shockCount(0.5) == 6 && TorexMath.shockLife(0.5) == 340, "0.5 ticks in: 6 puffs, 340 life");
		helper.assertTrue(TorexMath.shockCount(2.25) == 26 && TorexMath.shockLife(2.25) == 140, "2.25: 26 puffs, 140 life");
		helper.assertTrue(TorexMath.shockCount(10) == 120 && TorexMath.shockLife(10) == 50, "10: 120 puffs, life floored at 50");
		helper.assertTrue(TorexMath.shockCount(100) == 300, "the count is capped at 300");
		assertClose(helper, 0.0, TorexMath.shockMotion(0.5), 1e-12, "the ring starts at a standstill");
		assertClose(helper, 1.0, TorexMath.shockMotion(10), 1e-12, "and is at full speed after a few ticks");
		assertClose(helper, 0.0, TorexMath.shockMotion(3.0), 1e-12, "still at rest at 3 ticks in: 18 * 0.25 - 5 is negative");
		assertClose(helper, 0.25, TorexMath.shockMotion(3.5), 1e-12, "a quarter at 3.5: 21 * 0.25 - 5");
		assertClose(helper, 0.5, TorexMath.shockMotion(3.7), 1e-12, "a half at 3.7: 22 * 0.25 - 5");
		assertClose(helper, 1.0, TorexMath.shockMotion(4.0), 1e-12, "full at 4");
		helper.succeed();
	}

	/** The torus's growth: what one tick adds to each of the five quantities, early and late. */
	@GameTest(template = EMPTY)
	public static void theGrowthCurve(GameTestHelper helper) {
		assertClose(helper, 0.15, TorexMath.coreHeightStep(0, 1), 1e-12, "core height step at the start");
		assertClose(helper, 0.15, TorexMath.coreHeightStep(240, 1), 1e-12, "and until tick 240");
		assertClose(helper, 0.0013590033975, TorexMath.coreHeightStep(340, 1), 1e-12, "100 ticks later it has slowed 110-fold");
		assertClose(helper, 0.0035176231823, TorexMath.coreHeightStep(340, 5), 1e-12, "a big cloud keeps climbing longer");
		assertClose(helper, 0.05, TorexMath.torusWidthStep(300, 1), 1e-12, "torus width step until tick 300");
		assertClose(helper, 0.05 / 101, TorexMath.torusWidthStep(400, 1), 1e-12, "and 100 ticks after");
		assertClose(helper, 2.054406347e-5, TorexMath.scaleCurrentStep(400, 1, 1.0), 1e-12, "scale growth at full speed");
		assertClose(helper, 1.55037841725e-5, TorexMath.scaleCurrentStep(400, 2, 0.5), 1e-12, "and for a bigger, slower cloud");
		assertClose(helper, 0.015, TorexMath.scaleGrowStep(600), 1e-12, "the grow rate until tick 600");
		assertClose(helper, 0.007, TorexMath.scaleGrowStep(1000), 1e-12, "and 400 ticks after");

		helper.assertTrue(TorexMath.maxHeat(1, 0) == 50, "heat is 50 at scale 1");
		helper.assertTrue(TorexMath.maxHeat(0.25, 0) == 3, "and 3 at a quarter: 3.125 truncated");
		helper.assertTrue(TorexMath.maxHeat(1, 3) == 100, "doubled for the endothermic palette");
		helper.assertTrue(TorexMath.maxHeat(5, 0) == 1250, "and 1250 at scale 5");
		// the heat falls in steps, because the division inside is integer: 50 * 100 / 900 = 5, not 5.55
		assertClose(helper, 47.373472195596236, TorexMath.heat(50, 100, 0, 900), 1e-12, "heat at tick 100");
		assertClose(helper, 47.373472195596236, TorexMath.heat(50, 100, 10, 900), 1e-12, "and with 10 banked, which still divides to 5");
		helper.assertTrue(TorexMath.heat(50, 17, 0, 900) == 50.0, "50 * 17 / 900 is 0 in integers, so no loss yet");
		helper.assertTrue(TorexMath.heat(50, 18, 0, 900) == 49.0, "and 1 at tick 18");
		assertClose(helper, 0.9474694439119247, TorexMath.heatScaled(50, 100, 900), 1e-12, "the scaled heat is the same, over 50");
		helper.succeed();
	}

	/** The blast's effect on the player: which sound, how long the hurt wobble. */
	@GameTest(template = EMPTY)
	public static void theBlastReachingThePlayer(GameTestHelper helper) {
		// the shock band: 10 or less, and it starts far away for a big cloud
		assertClose(helper, 100 - 9 * 5 + 1 * 15 * 6 * 6, TorexMath.shockwaveDistance(100, 5, 1), 1e-9, "distance at five ticks");
		assertClose(helper, 100 - 9 * 5 + 5 * 540, TorexMath.shockwaveDistance(100, 5, 5), 1e-9, "a larger cloud starts further out");

		helper.assertTrue(TorexMath.blastSound(40, 10) == 4, "a small blast plays the muke explosion");
		helper.assertTrue(TorexMath.blastSound(100, 10) == 1, "a medium one the small nuke");
		helper.assertTrue(TorexMath.blastSound(48, 10) == 4 && TorexMath.blastSound(48.01, 10) == 1, "the line is above 48");
		helper.assertTrue(TorexMath.blastSound(128, 10) == 1 && TorexMath.blastSound(128.01, 10) == 2, "and above 128 it is near or far");
		helper.assertTrue(TorexMath.blastSound(200, 100 + Math.pow(200, 0.95)) == 2, "near at the boundary");
		helper.assertTrue(TorexMath.blastSound(200, 100 + Math.pow(200, 0.95) + 1) == 0, "the plain one between");
		helper.assertTrue(TorexMath.blastSound(200, 300 + 200 + Math.pow(200, 0.8) * 2 + 1) == 3, "far past the far boundary");

		helper.assertTrue(TorexMath.hurtDuration(100, 50) == 100, "within the amplitude the wobble is the amplitude");
		helper.assertTrue(TorexMath.hurtDuration(100, 200) == 25, "and falls with the square of the distance past it");
		helper.assertTrue(TorexMath.hurtDuration(100, 0.0001) == 100, "never above it");
		helper.succeed();
	}

	/** The flash's envelope: starts at nothing, eases to full size by half its time, then grows slowly and fades. */
	@GameTest(template = EMPTY)
	public static void theFlashEnvelope(GameTestHelper helper) {
		float dur = 15F;
		double[] f0 = TorexMath.flash(0, dur);
		helper.assertTrue(f0 != null && f0[0] == 0 && f0[1] == 1, "starts at nothing, opaque");
		// intensity = age / dur * 2; at age = dur/2 it reaches 1 and the ease has finished
		double[] f1 = TorexMath.flash(dur / 2, dur);
		assertClose(helper, 1.0, f1[0], 1e-12, "full size at half the duration");
		assertClose(helper, 1.0, f1[1], 1e-12, "still opaque there");
		double[] fq = TorexMath.flash(dur / 4, dur);
		assertClose(helper, 0.75, fq[0], 1e-12, "a quadratic out is 0.75 at a quarter");
		double[] f2 = TorexMath.flash(dur * 2, dur);
		assertClose(helper, 2.0, f2[0], 1e-12, "at intensity 4 the size is its square root");
		assertClose(helper, Math.max(1 - Math.pow(3.0 / 8, 0.5), 0), f2[1], 1e-12, "and the alpha has begun to fall");
		double[] f3 = TorexMath.flash(dur * 6, dur);
		assertClose(helper, Math.sqrt(12), f3[0], 1e-12, "the last moment");
		helper.assertTrue(f3[1] == 0, "fully faded by intensity 9: " + f3[1]);
		helper.assertTrue(TorexMath.flash(dur * 6 + 0.001, dur) == null, "and nothing after six times the duration");
		helper.succeed();
	}

	/** The 300 spikes: from {@code Random(432L)}, five rotations then two size draws each. */
	@GameTest(template = EMPTY)
	public static void theFlashSpikeTable(GameTestHelper helper) {
		helper.assertTrue(TorexMath.FLASH_FANS == 300 && TorexMath.FLASH_SEED == 432L, "300 spikes from 432");
		helper.assertTrue(TorexMath.FLASH_INTENSITY == 50F, "intensity 50");
		float[][][] rot = new float[300][][];
		float[] len = new float[300];
		float[] wid = new float[300];
		TorexMath.buildFlashTable(rot, len, wid);
		helper.assertTrue(java.util.Arrays.equals(len, TorexMath.FLASH_LEN) && java.util.Arrays.equals(wid, TorexMath.FLASH_WID),
				"building twice gives the same table");

		// the sizes come after the five rotation draws of each spike
		Random r = new Random(432L);
		for(int i = 0; i < 5; i++) r.nextFloat();
		float expectLen = r.nextFloat() * 20.0F + 5.0F + 1 * 10.0F;
		float expectWid = r.nextFloat() * 2.0F + 1.0F + 1 * 2.0F;
		helper.assertTrue(len[0] == expectLen && wid[0] == expectWid, "the first spike's sizes are the sixth and seventh draws");
		for(int i = 0; i < 5; i++) r.nextFloat();
		helper.assertTrue(len[1] == r.nextFloat() * 20.0F + 5.0F + 1 * 10.0F, "the second spike's follow ten draws in");

		for(int i = 0; i < 300; i++) {
			helper.assertTrue(len[i] >= 15 && len[i] < 35, "length draw in [15, 35): " + len[i]);
			helper.assertTrue(wid[i] >= 3 && wid[i] < 5, "width draw in [3, 5): " + wid[i]);
			float[][] m = rot[i];
			// rows of a rotation are unit and orthogonal; five accumulated float rotations keep that to 1e-4
			for(int a = 0; a < 3; a++) {
				float n = m[a][0] * m[a][0] + m[a][1] * m[a][1] + m[a][2] * m[a][2];
				helper.assertTrue(Math.abs(n - 1) < 1e-3, "row " + a + " of spike " + i + " is unit: " + n);
			}
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------------------ knockback

	@GameTest(template = EMPTY)
	public static void theBlastKnockback(GameTestHelper helper) {
		Vec3 a = LeafiaUtil.getKnockback(new Vec3(10, 0, 0), Vec3.ZERO, 100);
		assertClose(helper, 14.382768630981445, a.x, 1e-9, "10 blocks away, amplitude 100");
		helper.assertTrue(a.y == 0 && a.z == 0, "straight away from the blast");
		Vec3 b = LeafiaUtil.getKnockback(new Vec3(3, 4, 0), Vec3.ZERO, 100);
		assertClose(helper, 9.042729949951172, b.x, 1e-9, "the 3-4-5 triangle, x");
		assertClose(helper, 12.056973266601563, b.y, 1e-9, "and y, in the ratio 3:4");
		Vec3 c = LeafiaUtil.getKnockback(new Vec3(0, 50, 0), Vec3.ZERO, 50);
		assertClose(helper, 2.4274988174438477, c.y, 1e-9, "amplitude 50 at 50 blocks");
		Vec3 far = LeafiaUtil.getKnockback(new Vec3(300, 0, 0), Vec3.ZERO, 100);
		helper.assertTrue(far.x == 0 && far.y == 0 && far.z == 0, "past twice the amplitude it is nothing");
		Vec3 origin = LeafiaUtil.getKnockback(Vec3.ZERO, Vec3.ZERO, 100);
		helper.assertTrue(Double.isNaN(origin.x), "at the blast's centre the direction is 0/0, upstream's NaN");
		helper.succeed();
	}

	// ------------------------------------------------------------------------------ the simulation

	private static TorexPacket spawnPacket(BlockPos at, float scale, int type, boolean doWait) {
		TorexPacket p = new TorexPacket();
		p.entityId = 77;
		p.uuid = UUID.randomUUID();
		p.x = at.getX();
		p.y = at.getY();
		p.z = at.getZ();
		p.scale = scale;
		p.type = (byte) type;
		p.sound = false;
		p.range = 100;
		p.doWait = doWait;
		return p;
	}

	/** A client torex on a server level, seeded, built the way a packet builds one. */
	private static LCETorex client(GameTestHelper helper, float scale, int type) {
		BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
		TorexPacket p = spawnPacket(at, scale, type, false);
		LCETorex t = LCETorex.fromPacket(helper.getLevel(), p, p.x, p.z);
		t.rand = RandomSource.create(1234L);
		return t;
	}

	private static void tick(LCETorex t, int n) {
		for(int i = 0; i < n; i++) {
			++t.ticksExisted;
			t.onUpdate();
		}
	}

	private static int count(LCETorex t, TorexType type) {
		int n = 0;
		for(Cloudlet c : t.cloudlets) if(c.type == type) n++;
		return n;
	}

	/**
	 * Three ticks of a scale-1 nuke cloud. At that age the speed is above 1, so the spawn bound is 1
	 * and the draw cannot fail: four standard puffs a tick, and two ring puffs. Their lifetimes are
	 * the square law: 201, 204, 209.
	 */
	@GameTest(template = EMPTY)
	public static void aNukeCloudSpawnsWhatTheMathSays(GameTestHelper helper) {
		LCETorex t = client(helper, 1F, TorexMath.TYPE_NUKE);
		try {
			helper.assertTrue(t.maxAge == 900 && t.getScale() == 1.0, "scale 1, 900 ticks");
			tick(t, 3);
			helper.assertTrue(count(t, TorexType.STANDARD) == 12, "12 standard puffs, got " + count(t, TorexType.STANDARD));
			helper.assertTrue(count(t, TorexType.RING) == 6, "6 ring puffs, got " + count(t, TorexType.RING));
			helper.assertTrue(count(t, TorexType.SHOCK) == 0 && count(t, TorexType.CONDENSATION) == 0, "no shock and no condensation yet");
			int[] lives = new int[3];
			int n = 0;
			for(Cloudlet c : t.cloudlets) {
				if(c.type != TorexType.STANDARD) continue;
				lives[n++ / 4] = c.cloudletLife;
			}
			helper.assertTrue(lives[0] == 201 && lives[1] == 204 && lives[2] == 209, "lifetimes 201, 204, 209, got " + java.util.Arrays.toString(lives));
			for(Cloudlet c : t.cloudlets) helper.assertTrue(c.age >= 1 && c.age <= 3, "each puff has been updated one to three times: " + c.age);
			helper.assertTrue(t.heat == 50.0, "heat 50 while 50 * life / 900 is 0: " + t.heat);
			helper.assertTrue(t.ticksExisted == 3 && !t.isDead, "alive at 3");
			helper.succeed();
		} finally {
			LCETorex.forget(t);
		}
	}

	/**
	 * Upstream's quirk: the scale multiplies the heights once when the packet is read and again on the
	 * first tick, so at scale 2 the core is {@code 3 * 2 * 2} plus the first tick's step, not
	 * {@code 3 * 2}. Pinned so nobody fixes it without meaning to.
	 */
	@GameTest(template = EMPTY)
	public static void scaleAppliesTwiceToTheHeights(GameTestHelper helper) {
		LCETorex t = client(helper, 2F, TorexMath.TYPE_NUKE);
		try {
			assertClose(helper, 6.0, t.coreHeight, 1e-12, "read from the packet: 3 * 2");
			assertClose(helper, 6.0, t.torusWidth, 1e-12, "the torus too");
			helper.assertTrue(t.maxAge == 1800, "and the age");
			tick(t, 1);
			assertClose(helper, 12.0 + 0.15, t.coreHeight, 1e-12, "after one tick: 3 * 2 * 2 + 0.15");
			assertClose(helper, 12.0 + 0.05, t.torusWidth, 1e-12, "torus: 3 * 4 + 0.05");
			assertClose(helper, (12.0 + 0.05) * 0.35, t.rollerSize, 1e-12, "the roller is 0.35 of the torus");
			assertClose(helper, 12.15 + (12.05 * 0.35), t.convectionHeight, 1e-12, "convection is core plus roller");
			helper.assertTrue(t.maxAge == 1800, "the age is unchanged by the second application");
			helper.succeed();
		} finally {
			LCETorex.forget(t);
		}
	}

	/** The endothermic palette: its own two loops, no ring, and a core that never grows. */
	@GameTest(template = EMPTY)
	public static void anEndothermicCloudHasItsOwnPuffs(GameTestHelper helper) {
		LCETorex t = client(helper, 1F, TorexMath.TYPE_ENDOTHERMIC);
		try {
			tick(t, 1);
			// sim speed at tick 1 is 1.33: ceil(1.23) = 2 pairs, times 5 for the first loop and 6 for the pillar
			helper.assertTrue(count(t, TorexType.ENDOTHERMIC) == 20, "20 sequenced puffs, got " + count(t, TorexType.ENDOTHERMIC));
			helper.assertTrue(count(t, TorexType.ENDOTHERMIC_PILLAR) == 24, "24 pillar puffs, got " + count(t, TorexType.ENDOTHERMIC_PILLAR));
			helper.assertTrue(count(t, TorexType.RING) == 0 && count(t, TorexType.STANDARD) == 0, "no ring and no standard puffs");
			assertClose(helper, 3.0, t.coreHeight, 1e-12, "the core does not grow: 3 * 1 * 1");
			helper.assertTrue(TorexMath.maxHeat(1, t.getType()) == 100, "and it is twice as hot");
			helper.assertTrue(t.getType() == 3, "type 3");
			for(Cloudlet c : t.cloudlets) {
				helper.assertTrue(Double.isFinite(c.initPosX) && Double.isFinite(c.initPosY) && Double.isFinite(c.initPosZ), "positions are finite");
			}
			helper.succeed();
		} finally {
			LCETorex.forget(t);
		}
	}

	/** Condensation: only when there is humidity, and in the spreads upstream's two calls give it. */
	@GameTest(template = EMPTY)
	public static void condensationCounts(GameTestHelper helper) {
		LCETorex t = client(helper, 1F, TorexMath.TYPE_NUKE);
		try {
			t.cloudlets.clear();
			// below the height nothing happens: initPosY + age must pass it
			t.spawnCondensationClouds(0, 1F, 130, 80, 4, 1, 1.5);
			helper.assertTrue(t.cloudlets.isEmpty(), "age 0 spawns nothing below 130");
			// humidity 1, count 80, spread 4: (int)(5 * 1 * 80 / 4) = 100 passes of 3
			t.spawnCondensationClouds((int) (130 - t.initPosY) + 5, 1F, 130, 80, 4, 1, 1.5);
			helper.assertTrue(t.cloudlets.size() == 300, "100 x 3 = 300 puffs, got " + t.cloudlets.size());
			helper.assertTrue(count(t, TorexType.CONDENSATION) == 300, "all condensation");
			t.cloudlets.clear();
			// the upper layer has spread 2: (int)(5 * 0.5 * 80 / 2) = 100 passes of 1
			t.spawnCondensationClouds((int) (170 - t.initPosY) + 5, 0.5F, 170, 80, 2, 1, 1.5);
			helper.assertTrue(t.cloudlets.size() == 100, "100 x 1, got " + t.cloudlets.size());
			helper.succeed();
		} finally {
			LCETorex.forget(t);
		}
	}

	/**
	 * Six hundred ticks of a scale-1 cloud, asserting only what the passage of time does not change:
	 * every puff within its life, every position finite, the core never shrinking, the heat never
	 * rising, the puffs of each kind never outliving what their spawn gave them.
	 */
	@GameTest(template = EMPTY, timeoutTicks = 200)
	public static void aCloudGrowsAndNeverMisbehaves(GameTestHelper helper) {
		LCETorex t = client(helper, 1F, TorexMath.TYPE_NUKE);
		try {
			double lastCore = t.coreHeight;
			double lastHeat = Double.MAX_VALUE;
			int peak = 0;
			for(int i = 0; i < 600; i++) {
				tick(t, 1);
				helper.assertTrue(t.coreHeight >= lastCore, "the core never shrinks, tick " + i);
				lastCore = t.coreHeight;
				helper.assertTrue(t.heat <= lastHeat + 1e-9, "the heat never rises, tick " + i);
				lastHeat = t.heat;
				peak = Math.max(peak, t.cloudlets.size());
				for(Cloudlet c : t.cloudlets) {
					helper.assertTrue(c.age <= c.cloudletLife + 1, "a puff is past its life at tick " + i);
					helper.assertTrue(Double.isFinite(c.initPosX) && Double.isFinite(c.initPosY) && Double.isFinite(c.initPosZ),
							"a " + c.type + " puff has a non-finite position at tick " + i);
					helper.assertTrue(c.getAlpha() > 0 && c.getAlpha() <= 1F, "alpha in (0, 1]");
				}
			}
			helper.assertTrue(!t.isDead, "a scale-1 cloud is alive at 600 of its 900");
			helper.assertTrue(t.cloudlets.size() > 0 && peak > 1000, "a real cloud: peak " + peak + " puffs");
			helper.assertTrue(t.cloudlets.size() < TorexMath.MAX_CLOUDLETS * 2, "and not out of hand: " + t.cloudlets.size());
			helper.assertTrue(t.coreHeight > 3 + 0.15 * 200, "the core climbed: " + t.coreHeight);
			System.out.println("[torex] scale 1, tick 600: " + t.cloudlets.size() + " puffs, peak " + peak + ", core " + t.coreHeight);
			helper.succeed();
		} finally {
			LCETorex.forget(t);
		}
	}

	/**
	 * The largest cloud, through its whole life: scale 5, 4,500 ticks. Nothing caps the puff count
	 * upstream ({@code maxCloudlets} is read nowhere), so this is where a regression that stops puffs
	 * dying would show: the count must come back to nothing when the cloud's life is up. The numbers are
	 * printed for D-1089.
	 */
	@GameTest(template = EMPTY, timeoutTicks = 400)
	public static void theLargestCloudLivesAndDiesWithoutALeak(GameTestHelper helper) {
		LCETorex t = client(helper, 5F, TorexMath.TYPE_NUKE);
		try {
			long started = System.nanoTime();
			int peak = 0;
			int peakTick = 0;
			int at1000 = 0;
			int at3000 = 0;
			int i = 0;
			while(!t.isDead && i < 5000) {
				i++;
				tick(t, 1);
				if(t.cloudlets.size() > peak) {
					peak = t.cloudlets.size();
					peakTick = i;
				}
				if(i == 1000) at1000 = t.cloudlets.size();
				if(i == 3000) at3000 = t.cloudlets.size();
			}
			long ms = (System.nanoTime() - started) / 1_000_000;
			System.out.println("[torex] scale 5, " + i + " ticks to death: peak " + peak + " at tick " + peakTick + ", " + at1000 + " at 1000, "
					+ at3000 + " at 3000, " + t.cloudlets.size() + " left; " + ms + " ms in all");
			helper.assertTrue(i == 4501, "it dies on the tick after its 4500: " + i);
			helper.assertTrue(peak > 3000 && peak < TorexMath.MAX_CLOUDLETS, "a big cloud is big, and under the constant upstream never enforced: " + peak);
			helper.assertTrue(t.cloudlets.size() < peak, "and it has shrunk by the end, not grown without bound: " + t.cloudlets.size());
			assertClose(helper, 5.0, t.getScale(), 0, "scale 5");
			helper.succeed();
		} finally {
			LCETorex.forget(t);
		}
	}

	/** A torex that has lived out its age is dead, on the same tick the arithmetic says. */
	@GameTest(template = EMPTY)
	public static void aCloudDiesWhenItsAgeIsUp(GameTestHelper helper) {
		LCETorex t = client(helper, 0.25F, TorexMath.TYPE_BALEFIRE);
		try {
			helper.assertTrue(t.maxAge == 225, "a quarter-scale cloud lives 225 ticks");
			t.ticksExisted = 224;
			tick(t, 1);
			helper.assertTrue(!t.isDead, "at 225 it is not yet dead: the test is a strict greater-than");
			tick(t, 1);
			helper.assertTrue(t.isDead, "at 226 it is");
			helper.succeed();
		} finally {
			LCETorex.forget(t);
		}
	}

	/** A cloud waiting on an explosion pauses at a quarter of its life, and a finish packet lets it go on. */
	@GameTest(template = EMPTY)
	public static void aWaitingCloudPausesUntilFinished(GameTestHelper helper) {
		BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
		TorexPacket p = spawnPacket(at, 1F, TorexMath.TYPE_NUKE, true);
		LCETorex t = LCETorex.fromPacket(helper.getLevel(), p, p.x, p.z);
		try {
			helper.assertTrue(t != null && !t.calculationFinished, "built waiting");
			t.rand = RandomSource.create(5L);
			// at 300 ticks of a 900 life, pause tick is 225: the clock reads 225 and banks 75
			t.ticksExisted = 299;
			tick(t, 1);
			helper.assertTrue(t.getSubt() == 75, "subt banks the overshoot: " + t.getSubt());
			t.ticksExisted = 399;
			tick(t, 1);
			helper.assertTrue(t.getSubt() == 175, "and keeps banking, one per tick: " + t.getSubt());
			assertClose(helper, TorexMath.simulationSpeed(400, 225, 900, 1), t.getSimulationSpeed(), 1e-12, "the speed is that of tick 225's life");

			TorexFinishPacket.finish(UUID.randomUUID());
			helper.assertTrue(!t.calculationFinished, "another cloud's finish packet does nothing");
			TorexFinishPacket.finish(p.uuid);
			helper.assertTrue(t.calculationFinished, "its own finishes it");
			int banked = t.getSubt();
			tick(t, 1);
			helper.assertTrue(t.getSubt() == banked, "and nothing more is banked");
			helper.succeed();
		} finally {
			LCETorex.forget(t);
		}
	}

	/** A packet from out of range builds nothing; one in range builds a client copy with the packet's scale, palette and sound. */
	@GameTest(template = EMPTY)
	public static void aPacketBuildsAClientCopyOnlyInRange(GameTestHelper helper) {
		BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
		TorexPacket p = spawnPacket(at, 3F, TorexMath.TYPE_DIGAMMA, false);
		p.sound = true;
		p.range = 500;
		LCETorex far = LCETorex.fromPacket(helper.getLevel(), p, p.x + 501, p.z);
		helper.assertTrue(far == null, "501 blocks away is out of a range of 500");
		LCETorex near = LCETorex.fromPacket(helper.getLevel(), p, p.x + 300, p.z + 400);
		try {
			helper.assertTrue(near != null, "300 and 400 away is exactly 500: in");
			helper.assertTrue(near.getScale() == 3.0 && near.getType() == 2 && near.sound, "scale, palette and sound came across");
			helper.assertTrue(near.clientSide && near.isValid() && near.entityId == 77 && near.uuid.equals(p.uuid), "a valid client copy with the server's ids");
			helper.assertTrue(near.initPosX == p.x && near.initPosZ == p.z, "at the packet's position");
			helper.assertTrue(near.maxAge == 2700, "scale 3 lives 2700");
			helper.succeed();
		} finally {
			LCETorex.forget(near);
		}
	}

	// ------------------------------------------------------------------------------ the server timer

	/** The server's torex: spawned through the API, ticked by the level, dead when its time is up and gone from the list. */
	@GameTest(template = EMPTY)
	public static void theServerTimesACloudOut(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
		LCETorex t = LCETorex.statFacBale(level, at.getX(), at.getY(), at.getZ(), 25F);
		helper.assertTrue(t != null, "statFacBale made one");
		helper.assertTrue(LCETorex.serverTorexes(level).contains(t), "and the server is tracking it");
		helper.assertTrue(t.getType() == 1 && t.getScale() == 0.25 && t.maxAge == 225, "balefire, a quarter scale, 225 ticks");
		helper.assertTrue(t.isValid() && !t.clientSide && t.calculationFinished && t.sound, "valid, server side, unbound, with sound");
		helper.assertTrue(t.initPosX == at.getX() && t.initPosY == at.getY() && t.initPosZ == at.getZ(), "initial position is where it was made");
		t.ticksExisted = 222;
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(t.ticksExisted >= 224, "the level ticks it: " + t.ticksExisted);
			helper.assertTrue(t.cloudlets.isEmpty(), "the server's copy has no puffs: it is only a timer");
			helper.runAfterDelay(4, () -> {
				helper.assertTrue(t.isDead, "dead once past its age");
				helper.assertTrue(!LCETorex.serverTorexes(level).contains(t), "and dropped from the list");
				helper.succeed();
			});
		});
	}

	/** An explosion bound at the moment of spawning holds the cloud's age back, and releases it when it dies. */
	@GameTest(template = EMPTY)
	public static void aBoundExplosionHoldsTheServersClock(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ArmorStand stand = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(2, 1, 2));
		LCETorex.bindMe = stand;
		LCETorex t = LCETorex.statFacBale(level, stand.getX(), stand.getY(), stand.getZ(), 100F);
		helper.assertTrue(t.getBoundEntity() == stand, "bound to the young entity at the same spot");
		helper.assertTrue(LCETorex.bindMe == null, "bindMe is consumed");
		helper.assertTrue(!t.calculationFinished, "the cloud is waiting");
		helper.runAfterDelay(4, () -> {
			helper.assertTrue(t.ticksExisted >= 3, "ticked: " + t.ticksExisted);
			helper.assertTrue(t.getSubt() == t.ticksExisted, "while the explosion lives its age is held at zero: subt " + t.getSubt() + " of " + t.ticksExisted);
			stand.discard();
			helper.runAfterDelay(3, () -> {
				helper.assertTrue(t.calculationFinished, "released once it is gone");
				int held = t.getSubt();
				helper.assertTrue(held > 0 && held < t.ticksExisted, "with some time banked: " + held + " of " + t.ticksExisted);
				helper.runAfterDelay(2, () -> {
					helper.assertTrue(t.getSubt() == held, "and no more banked after");
					t.setDead();
					helper.succeed();
				});
			});
		});
	}

	/** A bound entity that is old, far or in another dimension is not bound to, and is forgotten. */
	@GameTest(template = EMPTY)
	public static void bindMeOnlyBindsAYoungNearbyEntity(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ArmorStand far = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(2, 1, 2));
		LCETorex.bindMe = far;
		LCETorex t = LCETorex.statFacBale(level, far.getX() + 5, far.getY(), far.getZ(), 100F);
		helper.assertTrue(t.getBoundEntity() == null && t.calculationFinished, "5 blocks away is not bound");
		helper.assertTrue(LCETorex.bindMe == far, "but it is kept for the next spawn: the near test does not clear it");
		t.setDead();

		far.tickCount = 10;
		LCETorex t2 = LCETorex.statFacBale(level, far.getX(), far.getY(), far.getZ(), 100F);
		helper.assertTrue(t2.getBoundEntity() == null, "ten ticks old is too old");
		helper.assertTrue(LCETorex.bindMe == null, "and an old one is forgotten");
		t2.setDead();
		far.discard();
		helper.succeed();
	}

	/** With the switch off nothing spawns and nothing ticks; its own batch because the switch is global. */
	@GameTest(template = EMPTY, batch = "torexOff")
	public static void withTheSwitchOffNothingSpawns(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
		int before = LCETorex.serverTorexes(level).size();
		LCETorex alive = LCETorex.statFacBale(level, at.getX(), at.getY(), at.getZ(), 25F);
		boolean was = LeafiaFeatures.torex;
		LeafiaFeatures.torex = false;
		try {
			helper.assertTrue(LCETorex.statFac(level, at.getX(), at.getY(), at.getZ(), 100F) == null, "statFac declines");
			helper.assertTrue(LCETorex.statFacBale(level, at.getX(), at.getY(), at.getZ(), 100F) == null, "statFacBale declines");
			helper.assertTrue(LCETorex.statFacEndo(level, at.getX(), at.getY(), at.getZ(), 100F) == null, "statFacEndo declines");
			helper.assertTrue(LCETorex.statFacDigamma(level, at.getX(), at.getY(), at.getZ(), 100F) == null, "statFacDigamma declines");
			helper.assertTrue(LCETorex.serverTorexes(level).size() == before + 1, "only the one made before the switch");
			int ticks = alive.ticksExisted;
			LCETorex.tickLevel(level);
			helper.assertTrue(alive.ticksExisted == ticks, "and the ticker does not age it");
		} finally {
			LeafiaFeatures.torex = was;
			alive.setDead();
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------------------ packets

	private static LeafiaBuf buf() {
		return new LeafiaBuf(Unpooled.buffer());
	}

	@GameTest(template = EMPTY)
	public static void theTorexPacketsOnTheWire(GameTestHelper helper) {
		helper.assertTrue(LeafiaPacketTypes.TOREX_SPAWN == 17 && LeafiaPacketTypes.TOREX_FINISH == 18 && LeafiaPacketTypes.SHAKECAM == 19,
				"the three ids are appended after the radio's 16");
		helper.assertTrue(LeafiaPacketTypes.idOf(new TorexPacket()) == 17, "a spawn packet names itself");
		helper.assertTrue(LeafiaPacketTypes.idOf(new TorexFinishPacket()) == 18, "a finish packet");
		helper.assertTrue(LeafiaPacketTypes.idOf(new ShakecamPacket()) == 19, "a shake packet");

		TorexPacket p = new TorexPacket();
		p.entityId = -42;
		p.uuid = new UUID(0x0123456789ABCDEFL, 0xFEDCBA9876543210L);
		p.x = 1234.5;
		p.y = -64.25;
		p.z = 99999.125;
		p.doWait = true;
		p.scale = 2.5F;
		p.type = 3;
		p.sound = false;
		p.range = 2123.9;
		LeafiaBuf b = buf();
		p.encode(b);
		// 32 + 128 + 3 * 64 + 1 + 32 + 8 + 1 + 64 bits
		helper.assertTrue(b.writerIndex == 458, "a spawn packet is 458 bits, was " + b.writerIndex);
		TorexPacket q = TorexPacket.read(b);
		helper.assertTrue(q.entityId == -42 && q.uuid.equals(p.uuid) && q.x == p.x && q.y == p.y && q.z == p.z, "ids and position");
		helper.assertTrue(q.doWait && q.scale == 2.5F && q.type == 3 && !q.sound && q.range == 2123.9, "flags, scale, palette, range");
		helper.assertTrue(b.readableBits() == 0, "nothing left over");
		LeafiaCustomPacket wire = LeafiaCustomPacket.__start(p);
		helper.assertTrue(wire != null && wire.bitLength() == 16 + 458, "framed: the 16-bit header and the body, " + (wire == null ? -1 : wire.bitLength()));

		TorexFinishPacket f = new TorexFinishPacket();
		f.uuid = p.uuid;
		LeafiaBuf fb = buf();
		f.encode(fb);
		helper.assertTrue(fb.writerIndex == 128, "a finish packet is a UUID: 128 bits, was " + fb.writerIndex);
		helper.assertTrue(TorexFinishPacket.read(fb).uuid.equals(p.uuid), "and it reads back");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theShakePacketOnTheWire(GameTestHelper helper) {
		ShakecamPacket without = new ShakecamPacket(new String[] {"intensity=9", "type=smooth"}, null);
		ShakecamPacket with = new ShakecamPacket(new String[] {"intensity=9", "type=smooth"}, new BlockPos(-1000, 64, 2_000_000));
		LeafiaBuf a = buf();
		without.encode(a);
		LeafiaBuf b = buf();
		with.encode(b);
		helper.assertTrue(b.writerIndex - a.writerIndex == 96, "a position is three ints: " + (b.writerIndex - a.writerIndex));
		ShakecamPacket ra = ShakecamPacket.read(a);
		helper.assertTrue(ra.pos == null && ra.params.length == 2 && ra.params[0].equals("intensity=9") && ra.params[1].equals("type=smooth"),
				"strings round-trip and absent means null");
		ShakecamPacket rb = ShakecamPacket.read(b);
		helper.assertTrue(new BlockPos(-1000, 64, 2_000_000).equals(rb.pos) && rb.params.length == 2, "and with a position");

		ShakecamPacket none = new ShakecamPacket(new String[0], null);
		LeafiaBuf c = buf();
		none.encode(c);
		helper.assertTrue(c.writerIndex == 8, "no parameters and no position is the count byte alone");
		helper.assertTrue(ShakecamPacket.read(c).params.length == 0, "reads back empty");
		helper.succeed();
	}

	// ------------------------------------------------------------------------------ the shake

	@GameTest(template = EMPTY)
	public static void theShakePresets(GameTestHelper helper) {
		helper.assertTrue(Preset.values().length == 5, "five presets");
		String[] names = {"RUPTURE", "EXPLOSION", "QUAKE", "PWR_NEAR", "PWR_FAR"};
		for(int i = 0; i < 5; i++) helper.assertTrue(Preset.values()[i].name().equals(names[i]), "preset " + i + " is " + names[i]);

		shakeSimple s = new shakeSimple(null, null, null);
		s.loadPreset(Preset.RUPTURE);
		helper.assertTrue(s.range == 25 && s.intensity == 8 && s.curve == 2 && s.speed == 8, "RUPTURE 25 8 2 8");
		s.loadPreset(Preset.EXPLOSION);
		helper.assertTrue(s.range == 62.5F && s.intensity == 12 && s.curve == 3 && s.speed == 8, "EXPLOSION 62.5 12 3 8");
		s.loadPreset(Preset.QUAKE);
		helper.assertTrue(s.range == 350 && s.intensity == 4 && s.curve == 3 && s.speed == 4, "QUAKE 350 4 3 4");

		shakeSimple near = new shakeSimple(null, null, null);
		near.loadPreset(Preset.PWR_NEAR);
		helper.assertTrue(near.intensity == 8 && near.speed == 8 && near.bloomDulling == 12, "PWR_NEAR doubles intensity and speed, bloom dulling x0.75");
		assertClose(helper, 5 / 1.5, near.duration, 1e-6, "and shortens it to a third less");
		helper.assertTrue(near.range == 25 && near.curve == 2, "range and curve untouched");
		shakeSimple far = new shakeSimple(null, null, null);
		far.loadPreset(Preset.PWR_FAR);
		helper.assertTrue(far.intensity == 2 && far.speed == 2 && far.blurDulling == 32 && far.duration == 5, "PWR_FAR halves them and doubles blur dulling");

		helper.assertTrue(Preset.RUPTURE.paramString.equals("r=25.0 i=8.0 c=2.0 s=8.0"), "the help text: " + Preset.RUPTURE.paramString);
		helper.assertTrue(Preset.PWR_FAR.paramString.equals("i/2 s/2 blurD*2"), "and the relative ones");

		// the defaults every instance starts from
		helper.assertTrue(s.blurExponent == 2 && new shakeSimple(null, null, null).blurDulling == 16 && s.bloomExponent == 6
				&& new shakeSimple(null, null, null).bloomDulling == 16, "blur and bloom defaults");
		helper.assertTrue(new shakeSimple(null, null, null).ease == Ease.EXPO && new shakeSimple(null, null, null).direction == Direction.O, "expo out");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aShakesStrengthFallsWithDistanceAndTime(GameTestHelper helper) {
		shakeSimple s = new shakeSimple(5F, null, null);
		helper.assertTrue(s.getCalculatedIntensity(0) == 4, "full strength at the source");
		assertClose(helper, 4 * 0.25, s.getCalculatedIntensity(12.5), 1e-12, "half the range: (1/2)^curve of it");
		helper.assertTrue(s.getCalculatedIntensity(25) == 0 && s.getCalculatedIntensity(100) == 0, "nothing at and past the range");
		s.setCurve(1).setIntensity(10).setRange(100);
		assertClose(helper, 7.5, s.getCalculatedIntensity(25), 1e-12, "a linear one");

		helper.assertTrue(s.getTimeMultiplier(0) == 1, "full at the start");
		assertClose(helper, 0.5, s.getTimeMultiplier(2.5), 1e-12, "half at half the duration");
		assertClose(helper, 0.0, s.getTimeMultiplier(5), 1e-12, "none at the end");
		assertClose(helper, 0.0, s.getTimeMultiplier(50), 1e-12, "and clamped there");
		// the ease is inert, which is upstream's (D-1004): any curve gives the same
		s.ease = Ease.QUAD;
		s.direction = Direction.I;
		assertClose(helper, 0.5, s.getTimeMultiplier(2.5), 1e-12, "a quad-in ease changes nothing");
		s.easeInstance = null;
		helper.assertTrue(s.getTimeMultiplier(4.9) == 1, "without an ease it never fades");

		// a simple shake lasts exactly its duration in ticks
		shakeSimple t = new shakeSimple(1F, null, null);
		for(int i = 1; i < 20; i++) helper.assertTrue(t.tick(), "still going at tick " + i);
		helper.assertTrue(!t.tick(), "over at tick 20: one second");
		helper.succeed();
	}

	/** The simple shake writes a translation of 0.04 * 2 * jitter * intensity * fade, and returns four times the strength. */
	@GameTest(template = EMPTY)
	public static void theSimpleShakeTranslates(GameTestHelper helper) {
		shakeSimple s = new shakeSimple(5F, null, null);
		s.configure(null, 10F, 1F, null);
		s.tick(); // jitter is drawn, one tick old
		ShakeFrame frame = new ShakeFrame();
		double n = s.render(0, frame);
		double fade = 1 - (1 / 20.0) / 5;
		assertClose(helper, 10 * fade * 4, n, 1e-9, "returns intensity * fade * 4");
		Vector3d t = frame.translation();
		helper.assertTrue(Math.abs(t.x) <= 0.04 * 0.5 * 10 * fade * 2 + 1e-9 && Math.abs(t.y) <= 0.04 * 0.5 * 10 * fade * 2 + 1e-9,
				"within 0.04 * 2 * 0.5 * intensity * fade: " + t);
		helper.assertTrue(t.z == 0, "never in depth");
		// a far-away one is nothing
		ShakeFrame none = new ShakeFrame();
		helper.assertTrue(s.render(25, none) == 0 && none.isIdentity(), "beyond its range it writes nothing and returns 0");
		helper.succeed();
	}

	/** The smooth shake: noise of real time on three axes, clock injected. */
	@GameTest(template = EMPTY)
	public static void theSmoothShakeRotates(GameTestHelper helper) {
		SimplexNoise saved = LeafiaShakecam.noise;
		LeafiaShakecam.noise = new SimplexNoise(RandomSource.create(99L));
		try {
			shakeSmooth s = new shakeSmooth(5F, null, null);
			long t0 = 7_000L;
			s.startClock(t0);
			ShakeFrame frame = new ShakeFrame();
			double n = s.renderAt(0, frame, t0 + 250);
			assertClose(helper, 0.25, s.timer, 1e-12, "250 ms later the timer reads a quarter second");
			double fade = 1 - 0.25 / 5;
			assertClose(helper, 4 * fade * 4, n, 1e-9, "returns intensity * fade * speed");

			SimplexNoise noise = LeafiaShakecam.noise;
			double k = 4 * fade;
			Matrix4d expect = new Matrix4d()
					.rotate(Math.toRadians(noise.getValue(0.25 * 4, 0) * k), 1, 0, 0)
					.rotate(Math.toRadians(noise.getValue(0, 0.25 * 4) * k), 0, 1, 0)
					.rotate(Math.toRadians(noise.getValue(0.25 * 4, 0.25 * 4) * k), 0, 0, 1);
			helper.assertTrue(frame.matrix.equals(expect, 1e-12), "x, y then z, each its own noise of the timer times the speed");

			// the timer wraps through the 10 s clock: 9.9 s to 0.1 s is 0.2 s, not minus 9.8
			shakeSmooth w = new shakeSmooth(5F, null, null).startClock(9_900L);
			w.renderAt(0, new ShakeFrame(), 10_100L);
			assertClose(helper, 0.2, w.timer, 1e-12, "the clock wraps at ten seconds");

			// ends when the timer passes the duration
			shakeSmooth e = new shakeSmooth(1F, null, null);
			helper.assertTrue(e.tick(), "alive at 0");
			e.timer = 1.0;
			helper.assertTrue(!e.tick(), "over once the timer reaches the duration");
			helper.succeed();
		} finally {
			LeafiaShakecam.noise = saved;
		}
	}

	/** The blur and bloom the "nuclear" shader will read, from a shake at known distances. */
	@GameTest(template = EMPTY)
	public static void theBlurAndBloomSums(GameTestHelper helper) {
		List<LeafiaShakecam.shakeInstance> saved = new ArrayList<>(LeafiaShakecam.instances);
		LeafiaShakecam.instances.clear();
		try {
			BlockPos source = new BlockPos(10, 20, 30);
			shakeSimple s = new shakeSimple(5F, null, null);
			LeafiaShakecam._addShake(source, s);
			helper.assertTrue(LeafiaShakecam.instances.contains(s) && s.position.equals(source), "added, with its position");

			Vec3 here = new Vec3(10.5, 20.5, 30.5);
			ShakeFrame f = new ShakeFrame();
			LeafiaShakecam.shakeCam(here, f);
			// n = 4 * 1 * 4 = 16: blur (16/16)^2 * 0.005; bloom (16/16/5)^6 * 0.25
			assertClose(helper, 0.005, LeafiaShakecam.blurSum, 1e-9, "blur at the source");
			assertClose(helper, Math.pow(0.2, 6) * 0.25, LeafiaShakecam.bloomSum, 1e-9, "bloom at the source");
			// 12.5 blocks along x: intensity 4 * 0.5^2 = 1, n = 4
			LeafiaShakecam.shakeCam(new Vec3(23, 20.5, 30.5), new ShakeFrame());
			assertClose(helper, Math.pow(4 / 16.0, 2) * 0.005, LeafiaShakecam.blurSum, 1e-9, "blur at half range");
			LeafiaShakecam.instances.clear();
			LeafiaShakecam.shakeCam(here, new ShakeFrame());
			helper.assertTrue(LeafiaShakecam.blurSum == 0 && LeafiaShakecam.bloomSum == 0, "nothing left, nothing summed");

			// a shake with no position is the same anywhere
			shakeSimple anywhere = new shakeSimple(5F, null, null);
			LeafiaShakecam._addShake(null, anywhere);
			LeafiaShakecam.shakeCam(new Vec3(1e6, 0, 1e6), new ShakeFrame());
			assertClose(helper, 0.005, LeafiaShakecam.blurSum, 1e-9, "full strength a million blocks away");

			// localTick drops the finished
			LeafiaShakecam.instances.clear();
			shakeSimple brief = new shakeSimple(0.05F, null, null);
			LeafiaShakecam._addShake(null, brief);
			LeafiaShakecam.localTick();
			helper.assertTrue(LeafiaShakecam.instances.size() == 1, "one tick in: 0.05 s is exactly 1 tick, the shake is not over until it is reached");
			LeafiaShakecam.localTick();
			helper.assertTrue(LeafiaShakecam.instances.isEmpty(), "gone after its second");
			helper.succeed();
		} finally {
			LeafiaShakecam.instances.clear();
			LeafiaShakecam.instances.addAll(saved);
			LeafiaShakecam.blurSum = 0;
			LeafiaShakecam.bloomSum = 0;
		}
	}

	/** The frame is applied to the camera as three angles, and a rotation of the frame is a rotation of the angle. */
	@GameTest(template = EMPTY)
	public static void theCameraAnglesAbsorbTheFrame(GameTestHelper helper) {
		float[][] poses = {{0, 0, 0}, {30, 10, 0}, {-120, -45, 5}, {200, 70, -20}};
		for(float[] pose : poses) {
			float[] same = LeafiaShakecam.cameraAngles(pose[0], pose[1], pose[2], new ShakeFrame());
			assertClose(helper, 0, angleDiff(pose[0], same[0]), 1e-2, "an empty frame leaves the yaw of " + pose[0]);
			assertClose(helper, pose[1], same[1], 1e-2, "and the pitch of " + pose[1]);
			assertClose(helper, pose[2], same[2], 1e-2, "and the roll of " + pose[2]);
		}
		ShakeFrame roll = new ShakeFrame();
		roll.rotate(10, 0, 0, 1);
		float[] r = LeafiaShakecam.cameraAngles(0, 0, 0, roll);
		assertClose(helper, 10, r[2], 1e-2, "a z shake of 10 is 10 of roll");
		assertClose(helper, 0, r[1], 1e-2, "and no pitch");
		ShakeFrame pitch = new ShakeFrame();
		pitch.rotate(5, 1, 0, 0);
		float[] p = LeafiaShakecam.cameraAngles(0, 0, 0, pitch);
		assertClose(helper, 5, p[1], 1e-2, "an x shake of 5 is 5 of pitch");
		ShakeFrame yaw = new ShakeFrame();
		yaw.rotate(7, 0, 1, 0);
		float[] y = LeafiaShakecam.cameraAngles(0, 0, 0, yaw);
		assertClose(helper, 7, angleDiff(y[0], 0), 1e-2, "a y shake of 7 is 7 of yaw");

		// a translation is turned into a small turn at the nominal depth
		ShakeFrame shift = new ShakeFrame();
		shift.translate(0.8, 0, 0);
		float[] s = LeafiaShakecam.cameraAngles(0, 0, 0, shift);
		assertClose(helper, -Math.toDegrees(Math.atan2(0.8, LeafiaShakecam.SIMPLE_SHAKE_DEPTH)), angleDiff(s[0], 0), 1e-2, "0.8 sideways is atan(0.1) of yaw, against the shift");
		helper.assertTrue(LeafiaShakecam.SIMPLE_SHAKE_DEPTH == 8.0, "at a nominal depth of eight blocks");
		helper.succeed();
	}

	private static double angleDiff(double a, double b) {
		double d = (a - b) % 360;
		if(d > 180) d -= 360;
		if(d < -180) d += 360;
		return d;
	}

	// ------------------------------------------------------------------------------ the parameter language

	@GameTest(template = EMPTY)
	public static void theShakeParameterLanguage(GameTestHelper helper) {
		Parsed none = LeafiaShakecam.parse(new String[0]);
		helper.assertTrue(none.shake() instanceof shakeSimple && none.warnings().isEmpty(), "no parameters: a simple shake with the defaults");
		helper.assertTrue(none.shake().range == 25 && none.shake().intensity == 4 && none.shake().curve == 2
				&& none.shake().speed == 4 && none.shake().duration == 5, "range 25 intensity 4 curve 2 speed 4 duration 5");

		var a = LeafiaShakecam.parse(new String[] {"intensity=9", "range=30", "duration=15"}).shake();
		helper.assertTrue(a.intensity == 9 && a.range == 30 && a.duration == 15 && a.curve == 2, "set");
		var b = LeafiaShakecam.parse(new String[] {"type=smooth", "preset=RUPTURE", "intensity*2"});
		helper.assertTrue(b.shake() instanceof shakeSmooth, "smooth");
		helper.assertTrue(b.shake().range == 25 && b.shake().intensity == 16 && b.shake().curve == 2 && b.shake().speed == 8, "a preset, then doubled");
		var c = LeafiaShakecam.parse(new String[] {"preset=EXPLOSION", "range=10"}).shake();
		helper.assertTrue(c.range == 10 && c.intensity == 12, "an explicit value beats the preset, whatever the order");
		var d = LeafiaShakecam.parse(new String[] {"range+5", "range*2", "speed/2", "curve-1"}).shake();
		helper.assertTrue(d.range == 55 && d.speed == 2 && d.curve == 1, "multiply, then add: (25 * 2) + 5, 4 / 2, 2 - 1");
		var e = LeafiaShakecam.parse(new String[] {"blurDulling=8", "blurExponent=3", "bloomDulling=4", "bloomExponent=2"}).shake();
		helper.assertTrue(e.blurDulling == 8 && e.blurExponent == 3 && e.bloomDulling == 4 && e.bloomExponent == 2, "the shader inputs");
		var f = LeafiaShakecam.parse(new String[] {"ease=quadIn"}).shake();
		helper.assertTrue(f.ease == Ease.QUAD && f.direction == Direction.I, "an ease");
		var g = LeafiaShakecam.parse(new String[] {"ease=none"}).shake();
		helper.assertTrue(g.easeInstance == null, "ease=none removes it");

		Parsed bad = LeafiaShakecam.parse(new String[] {"type=wobbly"});
		helper.assertTrue(bad.shake() == null && bad.warnings().size() == 1 && bad.warnings().get(0).equals("Invalid type: wobbly"), "a bad type is no shake");
		Parsed num = LeafiaShakecam.parse(new String[] {"range=abc", "curve%2", "speed="});
		helper.assertTrue(num.shake() != null && num.warnings().size() == 3, "bad parameters are warnings, the shake survives: " + num.warnings());
		helper.assertTrue(num.warnings().get(0).equals("'abc' is not a valid number"), "the first is the number: " + num.warnings().get(0));
		helper.assertTrue(num.warnings().get(1).equals("Invalid operator: %"), "then the operator: " + num.warnings().get(1));
		helper.assertTrue(num.warnings().get(2).equals("Malformed numeric parameter!"), "and a bare 'speed=' is malformed: " + num.warnings());
		Parsed preset = LeafiaShakecam.parse(new String[] {"preset=NOPE"});
		helper.assertTrue(preset.shake() != null && preset.warnings().get(0).equals("Invalid preset: NOPE"), "a bad preset is a warning");
		Parsed nan = LeafiaShakecam.parse(new String[] {"range=NaN"});
		helper.assertTrue(nan.warnings().size() == 1, "NaN is not a number, as CommandBase.parseDouble had it");
		Parsed op = LeafiaShakecam.parse(new String[] {"range%5"});
		helper.assertTrue(op.warnings().size() == 1 && op.warnings().get(0).equals("Invalid operator: %"), "an operator that is not one");
		helper.succeed();
	}

	// ------------------------------------------------------------------------------ the command

	private static final class Sink implements CommandSource {
		final List<String> messages = new ArrayList<>();

		@Override
		public void sendSystemMessage(Component component) {
			messages.add(component.getString());
		}

		@Override
		public boolean acceptsSuccess() {
			return true;
		}

		@Override
		public boolean acceptsFailure() {
			return true;
		}

		@Override
		public boolean shouldInformAdmins() {
			return false;
		}
	}

	private static CommandSourceStack source(GameTestHelper helper, Sink sink, int permission) {
		ServerLevel level = helper.getLevel();
		return new CommandSourceStack(sink, Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 1, 1))), Vec2.ZERO, level, permission, "test",
				Component.literal("test"), level.getServer(), null);
	}

	private static CommandDispatcher<CommandSourceStack> dispatcher() {
		CommandDispatcher<CommandSourceStack> d = new CommandDispatcher<>();
		CommandLeaf.register(d);
		return d;
	}

	@GameTest(template = EMPTY)
	public static void theTorexCommandSpawnsEachPalette(GameTestHelper helper) throws CommandSyntaxException {
		ServerLevel level = helper.getLevel();
		CommandDispatcher<CommandSourceStack> d = dispatcher();
		Sink sink = new Sink();
		CommandSourceStack src = source(helper, sink, 2);
		BlockPos base = helper.absolutePos(new BlockPos(1, 1, 1));
		List<LCETorex> before = new ArrayList<>(LCETorex.serverTorexes(level));
		String xyz = base.getX() + " " + base.getY() + " " + base.getZ();

		d.execute("hbmleaf torex statFac " + xyz + " 100", src);
		d.execute("hbmleaf torex statFacBale " + xyz + " 100 false", src);
		d.execute("hbmleaf torex statFacEndo " + xyz + " 200", src);
		d.execute("hbmleaf torex statFacDigamma " + xyz + " 100000", src);
		List<LCETorex> made = new ArrayList<>(LCETorex.serverTorexes(level));
		made.removeAll(before);
		try {
			helper.assertTrue(made.size() == 4, "four spawned, got " + made.size());
			LCETorex nuke = made.get(0), bale = made.get(1), endo = made.get(2), digamma = made.get(3);
			helper.assertTrue(nuke.getType() == 0 && Math.abs(nuke.getScale() - 0.794328) < 1e-5 && nuke.sound, "statFac: nuke, bent, with sound");
			helper.assertTrue(bale.getType() == 1 && bale.getScale() == 1.0 && !bale.sound, "statFacBale 100 false: balefire, scale 1, silent");
			helper.assertTrue(endo.getType() == 3 && endo.getScale() == 2.0, "statFacEndo 200: endothermic, scale 2");
			helper.assertTrue(digamma.getType() == 2 && digamma.getScale() == 5.0, "statFacDigamma 100000: digamma, capped at 5");
			helper.assertTrue(nuke.initPosX == base.getX() && nuke.initPosY == base.getY() && nuke.initPosZ == base.getZ(), "at the block's corner, not its centre");
			helper.assertTrue(sink.messages.size() == 4 && sink.messages.get(0).startsWith("Summoned a torex"), "each says so: " + sink.messages);
		} finally {
			for(LCETorex t : made) t.setDead();
		}

		// relative coordinates come from the source: ~ ~ ~ is the source's block, 1 above for ~ ~1 ~
		int n = LCETorex.serverTorexes(level).size();
		d.execute("hbmleaf torex statFacBale ~ ~1 ~ 50", src);
		LCETorex rel = LCETorex.serverTorexes(level).get(n);
		helper.assertTrue(rel.initPosX == base.getX() && rel.initPosY == base.getY() + 1 && rel.initPosZ == base.getZ(), "~ ~1 ~ is relative to the source");
		rel.setDead();
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theTorexCommandRejectsWhatItShould(GameTestHelper helper) {
		CommandDispatcher<CommandSourceStack> d = dispatcher();
		CommandSourceStack op = source(helper, new Sink(), 2);
		String[] bad = {
				"hbmleaf torex statFac 1 2 3", // no scale
				"hbmleaf torex statFac 1 2 100", // two coordinates
				"hbmleaf torex statFac 1 2 3 abc",
				"hbmleaf torex statFac 1 2 3 100 maybe",
				"hbmleaf torex statFacAtomic 1 2 3 100",
				"hbmleaf torex",
				"hbmleaf unknown",
		};
		for(String command : bad) {
			boolean threw = false;
			try {
				d.execute(command, op);
			} catch(CommandSyntaxException e) {
				threw = true;
			}
			helper.assertTrue(threw, "'" + command + "' should not parse");
		}
		// permission level 2: a level-1 source cannot even see it
		boolean threw = false;
		try {
			d.execute("hbmleaf torex statFac 1 2 3 100", source(helper, new Sink(), 1));
		} catch(CommandSyntaxException e) {
			threw = true;
		}
		helper.assertTrue(threw, "a permission level of 1 is refused");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theShakeCommandHelpAndSuggestions(GameTestHelper helper) throws CommandSyntaxException {
		CommandDispatcher<CommandSourceStack> d = dispatcher();
		Sink sink = new Sink();
		d.execute("hbmleaf shake ?", source(helper, sink, 2));
		helper.assertTrue(sink.messages.get(0).equals("Many examples:"), "starts with examples: " + sink.messages.get(0));
		helper.assertTrue(sink.messages.contains("  /hbmleaf shake @a type=smooth preset=RUPTURE intensity*2"), "carries upstream's examples");
		helper.assertTrue(sink.messages.contains("  RUPTURE (r=25.0 i=8.0 c=2.0 s=8.0)"), "lists the presets with their numbers");
		helper.assertTrue(sink.messages.contains("  PWR_NEAR (i*2 s*2 d/1.5 bloomD*0.75)"), "and the relative ones");
		helper.assertTrue(sink.messages.contains("  range=25 intensity=4 curve=2 speed=4 duration=5 ease=expoOut"), "and the defaults");
		helper.assertTrue(sink.messages.contains("Use /hbmleaf eases to see all available eases."), "and points at the eases");

		Sink eases = new Sink();
		d.execute("hbmleaf eases", source(helper, eases, 2));
		helper.assertTrue(eases.messages.size() == 1 + 37 && eases.messages.get(1).equals("  linear"), "the ease list: 37 and a header, " + eases.messages.size());

		List<String> params = CommandLeaf.paramSuggestions();
		// types 2 + presets 5 + 9 numerics * 5 operators + eases 37 + none 1
		helper.assertTrue(params.size() == 2 + 5 + 45 + 37 + 1, "90 suggestions, got " + params.size());
		helper.assertTrue(params.contains("type=smooth") && params.contains("preset=QUAKE") && params.contains("bloomExponent/")
				&& params.contains("ease=bounceOut") && params.contains("ease=none"), "and the expected ones");

		helper.assertTrue(java.util.Arrays.equals(CommandLeaf.splitParams("  a=1   b=2 "), new String[] {"a=1", "b=2"}), "params split on any run of spaces");
		helper.assertTrue(CommandLeaf.splitParams("   ").length == 0 && CommandLeaf.splitParams("").length == 0, "and empty is none");

		// without any players, the selector fails, as 1.12.2's getPlayers did
		boolean threw = false;
		try {
			d.execute("hbmleaf shake @a[name=nobody] ~ ~ ~ intensity=3", source(helper, new Sink(), 2));
		} catch(CommandSyntaxException e) {
			threw = true;
		}
		helper.assertTrue(threw, "no players found is an error");
		helper.succeed();
	}

	/**
	 * A mock player in the level but not in the server's player list: a mock has no connection, and
	 * anything that broadcasts (the torex's finish packet does) would otherwise try to send to it and
	 * take the server down. The same detachment {@code LeafiaAdvisorTests} makes.
	 */
	@SuppressWarnings("deprecation")
	private static ServerPlayer mockPlayer(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			var field = net.minecraft.server.players.PlayerList.class.getDeclaredField("players");
			field.setAccessible(true);
			((List<?>) field.get(helper.getLevel().getServer().getPlayerList())).remove(player);
		} catch(ReflectiveOperationException e) {
			throw new IllegalStateException("could not detach the mock player", e);
		}
		return player;
	}

	@GameTest(template = EMPTY)
	public static void shakePlayersNearSelectsByDistance(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = mockPlayer(helper);
		try {
			BlockPos at = helper.absolutePos(new BlockPos(2, 1, 2));
			player.moveTo(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5);
			helper.assertTrue(LeafiaShakecam.playersNear(level, at, 1).contains(player), "a player at the spot is within 1");
			helper.assertTrue(!LeafiaShakecam.playersNear(level, at.offset(10, 0, 0), 9.9).contains(player), "ten blocks away is outside 9.9");
			helper.assertTrue(LeafiaShakecam.playersNear(level, at.offset(10, 0, 0), 10).contains(player), "and inside 10");
		} finally {
			player.discard();
		}
		helper.succeed();
	}
}
