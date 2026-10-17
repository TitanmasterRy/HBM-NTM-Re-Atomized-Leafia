package com.leafia.contents.machines.powercores.dfc.components.absorber;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.leafia.contents.machines.controlpanel.core.ControlEvent;
import com.leafia.contents.machines.controlpanel.core.ControllableLifecycle;
import com.leafia.contents.machines.controlpanel.core.IControllable;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

import com.hbm.blockentity.machine.BlockEntityCore;
import com.hbm.blockentity.machine.BlockEntityCoreReceiver;
import com.hbm.blocks.ModBlocks;
import com.hbm.blocks.machine.CoreComponent;
import com.hbm.explosion.ExplosionLarge;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.util.DirPos;
import com.leafia.config.LeafiaConfig;
import com.leafia.contents.machines.powercores.dfc.IDFCClientTicked;
import com.leafia.contents.machines.powercores.dfc.components.DFCParts;
import com.leafia.contents.machines.powercores.dfc.debris.AbsorberShrapnelEntity;
import com.leafia.contents.machines.powercores.dfc.debris.AbsorberShrapnelEntity.DebrisType;
import com.leafia.dev.custompacket.PacketGuard;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.dev.math.FiaMatrix;
import com.leafia.init.LeafiaSounds;
import com.leafia.overwrite_contents.interfaces.IMixinTileEntityCore;
import com.leafia.overwrite_contents.interfaces.IMixinTileEntityCoreReceiver;
import com.llib.LeafiaLib.NumScale;

import api.hbm.fluidmk2.IFluidStandardTransceiverMK2;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The overhauled DFC receiver ("absorber"), and the reinforced absorber: it aims at a core, takes its
 * share of the core's contained energy, turns it into power at 5,000 HE a spark and sends it out two
 * ways - HE to every neighbour, and SPK over the cable out of its back.
 *
 * <p>Upstream: {@code com/leafia/overwrite_contents/mixin/mod/hbm/MixinTileEntityCoreReceiver.java}, which
 * overwrites NTM-CE's receiver {@code update}; the reinforced absorber ({@code dfc_reinforced}) is the
 * same block entity in an addon block ({@code MixinCoreComponent.onCreateNewTileEntity}). Here a subclass
 * of the base mod's {@link BlockEntityCoreReceiver} under its own type, valid for both blocks, put into
 * the base block by {@code BlockEntityOverrides} while {@code overhauls.dfcOverhaul} is on (D-3014).
 *
 * <h2>What changes with the overhaul</h2>
 *
 * <ul>
 * <li><b>It is paid by the core, not by a beam.</b> Each tick it finds the core it points at and joins
 *     the core's absorber list; the core puts its share into {@code joules} ({@code LeafiaCoreTE}), at the
 *     receiver's {@link #leafia$getLevel() level} (0 to 1, typed into its screen).</li>
 * <li><b>Power accumulates.</b> {@code power += joules * 5,000}, clamped at {@code Long.MAX_VALUE}, and is
 *     not reset each tick: the base receiver's power is this tick's only. The HE goes to every neighbour
 *     every tick (D-969: a provide loop, hand-rolled), and {@code power / 5,000} SPK goes out of the back
 *     ({@code tryProvideSPK} on the block behind it), each SPK taken costing 5,000 HE.</li>
 * <li><b>It strains.</b> At 100 billion SPK in one tick the plain absorber's {@code destructionLevel}
 *     climbs by 1 or 2 (to 400) and, past 300, it blows apart one tick in a hundred; below that it falls
 *     by one a tick. The reinforced absorber does not strain.</li>
 * <li><b>Blowing apart</b> ({@link #leafia$explode}) removes it, throws eight pieces of shrapnel, a burst,
 *     a sound and a radius-2 fire explosion. A beam that strikes it from any side but its front does this,
 *     and so does a collapsing core.</li>
 * <li><b>Cooling</b> is the emitter's: cryogel or cold perfluoromethyl, into a 64,000 mB output tank, or
 *     molten DFC component - but only on a tick it was paid.</li>
 * </ul>
 *
 * <h2>Upstream oddity, kept (rule 4)</h2>
 *
 * Its SPK subscription is to <b>its own position</b> ({@code trySubscribeSPK(world, pos, f)}, not the
 * neighbour's), which is never a cable, so the absorber never takes SPK from a cable. It still provides
 * SPK, and an emitter's beam can still pay it directly. Pinned by
 * {@code theAbsorberNeverSubscribesToACable}.
 *
 * <h2>Translations</h2>
 *
 * It registers with the core on the server only: upstream also adds itself to the client core's list,
 * which nothing on the client clears, so that list grew for as long as the chunk was loaded. A client
 * leak avoided; nothing reads the client list. The screen's level is clamped to 0..1 on arrival
 * (D-1094: a modified client could otherwise set any level, the screen clamps). {@code IControllable}
 * and OpenComputers are D-1093 #8.
 */
public class LeafiaCoreReceiverTE extends BlockEntityCoreReceiver
		implements IMixinTileEntityCoreReceiver, IFluidStandardTransceiverMK2, IDFCClientTicked, IControllable {

	/** Joules in one tick at which a plain absorber starts to strain: 100 billion. */
	public static final long STRAIN_JOULES = NumScale.GIGA * 100L;

	public FluidTank output = new FluidTank(DFCParts.pyrogel(), DFCParts.OUTPUT_TANK);
	@Nullable
	public BlockEntityCore core = null;
	public double absorbLevel = 1;
	public int destructionLevel = 0;

	protected BlockPos targetPosition = BlockPos.ZERO;
	protected boolean targetSet = false;
	@Nullable
	protected BlockEntityCore lastGetCore = null;

	/** What this tick paid, for the screen; upstream's {@code syncJoules}. */
	public long syncJoules;
	/** What the cable took last tick, synced; upstream's {@code syncSpk}. */
	public long syncSpk = 0;
	long cableTransfer = 0;

	// client
	public int fanAngle = 0;
	public double joulesPerSec = 0;
	final long[] tickJoules = new long[20];
	int needle = 0;

	public LeafiaCoreReceiverTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	// ===================== the tick =====================

	@Override
	public void updateEntity() {
		Level world = getLevel();
		if(world == null) return;
		if(world.isClientSide) {
			leafia$clientTick();
			return;
		}
		aimIfUnaimed();
		core = null;
		Direction facing = leafia$getFront();
		long remaining = power / 5000L;
		if(remaining > 0) {
			Direction outFace = facing.getOpposite();
			tryProvideSPK(world, worldPosition.relative(outFace), outFace, false);
		}
		core = leafia$getCore(LeafiaConfig.dfcComponentRange);
		if(core instanceof IMixinTileEntityCore mcore)
			mcore.getDFCAbsorbers().add(this);

		LeafiaPacket._start(this).__write(KEY_TARGET, targetPosition).__sendToAffectedClients();
		if(joules >= STRAIN_JOULES && world.getBlockState(worldPosition).getBlock() == ModBlocks.dfc_receiver.get()) {
			destructionLevel = Math.min(destructionLevel + world.random.nextInt(2) + 1, 400);
			if(destructionLevel > 300 && world.random.nextInt(100) == 0) {
				this.leafia$explode();
				return;
			}
		} else {
			destructionLevel = Math.max(destructionLevel - 1, 0);
		}

		updateSPKConnections();
		if(Long.MAX_VALUE - power < joules * 5000L)
			power = Long.MAX_VALUE;
		else
			power += joules * 5000L;

		for(DirPos pos : ALL_AROUND) this.trySubscribe(tank.getTankType(), world, pos);
		// A provide loop is the transfer itself, so it is hand-rolled every tick rather than autoPort's (D-969).
		for(DirPos pos : ALL_AROUND) this.tryProvide(world, pos);

		if(joules > 0) {
			if(DFCParts.coolOrMelt(world, worldPosition, tank, output)) return;
		}

		for(DirPos pos : ALL_AROUND) this.tryProvide(output, world, pos);

		syncJoules = joules;
		joules = 0;
		CompoundTag compound = new CompoundTag();
		tank.writeToNBT(compound, "t");
		output.writeToNBT(compound, "o");
		LeafiaPacket._start(this).__write(2, this.absorbLevel).__write(4, cableTransfer).__write(5, compound).__sendToAffectedClients();
		cableTransfer = 0;
		this.markChanged();
	}

	/** Upstream's placement hook, taken on the first tick instead (as the emitter). */
	protected void aimIfUnaimed() {
		if(targetSet) return;
		BlockState state = getBlockState();
		if(state.hasProperty(CoreComponent.FACING))
			leafia$targetPosition(worldPosition.relative(state.getValue(CoreComponent.FACING)));
	}

	@Override
	public void leafia$clientTick() {
		lastGetCore = leafia$getCore(LeafiaConfig.dfcComponentRange);
		tickJoules[needle] = joules;
		needle = Math.floorMod(needle + 1, 20);
		joulesPerSec = 0;
		for(long tickJoule : tickJoules)
			joulesPerSec += Math.max(0, tickJoule - syncSpk) / 20d;
		fanAngle += Math.floorMod(720 / 20, 360);
	}

	/** Upstream's, with its own position where the cable's should be - the class note's oddity. */
	public void updateSPKConnections() {
		for(Direction f : Direction.values())
			if(isInputPreferrable(f)) trySubscribeSPK(getLevel(), worldPosition, f);
	}

	// ===================== blowing apart =====================

	@Override
	public void leafia$explode() {
		Level world = getLevel();
		if(world == null || world.isClientSide) return;
		BlockPos pos = worldPosition;
		Direction facing = getBlockState().hasProperty(CoreComponent.FACING) ? getBlockState().getValue(CoreComponent.FACING) : Direction.NORTH;
		world.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
		for(int i = 0; i < 3; i++) spawnShrapnel(world, pos, facing, DebrisType.BEAM);
		for(int i = 0; i < 2; i++) spawnShrapnel(world, pos, facing, DebrisType.CORNER);
		spawnShrapnel(world, pos, facing, DebrisType.CABLE);
		spawnShrapnel(world, pos, facing, DebrisType.CORE);
		spawnShrapnel(world, pos, facing, DebrisType.FRONT);
		ExplosionLarge.spawnBurst(world, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 12, 3);
		world.playSound(null, pos.getX(), pos.getY(), pos.getZ(), LeafiaSounds.machineExplode.get(), SoundSource.BLOCKS, 10.0F, 1);
		world.explode(null, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, 2f, true, Level.ExplosionInteraction.BLOCK);
	}

	/** Upstream's {@code spawnShrapnel}: each kind leaves at its own angle off the block's facing. */
	public static void spawnShrapnel(Level world, BlockPos pos, Direction facing, DebrisType type) {
		Vec3 center = Vec3.atCenterOf(pos);
		FiaMatrix mat = new FiaMatrix(center, center.add(Vec3.atLowerCornerOf(facing.getNormal())));
		AbsorberShrapnelEntity entity = new AbsorberShrapnelEntity(world, mat.getX(), mat.getY(), mat.getZ(), type);
		double forward = 0.35;
		double spread = 0.15;
		switch(type) {
			case CABLE -> {
				mat = mat.rotateX(90).rotateY(world.random.nextInt(4) * 90);
				spread = 0.2;
			}
			case CORE -> {
				entity.setDeltaMovement(world.random.nextGaussian() * 0.05, world.random.nextGaussian() * 0.05, world.random.nextGaussian() * 0.05);
				world.addFreshEntity(entity);
				return;
			}
			case CORNER -> mat = mat.rotateX(world.random.nextInt(4) * 90 - 45).rotateY(world.random.nextInt(2) * 90 - 45);
			case FRONT -> spread = 0.2;
			case BEAM -> {
				int rand = world.random.nextInt(12);
				if(rand < 8) mat = mat.rotateY(Math.floorDiv(rand, 2) * 90).rotateX(Math.floorMod(rand, 2) * 90 - 45);
				else mat = mat.rotateY((rand - 8) * 90 - 45);
			}
		}
		Vec3 flyDirection = mat.frontVector.scale((world.random.nextDouble() + 1) / 2 * forward)
				.add(mat.upVector.scale(world.random.nextGaussian() * spread)).add(mat.rightVector.scale(world.random.nextGaussian() * spread));
		entity.setPos(mat.getX() + mat.frontVector.x / 3, mat.getY() + mat.frontVector.y / 3, mat.getZ() + mat.frontVector.z / 3);
		entity.setDeltaMovement(flyDirection);
		world.addFreshEntity(entity);
	}

	// ===================== SPK =====================

	/** Upstream's: a side whose axis carries more than 0.707 of the aim, on the far side from the target. */
	@Override
	public boolean isInputPreferrable(Direction dir) {
		Vec3 unit = leafia$getDirection();
		double component;
		if(dir.getAxis() == Direction.Axis.X) component = unit.x;
		else if(dir.getAxis() == Direction.Axis.Y) component = unit.y;
		else component = unit.z;
		component *= dir.getOpposite().getAxisDirection().getStep();
		return component > 0.707;
	}

	@Override public long getSPK() { return joules; }
	@Override public void setSPK(long power) { this.joules = power; }
	@Override public long getMaxSPK() { return Long.MAX_VALUE; }
	@Override public long getSendable() { return power / 5000; }

	@Override
	public void setTransferredSpk(long power) {
		cableTransfer += power;
		this.power = Math.max(this.power - power * 5000, 0);
	}

	// ===================== tanks =====================

	@Override public FluidTank[] getAllTanks() { return new FluidTank[] { tank, output }; }
	@Override public FluidTank[] getSendingTanks() { return new FluidTank[] { output }; }
	@Override public FluidTank[] getReceivingTanks() { return new FluidTank[] { tank }; }
	@Override public FluidTank leafia$getOutputTank() { return output; }

	// ===================== NBT, packets, menu =====================

	@Override
	protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.loadAdditional(nbt, registries);
		if(nbt.contains("x1")) {
			targetPosition = new BlockPos(nbt.getInt("x1"), nbt.getInt("y1"), nbt.getInt("z1"));
			targetSet = true;
		}
		this.absorbLevel = nbt.getDouble("level");
		if(nbt.contains("output")) output.readFromNBT(nbt, "output");
	}

	@Override
	protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.saveAdditional(nbt, registries);
		leafia$writeTargetPos(nbt);
		nbt.putDouble("level", this.absorbLevel);
		output.writeToNBT(nbt, "output");
	}

	@Override
	public String getPacketIdentifier() {
		return "dfc_absorber";
	}

	@Override
	public void leafia$sendToPlayer(Player player) {
		LeafiaPacket._start(this)
				.__write(0, syncJoules)
				.__write(1, power)
				.__write(2, this.absorbLevel)
				.__sendToClient(player);
	}

	@Override
	public void onReceivePacketLocal(byte key, Object value) {
		IMixinTileEntityCoreReceiver.super.onReceivePacketLocal(key, value);
		switch(key) {
			case 0 -> joules = (long) value;
			case 1 -> power = (long) value;
			case 2 -> this.absorbLevel = (double) value;
			case 5 -> {
				CompoundTag tag = (CompoundTag) value;
				tank.readFromNBT(tag, "t");
				output.readFromNBT(tag, "o");
			}
			case 4 -> syncSpk = (long) value;
			default -> { }
		}
	}

	@Override
	public void onReceivePacketServer(byte key, Object value, Player plr) {
		IMixinTileEntityCoreReceiver.super.onReceivePacketServer(key, value, plr);
		if(key != 0) return;
		// D-1094: the level comes from this absorber's open screen, in reach, as a number the screen
		// already clamped to 0..1; clamped again here, because the screen is not the authority.
		if(!(plr != null && plr.containerMenu instanceof CoreReceiverMenu menu && menu.receiver == this)) {
			PacketGuard.reject("dfc_absorber level", plr, "this absorber's screen is not open");
			return;
		}
		if(!(value instanceof Double d) || d.isNaN()) {
			PacketGuard.reject("dfc_absorber level", plr, "level " + value);
			return;
		}
		this.absorbLevel = Mth.clamp(d, 0, 1);
		this.markChanged();
	}

	/** The screen's power column in pixels: this tick's pay over 100 billion SPK, of 52. Here so a server test can ask. */
	public static int joulesColumn(long joules) {
		return (int) (Mth.clamp(joules / (double) STRAIN_JOULES, 0, 1) * 52);
	}

	@Override
	public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
		return new CoreReceiverMenu(id, playerInventory, this);
	}

	@Override
	public void onLoad() {
		ControllableLifecycle.join(this);
		super.onLoad();
		if(getLevel() != null && getLevel().isClientSide) com.leafia.client.LeafiaDFCClient.track(this);
	}

	// ===================== the interface =====================

	@Override public double leafia$getLevel() { return this.absorbLevel; }
	@Override public void leafia$setLevel(double value) { this.absorbLevel = value; }
	@Override @Nullable public BlockEntityCore leafia$getCore() { return core; }
	@Override public int leafia$fanAngle() { return fanAngle; }
	@Override public double leafia$joulesPerSec() { return joulesPerSec; }
	@Override public long leafia$syncJoules() { return syncJoules; }
	@Override public int leafia$destructionLevel() { return destructionLevel; }
	@Override public long leafia$syncSpk() { return syncSpk; }
	@Override @Nullable public BlockEntityCore leafia$lastGetCore() { return lastGetCore; }
	@Override public void leafia$lastGetCore(@Nullable BlockEntityCore c) { lastGetCore = c; }
	@Override public BlockPos leafia$getTargetPosition() { return targetPosition; }
	@Override public void leafia$targetPosition(BlockPos pos) { targetPosition = pos; targetSet = true; }

	// ---- the Custom Control Panel (CCP-RETROFIT, D-3069) ----

	/** Upstream's four query keys: {@code level} in percent, {@code stress} as a percent of the 300 that breaks it. */
	@Override
	public Map<String, DataValue> getQueryData() {
		return ControllableLifecycle.values("level", (float) (absorbLevel * 100), "stress", destructionLevel * 100 / 300f,
				"received", syncJoules, "power", power);
	}

	@Override
	public List<String> getInEvents() {
		return Collections.singletonList("set_absorber_level");
	}

	/** {@code level} is a percent, held to the screen's 0..1 (a modified client could otherwise set any level, D-1094); NaN changes nothing. */
	@Override
	public void receiveEvent(BlockPos from, ControlEvent e) {
		if(e.name.equals("set_absorber_level")) {
			double fraction = ControllableLifecycle.fraction(e, "level");
			if(!Double.isNaN(fraction))
				absorbLevel = fraction;
		}
	}

	@Override public BlockPos getControlPos() { return worldPosition; }
	@Override public Level getControlWorld() { return level; }

	@Override
	public void setRemoved() {
		ControllableLifecycle.leave(this);
		super.setRemoved();
	}

	@Override
	public void onChunkUnloaded() {
		ControllableLifecycle.leave(this);
		super.onChunkUnloaded();
	}
}
