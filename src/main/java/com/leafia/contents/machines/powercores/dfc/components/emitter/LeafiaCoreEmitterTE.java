package com.leafia.contents.machines.powercores.dfc.components.emitter;

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

import java.util.List;

import javax.annotation.Nullable;

import com.hbm.blockentity.machine.BlockEntityCore;
import com.hbm.blockentity.machine.BlockEntityCoreEmitter;
import com.hbm.blockentity.machine.BlockEntityCoreReceiver;
import com.hbm.blocks.machine.CoreComponent;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.lib.ModDamageSource;
import com.leafia.config.LeafiaConfig;
import com.leafia.contents.machines.powercores.dfc.IDFCClientTicked;
import com.leafia.contents.machines.powercores.dfc.components.DFCParts;
import com.leafia.contents.network.spk_cable.uninos.ISPKReceiver;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.overwrite_contents.interfaces.IMixinTileEntityCore;
import com.leafia.overwrite_contents.interfaces.IMixinTileEntityCoreEmitter;
import com.leafia.overwrite_contents.interfaces.IMixinTileEntityCoreReceiver;
import com.llib.LeafiaLib;

import api.hbm.fluidmk2.IFluidStandardTransceiverMK2;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The overhauled DFC emitter ("booster"): fed SPK over the cable as well as power, it fires its beam
 * at a <em>target</em> rather than along its facing, hands the beam to the first SPK receiver or core on
 * the line, and cools itself into a second tank.
 *
 * <p>Upstream: {@code com/leafia/overwrite_contents/mixin/mod/hbm/MixinTileEntityCoreEmitter.java}, which
 * overwrites NTM-CE's emitter {@code update}. Here a subclass of the base mod's
 * {@link BlockEntityCoreEmitter} under its own type, put into the base block by {@code BlockEntityOverrides}
 * while {@code overhauls.dfcOverhaul} is on (D-3011, D-3014).
 *
 * <h2>What changes with the overhaul</h2>
 *
 * <ul>
 * <li><b>Power and SPK both make sparks.</b> {@code joules} is the SPK buffer ({@link #getSPK}); an SPK
 *     cable on any side but the back (the side opposite the block's facing) fills it, and the setting's
 *     {@code watts * 100} is added on top when the {@code watts * 500,000} HE are there.</li>
 * <li><b>The beam is a ray to the target</b>, up to {@code dfcComponentRange} (50) blocks, through the
 *     addon's block raytrace, so it can run diagonally. The base beam's 5% loss is gone: all of
 *     {@code joules} is fired.</li>
 * <li><b>What it hits:</b> an SPK receiver that accepts the side gets {@code joules * 100 * watts / 10,000}
 *     SPK and ends the beam; a DFC absorber struck from any other side is blown apart; a core gets
 *     {@code joules / 1,000,000} MSPK of {@code incomingSpk}; a liquid hisses away; any other block under
 *     10,000 resistance breaks on {@code rand / 20 < joules * 1e-8 / resistance}, without drops.</li>
 * <li><b>Every cell</b> it passes (its own included) burns what stands in it: {@code joules * 1e-6} of
 *     {@code amsCore} damage and ten seconds of fire.</li>
 * <li><b>Cooling</b> is cryogel 20 mB or cold perfluoromethyl 2,000 mB a tick into the output tank; with
 *     neither, the emitter melts into molten DFC component (lava when the world fluids are off).</li>
 * </ul>
 *
 * <h2>Translations</h2>
 *
 * The block's state still has the base mod's {@code FACING}; the first server tick with no saved target
 * aims one block along it, which is upstream's placement hook ({@code MixinCoreComponent.onBlockPlacedBy}).
 * The base screen keeps reading {@code networkPackNT}, which is sent beside upstream's own packets. Power
 * and coolant subscriptions and the output tank go through {@code autoPort} (D-300, D-969: all three are
 * subscriptions or a fluid net). {@code IControllable} and OpenComputers are D-1093 #8. A beam that meets a
 * base mod core (one placed before the overhaul was switched on, so not an addon core) stops there and
 * feeds it nothing: upstream cannot have such a core.
 */
public class LeafiaCoreEmitterTE extends BlockEntityCoreEmitter
		implements IMixinTileEntityCoreEmitter, IFluidStandardTransceiverMK2, IDFCClientTicked, IControllable {

	/** Below this blast resistance the beam can break a block: upstream's {@code hardness < 10000}. */
	public static final float BREAKABLE_BELOW = 10000F;

	public FluidTank output = new FluidTank(DFCParts.pyrogel(), DFCParts.OUTPUT_TANK);

	protected BlockPos targetPosition = BlockPos.ZERO;
	protected boolean targetSet = false;
	@Nullable
	protected BlockHitResult lastRaycast;
	@Nullable
	protected BlockEntityCore lastGetCore;
	protected boolean active;
	protected final DFCParts.Hum hum = new DFCParts.Hum(true);

	public LeafiaCoreEmitterTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	// ===================== the tick =====================

	@Override
	public void updateEntity() {
		if(level.isClientSide) {
			leafia$clientTick();
			return;
		}
		aimIfUnaimed();
		LeafiaPacket._start(this).__write(KEY_TARGET, targetPosition).__sendToAffectedClients();

		this.autoPort(ALL_AROUND);
		this.updateSPKConnections();

		watts = Mth.clamp(watts, 1, 100);
		long demand = maxPower * Math.min(watts, 100) / 2000;
		active = false;

		beam = 0;

		if(joules > 0 || prev > 0) {
			if(DFCParts.coolOrMelt(level, worldPosition, tank, output)) return;
		}

		if(isOn) {
			//i.e. 50,000,000 HE = 10,000 SPK
			//1 SPK = 5,000HE
			if(power >= demand) {
				power -= demand;
				long add = watts * 100L;
				if(add > Long.MAX_VALUE - joules)
					joules = Long.MAX_VALUE;
				else
					joules += add;
			}
			prev = joules;

			if(joules > 0) {
				long out = joules;
				active = true;
				leafia$raycast(out);
				joules = 0;
			}
		} else {
			joules = 0;
			prev = 0;
		}

		this.markChanged();

		CompoundTag compound = new CompoundTag();
		tank.writeToNBT(compound, "t");
		output.writeToNBT(compound, "o");
		LeafiaPacket._start(this)
				.__write(0, isOn)
				.__write(1, watts)
				.__write(2, prev)
				.__write(3, active)
				.__write(4, compound)
				.__write(5, power)
				.__sendToAffectedClients();
		this.networkPackNT(250);
	}

	/** Upstream's placement hook, taken on the first tick instead (class note). */
	protected void aimIfUnaimed() {
		if(targetSet) return;
		BlockState state = getBlockState();
		if(state.hasProperty(CoreComponent.FACING))
			leafia$targetPosition(worldPosition.relative(state.getValue(CoreComponent.FACING)));
	}

	@Override
	public void leafia$clientTick() {
		if(isOn) {
			hum.play(level, worldPosition);
			lastRaycast = leafia$raycast(0);
		} else
			hum.stop();
	}

	/** Upstream's {@code updateSPKConnections}: a subscription to the cable on every side the emitter takes SPK from. */
	protected void updateSPKConnections() {
		for(Direction dir : Direction.values()) {
			if(isInputPreferrable(dir))
				this.trySubscribeSPK(level, worldPosition.relative(dir), dir);
		}
	}

	// ===================== the beam =====================

	/**
	 * Upstream's ray, case for case. On the client it is only traced (for the renderer's
	 * {@link #leafia$lastRaycast}); damage, SPK, the core's intake and block breaking are server-side,
	 * as upstream's {@code !world.isRemote} guards have them. The absorber's destruction and the core's
	 * component list are not guarded upstream and are not here.
	 */
	@Override
	public BlockHitResult leafia$raycast(long out) {
		Vec3 start = Vec3.atCenterOf(worldPosition);
		return LeafiaLib.<BlockHitResult>leafiaRayTraceBlocksCustom(level, start,
				start.add(leafia$getDirection().scale(LeafiaConfig.dfcComponentRange)), (process, config, current) -> {
			if(!level.isClientSide) {
				Vec3 centerVec = current.posIntended.add(Vec3.atLowerCornerOf(config.pivotAxisFace.getNormal()).scale(0.5)
						.add(config.secondaryVector.scale(0.5)));
				List<Entity> list = level.getEntitiesOfClass(Entity.class,
						new AABB(centerVec.subtract(0.5, 0.5, 0.5), centerVec.add(0.5, 0.5, 0.5)));
				for(Entity e : list) {
					e.hurt(level.damageSources().source(ModDamageSource.amsCore), joules * 0.000001F);
					e.igniteForSeconds(10);
				}
			}
			if(current.posSnapped.equals(worldPosition)) return process.CONTINUE();

			BlockHitResult miss = BlockHitResult.miss(current.posIntended, config.pivotAxisFace, current.posSnapped);
			BlockState state = current.state;
			if(!canCollideCheck(state, current.posSnapped))
				return process.CONTINUE(miss);

			BlockHitResult result = collisionRayTrace(state, current.posSnapped,
					current.posIntended.subtract(config.unitDir.scale(2)), current.posIntended.add(config.unitDir.scale(2)));
			if(result == null)
				return process.CONTINUE(miss);

			Vec3 vec = result.getLocation();
			BlockEntity te = level.getBlockEntity(current.posSnapped);
			if(te instanceof ISPKReceiver receiver) {
				if(receiver.isInputPreferrable(config.pivotAxisFace)) {
					if(!level.isClientSide) receiver.transferSPK(out * 100 * watts / 10000, false);
					return process.RETURN(result);
				} else if(te instanceof BlockEntityCoreReceiver && te instanceof IMixinTileEntityCoreReceiver absorber) {
					absorber.leafia$explode();
				}
			}

			if(te instanceof BlockEntityCore) {
				if(!(te instanceof IMixinTileEntityCore mixin)) return process.RETURN(result); // a base core (class note)
				if(!level.isClientSide) mixin.setDFCIncomingSpk(mixin.getDFCIncomingSpk() + out / 1000_000d);
				mixin.getDFCComponentPositions().add(worldPosition);
				// align to the center (upstream's comment; it is the core's corner)
				return process.RETURN(new BlockHitResult(Vec3.atLowerCornerOf(te.getBlockPos()), result.getDirection(), result.getBlockPos(), result.isInside()));
			}

			if(!state.isAir()) {
				if(!level.isClientSide) {
					if(state.liquid()) {
						level.playSound(null, vec.x, vec.y, vec.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 1.0F);
						level.setBlock(result.getBlockPos(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
						return process.RETURN(result);
					}
					float hardness = state.getBlock().getExplosionResistance();
					if(hardness < BREAKABLE_BELOW && level.random.nextDouble() / 20 < (out * 0.00000001F) / hardness) {
						level.playSound(null, vec.x, vec.y, vec.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 1.0F);
						BlockPos at = result.getBlockPos();
						level.getBlockState(at).onBlockExploded(level, at, new Explosion(level, null, at.getX(), at.getY(), at.getZ(), 5, false, Explosion.BlockInteraction.DESTROY));
					}
				}
				return process.RETURN(result);
			} else
				return process.CONTINUE(result);
		});
	}

	/**
	 * 1.12.2's {@code canCollideCheck(state, true)}: false for air and a flowing liquid only, the reading
	 * {@code IDFCBase.leafia$getCore} uses (D-3011).
	 */
	protected boolean canCollideCheck(BlockState state, BlockPos pos) {
		if(state.isAir()) return false;
		FluidState fluid = state.getFluidState();
		return fluid.isEmpty() || fluid.isSource() || !state.getShape(level, pos).isEmpty();
	}

	/** 1.12.2's {@code collisionRayTrace} against the selection box; a still liquid is a full cube, as its 1.12.2 box. */
	@Nullable
	protected BlockHitResult collisionRayTrace(BlockState state, BlockPos pos, Vec3 from, Vec3 to) {
		VoxelShape shape = state.getShape(level, pos);
		if(shape.isEmpty() && !state.getFluidState().isEmpty()) shape = Shapes.block();
		return shape.clip(from, to, pos);
	}

	// ===================== SPK =====================

	/** Upstream's {@code side.getOpposite().ordinal() != getBlockMetadata()}: every side but the back. */
	@Override
	public boolean isInputPreferrable(Direction side) {
		return side.getOpposite() != beamDirection();
	}

	@Override public long getSPK() { return joules; }
	@Override public void setSPK(long power) { joules = power; }
	@Override public long getMaxSPK() { return Long.MAX_VALUE; }

	// ===================== tanks =====================

	@Override public FluidTank[] getAllTanks() { return new FluidTank[] { tank, output }; }
	@Override public FluidTank[] getSendingTanks() { return new FluidTank[] { output }; }
	@Override public FluidTank[] getReceivingTanks() { return new FluidTank[] { tank }; }
	@Override public FluidTank leafia$getOutputTank() { return output; }

	// ===================== NBT and packets =====================

	@Override
	protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.loadAdditional(nbt, registries);
		if(nbt.contains("x1")) {
			targetPosition = new BlockPos(nbt.getInt("x1"), nbt.getInt("y1"), nbt.getInt("z1"));
			targetSet = true;
		}
		if(nbt.contains("output")) output.readFromNBT(nbt, "output");
	}

	@Override
	protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.saveAdditional(nbt, registries);
		leafia$writeTargetPos(nbt);
		output.writeToNBT(nbt, "output");
	}

	@Override
	public String getPacketIdentifier() {
		return "DFC_BOOSTER";
	}

	@Override
	public void onReceivePacketLocal(byte key, Object value) {
		IMixinTileEntityCoreEmitter.super.onReceivePacketLocal(key, value);
		switch(key) {
			case 0 -> isOn = (boolean) value;
			case 1 -> watts = (int) value;
			case 2 -> prev = (long) value;
			case 3 -> active = (boolean) value;
			case 4 -> {
				CompoundTag tag = (CompoundTag) value;
				tank.readFromNBT(tag, "t");
				output.readFromNBT(tag, "o");
			}
			case 5 -> power = (long) value;
			default -> { }
		}
	}

	@Override
	public void onLoad() {
		ControllableLifecycle.join(this);
		super.onLoad();
		if(level != null && level.isClientSide) com.leafia.client.LeafiaDFCClient.track(this);
	}

	@Override
	public void setRemoved() {
		ControllableLifecycle.leave(this);
		hum.release();
		super.setRemoved();
	}

	@Override
	public void onChunkUnloaded() {
		ControllableLifecycle.leave(this);
		super.onChunkUnloaded();
		hum.release();
	}

	// ===================== IDFCBase / the interface =====================

	@Override @Nullable public BlockEntityCore leafia$lastGetCore() { return lastGetCore; }
	@Override public void leafia$lastGetCore(@Nullable BlockEntityCore core) { lastGetCore = core; }
	@Override public BlockPos leafia$getTargetPosition() { return targetPosition; }
	@Override public void leafia$targetPosition(BlockPos pos) { targetPosition = pos; targetSet = true; }
	@Override public boolean leafia$isActive() { return active; }
	@Override public void leafia$isActive(boolean active) { this.active = active; }
	@Override @Nullable public BlockHitResult leafia$lastRaycast() { return lastRaycast; }

	// ---- the Custom Control Panel (CCP-RETROFIT, D-3069) ----

	/** Upstream's three query keys: {@code level} is the watts dial, {@code emitted} what the last shot sent. */
	@Override
	public Map<String, DataValue> getQueryData() {
		return ControllableLifecycle.values("active", isOn ? 1 : 0, "level", watts, "emitted", prev);
	}

	@Override
	public List<String> getInEvents() {
		return Arrays.asList("set_booster_level", "set_booster_active");
	}

	/** {@code level} is held to the dial's 1..100, which {@code updateEntity} clamps to anyway; a NaN changes nothing. */
	@Override
	public void receiveEvent(BlockPos from, ControlEvent e) {
		if(e.name.equals("set_booster_active")) {
			DataValue active = e.vars.get("active");
			if(active != null)
				isOn = active.getNumber() >= 1f;
		} else if(e.name.equals("set_booster_level")) {
			int level = ControllableLifecycle.clampedInt(e, "level", 1, 100);
			if(level != Integer.MIN_VALUE)
				watts = level;
		}
	}

	@Override public BlockPos getControlPos() { return worldPosition; }
	@Override public Level getControlWorld() { return level; }
}
