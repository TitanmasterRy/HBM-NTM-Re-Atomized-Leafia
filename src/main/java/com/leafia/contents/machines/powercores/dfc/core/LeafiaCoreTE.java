package com.leafia.contents.machines.powercores.dfc.core;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.custom_hbm.explosion.LCEExplosionNT;
import com.custom_hbm.sound.LCEAudioProxy;
import com.custom_hbm.sound.LCEAudioWrapper;
import com.hbm.blockentity.machine.BlockEntityCore;
import com.hbm.blockentity.machine.BlockEntityCoreReceiver;
import com.hbm.blocks.ModBlocks;
import com.hbm.entity.effect.EntityCloudFleijaRainbow;
import com.hbm.entity.logic.EntityBalefire;
import com.hbm.entity.logic.EntityNukeExplosionMK3;
import com.hbm.entity.logic.EntityNukeExplosionMK3.ATEntry;
import com.hbm.entity.logic.EntityNukeExplosionMK5;
import com.hbm.explosion.ExplosionLarge;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemCatalyst;
import com.hbm.items.special.ItemAMSCore;
import com.hbm.lib.Library;
import com.hbm.util.ArmorUtil;
import com.leafia.contents.LeafiaItems;
import com.leafia.contents.fluids.traits.FT_DFCFuel;
import com.leafia.contents.machines.powercores.dfc.DFCCEStats;
import com.leafia.contents.machines.powercores.dfc.components.pulser.CoreDetonatorTE;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.dev.container_utility.LeafiaPacketReceiver;
import com.leafia.dev.custompacket.LeafiaCustomPacket;
import com.leafia.dev.math.FiaMatrix;
import com.leafia.dev.optimization.LeafiaParticlePacket;
import com.leafia.dev.optimization.LeafiaParticlePacket.DFCNukeParticle;
import com.leafia.dev.optimization.LeafiaParticlePacket.FlashParticle;
import com.leafia.init.LeafiaDamageSources;
import com.leafia.init.LeafiaSounds;
import com.leafia.overwrite_contents.interfaces.IMixinTileEntityCore;
import com.leafia.overwrite_contents.interfaces.IMixinTileEntityCoreReceiver;
import com.leafia.overwrite_contents.other.LCEItemCatalyst;
import com.leafia.passive.effects.LeafiaShakecam;
import com.llib.LeafiaLib;
import com.llib.math.LeafiaColor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The addon's Dark Fusion Core: a temperature, a contained energy and a collapse, where the base mod's
 * core has a per-tick heat against a decaying field.
 *
 * <p>Upstream: {@code com/leafia/overwrite_contents/mixin/mod/hbm/MixinTileEntityCore.java} (1,608 lines),
 * which overwrites the base core's {@code update}, NBT, fuel table and GUI. Here it is a subclass of the
 * base mod's {@link BlockEntityCore} under its own type, put into the base block by
 * {@code BlockEntityOverrides} <b>only while {@code overhauls.dfcOverhaul} is on</b>; off (the default),
 * the base mod's core is placed and nothing here runs (D-3011).
 *
 * <h2>The model, in one paragraph</h2>
 *
 * The emitters (DFC-3) add their beam to {@code incomingSpk}; the stabilizers add {@code stabilization}
 * and scale {@code energyMod}; the receivers register as absorbers. Each tick the core turns the beam
 * into contained energy - a little directly ({@code addition0}), and, once it is above 100 degrees, a
 * lot more from its own temperature ({@code addition1}) - and the absorbers take a share out in
 * proportion to their level. Temperature chases a target that the beam pushes up and that full
 * containment, the stabilizers and the melting point pull down; above 100 degrees a random term
 * ({@code potentialGain}) is added that the stabilization divides. Fuel burns as the square root of the
 * beam. The two catalysts melt when the temperature passes their melting point - five ten-second
 * steps from new to gone, regenerating at a third of that - and a melted catalyst above 100 degrees is
 * an overload: six seconds of warning, then a 50-radius blast and a F.L.E.I.J.A. of
 * {@code 20 + temperature^0.4}.
 *
 * <h2>Two ways not to die, both ported</h2>
 *
 * <ul>
 * <li><b>A field disturber within 300 blocks</b> turns the overload into a collapse: a 120-second clock
 *     on the wall (not the tick counter), a pull that grows with it, and in the last 15 seconds a
 *     landslide blast ({@code LCEExplosionNT} with {@code DFC_FALL}) and the absorbers blown off.
 *     The base mod's field disturber is a plain block that writes no protection entry yet, so the
 *     branch is reachable only through an entry something else wrote (hand-off).</li>
 * <li><b>A crucifix thrown in</b> during a collapse resets the core - see the oddity below.</li>
 * </ul>
 *
 * <h2>The pulse detonator</h2>
 *
 * Detonators pointed at the core register every tick ({@link #getDFCPulsers}); one that arms it starts
 * a 30-second count. At 24 seconds the overload sound plays and the camera shakes; one tick before 30
 * the temperature jumps by 25,000; at 30 the core is removed and the blast depends on how many
 * detonators were armed: one or none, a 150-radius fire blast; two to four, a nuke of radius
 * {@code 130 x (n - 1)}; five to seven, a balefire of {@code 130(n-1) + 30(n-8)}; eight or more,
 * a F.L.E.I.J.A. of {@code 130(n-1) + 130(n-8)} and a rainbow cloud. {@link #detonationRadius}.
 *
 * <h2>Upstream oddities kept (rule 4), each pinned</h2>
 *
 * <ul>
 * <li><b>{@code fix_tool} works in a collapse</b> (D-3075). The vaporisation loop used to skip every
 *     {@code fix_tool} item entity at its top, before the crucifix branch that names it.</li>
 * <li>{@code hasCore} is true when both catalyst slots hold catalysts, not when there is a core.</li>
 * <li>The radius table's five-to-seven branch no longer subtracts (D-3075): five detonators is 520, not 430.</li>
 * <li>A collapse counts wall-clock seconds; a single-player pause is excluded the way upstream does
 *     it, by moving the clock from the client while paused ({@link #SINGLEPLAYER_CORES}).</li>
 * </ul>
 *
 * <h2>What this batch does not carry</h2>
 *
 * The client vacuum particle a collapse throws at the local player ({@code ParticleFleijaVacuum} on an
 * {@code EntityNukeFolkvangr.VacuumInstance}) landed with EXPL-B and is thrown from {@code LeafiaDFCClient.pullLocal}
 * (D-3013); the MK3 a meltdown spawns becomes a Folkvangr, which its rainbow cloud binds. The {@code progress_dfc} advancement has no advancement tree to live in. The
 * glitch core's infinite render box is the renderer's (DFC-4). Until DFC-3's components land, nothing
 * feeds {@code incomingSpk}: a base mod emitter's {@code burn} finds this core not ready and passes
 * through it, so an overhauled core with base components is inert, which is the honest middle state.
 */
public class LeafiaCoreTE extends BlockEntityCore implements LeafiaPacketReceiver, IMixinTileEntityCore {

	/**
	 * The cores on an integrated server, whose collapse clock the client moves while the game is
	 * paused. Upstream's {@code LeafiaPassiveLocal.trackingCores}.
	 */
	public static final Set<LeafiaCoreTE> SINGLEPLAYER_CORES = ConcurrentHashMap.newKeySet();

	private boolean hasCore = false;
	private double temperature = 0;
	private double stabilization = 0;
	private double containedEnergy = 0; // 1 = 1MSPK
	private double expellingEnergy = 0;
	private double potentialGain = 0;
	private double gainedEnergy = 0;
	private double collapsing = 0;
	private int stabilizers = 0;
	private int lastStabilizers = 0;
	private boolean wasBoosted = false;
	private double[] expelTicks = new double[20];
	private double energyMod = 1;
	private double bonus = 0;
	private List<BlockEntityCoreReceiver> absorbers = new ArrayList<>();
	private final List<CoreDetonatorTE> pulsers = new ArrayList<>();
	private boolean destroyed = false;
	private double explosionIn = -1;
	private long explosionClock = 0;
	@Nullable
	private BlockPos jammerPos = null;
	private List<BlockPos> componentPositions = new ArrayList<>();
	private List<BlockPos> prevComponentPositions = new ArrayList<>();
	private double incomingSpk = 0;
	private double expellingSpk = 0;
	private int meltingPoint = 2250;
	private int ticks = 0;
	private int overloadTimer = 0;
	private int colorCatalyst = 0xFFFFFF;
	private int shockCooldown = 0;
	// == Client ==
	private double client_maxDial = 0.95;
	@Nullable
	private Cores client_type = null;
	@Nullable
	private LCEAudioWrapper client_sfx = null;
	private boolean sfxPlaying = false;
	@Nullable
	private LCEAudioWrapper meltdownSFX = null;
	@Nullable
	private LCEAudioWrapper overloadSFX = null;
	@Nullable
	private LCEAudioWrapper extinguishSFX = null;
	@Nullable
	private LCEAudioWrapper explosionsSFX = null;
	private boolean clientSoundsMade = false;
	private float angle = 0;
	private float lightRotateSpeed = 15 / 20.0f;
	private boolean finalPhase = false;
	private float ringSpinSpeed = 360 / 20.0f;
	private float ringAngle = 0;
	private float ringAlpha = 0;
	private final List<DFCShock> dfcShocks = new ArrayList<>();
	private boolean wasActive = false;
	private int particleTicks = 0;

	boolean detonation = false;
	int detonationTimer = 0;
	int lastPulserCount = 0;
	int lastPulserPower;

	/** 30 seconds of count from arming to the blast. */
	public static final int DETONATION_TICKS = 20 * 30;
	/** When the overload sound and the shaking start, 24 seconds in. */
	public static final int DETONATION_WARNING = 20 * (30 - 6);
	/** Upstream's {@code getPullRange()}. */
	public static final double PULL_RANGE = 150;

	public LeafiaCoreTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void updateEntity() {
		prevComponentPositions.clear();
		prevComponentPositions.addAll(componentPositions);
		componentPositions.clear();
		if(destroyed) return;
		if(!level.isClientSide) tickServer();
		else tickClient();
	}

	@Override
	public void onLoad() {
		super.onLoad();
		if(level != null && !level.isClientSide) {
			MinecraftServer server = level.getServer();
			if(server != null && !server.isDedicatedServer()) SINGLEPLAYER_CORES.add(this);
		} else if(level != null && level.isClientSide) {
			com.leafia.client.LeafiaDFCClient.track(this);
		}
	}

	@Override
	public void setRemoved() {
		stopAllSounds();
		SINGLEPLAYER_CORES.remove(this);
		super.setRemoved();
	}

	@Override
	public void onChunkUnloaded() {
		if(client_sfx != null) {
			client_sfx.stopSound();
			client_sfx = null;
			sfxPlaying = false;
		}
		SINGLEPLAYER_CORES.remove(this);
		super.onChunkUnloaded();
	}

	private void stopAllSounds() {
		if(client_sfx != null) {
			client_sfx.stopSound();
			client_sfx = null;
			sfxPlaying = false;
		}
		if(meltdownSFX != null) { meltdownSFX.stopSound(); meltdownSFX = null; }
		if(extinguishSFX != null) { extinguishSFX.stopSound(); extinguishSFX = null; }
		if(overloadSFX != null) { overloadSFX.stopSound(); overloadSFX = null; }
		if(explosionsSFX != null) { explosionsSFX.stopSound(); explosionsSFX = null; }
	}

	// ===================== persistence =====================

	@Override
	protected void loadAdditional(CompoundTag compound, HolderLookup.Provider registries) {
		super.loadAdditional(compound, registries);
		temperature = compound.getDouble("temperature");
		containedEnergy = compound.getDouble("energy");
		wasActive = compound.getBoolean("wasActive");
	}

	@Override
	protected void saveAdditional(CompoundTag compound, HolderLookup.Provider registries) {
		super.saveAdditional(compound, registries);
		compound.putDouble("temperature", temperature);
		compound.putDouble("energy", containedEnergy);
		compound.putBoolean("wasActive", wasActive);
	}

	@Override
	public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
		return new CoreMenu(id, playerInventory, this);
	}

	// ===================== detonation =====================

	public boolean getDetonation() {
		return detonation;
	}

	public int getDetonationTimer() {
		return detonationTimer;
	}

	public void setDetonation(boolean v) {
		detonation = v;
	}

	/** Not upstream's: lets a test start the count part-way rather than spending thirty seconds. */
	public void setDetonationTimer(int v) {
		detonationTimer = v;
	}

	/**
	 * Upstream's blast radius for {@code power} armed detonators, from the three branches of
	 * {@code tickDetonation}. Below two it is the 150-radius fire blast and this answers 0.
	 */
	public static int detonationRadius(int power) {
		if(power <= 1) return 0;
		int radius = 130 * (power - 1);
		if(power <= 4) return radius;
		if(power <= 7) return radius; // D-3075: upstream added 30 x (n - 8), a negative, so five detonators gave 430 and six 590
		radius += 130 * (power - 8);
		return radius;
	}

	private void tickDetonation() {
		if(pulsers.isEmpty())
			detonation = false;
		if(lastPulserCount != pulsers.size())
			detonationTimer = 0;
		lastPulserCount = pulsers.size();
		if(!detonation)
			detonationTimer = 0;
		else {
			detonationTimer++;
			if(detonationTimer == DETONATION_WARNING)
				LeafiaPacket._start(this).__write(packetKeys.PLAY_SOUND.key, 2).__sendToAll();
			if(detonationTimer >= DETONATION_WARNING && detonationTimer % 5 == 0)
				shake(250, "type=smooth", "preset=RUPTURE", "blurDulling*20", "bloomDulling*20", "duration/4", "speed*8", "intensity/16", "range=100");
			if(detonationTimer == DETONATION_TICKS - 1)
				temperature += 25000; // goodbye
			if(detonationTimer == DETONATION_TICKS)
				detonate(pulsers.size());
		}
		pulsers.clear();
	}

	/** The pulse detonators' blast. The block is gone first, as upstream's {@code setBlockToAir}. */
	protected void detonate(int power) {
		BlockPos pos = worldPosition;
		level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
		Vec3 center = Vec3.atCenterOf(pos);
		if(power <= 1) {
			level.playSound(null, pos, LeafiaSounds.machineExplode.get(), SoundSource.BLOCKS, 30, 1);
			ExplosionLarge.spawnParticles(level, center.x, center.y, center.z, 25);
			LCEExplosionNT nt = new LCEExplosionNT(level, null, pos.getX() + 0.5f, pos.getY() + 0.5f, pos.getZ() + 0.5f, 150);
			nt.overrideResolution(24);
			nt.ignoreBlockPoses.add(pos);
			nt.addAttrib(LCEExplosionNT.LCEExAttrib.FIRE);
			nt.addAttrib(LCEExplosionNT.LCEExAttrib.DFC_FALL);
			nt.explode();
		} else {
			int radius = detonationRadius(power);
			if(power <= 4) {
				level.playSound(null, pos, LeafiaSounds.dfc_detonate.get(), SoundSource.BLOCKS, 300, 1);
				DFCNukeParticle nuke = new DFCNukeParticle();
				nuke.radius = radius;
				if(power == 2)
					nuke.maxAge = 20;
				if(power == 3) {
					nuke.flashTime = 200;
					nuke.maxAge = 20 * 2 + 10;
				}
				if(power == 4) {
					nuke.flashTime = 500;
					nuke.maxAge = 20 * 4;
				}
				emit(nuke, radius * 5);
				EntityNukeExplosionMK5 mk5 = EntityNukeExplosionMK5.statFac(level, radius, center.x, center.y, center.z);
				level.addFreshEntity(mk5);
			} else if(power <= 7) {
				level.playSound(null, pos, LeafiaSounds.dfc_detonate.get(), SoundSource.BLOCKS, 300, 1);
				DFCNukeParticle nuke = new DFCNukeParticle();
				nuke.radius = radius;
				EntityBalefire bf = new EntityBalefire(level);
				bf.setPos(center.x, center.y, center.z);
				bf.destructionRange = radius;
				emit(nuke, radius * 5);
				level.addFreshEntity(bf);
			} else {
				EntityNukeExplosionMK3 exp = new EntityNukeExplosionMK3(level);
				exp.setPos(pos.getX(), pos.getY(), pos.getZ());
				exp.destructionRange = radius;
				exp.speed = 25;
				exp.coefficient = 1.0F;
				exp.waste = false;
				shake(300, "type=smooth", "preset=PWR_NEAR", "duration*2", "intensity*1.5", "range=200");
				level.playSound(null, pos, LeafiaSounds.dfc_explode.get(), SoundSource.BLOCKS, 100, 1);
				destroyed = true;
				level.addFreshEntity(exp);
				EntityCloudFleijaRainbow cloud = new EntityCloudFleijaRainbow(level, exp.destructionRange);
				cloud.setPos(pos.getX(), pos.getY(), pos.getZ());
				level.addFreshEntity(cloud);
			}
		}
	}

	// ===================== the server tick =====================

	private void tickServer() {
		tickDetonation();
		if(detonationTimer >= DETONATION_TICKS) return;
		lastStabilizers = stabilizers;
		stabilizers = 0;

		ItemStack catalystA = slots[0];
		ItemStack catalystB = slots[2];
		boolean tags = false;
		double damageA = 100;
		double damageB = 100;

		if(catalystA.getItem() instanceof ItemCatalyst a && catalystB.getItem() instanceof ItemCatalyst b) {
			LeafiaColor col = new LeafiaColor(calcAvgHex(a.getColor(), b.getColor()));
			colorCatalyst = col.toInARGB();
			tags = true;
			damageA = getCatalystDamage(catalystA);
			damageB = getCatalystDamage(catalystB);
			color = col.lerp(new LeafiaColor(level.random.nextFloat(), level.random.nextFloat(), level.random.nextFloat()), Math.pow(Math.max(damageA / 100, damageB / 100), 2))
					.toInARGB();
			hasCore = true;
		} else {
			color = 0;
			hasCore = false;
		}

		expellingSpk = 0;
		if(slots[1].getItem() instanceof ItemAMSCore || slots[1].getItem() == ModItems.glitch.get()) {
			if(tags) {
				meltingPoint = Math.min(LCEItemCatalyst.NO_CATALYST, Math.min(LCEItemCatalyst.getMelting(catalystA), LCEItemCatalyst.getMelting(catalystB)));

				double corePower = getCorePower();
				double coreHeatMod = getCoreHeat();
				long catalystPower = DFCCEStats.getPowerAbs(catalystA) + DFCCEStats.getPowerAbs(catalystB);
				float catalystPowerMod = DFCCEStats.getPowerMod(catalystA) * DFCCEStats.getPowerMod(catalystB);
				float catalystHeatMod = DFCCEStats.getHeatMod(catalystA) * DFCCEStats.getHeatMod(catalystB);
				float catalystFuelMod = DFCCEStats.getFuelMod(catalystA) * DFCCEStats.getFuelMod(catalystB);
				FluidType type0 = tanks[0].getTankType();
				FluidType type1 = tanks[1].getTankType();
				double fillPct0 = tanks[0].getFill() / (double) tanks[0].getMaxFill();
				double fillPct1 = tanks[1].getFill() / (double) tanks[1].getMaxFill();
				double fuelPower = getFuelEfficiency(type0) * getFuelEfficiency(type1);

				double tempRatio = temperature / meltingPoint;
				double energyRatio = containedEnergy / maxEnergy;

				ticks++;
				potentialGain = energyMod;
				if(temperature >= 100) {
					if(!wasActive) {
						wasActive = true;
						if(detonationTimer < DETONATION_TICKS - 1) {
							level.playSound(null, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, LeafiaSounds.fuckingfortnite.get(), SoundSource.BLOCKS, 100, 1);
							shake(400, "type=smooth", "preset=RUPTURE", "duration/4", "blurDulling*2", "intensity/2", "range=350");
							shake(550, "type=smooth", "preset=QUAKE", "intensity/2", "range=500");
							particleTicks = 20;
							FlashParticle flash = new FlashParticle();
							emit(flash, 500);
						}
					}
					if(particleTicks > 0) {
						blast(250);
						particleTicks--;
					}
					double randRange = Math.pow(tempRatio, 0.65) * 10;
					potentialGain += level.random.nextDouble() * randRange / getStabilizationDivAlt() / getStabilizationDiv() + Math.pow(collapsing, 0.666) * 66;
				} else
					wasActive = false;

				int consumption = (int) Math.ceil(Math.pow(incomingSpk * catalystFuelMod * getCoreFuel(), 0.5));
				drain(tanks[0], consumption);
				drain(tanks[1], consumption);

				double boost = catalystPowerMod * energyMod;
				double deltaEnergy = (Math.pow(Math.pow(incomingSpk, 0.666 / 2) + 1, 0.666 / 2) - 1) * 6.666 / 3 * Math.pow(1.2, potentialGain);
				double addition0 = (deltaEnergy * corePower + Math.pow(Math.max(0, incomingSpk - deltaEnergy), 0.9)) * boost * fillPct0 * fillPct1 / 666;
				double addition1 = Math.pow(Math.min(temperature, 10000) / 100, 0.75) * corePower * potentialGain * boost * fillPct0 * fillPct1 * fuelPower / 20 * Math.pow(0.9, potentialGain);
				addition0 = Math.max(addition0, 0);
				addition1 = Math.max(addition1, 0);
				containedEnergy = Math.min(Math.min(containedEnergy + addition0, failsafeLevel) + addition1, failsafeLevel);

				double tgtTemp = temperature;
				tgtTemp += Math.pow(deltaEnergy * 666 * catalystHeatMod, 2 / (1 + stabilization)) * (1 - tempRatio / 2) * coreHeatMod * Math.pow(potentialGain, 0.25);

				double absorbDiv = 0.001;
				for(BlockEntityCoreReceiver absorber : absorbers) absorbDiv += ((IMixinTileEntityCoreReceiver) absorber).leafia$getLevel();

				double collapseAddition = Math.pow(collapsing, 2) * 500_000;
				containedEnergy += Math.max(collapseAddition - addition0 - addition1, 0);

				containedEnergy = Math.min(containedEnergy, failsafeLevel);

				gainedEnergy = containedEnergy;
				double absorbed = Math.pow(containedEnergy, 0.75 + 0.25 * (1 - 1 / (1 + absorbDiv))) * absorbDiv;
				double transferred = 0;
				for(BlockEntityCoreReceiver absorber : absorbers) {
					IMixinTileEntityCoreReceiver mixin = (IMixinTileEntityCoreReceiver) absorber;
					if(finalPhase) {
						mixin.leafia$explode();
						continue;
					}
					long absorb = (long) (absorbed / absorbDiv * mixin.leafia$getLevel() * 1000_000);
					containedEnergy -= absorb / 1000_000.0d;
					transferred += absorb / 1000_000.0d;
					double val = (catalystPower * Math.pow(tempRatio, 0.1) + incomingSpk * 2000_000) / absorbDiv * mixin.leafia$getLevel();
					val = Math.min(val, Long.MAX_VALUE);
					absorber.joules += absorb + (long) val;
				}
				expellingSpk = transferred;
				expelTicks[Math.floorMod(ticks, 20)] = expellingSpk;
				containedEnergy = Math.max(containedEnergy, 0);
				double rdc = 1 - energyRatio;
				tgtTemp -= Math.pow(Math.abs(rdc), 0.5) * Math.signum(rdc) * tempRatio;

				tgtTemp -= Math.max(0, Math.pow(temperature / meltingPoint, 4) * temperature * getStabilizationDivAlt()) * (0.5 + (Math.pow(Math.abs(rdc), 0.01) * Math.signum(rdc)) / 2);
				tgtTemp = Math.min(Math.max(tgtTemp, 0), 5000000);
				double deltaTemp = tgtTemp - temperature;
				if(!finalPhase) {
					double limit = 1000 + level.random.nextInt(1000) + level.random.nextDouble();
					temperature += Math.min(Math.pow(Math.abs(deltaTemp), 0.5) * Math.signum(deltaTemp), limit);
				} else {
					temperature = temperature + level.random.nextInt(5000) + 10000 + level.random.nextDouble();
				}
				temperature = Math.max(temperature, 0);

				if(shockCooldown > 0) shockCooldown--;
				double energyPerShock = 300_000 * 0.75;
				if(containedEnergy >= 100_000 * (level.random.nextInt(150) + 6.66) + 0.5 && shockCooldown <= 0) {
					double count = Math.ceil(containedEnergy / energyPerShock);
					for(int i = 0; i < Math.pow(count, 0.25); i++) shock();
					level.playSound(null, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, LeafiaSounds.mus_sfx_a_lithit.get(), SoundSource.BLOCKS, 6.66f, 1 + (float) level.random.nextGaussian() * 0.1f);
					shake(100, "type=smooth", "preset=RUPTURE", "duration/4", "blurDulling*2", "intensity/2", "range=50");
					shake(150, "type=smooth", "preset=QUAKE", "duration/2", "intensity/4", "range=100");
					containedEnergy = Math.max(containedEnergy - count * energyPerShock, 0);
					shockCooldown = 100 - (int) (90 * Math.pow(collapsing, 1.75));
				}

				double timeToMeltdown = 10;
				double timeToRegen = 30;
				setCatalystDamage(catalystA, Mth.clamp(damageA + (temperature >= LCEItemCatalyst.getMelting(catalystA) ? 5 / timeToMeltdown : -5 / timeToRegen), 0, 100));
				setCatalystDamage(catalystB, Mth.clamp(damageB + (temperature >= LCEItemCatalyst.getMelting(catalystB) ? 5 / timeToMeltdown : -5 / timeToRegen), 0, 100));
			}
		}

		handleOverloadAndExplosion(damageA, damageB);
		accumulateExpellingEnergy();
		wasBoosted = incomingSpk > 0;
		incomingSpk = 0;
		energyMod = 1;
		absorbers.clear();

		if(temperature > 100) vaporization();
		broadcastState();
		heat = 0;
		stabilization = 0;
		if(this.collapsing > 0) {
			Vec3 c = Vec3.atCenterOf(worldPosition);
			List<Entity> list = level.getEntities((Entity) null, new AABB(c, c).inflate(PULL_RANGE), e -> !(e instanceof FallingBlockEntity));
			for(Entity e : list) pull(e);
		}
		this.setChanged();
	}

	/** A catalyst's wear, 0 to 100, from its custom data; 0 for a new one. Upstream's NBT {@code "damage"}. */
	public static double getCatalystDamage(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? 0 : data.copyTag().getDouble("damage");
	}

	public static void setCatalystDamage(ItemStack stack, double damage) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putDouble("damage", damage));
	}

	/** Upstream's {@code drain(n, true)} on an NTM-CE tank: nothing from an empty or untyped tank, never more than is there. */
	private static void drain(FluidTank tank, int amount) {
		if(tank.getTankType() == Fluids.NONE || tank.getFill() == 0) return;
		int taken = Math.min(amount, tank.getFill());
		tank.setFill(tank.getFill() - taken);
	}

	private void accumulateExpellingEnergy() {
		expellingEnergy = 0;
		for(double energy : expelTicks) expellingEnergy += energy;
	}

	/**
	 * The position of a field disturber's protection entry within 300 blocks of {@code (x, y, z)} in
	 * this dimension, pruning expired entries as it walks; null for none. NTM-CE's
	 * {@code EntityNukeExplosionMK3.isJammed} plus the addon's {@code FolkvangrJammers} hook that
	 * remembered which entry it was. The same walk as the base mod's {@code statFacFleija}.
	 */
	@Nullable
	public static BlockPos findJammer(Level level, double x, double y, double z) {
		Iterator<Entry<ATEntry, Long>> it = EntityNukeExplosionMK3.at.entrySet().iterator();
		while(it.hasNext()) {
			Entry<ATEntry, Long> next = it.next();
			if(next.getValue() < level.getGameTime()) {
				it.remove();
				continue;
			}
			ATEntry entry = next.getKey();
			if(!entry.dim().equals(level.dimension().location())) continue;
			Vec3 vec = new Vec3(x - entry.x(), y - entry.y(), z - entry.z());
			if(vec.length() < EntityNukeExplosionMK3.AT_RANGE) return new BlockPos(entry.x(), entry.y(), entry.z());
		}
		return null;
	}

	private void handleOverloadAndExplosion(double damageA, double damageB) {
		if((damageA >= 100 || damageB >= 100) && temperature > 100 || finalPhase) {
			EntityNukeExplosionMK3 exp = null;
			if(jammerPos != null) {
				if(level.getBlockState(jammerPos).getBlock() != ModBlocks.field_disturber.get()) jammerPos = null;
			}
			if(explosionIn < 10 || jammerPos == null) {
				exp = new EntityNukeExplosionMK3(level);
				exp.setPos(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
				exp.destructionRange = 20 + (int) Math.pow(temperature, 0.4);
				exp.speed = 25;
				exp.coefficient = 1.0F;
				exp.waste = false;
			}
			if(jammerPos == null) {
				if(overloadTimer <= 20 * 6) {
					if(overloadTimer == 0) {
						LeafiaPacket._start(this).__write(packetKeys.PLAY_SOUND.key, 2).__sendToAll();
					}
					overloadTimer++;
				} else {
					level.playSound(null, worldPosition.getX() + 0.5f, worldPosition.getY() + 0.5f, worldPosition.getZ() + 0.5f, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 100000.0F, 1.0F);
					level.playSound(null, worldPosition.getX() + 0.5f, worldPosition.getY() + 0.5f, worldPosition.getZ() + 0.5f, LeafiaSounds.actualexplosion.get(), SoundSource.BLOCKS, 50.0F, 1.0F);
					shake(400, "type=smooth", "preset=RUPTURE", "blurDulling*2", "speed*1.5", "duration/2", "range=300");
					blast(250);

					overloadBlast();

					BlockPos jammer = findJammer(level, exp.getX(), exp.getY(), exp.getZ());
					if(jammer == null) {
						destroyed = true;
						level.addFreshEntity(exp);
						EntityCloudFleijaRainbow cloud = new EntityCloudFleijaRainbow(level, exp.destructionRange);
						cloud.setPos(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
						level.addFreshEntity(cloud);
					} else {
						exp.discard();
						jammerPos = jammer;
						if(explosionIn < 0) {
							explosionIn = 120;
							explosionClock = System.currentTimeMillis();
							LeafiaPacket._start(this).__write(packetKeys.PLAY_SOUND.key, 0).__sendToAll();
						}
					}
				}
			}
			if(jammerPos != null) {
				boolean tick = true;
				MinecraftServer server = level.getServer();
				if(server != null) {
					if(!server.isDedicatedServer()) tick = !server.isPaused();
				}
				if(tick) {
					long time = System.currentTimeMillis();
					explosionIn = Math.max(explosionIn - (time - explosionClock) / 1000.0d, 0);
					collapsing = Mth.clamp(1 - explosionIn / 120, 0, 1);
					explosionClock = time;
					if(explosionIn <= 15 && !finalPhase) {
						finalPhase = true;
						shake(400, "type=smooth", "preset=RUPTURE", "blurDulling*2", "speed*1.5", "duration/2", "range=300");
						shake(400, "type=smooth", "preset=QUAKE", "blurDulling*4", "speed*3", "duration=40", "range=300");
						LeafiaPacket._start(this).__write(packetKeys.PLAY_SOUND.key, 3).__sendToAll();

						LCEExplosionNT nt = new LCEExplosionNT(level, null, worldPosition.getX() + 0.5f, worldPosition.getY() + 0.5f, worldPosition.getZ() + 0.5f, 150);
						nt.iterationLimit = 150;
						nt.overrideResolution(24);
						nt.ignoreBlockPoses.add(worldPosition);
						nt.addAttrib(LCEExplosionNT.LCEExAttrib.FIRE);
						nt.addAttrib(LCEExplosionNT.LCEExAttrib.DFC_FALL);
						nt.explode();
						blast(250);
					}
					if(finalPhase) {
						blast(20);
					}
					if(explosionIn <= 0 && exp != null) {
						shake(300, "type=smooth", "preset=PWR_NEAR", "duration*2", "intensity*1.5", "range=200");
						level.playSound(null, worldPosition, LeafiaSounds.dfc_explode.get(), SoundSource.BLOCKS, 100, 1);
						destroyed = true;
						level.addFreshEntity(exp);
						EntityCloudFleijaRainbow cloud = new EntityCloudFleijaRainbow(level, exp.destructionRange);
						cloud.setPos(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
						level.addFreshEntity(cloud);
					}
				}
			}
		} else {
			if(explosionIn >= 0) {
				jammerPos = null;
				explosionIn = -1;
				collapsing = 0;
				LeafiaPacket._start(this).__write(packetKeys.PLAY_SOUND.key, 1).__sendToAll();
			} else if(overloadTimer > 0) {
				LeafiaPacket._start(this).__write(packetKeys.PLAY_SOUND.key, 1).__sendToAll();
				overloadTimer = 0;
			}
		}
	}

	/** The overload's own blast: radius 50, nothing harder than 28 x 3/5 broken, the core's cell spared. */
	protected void overloadBlast() {
		LCEExplosionNT nt = new LCEExplosionNT(level, null, worldPosition.getX() + 0.5f, worldPosition.getY() + 0.5f, worldPosition.getZ() + 0.5f, 50);
		nt.maxExplosionResistance = 28;
		nt.ignoreBlockPoses.add(worldPosition);
		nt.explode();
	}

	@Override
	public int lastPulserPower() {
		return lastPulserPower;
	}

	// ===================== the client tick =====================

	/** Builds the four looped sounds the client keeps. Upstream's {@code validate}. */
	private void makeClientSounds() {
		if(clientSoundsMade) return;
		clientSoundsMade = true;
		float x = worldPosition.getX() + 0.5f, y = worldPosition.getY() + 0.5f, z = worldPosition.getZ() + 0.5f;
		meltdownSFX = LCEAudioProxy.getLoopedSoundStartStop(level, LeafiaSounds.dfc_meltdown.get(), null, null, SoundSource.BLOCKS, x, y, z, 1.0f, 1)
				.setCustomAttenuation((intended, distance) -> Math.pow(Mth.clamp(1 - (distance - 50) / 500, 0, 1), 6.66))
				.setLooped(false);
		extinguishSFX = LCEAudioProxy.getLoopedSoundStartStop(level, SoundEvents.FIRE_EXTINGUISH, null, null, SoundSource.BLOCKS, x, y, z, 1.0f, 0.8f)
				.setCustomAttenuation((intended, distance) -> Math.pow(Mth.clamp(1 - (distance - 50) / 500, 0, 1), 6.66))
				.setLooped(false);
		overloadSFX = LCEAudioProxy.getLoopedSoundStartStop(level, LeafiaSounds.overload.get(), null, null, SoundSource.BLOCKS, x, y, z, 1.0f, 1)
				.setCustomAttenuation((intended, distance) -> Math.pow(Mth.clamp(1 - (distance - 20) / 300, 0, 1), 6.66))
				.setLooped(false);
		explosionsSFX = LCEAudioProxy.getLoopedSoundStartStop(level, LeafiaSounds.longexplosion.get(), null, null, SoundSource.BLOCKS, x, y, z, 1.0f, 1)
				.setCustomAttenuation((intended, distance) -> Math.pow(Mth.clamp(1 - (distance - 50) / 500, 0, 1), 6.66))
				.setLooped(false);
	}

	private void tickClient() {
		makeClientSounds();
		ticks++;
		client_maxDial = level.random.nextDouble() * 0.08 + 0.9;
		lastPulserPower = pulsers.size();
		pulsers.clear();

		if(client_sfx != null) {
			if(temperature >= 100 && !sfxPlaying) {
				sfxPlaying = true;
				client_sfx.startSound();
			} else if(temperature < 100 && sfxPlaying) {
				sfxPlaying = false;
				client_sfx.stopSound();
			}
		}

		if(collapsing > 0.666) com.leafia.client.LeafiaDFCClient.pullLocal(this);
		for(DFCShock shock : dfcShocks) shock.ticks++;
		while(!dfcShocks.isEmpty()) {
			if(dfcShocks.get(0).ticks > 4) dfcShocks.remove(0);
			else break;
		}

		if(temperature > 100) {
			if(client_type == Cores.ams_core_eyeofharmony) {
				float r = (color >> 16 & 255) / 255.0F;
				float g = (color >> 8 & 255) / 255.0F;
				float b = (color & 255) / 255.0F;
				float scale = (float) Math.log(temperature / 50 + 1);
				com.leafia.client.LeafiaDFCClient.eyeOfHarmony(worldPosition, r, g, b, scale);
				angle = angle + lightRotateSpeed;
				if(angle > 360) angle -= 360;
			}
		}

		ringSpinSpeed = 360 / 20.0f;
		if(120 - collapsing * 120 <= 15) finalPhase = true;
		if(collapsing > 0.95) {
			double percent = (collapsing - 0.95) / 0.05;
			ringSpinSpeed += 10800 / 20.0f * (float) percent;
		}
		if(finalPhase) ringAlpha = Mth.clamp(ringAlpha + 0.025f, 0, 1);
		ringAngle = Mth.positiveModulo(ringAngle + ringSpinSpeed, 360);
	}

	// ===================== effects =====================

	/** One lightning path, thrown from the centre in a random direction, that blows up where it lands. */
	public void shock() {
		double rlen = 1;
		double length = 0.5;
		Vec3 core = Vec3.atCenterOf(worldPosition);
		Vec3 p0 = core;
		Vec3 p1 = new FiaMatrix(p0).rotateY(level.random.nextDouble() * 360).rotateX(level.random.nextDouble() * 360)
				.translate(0, 0, length + level.random.nextDouble() * rlen).position;
		DFCShockPacket packet = new DFCShockPacket();
		packet.pos = worldPosition;
		packet.poses0.add(p0);
		packet.poses0.add(p1);
		for(int i = 0; i < 25; i++) {
			p0 = p1;
			p1 = new FiaMatrix(p1, core).translate(level.random.nextGaussian() * 2, level.random.nextGaussian() * 2, length + level.random.nextDouble() * rlen).position;
			BlockHitResult res = LeafiaLib.leafiaRayTraceBlocks(level, p0, p1, false, true, false);
			if(res != null && res.getLocation() != null) {
				p1 = res.getLocation();
				packet.poses0.add(p1);
				level.explode(null, p1.x, p1.y, p1.z, level.random.nextFloat() * 5 + 2, true, Level.ExplosionInteraction.BLOCK);
				break;
			}
			packet.poses0.add(p1);
		}
		LeafiaCustomPacket wire = LeafiaCustomPacket.__start(packet);
		if(wire != null) wire.__sendToAll();
	}

	/** Kills, burns and (in a collapse) takes crucifixes. Upstream's {@code vaporization}. */
	private void vaporization() {
		double scale = (int) Math.log(temperature / 50 + 1) * 1.25 / 4 + 0.5;
		int range = (int) (scale * 4);
		int x = worldPosition.getX(), y = worldPosition.getY(), z = worldPosition.getZ();
		List<Entity> list = level.getEntities((Entity) null, new AABB(x - range + 0.5, y - range + 0.5, z - range + 0.5, x + range + 0.5, y + range + 0.5, z + range + 0.5));
		for(Entity e : list) {
			if(isFixTool(e) && collapsing <= 0) continue; // D-3075: upstream skipped it always, so the crucifix branch below never saw it
			if(e instanceof FallingBlockEntity) continue;
			boolean isPlayer = e instanceof Player;
			if(!(isPlayer && (ArmorUtil.checkForHazmat((Player) e) || ArmorUtil.checkArmor((Player) e, ModItems.hev_helmet.get(), ModItems.hev_plate.get(), ModItems.hev_legs.get(), ModItems.hev_boots.get())))) {
				if(!(Library.isObstructed(level, x + 0.5, y + 0.5 + 6, z + 0.5, e.getX(), e.getY() + e.getEyeHeight(), e.getZ()))) {
					if(!isPlayer || !((Player) e).getAbilities().instabuild)
						e.hurt(LeafiaDamageSources.of(level, LeafiaDamageSources.dfc), (int) (this.temperature / 100));
					e.igniteForSeconds(3);

					if(collapsing > 0 && (isFixTool(e) || isSurvivalFixTool(e))) {
						e.setInvulnerable(false);
						e.discard();
						level.explode(null, x + 0.5, y + 0.5, z + 0.5, 20, false, Level.ExplosionInteraction.NONE);
						if(finalPhase || wasBoosted || isSurvivalFixTool(e) && level.random.nextInt(100) < 80 - lastStabilizers * 10) {
							level.playSound(null, worldPosition, LeafiaSounds.crucifix_fail.get(), SoundSource.BLOCKS, 20, 1);
							continue;
						}
						temperature = 0;
						containedEnergy = 0;
						drain(tanks[0], 1000000000);
						drain(tanks[1], 1000000000);
						level.playSound(null, worldPosition, LeafiaSounds.crucifix.get(), SoundSource.BLOCKS, 20, 1);
						continue;
					}
				}
			}
			// upstream: if (isPlayer) AdvancementManager.grantAchievement(player, progress_dfc) - no advancement tree
		}

		List<Entity> list3 = level.getEntities((Entity) null, new AABB(x + 0.4, y + 0.4, z + 0.4, x + 0.6, y + 0.6, z + 0.6));
		if(collapsing > 0) {
			for(Entity e : list3) {
				e.hurt(LeafiaDamageSources.of(level, LeafiaDamageSources.dfcMeltdown), (int) (this.temperature / 10));
				if(!(e instanceof LivingEntity)) e.discard();
			}
		}
	}

	private void broadcastState() {
		CompoundTag fluidA = new CompoundTag();
		CompoundTag fluidB = new CompoundTag();
		tanks[0].writeToNBT(fluidA, "t0");
		tanks[1].writeToNBT(fluidB, "t1");
		Integer coreId = coreOrdinal(slots[1]);
		LeafiaPacket._start(this)
				.__write(packetKeys.TANK_A.key, fluidA)
				.__write(packetKeys.TANK_B.key, fluidB)
				.__write(packetKeys.TEMP.key, temperature)
				.__write(packetKeys.STABILIZATION.key, stabilization)
				.__write(packetKeys.CONTAINED.key, containedEnergy)
				.__write(packetKeys.EXPELLING.key, expellingEnergy)
				.__write(packetKeys.POTENTIAL.key, potentialGain)
				.__write(packetKeys.EXPEL_TICK.key, expellingSpk)
				.__write(packetKeys.MAXIMUM.key, meltingPoint)
				.__write(packetKeys.COLOR.key, color)
				.__write(packetKeys.COLOR_CATALYST.key, colorCatalyst)
				.__write(packetKeys.CORE_TYPE.key, coreId)
				.__write(packetKeys.JAMMER.key, jammerPos)
				.__write(packetKeys.COLLAPSE.key, collapsing)
				.__write(packetKeys.HASCORE.key, hasCore)
				.__write(packetKeys.DETONATION.key, detonation)
				.__write(packetKeys.DET_TIMER.key, detonationTimer)
				.__sendToAffectedClients();
	}

	/** Which {@link Cores} the stack is, by its registry path as upstream reads it; null for none. */
	@Nullable
	public static Integer coreOrdinal(ItemStack stack) {
		if(stack.isEmpty()) return null;
		try {
			return Cores.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath()).ordinal();
		} catch(IllegalArgumentException ignored) {
			return null;
		}
	}

	private double getPull(Entity e) {
		Vec3 p = Vec3.atCenterOf(worldPosition);
		double distance = p.distanceTo(e.position());
		if(distance > PULL_RANGE) return 0;
		double pull = Mth.clamp(1 - distance / PULL_RANGE, 0, 1);
		pull = Math.pow(pull, 1.5);
		return pull * 0.1 * Math.pow(Math.max(0, collapsing / 0.666 - 1) * 2, 0.5);
	}

	/** Pulls an entity toward the centre; nothing until the collapse is two thirds through. */
	public void pull(Entity e) {
		double pull = getPull(e);
		if(pull <= 0) return;
		Vec3 p = Vec3.atCenterOf(worldPosition);
		Vec3 lookAt = new FiaMatrix(e.position(), p).frontVector;
		e.push(lookAt.x * pull, lookAt.y * pull, lookAt.z * pull);
	}

	private double getStabilizationDiv() {
		return IMixinTileEntityCore.getStabilizationDiv(stabilization);
	}

	private double getStabilizationDivAlt() {
		return Math.pow(stabilization / 3.0, 2.0) * 3.0 + 1.0;
	}

	/** NTM-CE's core power, or upstream's 7,500 for the glitch. See {@link DFCCEStats}. */
	public int getCorePower() {
		if(slots[1].getItem() == ModItems.glitch.get())
			return 7500;
		return DFCCEStats.getPowerBase(slots[1]);
	}

	public float getCoreHeat() {
		if(slots[1].getItem() == ModItems.glitch.get())
			return 0.4F;
		return DFCCEStats.getHeatBase(slots[1]);
	}

	public float getCoreFuel() {
		if(slots[1].getItem() == ModItems.glitch.get())
			return 3;
		return DFCCEStats.getFuelBase(slots[1]);
	}

	boolean isFixTool(Entity e) {
		return e instanceof ItemEntity item && LeafiaItems.fix_tool != null && item.getItem().getItem() == LeafiaItems.fix_tool.get();
	}

	boolean isSurvivalFixTool(Entity e) {
		return e instanceof ItemEntity item && LeafiaItems.fix_survival != null && item.getItem().getItem() == LeafiaItems.fix_survival.get();
	}

	/** Upstream overwrites the base's ten-entry table: the efficiency is the fluid's {@code FT_DFCFuel}, or 0. */
	@Override
	public float getFuelEfficiency(FluidType type) {
		return type.hasTrait(FT_DFCFuel.class) ? type.getTrait(FT_DFCFuel.class).getModifier() : 0;
	}

	private void shake(double playerRange, String... params) {
		if(level instanceof ServerLevel server) LeafiaShakecam.sendNear(server, worldPosition, playerRange, params);
	}

	private void blast(int amount) {
		LeafiaColor col = new LeafiaColor(colorCatalyst);
		emit(new LeafiaParticlePacket.DFCBlastParticle((float) col.red, (float) col.green, (float) col.blue, amount), 200);
	}

	private void emit(LeafiaParticlePacket.LeafiaParticle particle, double range) {
		if(level instanceof ServerLevel server)
			particle.emit(server, Vec3.atCenterOf(worldPosition), new Vec3(0, 1, 0), range);
	}

	// ===================== LeafiaPacketReceiver =====================

	@Override
	public String getPacketIdentifier() { return "dfcore"; }

	@Override
	public void onReceivePacketLocal(byte key, Object value) {
		for(packetKeys pkt : packetKeys.values()) {
			if(key == pkt.key) {
				switch(pkt) {
					case TEMP -> temperature = (double) value;
					case STABILIZATION -> stabilization = (double) value;
					case MAXIMUM -> meltingPoint = (int) value;
					case CONTAINED -> containedEnergy = (double) value;
					case EXPELLING -> expellingEnergy = (double) value;
					case POTENTIAL -> potentialGain = (double) value;
					case TANK_A -> tanks[0].readFromNBT((CompoundTag) value, "t0");
					case TANK_B -> tanks[1].readFromNBT((CompoundTag) value, "t1");
					case EXPEL_TICK -> expellingSpk = (double) value;
					case COLOR -> color = (int) value;
					case COLOR_CATALYST -> colorCatalyst = (int) value;
					case JAMMER -> jammerPos = (BlockPos) value;
					case CORE_TYPE -> {
						Cores lastCore = client_type;
						Integer id = (Integer) value;
						client_type = id == null ? null : Cores.values()[id];
						if(client_type != lastCore) {
							if(client_sfx != null) {
								client_sfx.stopSound();
								client_sfx = null;
								sfxPlaying = false;
							}
							if(client_type != null && level != null) {
								client_sfx = LCEAudioProxy.getLoopedSoundStartStop(level, client_type.sfx.get(), null, null, SoundSource.BLOCKS,
										worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), 1, 1)
										.setCustomAttenuation(client_type.attentuationFunction);
							}
						}
					}
					case PLAY_SOUND -> {
						if(value == null) break;
						makeClientSounds();
						int type = (int) value;
						if(type == 0 || type == 1) {
							if(meltdownSFX != null) meltdownSFX.stopSound();
							if(overloadSFX != null && type == 1) overloadSFX.stopSound();
							if(type == 0 && meltdownSFX != null) meltdownSFX.startSound();
							if(type == 1 && extinguishSFX != null) extinguishSFX.startSound();
						} else if(type == 2 && overloadSFX != null) {
							overloadSFX.startSound();
						} else if(type == 3 && explosionsSFX != null) {
							explosionsSFX.startSound();
							finalPhase = true;
						} else if(type == 4 && overloadSFX != null)
							overloadSFX.stopSound();
					}
					case COLLAPSE -> collapsing = (double) value;
					case HASCORE -> hasCore = (boolean) value;
					case DETONATION -> detonation = (boolean) value;
					case DET_TIMER -> detonationTimer = (int) value;
				}
			}
		}
	}

	@Override
	public void onReceivePacketServer(byte key, Object value, Player plr) { }

	@Override
	public void onPlayerValidate(Player plr) { }

	@Override
	public double affectionRange() { return 300; }

	// ===================== boilerplate =====================

	@Override public double getDFCFailsafeLevel() { return failsafeLevel; }
	@Override public double getDFCMaxEnergy() { return maxEnergy; }
	@Override public boolean isDFCHasCore() { return hasCore; }
	@Override public void setDFCHasCore(boolean v) { hasCore = v; }
	@Override public double getDFCTemperature() { return temperature; }
	@Override public void setDFCTemperature(double v) { temperature = v; }
	@Override public double getDFCStabilization() { return stabilization; }
	@Override public void setDFCStabilization(double v) { stabilization = v; }
	@Override public double getDFCContainedEnergy() { return containedEnergy; }
	@Override public void setDFCContainedEnergy(double v) { containedEnergy = v; }
	@Override public double getDFCExpellingEnergy() { return expellingEnergy; }
	@Override public void setDFCExpellingEnergy(double v) { expellingEnergy = v; }
	@Override public double getDFCPotentialGain() { return potentialGain; }
	@Override public void setDFCPotentialGain(double v) { potentialGain = v; }
	@Override public double getDFCGainedEnergy() { return gainedEnergy; }
	@Override public void setDFCGainedEnergy(double v) { gainedEnergy = v; }
	@Override public double getDFCCollapsing() { return collapsing; }
	@Override public void setDFCCollapsing(double v) { collapsing = v; }
	@Override public int getDFCStabilizers() { return stabilizers; }
	@Override public void setDFCStabilizers(int v) { stabilizers = v; }
	@Override public int getDFCLastStabilizers() { return lastStabilizers; }
	@Override public void setDFCLastStabilizers(int v) { lastStabilizers = v; }
	@Override public boolean isDFCWasBoosted() { return wasBoosted; }
	@Override public void setDFCWasBoosted(boolean v) { wasBoosted = v; }
	@Override public double[] getDFCExpelTicks() { return expelTicks; }
	@Override public void setDFCExpelTicks(double[] v) { expelTicks = v; }
	@Override public double getDFCEnergyMod() { return energyMod; }
	@Override public void setDFCEnergyMod(double v) { energyMod = v; }
	@Override public double getDFCBonus() { return bonus; }
	@Override public void setDFCBonus(double v) { bonus = v; }
	@Override public List<BlockEntityCoreReceiver> getDFCAbsorbers() { return absorbers; }
	@Override public void setDFCAbsorbers(List<BlockEntityCoreReceiver> v) { absorbers = v; }
	@Override public List<CoreDetonatorTE> getDFCPulsers() { return pulsers; }
	@Override public boolean isDFCDestroyed() { return destroyed; }
	@Override public void setDFCDestroyed(boolean v) { destroyed = v; }
	@Override public double getDFCExplosionIn() { return explosionIn; }
	@Override public void setDFCExplosionIn(double v) { explosionIn = v; }
	@Override public long getDFCExplosionClock() { return explosionClock; }
	@Override public void setDFCExplosionClock(long v) { explosionClock = v; }
	@Override public BlockPos getDFCJammerPos() { return jammerPos; }
	@Override public void setDFCJammerPos(BlockPos v) { jammerPos = v; }
	@Override public List<BlockPos> getDFCComponentPositions() { return componentPositions; }
	@Override public void setDFCComponentPositions(List<BlockPos> v) { componentPositions = v; }
	@Override public List<BlockPos> getDFCPrevComponentPositions() { return prevComponentPositions; }
	@Override public void setDFCPrevComponentPositions(List<BlockPos> v) { prevComponentPositions = v; }
	@Override public double getDFCIncomingSpk() { return incomingSpk; }
	@Override public void setDFCIncomingSpk(double v) { incomingSpk = v; }
	@Override public double getDFCExpellingSpk() { return expellingSpk; }
	@Override public void setDFCExpellingSpk(double v) { expellingSpk = v; }
	@Override public int getDFCMeltingPoint() { return meltingPoint; }
	@Override public void setDFCMeltingPoint(int v) { meltingPoint = v; }
	@Override public int getDFCTicks() { return ticks; }
	@Override public void setDFCTicks(int v) { ticks = v; }
	@Override public int getDFCOverloadTimer() { return overloadTimer; }
	@Override public void setDFCOverloadTimer(int v) { overloadTimer = v; }
	@Override public int getDFCColorCatalyst() { return colorCatalyst; }
	@Override public void setDFCColorCatalyst(int v) { colorCatalyst = v; }
	@Override public int getDFCShockCooldown() { return shockCooldown; }
	@Override public void setDFCShockCooldown(int v) { shockCooldown = v; }
	@Override public double getDFCClientMaxDial() { return client_maxDial; }
	@Override public void setDFCClientMaxDial(double v) { client_maxDial = v; }
	@Override public Cores getDFCClientType() { return client_type; }
	@Override public void setDFCClientType(Cores v) { client_type = v; }
	@Override public LCEAudioWrapper getDFCClientSfx() { return client_sfx; }
	@Override public void setDFCClientSfx(LCEAudioWrapper v) { client_sfx = v; }
	@Override public boolean isDFCSfxPlaying() { return sfxPlaying; }
	@Override public void setDFCSfxPlaying(boolean v) { sfxPlaying = v; }
	@Override public LCEAudioWrapper getDFCMeltdownSFX() { return meltdownSFX; }
	@Override public void setDFCMeltdownSFX(LCEAudioWrapper v) { meltdownSFX = v; }
	@Override public LCEAudioWrapper getDFCOverloadSFX() { return overloadSFX; }
	@Override public void setDFCOverloadSFX(LCEAudioWrapper v) { overloadSFX = v; }
	@Override public LCEAudioWrapper getDFCExtinguishSFX() { return extinguishSFX; }
	@Override public void setDFCExtinguishSFX(LCEAudioWrapper v) { extinguishSFX = v; }
	@Override public LCEAudioWrapper getDFCExplosionsSFX() { return explosionsSFX; }
	@Override public void setDFCExplosionsSFX(LCEAudioWrapper v) { explosionsSFX = v; }
	@Override public float getDFCAngle() { return angle; }
	@Override public void setDFCAngle(float v) { angle = v; }
	@Override public float getDFCLightRotateSpeed() { return lightRotateSpeed; }
	@Override public void setDFCLightRotateSpeed(float v) { lightRotateSpeed = v; }
	@Override public boolean isDFCFinalPhase() { return finalPhase; }
	@Override public void setDFCFinalPhase(boolean v) { finalPhase = v; }
	@Override public float getDFCRingSpinSpeed() { return ringSpinSpeed; }
	@Override public void setDFCRingSpinSpeed(float v) { ringSpinSpeed = v; }
	@Override public float getDFCRingAngle() { return ringAngle; }
	@Override public void setDFCRingAngle(float v) { ringAngle = v; }
	@Override public float getDFCRingAlpha() { return ringAlpha; }
	@Override public void setDFCRingAlpha(float v) { ringAlpha = v; }
	@Override public List<DFCShock> getDfcShocks() { return dfcShocks; }
}
