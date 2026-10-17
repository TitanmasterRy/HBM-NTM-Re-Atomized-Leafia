package com.leafia.gametest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;

import com.hbm.lib.RefStrings;
import com.leafia.client.particle.DfcFlash;
import com.leafia.client.particle.LeafiaFireParticle;
import com.leafia.client.particle.LeafiaLavaParticle;
import com.leafia.client.particle.LeafiaParticleMath;
import com.leafia.client.particle.SphereMesh;
import com.leafia.contents.effects.folkvangr.particles.ParticleFleijaAntischrabA;
import com.leafia.contents.effects.folkvangr.particles.ParticleFleijaAntischrabB;
import com.leafia.contents.machines.powercores.dfc.particles.ParticleDFC;
import com.leafia.contents.machines.powercores.dfc.particles.ParticleNuke;
import com.leafia.contents.machines.powercores.dfc.particles.ParticlePulserCharge;
import com.leafia.contents.machines.powercores.dfc.particles.ParticlePulserOvercharge;
import com.leafia.contents.machines.reactors.rbmk.effects.ParticleRBMKJet;
import com.leafia.contents.worldgen.biomes.effects.ParticleCloudSmall;
import com.leafia.dev.math.FiaMatrix;
import com.leafia.dev.math.FiaMatrix.RotationOrder;
import com.leafia.lib.LeafiaRef;
import com.leafia.unsorted.ParticleArcWelder;
import com.leafia.unsorted.ParticleBasicSmoke;
import com.leafia.unsorted.ParticleSmokeShockwave;
import com.leafia.unsorted.ParticleFlash;
import com.leafia.unsorted.ParticleRainBlood;
import com.leafia.unsorted.ParticleRedstoneLight;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The addon's particles (D-1075), as far as a server can see them.
 *
 * <p>Every particle is a client class, and a dedicated server cannot link one: not to construct it,
 * and not to call a static on it either, because linking the class links its client-only supertype.
 * So nothing here touches a particle's behaviour in a world. What a server <em>can</em> read is
 * what javac inlines: a {@code static final} primitive initialised from a literal is copied into
 * the caller's bytecode, and the particle class is never loaded. That is how the constants below are
 * pinned. The arithmetic itself lives in {@link LeafiaParticleMath}, which has no client import and is
 * called by the particles, and the sphere's geometry in {@link SphereMesh}, which is a plain OBJ
 * reader; both run here directly.
 *
 * <p>What this cannot check: that anything draws, or draws the right way up. That is the screenshot
 * D-1075 records.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaParticleTests {

	private static final String EMPTY = "empty";

	private static void assertClose(GameTestHelper helper, double expected, double actual, String what) {
		helper.assertTrue(Math.abs(expected - actual) < 1.0e-5D, what + ": expected " + expected + ", was " + actual);
	}

	private static void assertEq(GameTestHelper helper, long expected, long actual, String what) {
		helper.assertTrue(expected == actual, what + ": expected " + expected + ", was " + actual);
	}

	/**
	 * A resource from whichever jar holds it: the base mod's assets are not on the addon's own
	 * classpath root, so a path under {@code assets/hbm} is looked up through a base-mod class.
	 */
	private static InputStream open(String path) {
		Class<?> owner = path.startsWith("/assets/hbm/") ? RefStrings.class : LeafiaParticleTests.class;
		return owner.getResourceAsStream(path);
	}

	/** The width and height out of a PNG's header. */
	private static int[] pngSize(InputStream in) throws IOException {
		byte[] head = in.readNBytes(24);
		ByteBuffer b = ByteBuffer.wrap(head);
		return new int[] { b.getInt(16), b.getInt(20) };
	}

	/**
	 * The seven textures the addon's particles bind are shipped, at the sizes upstream ships them, and
	 * the four (D-1090: now the five, with the smoke's {@code contrail.png}) the base mod supplies are there to be found. A particle with no texture draws the
	 * missing-texture checkerboard and logs nothing, so the file is the thing to pin. The strip
	 * sizes matter beyond existing: {@code bloodrain.png} is four 8-wide frames and the code
	 * reads a quarter of its width.
	 */
	@GameTest(template = EMPTY)
	public static void everyTextureAParticleBindsIsShipped(GameTestHelper helper) {
		Object[][] addon = {
				{ "/assets/leafia/textures/particle/glow.png", 64, 64 },
				{ "/assets/leafia/textures/particle/overcharge.png", 128, 128 },
				{ "/assets/leafia/textures/particle/leafia/bloodrain.png", 32, 8 },
				{ "/assets/leafia/textures/particle/leafia/particle_balefire.png", 8, 8 },
				{ "/assets/leafia/textures/particle/leafia/particle_balelava.png", 8, 8 },
				{ "/assets/leafia/textures/particle/leafia/particle_fire_k.png", 8, 8 },
				{ "/assets/leafia/textures/particle/leafia/particle_lava_k.png", 8, 8 },
				// the base mod's, which the addon binds by name
				{ "/assets/hbm/textures/particle/white.png", 8, 8 },
				{ "/assets/hbm/textures/particle/particle_base.png", 8, 8 },
				{ "/assets/hbm/textures/particle/rbmk_jet_steam.png", 640, 64 },
				{ "/assets/hbm/textures/particle/contrail.png", 8, 8 },
		};
		for(Object[] entry : addon) {
			String path = (String) entry[0];
			try(InputStream in = open(path)) {
				helper.assertTrue(in != null, path + " is missing");
				int[] size = pngSize(in);
				helper.assertTrue(size[0] == (int) entry[1] && size[1] == (int) entry[2],
						path + " is " + size[0] + "x" + size[1] + ", upstream's is " + entry[1] + "x" + entry[2]);
			} catch(IOException e) {
				helper.fail(path + ": " + e);
			}
		}
		helper.succeed();
	}

	/**
	 * The sphere three particles draw is read from the base mod's OBJ rather than baked: an icosphere
	 * of 42 vertices and 80 triangles, diameter 1, so a scale is a diameter. A face that is a quad
	 * becomes two triangles, and a negative index counts back from the end.
	 */
	@GameTest(template = EMPTY)
	public static void theSphereIsReadFromTheBaseModsObj(GameTestHelper helper) throws IOException {
		try(InputStream in = open("/assets/hbm/models/sphere_ruv.obj")) {
			helper.assertTrue(in != null, "sphere_ruv.obj is missing");
			SphereMesh mesh = SphereMesh.parse(in);
			assertEq(helper, 42, mesh.vertexCount(), "vertices");
			assertEq(helper, 80, mesh.triangleCount(), "triangles");
			float max = 0;
			for(float p : mesh.positions) max = Math.max(max, Math.abs(p));
			assertClose(helper, 0.5, max, "the farthest coordinate, so the diameter is 1");
			for(int index : mesh.triangles)
				helper.assertTrue(index >= 0 && index < 42, "a triangle names vertex " + index);
		}

		String quad = "v 0 0 0\nv 1 0 0\nv 1 1 0\nv 0 1 0\nf 1/1/1 2/2/1 3/3/1 4/4/1\nf -4 -3 -2\n";
		SphereMesh m = SphereMesh.parse(new ByteArrayInputStream(quad.getBytes(StandardCharsets.UTF_8)));
		assertEq(helper, 3, m.triangleCount(), "a quad is two triangles and a triangle one");
		helper.assertTrue(m.triangles[0] == 0 && m.triangles[1] == 1 && m.triangles[2] == 2, "the first fan triangle is 1 2 3");
		helper.assertTrue(m.triangles[3] == 0 && m.triangles[4] == 2 && m.triangles[5] == 3, "the second is 1 3 4");
		helper.assertTrue(m.triangles[6] == 0 && m.triangles[7] == 1 && m.triangles[8] == 2, "-4 -3 -2 are the first three");
		helper.succeed();
	}

	/**
	 * How long each particle lives is a function of one random draw, so the ends of its range are
	 * pinned rather than sampled: the fire particles' 12 to 44, the lava ones' 16 to 80. A fire
	 * particle shrinks to exactly half size by the end of its life.
	 */
	@GameTest(template = EMPTY)
	public static void fireAndLavaLifetimesSpanUpstreamsRange(GameTestHelper helper) {
		assertEq(helper, 44, LeafiaParticleMath.fireLifetime(0.0), "the longest fire life");
		assertEq(helper, 17, LeafiaParticleMath.fireLifetime(0.5), "a fire life at a draw of one half");
		assertEq(helper, 12, LeafiaParticleMath.fireLifetime(0.999999), "the shortest fire life");
		assertEq(helper, 80, LeafiaParticleMath.lavaLifetime(0.0), "the longest lava life");
		assertEq(helper, 26, LeafiaParticleMath.lavaLifetime(0.5), "a lava life at a draw of one half");
		assertEq(helper, 16, LeafiaParticleMath.lavaLifetime(0.999999), "the shortest lava life");
		assertClose(helper, 1.0, LeafiaParticleMath.fireShrink(0F), "full size at birth");
		assertClose(helper, 0.875, LeafiaParticleMath.fireShrink(0.5F), "seven eighths at half life");
		assertClose(helper, 0.5, LeafiaParticleMath.fireShrink(1F), "half size at the end");
		assertClose(helper, 0.08, LeafiaFireParticle.SCALE, "the flame's size draw");
		assertClose(helper, 0.05, LeafiaFireParticle.JITTER, "the flame's jitter");
		assertClose(helper, 0.08, LeafiaLavaParticle.SCALE, "the lava spark's size draw");
		assertClose(helper, 0.03, LeafiaLavaParticle.GRAVITY, "the lava spark's gravity");
		assertClose(helper, 0.999F, LeafiaLavaParticle.DRAG, "its drag, the float widened");
		assertClose(helper, 0.7F, LeafiaLavaParticle.GROUND_DRAG, "its ground drag, the float widened");
		helper.succeed();
	}

	/**
	 * The puff's growth, the welder's fade, the blood strip's frame, and the jet's frame and alpha:
	 * every one an integer or a clamp whose ends are easy to get wrong by one.
	 */
	@GameTest(template = EMPTY)
	public static void framesFadesAndGrowthStepWhereUpstreamStepsThem(GameTestHelper helper) {
		assertClose(helper, 0.0, LeafiaParticleMath.cloudGrowth(0, 0F, 100), "a puff is nothing at birth");
		assertClose(helper, 0.5, LeafiaParticleMath.cloudGrowth(1, 0F, 64), "half grown a 64th of the way");
		assertClose(helper, 1.0, LeafiaParticleMath.cloudGrowth(3, 0F, 64), "and capped at full");
		assertClose(helper, 0.5, LeafiaParticleMath.arcWelderAlpha(0, 40), "the welder starts at one half");
		assertClose(helper, 0.375, LeafiaParticleMath.arcWelderAlpha(20, 40), "and has lost a quarter of that by half life");
		assertClose(helper, 0.0, LeafiaParticleMath.arcWelderAlpha(40, 40), "and is nothing at the end");

		double[] blood3 = { 0, 0.25, 0.5 };
		for(int age = 0; age < 3; age++)
			assertClose(helper, blood3[age], LeafiaParticleMath.bloodFrameU(age, 3), "blood frame at age " + age + " of 3");
		assertClose(helper, 0.75, LeafiaParticleMath.bloodFrameU(3, 4), "the fourth frame of a four-tick drop");

		assertEq(helper, 0, LeafiaParticleMath.jetFrame(0, 100), "jet frame at birth");
		assertEq(helper, 10, LeafiaParticleMath.jetFrame(50, 100), "jet frame at half life");
		assertEq(helper, 20, LeafiaParticleMath.jetFrame(100, 100), "jet frame at the clamped end, one past the strip");
		assertClose(helper, 0.0, LeafiaParticleMath.jetAlpha(0, 100), "the jet fades in from nothing");
		assertClose(helper, 0.25, LeafiaParticleMath.jetAlpha(10, 100), "a half-height ramp at ten ticks, halved");
		assertClose(helper, 0.5, LeafiaParticleMath.jetAlpha(50, 100), "it holds at one half");
		assertClose(helper, 0.25, LeafiaParticleMath.jetAlpha(90, 100), "and fades out over the last twenty");
		assertClose(helper, 0.45, LeafiaParticleMath.jetAlpha(12, 30), "a thirty-tick jet's ramps overlap and the later one wins");
		helper.succeed();
	}

	/**
	 * The Folkvangr burst: it fades from age ten to nothing at fifty, the ring grows by
	 * {@code 2 / r^0.35} from 1, and the angle step is an integer, so it is 20 degrees at birth and
	 * stops at all by age 100.
	 */
	@GameTest(template = EMPTY)
	public static void theFolkvangrRingSpiralsAndFades(GameTestHelper helper) {
		assertClose(helper, 1.0, LeafiaParticleMath.fleijaAlpha(10), "full alpha when the fade begins");
		assertClose(helper, 0.25, LeafiaParticleMath.fleijaAlpha(30), "a quarter at the middle of it");
		assertClose(helper, 0.0, LeafiaParticleMath.fleijaAlpha(50), "gone at fifty");
		assertClose(helper, 3.0, LeafiaParticleMath.fleijaNextPosition(1.0), "the first step outward");
		assertClose(helper, 3 + 2 / Math.pow(3, 0.35), LeafiaParticleMath.fleijaNextPosition(3.0), "the second");
		assertEq(helper, 20, LeafiaParticleMath.fleijaNextAngle(0, 0), "the angle step at age 0");
		assertEq(helper, 10, LeafiaParticleMath.fleijaNextAngle(0, 3), "and at age 3");
		assertEq(helper, 0, LeafiaParticleMath.fleijaNextAngle(0, 100), "and none from age 100");
		assertEq(helper, 50, ParticleFleijaAntischrabA.LIFETIME, "ring life");
		assertEq(helper, 50, ParticleFleijaAntischrabB.LIFETIME, "burst life");
		assertEq(helper, 10, ParticleFleijaAntischrabA.FADE_FROM, "ring fade");
		assertEq(helper, 10, ParticleFleijaAntischrabB.FADE_FROM, "burst fade");
		assertEq(helper, 2, (long) ParticleFleijaAntischrabA.SCALE, "ring size");
		assertEq(helper, 1, (long) ParticleFleijaAntischrabB.SCALE, "burst size");
		assertClose(helper, 0.149, ParticleFleijaAntischrabA.GREEN, "ring colour");
		assertClose(helper, 0.149, ParticleFleijaAntischrabB.GREEN, "burst colour");
		helper.succeed();
	}

	/**
	 * The DFC fireball and the flash: the flash grows over {@code ticksIn} and falls over
	 * {@code ticksOut}; the fireball is a quadratic ease-out to its diameter, opaque until the last
	 * twenty ticks. The counter {@code ParticleNuke} starts is {@code 100 + flashTime}. The test
	 * restores the static it writes.
	 */
	@GameTest(template = EMPTY)
	public static void theFlashAndTheFireballFollowTheirEnvelopes(GameTestHelper helper) {
		assertClose(helper, 0.0, LeafiaParticleMath.flashScale(0F, 5, 10), "the flash starts at nothing");
		assertClose(helper, 1.0, LeafiaParticleMath.flashScale(5F, 5, 10), "peaks when ticksIn is up");
		assertClose(helper, 0.5, LeafiaParticleMath.flashScale(10F, 5, 10), "and is half gone five ticks later");
		assertClose(helper, 0.0, LeafiaParticleMath.flashScale(15F, 5, 10), "and gone at the end");

		assertClose(helper, 0.0, LeafiaParticleMath.nukeDiameter(0F, 60, 250), "the fireball starts at nothing");
		assertClose(helper, 500 * 0.75, LeafiaParticleMath.nukeDiameter(30F, 60, 250), "three quarters of the way at half the time");
		assertClose(helper, 500.0, LeafiaParticleMath.nukeDiameter(60F, 60, 250), "and is the full diameter at the end");
		assertClose(helper, 1.0, LeafiaParticleMath.nukeAlpha(0, 60), "opaque at birth");
		assertClose(helper, 1.0, LeafiaParticleMath.nukeAlpha(40, 60), "and until twenty ticks from the end");
		assertClose(helper, 0.5, LeafiaParticleMath.nukeAlpha(50, 60), "half at ten ticks from it");
		assertClose(helper, 0.0, LeafiaParticleMath.nukeAlpha(60, 60), "and nothing at the end");

		assertEq(helper, 300, ParticleFlash.FAN_COUNT, "the flash's spikes");
		assertEq(helper, 432, ParticleFlash.SEED, "and their seed");
		assertEq(helper, 250, ParticleNuke.DEFAULT_RADIUS, "the fireball's radius");
		assertEq(helper, 60, ParticleNuke.DEFAULT_LIFETIME, "and life");
		assertEq(helper, 50, ParticleNuke.DEFAULT_FLASH_TIME, "and flash time");

		int saved = DfcFlash.dfcFlashTicks;
		try {
			DfcFlash.trigger(50);
			assertEq(helper, 150, DfcFlash.dfcFlashTicks, "the screen flash held for 100 plus the flash time");
		} finally {
			DfcFlash.dfcFlashTicks = saved;
		}
		helper.succeed();
	}

	/**
	 * The pulser's spark is drawn in along a sine ease-out, and the eye of harmony leaves its block and
	 * comes back: at progress 0 and 1 the mote is on the block's centre and at one half it is
	 * {@code flyHeight} from it, whatever the frame's tilt and spin.
	 */
	@GameTest(template = EMPTY)
	public static void theEyeOfHarmonyLeavesItsCentreAndReturns(GameTestHelper helper) {
		assertClose(helper, 0.0, LeafiaParticleMath.pulserEase(0), "the spark's ease at the centre");
		assertClose(helper, 1.0, LeafiaParticleMath.pulserEase(1), "and at the far end");
		helper.assertTrue(LeafiaParticleMath.pulserEase(0.25) < LeafiaParticleMath.pulserEase(0.5), "the ease rises");

		FiaMatrix mat = new FiaMatrix(new Vec3(10.5, 64.5, -3.5)).rotate(RotationOrder.YXZ, 12.0, 200.0, -9.0);
		float flyAngle = 233F;
		float flyHeight = 1.7F;
		assertClose(helper, 0.0, LeafiaParticleMath.eyeOfHarmony(mat, 0F, flyAngle, flyHeight).position.distanceTo(mat.position), "at the start");
		assertClose(helper, 0.0, LeafiaParticleMath.eyeOfHarmony(mat, 1F, flyAngle, flyHeight).position.distanceTo(mat.position), "and the end");
		assertClose(helper, flyHeight, LeafiaParticleMath.eyeOfHarmony(mat, 0.5F, flyAngle, flyHeight).position.distanceTo(mat.position), "and out by flyHeight at the middle");
		assertClose(helper, flyHeight, LeafiaParticleMath.eyeOfHarmony(mat, 0.25F, flyAngle, flyHeight).position.distanceTo(mat.position) * Math.sqrt(2), "and by flyHeight x sin(pi/4) a quarter of the way: the same turn applies");
		helper.succeed();
	}

	/**
	 * The constants the particles are written with, which a port changes by typing: lifetimes,
	 * sizes, colours, the pulser's three numbers, the welder's six. Each is upstream's literal.
	 */
	@GameTest(template = EMPTY)
	public static void theConstantsAreUpstreams(GameTestHelper helper) {
		assertEq(helper, 40, ParticleArcWelder.LIFETIME, "welder life");
		assertClose(helper, 0.5, ParticleArcWelder.START_SCALE, "welder size");
		assertClose(helper, 0.05, ParticleArcWelder.START_INCREASE, "welder growth");
		assertClose(helper, 0.005, ParticleArcWelder.INCREASE_STEP, "welder growth step");
		assertClose(helper, 0.3 / 20, ParticleArcWelder.LIFT, "welder lift");
		assertClose(helper, 0.8, ParticleArcWelder.LIFT_DRAG, "welder lift drag");
		assertClose(helper, 0.8, ParticleArcWelder.RED, "welder red");
		assertClose(helper, 0.925, ParticleArcWelder.GREEN, "welder green");
		assertClose(helper, 0.95, ParticleArcWelder.BLUE, "welder blue");

		assertClose(helper, 0.25, ParticlePulserCharge.BASE_SCALE, "pulser spark size");
		assertClose(helper, 0.35, ParticlePulserCharge.MIN_DISTANCE, "pulser spark nearest start");
		assertClose(helper, 0.25, ParticlePulserCharge.DISTANCE_SPREAD, "pulser spark spread");
		assertEq(helper, 3, ParticlePulserCharge.MIN_LIFETIME, "pulser spark life");
		assertEq(helper, 2, ParticlePulserCharge.AGE_STEP, "pulser spark age step");
		assertClose(helper, 7, ParticlePulserOvercharge.MIN_SCALE, "overcharge size");
		assertClose(helper, 3, ParticlePulserOvercharge.SCALE_SPREAD, "overcharge size spread");
		assertEq(helper, 1, ParticlePulserOvercharge.LIFETIME, "overcharge life");
		assertClose(helper, 0.5, ParticlePulserOvercharge.VISIBLE_UNTIL, "overcharge visible half of each tick");

		assertClose(helper, 0.2, ParticleDFC.SCALE, "dfc mote size");
		assertClose(helper, 3, ParticleDFC.SPEED, "dfc mote speed");
		assertEq(helper, 20, ParticleRBMKJet.FRAMES, "jet strip frames");
		assertEq(helper, 20, ParticleRBMKJet.FADE_TICKS, "jet fade");
		assertClose(helper, 4, ParticleRBMKJet.HEIGHT_FACTOR, "jet height");

		assertClose(helper, 0.01, ParticleRainBlood.HITBOX, "blood hitbox");
		assertClose(helper, 0.06, ParticleRainBlood.GRAVITY, "blood gravity");
		assertEq(helper, 3, ParticleRainBlood.MIN_LIFETIME, "blood life");
		assertEq(helper, 255, ParticleRedstoneLight.BRIGHTNESS, "light dust brightness");
		assertClose(helper, 0.96, ParticleRedstoneLight.FRICTION, "light dust friction");
		assertClose(helper, 0.75, ParticleCloudSmall.DEFAULT_SCALE, "small cloud size");
		assertClose(helper, 2.5, ParticleCloudSmall.LIFETIME_FACTOR, "small cloud life factor");
		assertClose(helper, 0.96F, ParticleCloudSmall.DRAG, "small cloud drag, the float widened");
		assertClose(helper, 2.0, ParticleCloudSmall.PLAYER_RANGE, "small cloud player range");
		helper.succeed();
	}

	/**
	 * The smoke puff and the shockwave that throws rings of them (D-1090): the puff lives 100 to 139
	 * ticks and fades as {@code (1 - age / life) x multiplier}, worked out before the age is counted so
	 * it never quite reaches nothing; the ring's radius is {@code 4 x age}, its fade is
	 * {@code sqrt(1 - radius / 200)}, its puffs are {@code 8 + (int)(radius^0.4 x 20)} to a ring at equal
	 * angles, each {@code 5 x (0.3 + 0.7 x fade)} across and {@code 0.8 x fade} bright.
	 */
	@GameTest(template = EMPTY)
	public static void theSmokeAndItsShockwaveFollowTheirNumbers(GameTestHelper helper) {
		assertEq(helper, 100, LeafiaParticleMath.smokeMaxAge(0), "the shortest puff");
		assertEq(helper, 139, LeafiaParticleMath.smokeMaxAge(ParticleBasicSmoke.LIFETIME_SPREAD - 1), "the longest puff");
		assertClose(helper, 1.0, LeafiaParticleMath.smokeAlpha(0, 100, 1F), "a puff is opaque on its first tick");
		assertClose(helper, 0.5, LeafiaParticleMath.smokeAlpha(50, 100, 1F), "and half gone at half life");
		assertClose(helper, 0.4, LeafiaParticleMath.smokeAlpha(50, 100, 0.8F), "scaled by the multiplier");
		assertClose(helper, 0.01, LeafiaParticleMath.smokeAlpha(99, 100, 1F), "and never reaches nothing: the last tick before it goes");

		assertClose(helper, 20.0, LeafiaParticleMath.shockwaveRadius(5, ParticleSmokeShockwave.INCREMENT), "the radius at age 5");
		assertClose(helper, 1.0, LeafiaParticleMath.shockwaveFade(0, 200), "full strength at the centre");
		assertClose(helper, 0.5, LeafiaParticleMath.shockwaveFade(150, 200), "a half at three quarters of the way out");
		assertClose(helper, 0.0, LeafiaParticleMath.shockwaveFade(200, 200), "and nothing at the rim");
		assertClose(helper, 5.0, LeafiaParticleMath.shockwaveScale(5F, 1F), "a puff at the centre is the whole size");
		assertClose(helper, 1.5, LeafiaParticleMath.shockwaveScale(5F, 0F), "and at the rim three tenths of it");
		assertClose(helper, 0.8, LeafiaParticleMath.shockwaveAlpha(1F), "the brightest a puff gets");
		assertEq(helper, 8, LeafiaParticleMath.shockwaveAmount(0), "puffs on the centre ring");
		assertEq(helper, 28, LeafiaParticleMath.shockwaveAmount(1), "and on the ring of radius 1");
		assertEq(helper, 134, LeafiaParticleMath.shockwaveAmount(100), "and of radius 100");
		assertEq(helper, 174, LeafiaParticleMath.shockwaveAmount(200), "and on the last ring, at the rim");
		assertClose(helper, Math.PI / 2, LeafiaParticleMath.shockwaveAngle(8, 2), "the third puff of eight is a quarter turn round");
		assertClose(helper, Math.PI * 7 / 4, LeafiaParticleMath.shockwaveAngle(8, 7), "and the last is short of a full turn");

		assertClose(helper, 0.76F, ParticleBasicSmoke.DRAG, "the puff's drag, the float widened");
		assertClose(helper, 0.25, ParticleBasicSmoke.GREY, "its grey");
		assertEq(helper, 100, ParticleBasicSmoke.MIN_LIFETIME, "its shortest life");
		assertEq(helper, 240, ParticleBasicSmoke.BRIGHTNESS, "its light, block 15 and sky 0");
		assertClose(helper, 4, ParticleSmokeShockwave.INCREMENT, "the ring's growth");
		assertClose(helper, 5, ParticleSmokeShockwave.PARTICLE_SCALE, "the puffs' size");
		assertClose(helper, 200, ParticleSmokeShockwave.MAX_RADIUS, "the wave's reach");
		assertEq(helper, 12, ParticleSmokeShockwave.PARTICLE_LIFETIME, "each puff's life");
		assertClose(helper, 0.2, ParticleSmokeShockwave.JITTER, "the position jitter");
		helper.succeed();
	}
}
