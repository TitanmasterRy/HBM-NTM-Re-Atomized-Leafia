package com.leafia.client.particle;

import com.custom_hbm.effectNT.EffectNT;
import com.hbm.particle.helper.ClientParticleDispatch;
import com.leafia.contents.machines.misc.modular_turbine.ParticleMTSteam;
import com.leafia.contents.machines.powercores.dfc.particles.ParticleDFC;
import com.leafia.contents.machines.powercores.dfc.particles.ParticleNuke;
import com.leafia.contents.machines.reactors.rbmk.effects.ParticleRBMKJet;
import com.leafia.unsorted.ParticleFireK;
import com.leafia.unsorted.ParticleFireLavaK;
import com.leafia.dev.math.FiaMatrix;
import com.leafia.unsorted.ParticleDFCShockParticleEditionTM;
import com.leafia.unsorted.ParticleFlash;
import com.leafia.unsorted.ParticleSmokeShockwave;
import com.leafia.unsorted.ParticleSpark;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * What each kind of {@code LeafiaParticlePacket} does when it reaches a client: the {@code emit}
 * bodies of upstream's nested classes, as statics.
 *
 * <p>Upstream wrote them in the packet's own nested classes behind {@code @SideOnly(Side.CLIENT)}, which
 * Forge stripped from a dedicated server's copy. NeoForge does not strip, and a class a server loads
 * must not name a client class in anything the verifier checks - so the bodies live here, the packet
 * calls them by name, and nothing in this class is resolved until a client runs one. Every parameter
 * is a primitive or a {@link CompoundTag}, so the calls themselves load nothing either.
 *
 * <p>Each takes the packet's data buffer for the position (upstream reads {@code posX/Y/Z} out of it
 * in every one) and the kind's own fields as arguments, which makes each body checkable against the
 * upstream one it came from without a decoded packet in hand.
 */
@OnlyIn(Dist.CLIENT)
public final class LeafiaParticleClient {

	private LeafiaParticleClient() {}

	private static double x(CompoundTag nbt) { return nbt.getDouble("posX"); }
	private static double y(CompoundTag nbt) { return nbt.getDouble("posY"); }
	private static double z(CompoundTag nbt) { return nbt.getDouble("posZ"); }

	/** Upstream's default {@code emit}: {@code MainRegistry.proxy.effectNT(nbt)}. */
	public static void dispatch(CompoundTag nbt) {
		ClientParticleDispatch.dispatch(nbt);
	}

	/** {@code VanillaExt}: {@code EffectNT.effectNT(nbt)}. */
	public static void vanillaExt(CompoundTag nbt) {
		EffectNT.effectNT(nbt);
	}

	/**
	 * {@code AlkaliFire}. Each of x, y and z is jittered by a quarter block either way. Period 3 draws
	 * the addon's {@link ParticleFireK}; anything else is vanilla flame. From period 3 up, half the
	 * time a spark of lava follows - the addon's {@link ParticleFireLavaK} at exactly 3, vanilla's at
	 * higher periods.
	 */
	public static void alkaliFire(int period, CompoundTag nbt) {

		ClientLevel level = Minecraft.getInstance().level;
		if(level == null) return;

		double x = x(nbt) + level.random.nextDouble() * 0.5D - 0.25D;
		double y = y(nbt) + level.random.nextDouble() * 0.5D - 0.25D;
		double z = z(nbt) + level.random.nextDouble() * 0.5D - 0.25D;

		if(period == 3) {
			Minecraft.getInstance().particleEngine.add(new ParticleFireK(level, x, y, z));
		} else {
			level.addParticle(ParticleTypes.FLAME, x, y, z, 0, 0, 0);
		}

		if(period >= 3 && level.random.nextInt(2) == 0) {
			if(period == 3) {
				Minecraft.getInstance().particleEngine.add(new ParticleFireLavaK(level, x, y, z));
			} else {
				level.addParticle(ParticleTypes.LAVA, x, y, z, 0, 0, 0);
			}
		}
	}

	/** {@code DFCBlastParticle}: {@code amount} spheres of one colour at the same point. */
	public static void dfcBlast(float red, float green, float blue, int amount, CompoundTag nbt) {

		ClientLevel level = Minecraft.getInstance().level;
		if(level == null) return;

		for(int i = 0; i < amount; i++)
			Minecraft.getInstance().particleEngine.add(new ParticleDFC(level, x(nbt), y(nbt), z(nbt), red, green, blue));
	}

	/** {@code FlashParticle}. */
	public static void flash(int ticksIn, int ticksOut, float scale, CompoundTag nbt) {

		ClientLevel level = Minecraft.getInstance().level;
		if(level == null) return;

		Minecraft.getInstance().particleEngine.add(
				new ParticleFlash(level, x(nbt), y(nbt), z(nbt), scale, ticksIn, ticksOut));
	}

	/** {@code SmokeShockwaveParticle}: the four knobs are set after construction, as upstream does. */
	public static void smokeShockwave(double increment, float particleScale, double maxRadius, int particleLifetime, CompoundTag nbt) {

		ClientLevel level = Minecraft.getInstance().level;
		if(level == null) return;

		ParticleSmokeShockwave particle = new ParticleSmokeShockwave(level, x(nbt), y(nbt), z(nbt));
		particle.increment = increment;
		particle.particleScale = particleScale;
		particle.maxRadius = maxRadius;
		particle.particleLifetime = particleLifetime;
		Minecraft.getInstance().particleEngine.add(particle);
	}

	/** {@code RBMKJetParticle}. */
	public static void rbmkJet(int maxAge, CompoundTag nbt) {

		ClientLevel level = Minecraft.getInstance().level;
		if(level == null) return;

		Minecraft.getInstance().particleEngine.add(new ParticleRBMKJet(level, x(nbt), y(nbt), z(nbt), maxAge));
	}

	/** {@code PinkRBMK}: the base mod's meltdown mushroom at size 12, tinted pink. */
	public static void pinkRBMK(CompoundTag nbt) {

		ClientLevel level = Minecraft.getInstance().level;
		if(level == null) return;

		com.hbm.particle.ParticleRBMKMush mush = new com.hbm.particle.ParticleRBMKMush(level, x(nbt), y(nbt), z(nbt), 12);
		com.leafia.overwrite_contents.interfaces.IMixinParticleRBMKMush.setPink(mush, true);
		Minecraft.getInstance().particleEngine.add(mush);
	}

	/** {@code JumpingRBMKParticle}: a lid in the air over the column at that position. */
	public static void jumpingRBMK(int x, int y, int z) {

		ClientLevel level = Minecraft.getInstance().level;
		if(level == null) return;

		com.leafia.contents.machines.reactors.rbmk.effects.ParticleJumpingRBMK.spawn(level, new BlockPos(x, y, z));
	}

	/** {@code MTSteamParticle}: the packet's motion is the steam's. */
	public static void mtSteam(int travelDistance, CompoundTag nbt) {

		ClientLevel level = Minecraft.getInstance().level;
		if(level == null) return;

		Minecraft.getInstance().particleEngine.add(new ParticleMTSteam(level, x(nbt), y(nbt), z(nbt),
				nbt.getDouble("mX"), nbt.getDouble("mY"), nbt.getDouble("mZ"), travelDistance));
	}

	/**
	 * {@code DFCNukeParticle}. Upstream builds the position with {@code new BlockPos(double, double,
	 * double)}, which floors; {@link BlockPos#containing} floors too.
	 */
	public static void dfcNuke(int radius, int maxAge, int flashTime, CompoundTag nbt) {

		ClientLevel level = Minecraft.getInstance().level;
		if(level == null) return;

		ParticleNuke nuke = new ParticleNuke(level, BlockPos.containing(x(nbt), y(nbt), z(nbt)));
		nuke.radius = radius;
		nuke.setLifetime(maxAge);
		nuke.flashTime = flashTime;
		Minecraft.getInstance().particleEngine.add(nuke);
	}

	/**
	 * {@code FiaSpark}: {@code count} sparks from the packet's position along its motion vector. Upstream
	 * aims each at {@code vec.add(m)} from {@code vec}, which is a frame looking along {@code m}; the speed
	 * of each is drawn between the two bounds.
	 */
	public static void fiaSpark(int color, double spread, double speedMin, double speedMax, int count, int length,
			float thickness, float segmentsPerTick, CompoundTag nbt) {

		ClientLevel level = Minecraft.getInstance().level;
		if(level == null) return;

		Vec3 vec = new Vec3(x(nbt), y(nbt), z(nbt));
		for(int i = 0; i < count; i++) {
			ParticleSpark spark = new ParticleSpark(level,
					new FiaMatrix(vec, vec.add(nbt.getDouble("mX"), nbt.getDouble("mY"), nbt.getDouble("mZ"))),
					color, length, speedMin + (speedMax - speedMin) * level.random.nextDouble(), spread);
			spark.thickness = thickness;
			spark.segmentsPerTick = segmentsPerTick;
			Minecraft.getInstance().particleEngine.add(spark);
		}
	}

	/** {@code DFCShockParticleEditionTM}: a four-tick lightning path, at the packet's position. */
	public static void dfcShock(java.util.List<Vec3> poses, CompoundTag nbt) {

		ClientLevel level = Minecraft.getInstance().level;
		if(level == null) return;

		Minecraft.getInstance().particleEngine.add(new ParticleDFCShockParticleEditionTM(level, x(nbt), y(nbt), z(nbt), poses));
	}
}
