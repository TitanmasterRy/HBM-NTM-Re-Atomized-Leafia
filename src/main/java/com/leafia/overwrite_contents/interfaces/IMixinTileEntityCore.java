package com.leafia.overwrite_contents.interfaces;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.custom_hbm.sound.LCEAudioWrapper;
import com.hbm.blockentity.machine.BlockEntityCoreReceiver;
import com.leafia.contents.machines.powercores.dfc.components.pulser.CoreDetonatorTE;
import com.leafia.dev.custompacket.LeafiaCustomPacketEncoder;
import com.leafia.dev.math.FiaMatrix;
import com.leafia.dev.optimization.LeafiaParticlePacket;
import com.leafia.dev.optimization.bitbyte.LeafiaBuf;
import com.leafia.init.LeafiaSounds;
import com.llib.LeafiaLib;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The addon's Dark Fusion Core, as the rest of the addon sees it: its constants, its state, and the two
 * nested types the renderer and the shock packet need.
 *
 * <p>Upstream: {@code com/leafia/overwrite_contents/interfaces/IMixinTileEntityCore.java}, the interface
 * {@code MixinTileEntityCore} added to the base mod's core. The path is kept so the components (DFC-3),
 * the renderers (DFC-4) and the crates (M5) port against the name they name; the implementor here is
 * {@link com.leafia.contents.machines.powercores.dfc.core.LeafiaCoreTE}, a subclass of the base core put
 * in its place through {@code BlockEntityOverrides} (D-3011).
 *
 * <h2>Units</h2>
 *
 * Energy is in MSPK: {@code containedEnergy} 1 is a million SPK, and an emitter adds its beam as
 * {@code joules / 1,000,000}. {@link #failsafeLevel} (250 million MSPK) is where contained energy is
 * clamped and where the screen starts reading ERROR; {@link #maxEnergy} is the 10,000 MSPK that the
 * temperature curve treats as "full".
 *
 * <h2>What did not come across</h2>
 *
 * <ul>
 * <li>(Nothing else: {@code shockParticleEditionTrademark}, the static that throws a lightning path out of a
 *     point as the particle packet's {@code DFCShockParticleEditionTM}, landed with that kind in DFC-4,
 *     D-3017.)</li>
 * <li>{@code IControllable} is not here because upstream never put it here; the panel is D (D-1093 #8).</li>
 * </ul>
 */
public interface IMixinTileEntityCore {

	// ===== constants =====
	double failsafeLevel = 250000000.0;
	double maxEnergy = 10_000.0;

	static double getStabilizationDiv(double stabilization) {
		return 1.0 + Math.sqrt(stabilization * 10.0);
	}

	/**
	 * Each core item's loop, and how fast it fades with distance. The ordinal is what the core syncs
	 * ({@code CORE_TYPE}), so the order is upstream's and is pinned. {@code glitch} is the base mod's
	 * {@code ModItems.glitch}, which upstream lets stand in for a core.
	 */
	enum Cores {
		ams_core_sing(() -> LeafiaSounds.dfc_vs.get(), (intended, distance) ->
				Math.pow(Mth.clamp(1 - (distance - 3) / 15, 0, 1), 1.5)),
		ams_core_wormhole(() -> LeafiaSounds.dfc_tw.get(), (intended, distance) ->
				Math.pow(Mth.clamp(1 - (distance - 3) / 40, 0, 1), 2)),
		ams_core_eyeofharmony(() -> LeafiaSounds.dfc_eoh.get(), (intended, distance) ->
				Math.pow(Mth.clamp(1 - (distance - 3) / 150, 0, 1), 3)),
		glitch(() -> LeafiaSounds.glitch_alpha10302.get(), (intended, distance) ->
				Math.pow(Mth.clamp(1 - (distance - 3) / 125, 0, 1), 3)),
		ams_core_thingy(() -> LeafiaSounds.dfc_thingy.get(), (intended, distance) ->
				Math.pow(Mth.clamp(1 - (distance - 3) / 350, 0, 1), 3)); // haha

		public final Supplier<SoundEvent> sfx;
		public final BiFunction<Float, Double, Double> attentuationFunction;

		Cores(Supplier<SoundEvent> sfx, BiFunction<Float, Double, Double> attentuationFunction) {
			this.sfx = sfx;
			this.attentuationFunction = attentuationFunction;
		}
	}

	/** The core's sync keys. The ordinal is the key on the wire, so the order is upstream's. */
	enum packetKeys {
		TEMP, STABILIZATION, MAXIMUM,
		CONTAINED, EXPELLING, POTENTIAL,
		TANK_A, TANK_B,
		EXPEL_TICK, COLOR, COLOR_CATALYST, CORE_TYPE,
		PLAY_SOUND, JAMMER,
		COLLAPSE,
		HASCORE,

		DETONATION, DET_TIMER;

		public final int key;

		packetKeys() { this.key = this.ordinal(); }
	}

	/**
	 * A lightning path out of {@code core}: a random heading, a hop of half a block to one and a half, and
	 * then up to 25 hops of the same length that wander two blocks (a Gaussian each) sideways of the line
	 * back to the core. The first hop that meets a block ends the path there, in a flaming explosion of
	 * two to seven blocks; the whole path goes out as the particle packet's
	 * {@code DFCShockParticleEditionTM}, to everyone within 340 blocks. Upstream's static, and its only
	 * caller is the battery socket mixin (a later batch).
	 *
	 * <p>The explosion is {@code newExplosion(null, ..., true, true)}: fire, and the terrain. It happens
	 * on a server only, as does the packet; a client call does nothing.
	 */
	static void shockParticleEditionTrademark(Level world, Vec3 core) {
		if(!(world instanceof ServerLevel server)) return;
		new LeafiaParticlePacket.DFCShockParticleEditionTM(shockPath(world, core, true)).emit(server, core, new Vec3(0, 1, 0));
	}

	/**
	 * The path {@link #shockParticleEditionTrademark} throws: its points, from {@code core}, ending at the
	 * first block a hop meets (where the explosion is, if {@code explode}) or after 25 hops that met none.
	 */
	static List<Vec3> shockPath(Level world, Vec3 core, boolean explode) {
		double rlen = 1;
		double length = 0.5;
		Vec3 p0 = core;
		Vec3 p1 = new FiaMatrix(p0).rotateY(world.random.nextDouble() * 360).rotateX(world.random.nextDouble() * 360)
				.translate(0, 0, length + world.random.nextDouble() * rlen).position;
		List<Vec3> poses0 = new ArrayList<>();
		poses0.add(p0);
		poses0.add(p1);
		for(int i = 0; i < 25; i++) {
			p0 = p1;
			p1 = new FiaMatrix(p1, core).translate(world.random.nextGaussian() * 2, world.random.nextGaussian() * 2, length + world.random.nextDouble() * rlen).position;
			BlockHitResult res = LeafiaLib.leafiaRayTraceBlocks(world, p0, p1, false, true, false);
			if(res != null && res.getLocation() != null) {
				p1 = res.getLocation();
				poses0.add(p1);
				if(explode) world.explode(null, p1.x, p1.y, p1.z, world.random.nextFloat() * 5 + 2, true, Level.ExplosionInteraction.BLOCK);
				break;
			}
			poses0.add(p1);
		}
		return poses0;
	}

	/** One lightning path out of the core, kept on the client for four ticks for the renderer. */
	class DFCShock {
		public final List<Vec3> poses;
		public int ticks = 0;
		public DFCShock(List<Vec3> poses) { this.poses = poses; }
	}

	/**
	 * A shock, server to client: the core's position, then up to 255 points as three floats each.
	 * Upstream's {@code DFCShockPacket}; the bytes are the same.
	 */
	class DFCShockPacket implements LeafiaCustomPacketEncoder {
		public BlockPos pos;
		public List<Vec3> poses0 = new ArrayList<>();

		@Override
		public void encode(LeafiaBuf buf) {
			buf.writeVec3i(pos);
			buf.writeByte(poses0.size());
			for(Vec3 p : poses0) {
				buf.writeFloat((float) p.x);
				buf.writeFloat((float) p.y);
				buf.writeFloat((float) p.z);
			}
		}

		@Nullable
		@Override
		public Consumer<IPayloadContext> decode(LeafiaBuf buf) {
			BlockPos at = new BlockPos(buf.readVec3i());
			int leng = buf.readByte();
			List<Vec3> poses = new ArrayList<>();
			for(int i = 0; i < leng; i++) poses.add(new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat()));
			return context -> {
				if(context.flow() == PacketFlow.CLIENTBOUND && FMLEnvironment.dist.isClient())
					com.leafia.client.LeafiaDFCClient.addShock(at, poses);
			};
		}
	}

	double getDFCFailsafeLevel();
	double getDFCMaxEnergy();

	// ===== state =====
	boolean isDFCHasCore();
	void setDFCHasCore(boolean value);

	double getDFCTemperature();
	void setDFCTemperature(double value);

	double getDFCStabilization();
	void setDFCStabilization(double value);

	double getDFCContainedEnergy();
	void setDFCContainedEnergy(double value);

	double getDFCExpellingEnergy();
	void setDFCExpellingEnergy(double value);

	double getDFCPotentialGain();
	void setDFCPotentialGain(double value);

	double getDFCGainedEnergy();
	void setDFCGainedEnergy(double value);

	double getDFCCollapsing();
	void setDFCCollapsing(double value);

	int getDFCStabilizers();
	void setDFCStabilizers(int value);

	int getDFCLastStabilizers();
	void setDFCLastStabilizers(int value);

	boolean isDFCWasBoosted();
	void setDFCWasBoosted(boolean value);

	double[] getDFCExpelTicks();
	void setDFCExpelTicks(double[] value);

	double getDFCEnergyMod();
	void setDFCEnergyMod(double value);

	double getDFCBonus();
	void setDFCBonus(double value);

	List<BlockEntityCoreReceiver> getDFCAbsorbers();
	void setDFCAbsorbers(List<BlockEntityCoreReceiver> value);

	List<CoreDetonatorTE> getDFCPulsers();

	int lastPulserPower();

	boolean getDetonation();
	int getDetonationTimer();
	void setDetonation(boolean v);

	boolean isDFCDestroyed();
	void setDFCDestroyed(boolean value);

	double getDFCExplosionIn();
	void setDFCExplosionIn(double value);

	long getDFCExplosionClock();
	void setDFCExplosionClock(long value);

	BlockPos getDFCJammerPos();
	void setDFCJammerPos(BlockPos value);

	List<BlockPos> getDFCComponentPositions();
	void setDFCComponentPositions(List<BlockPos> value);

	List<BlockPos> getDFCPrevComponentPositions();
	void setDFCPrevComponentPositions(List<BlockPos> value);

	double getDFCIncomingSpk();
	void setDFCIncomingSpk(double value);

	double getDFCExpellingSpk();
	void setDFCExpellingSpk(double value);

	int getDFCMeltingPoint();
	void setDFCMeltingPoint(int value);

	int getDFCTicks();
	void setDFCTicks(int value);

	int getDFCOverloadTimer();
	void setDFCOverloadTimer(int value);

	int getDFCColorCatalyst();
	void setDFCColorCatalyst(int value);

	int getDFCShockCooldown();
	void setDFCShockCooldown(int value);

	// ===== client (visual) =====
	double getDFCClientMaxDial();
	void setDFCClientMaxDial(double value);

	Cores getDFCClientType();
	void setDFCClientType(Cores value);

	LCEAudioWrapper getDFCClientSfx();
	void setDFCClientSfx(LCEAudioWrapper value);

	boolean isDFCSfxPlaying();
	void setDFCSfxPlaying(boolean value);

	LCEAudioWrapper getDFCMeltdownSFX();
	void setDFCMeltdownSFX(LCEAudioWrapper value);

	LCEAudioWrapper getDFCOverloadSFX();
	void setDFCOverloadSFX(LCEAudioWrapper value);

	LCEAudioWrapper getDFCExtinguishSFX();
	void setDFCExtinguishSFX(LCEAudioWrapper value);

	LCEAudioWrapper getDFCExplosionsSFX();
	void setDFCExplosionsSFX(LCEAudioWrapper value);

	float getDFCAngle();
	void setDFCAngle(float value);

	float getDFCLightRotateSpeed();
	void setDFCLightRotateSpeed(float value);

	boolean isDFCFinalPhase();
	void setDFCFinalPhase(boolean value);

	float getDFCRingSpinSpeed();
	void setDFCRingSpinSpeed(float value);

	float getDFCRingAngle();
	void setDFCRingAngle(float value);

	float getDFCRingAlpha();
	void setDFCRingAlpha(float value);

	List<DFCShock> getDfcShocks();
}
