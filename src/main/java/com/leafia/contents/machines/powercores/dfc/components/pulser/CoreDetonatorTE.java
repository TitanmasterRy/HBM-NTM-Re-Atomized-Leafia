package com.leafia.contents.machines.powercores.dfc.components.pulser;

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

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.custom_hbm.sound.LCEAudioProxy;
import com.custom_hbm.sound.LCEAudioWrapper;
import api.hbm.energymk2.IEnergyReceiverMK2;
import com.hbm.blockentity.BlockEntityMachineBase;
import com.hbm.blockentity.machine.BlockEntityCore;
import com.hbm.blocks.machine.CoreComponent;
import com.leafia.config.LeafiaConfig;
import com.leafia.contents.machines.powercores.dfc.IDFCBase;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.init.LeafiaSounds;
import com.leafia.overwrite_contents.interfaces.IMixinTileEntityCore;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The DFC pulse detonator: a keypad-locked switch that, with enough of them armed at once, turns a
 * Dark Fusion Core into a bomb of a size you choose.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/powercores/dfc/components/pulser/CoreDetonatorTE.java},
 * on the block {@code dfc_pulser} - upstream's {@code AddonCoreComponent}, here the base mod's own
 * {@link CoreComponent} with this block entity's type passed in (the base block was built to take one,
 * D-968), so the pulser faces and opens a menu exactly as the base components do.
 *
 * <h2>What it costs</h2>
 *
 * {@link #consumption}, <b>5 trillion HE a tick</b>, while the core it points at is counting down, and
 * it buffers five ticks of that. A detonator that cannot pay a tick cancels the whole count - the core's
 * {@code detonation} flag is shared, so one poor detonator stops all of them. Each armed detonator
 * pointed at the core counts toward the blast's size ({@code LeafiaCoreTE.detonationRadius}), and the
 * count restarts whenever the number of detonators changes.
 *
 * <h2>The keypad</h2>
 *
 * A fresh detonator takes whatever code is typed first and keeps it; after that the screen unlocks only
 * for that code. The switches send the code along with the command, and the server authorises the
 * player for the rest of that one packet. Upstream's clean-up loop removes any authorisation older than
 * five ticks <em>in the future</em> - every one, every tick - which is why a session never outlives its
 * packet; kept as written.
 *
 * <h2>Translations</h2>
 *
 * The target is set from the block's own facing on the first server tick when nothing has set it,
 * which is what upstream's placement hook ({@code MixinCoreComponent.onBlockPlacedBy}, DFC-3) does with
 * the same piston rule; a designator can move it once DFC-3 lands. {@code IControllable} (the custom
 * control panel's {@code set_pulser_active}, {@code set_pulser_detonation} and its query data) is not
 * ported (D-1093 #8). The client half - the particles, the hum - runs from the addon's client ticker,
 * since the base block ticks only on the server.
 */
public class CoreDetonatorTE extends BlockEntityMachineBase implements IDFCBase, IEnergyReceiverMK2, IControllable {

	public long power = 0;
	public boolean isOn = true;
	String code = "";
	public boolean local$codeSet = false;
	@Nullable
	LCEAudioWrapper leafia$sound;
	boolean leafia$isPlaying = false;
	public static final long consumption = 5_000_000_000_000L;

	protected BlockPos targetPosition = BlockPos.ZERO;
	protected boolean targetSet = false;
	@Nullable
	public BlockEntityCore lastGetCore = null;
	final Map<Player, Long> authorized = new ConcurrentHashMap<>();

	public CoreDetonatorTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state, 0);
	}

	@Override
	public String getName() {
		return "tile.dfc_pulser.name";
	}

	@Override public long getPower() { return power; }
	@Override public void setPower(long l) { power = l; }
	@Override public long getMaxPower() { return consumption * 5; }

	@Override
	public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
		return new CoreDetonatorMenu(id, playerInventory, this);
	}

	// ===== IDFCBase =====

	@Override public BlockEntityCore leafia$lastGetCore() { return lastGetCore; }
	@Override public void leafia$lastGetCore(BlockEntityCore core) { lastGetCore = core; }
	@Override public BlockPos leafia$getTargetPosition() { return targetPosition; }
	@Override public void leafia$targetPosition(BlockPos pos) { targetPosition = pos; targetSet = true; }

	@Override
	public String getPacketIdentifier() {
		return "DFC_PULSER";
	}

	/** Whether this detonator can pay a tick of the count. */
	public boolean isConditionMet() {
		return power >= consumption;
	}

	@Override
	public void onReceivePacketServer(byte key, Object value, Player plr) {
		IDFCBase.super.onReceivePacketServer(key, value, plr);
		if(key == 30) {
			boolean accepted = tryCode((String) value);
			LeafiaPacket._start(this).__write(30, accepted).__sendToClient(plr);
			if(accepted) return;
		}
		if(key == 0) authorize(plr, value);
		if(authorized.containsKey(plr)) {
			switch(key) {
				case 1 -> isOn = (boolean) value;
				case 2 -> {
					BlockEntityCore core = leafia$getCore(LeafiaConfig.dfcComponentRange);
					if(core instanceof IMixinTileEntityCore mixin) {
						boolean charge = !mixin.getDetonation();
						if(charge) {
							if(isConditionMet())
								mixin.setDetonation(charge);
						} else
							mixin.setDetonation(charge);
					}
				}
				default -> { }
			}
		}
	}

	@Override
	public void onReceivePacketLocal(byte key, Object value) {
		IDFCBase.super.onReceivePacketLocal(key, value);
		if(key == 30) com.leafia.client.LeafiaDFCClient.pulserCodeAnswer((boolean) value);
		switch(key) {
			case 0 -> local$codeSet = (boolean) value;
			case 1 -> power = (long) value;
			case 2 -> isOn = (boolean) value;
			default -> { }
		}
	}

	/**
	 * Upstream's key-30 answer, split out so it can be asked without a client: a fresh detonator takes
	 * the first code it is given and says yes; after that, yes only to the same code.
	 */
	public boolean tryCode(String c) {
		if(!code.isEmpty())
			return code.equals(c);
		code = c;
		setChanged();
		return true;
	}

	/** Upstream's key 0: the player may command this detonator for the rest of this packet if the code matches. */
	public void authorize(Player plr, Object value) {
		if(code.equals(value))
			authorized.put(plr, level.getGameTime());
	}

	public boolean isAuthorized(Player plr) {
		return authorized.containsKey(plr);
	}

	/** The code a player has to type; empty until the first one is entered. */
	public String getCode() {
		return code;
	}

	@Override
	protected void saveAdditional(CompoundTag compound, HolderLookup.Provider registries) {
		super.saveAdditional(compound, registries);
		leafia$writeTargetPos(compound);
		compound.putString("code", code);
		compound.putBoolean("isOn", isOn);
		compound.putLong("power", power);
	}

	@Override
	protected void loadAdditional(CompoundTag compound, HolderLookup.Provider registries) {
		super.loadAdditional(compound, registries);
		if(compound.contains("x1")) {
			targetPosition = new BlockPos(compound.getInt("x1"), compound.getInt("y1"), compound.getInt("z1"));
			targetSet = true;
		}
		if(compound.contains("code"))
			code = compound.getString("code");
		isOn = compound.getBoolean("isOn");
		power = compound.getLong("power");
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
		if(leafia$sound != null) {
			leafia$sound.stopSound();
			leafia$sound = null;
		}
		super.setRemoved();
	}

	@Override
	public void onChunkUnloaded() {
		ControllableLifecycle.leave(this);
		super.onChunkUnloaded();
		if(leafia$sound != null) {
			leafia$sound.stopSound();
			leafia$sound = null;
		}
	}

	@Override
	public void updateEntity() {
		if(level.isClientSide) {
			localTick(leafia$getCore(LeafiaConfig.dfcComponentRange));
			return;
		}
		if(!targetSet) {
			BlockState state = getBlockState();
			if(state.hasProperty(CoreComponent.FACING))
				leafia$targetPosition(worldPosition.relative(state.getValue(CoreComponent.FACING)));
		}
		BlockEntityCore core = leafia$getCore(LeafiaConfig.dfcComponentRange);
		Iterator<Entry<Player, Long>> it = authorized.entrySet().iterator();
		while(it.hasNext()) {
			if(it.next().getValue() < level.getGameTime() + 5)
				it.remove();
		}
		LeafiaPacket._start(this).__write(KEY_TARGET, targetPosition).__sendToAffectedClients();
		this.autoPort(ALL_AROUND);
		if(isOn && core instanceof IMixinTileEntityCore mixin) {
			mixin.getDFCPulsers().add(this);
			if(mixin.getDetonation()) {
				if(!isConditionMet())
					mixin.setDetonation(false);
				else
					power -= consumption;
			}
		}
		LeafiaPacket._start(this)
				.__write(0, !code.isEmpty())
				.__write(1, power)
				.__write(2, isOn)
				.__sendToAffectedClients();
	}

	/** The client half: register with the core, draw the charge, play the hum. */
	void localTick(@Nullable BlockEntityCore core) {
		boolean active = false;
		if(isOn && core instanceof IMixinTileEntityCore mixin) {
			mixin.getDFCPulsers().add(this);
			if(mixin.getDetonation()) {
				active = true;
				com.leafia.client.LeafiaDFCClient.pulserParticles(this, mixin.getDetonationTimer() >= 20 * (30 - 6));
			}
		}
		if(active)
			playSound();
		else
			stopSound();
	}

	private void playSound() {
		if(leafia$sound == null) {
			leafia$sound = LCEAudioProxy.getLoopedSoundStartStop(level, LeafiaSounds.hspActive.get(), LeafiaSounds.hspIgnite.get(), LeafiaSounds.laser1stop.get(),
					SoundSource.BLOCKS, worldPosition.getX() + 0.5f, worldPosition.getY() + 0.5f, worldPosition.getZ() + 0.5f, 0.25f, 1)
					.setCustomAttenuation((intended, distance) -> {
						double div = 4; // alright fUCK OFF
						if(leafia$getCore(LeafiaConfig.dfcComponentRange) instanceof IMixinTileEntityCore mixin)
							div = Math.max(div, mixin.getDFCPulsers().size());
						return Math.pow(Math.max(0, 1 - distance / 50), 6) / div;
					});
		}
		if(!leafia$isPlaying)
			leafia$sound.startSound();
		leafia$isPlaying = true;
	}

	private void stopSound() {
		if(leafia$sound == null) return;
		if(leafia$isPlaying)
			leafia$sound.stopSound();
		leafia$isPlaying = false;
	}

	public long getPowerScaled(long i) {
		return (power * i) / getMaxPower();
	}

	public int getDetScaled(int i) {
		int det = 0;
		if(leafia$getCore(LeafiaConfig.dfcComponentRange) instanceof IMixinTileEntityCore core)
			det = core.getDetonationTimer();
		return (det * i) / (20 * 30);
	}

	@Override
	public double affectionRange() {
		return 32;
	}

	// ---- the Custom Control Panel (CCP-RETROFIT, D-3069) ----

	/** Upstream's three query keys; {@code timeRemaining} is the 30-second count less what the core has run. */
	@Override
	public Map<String, DataValue> getQueryData() {
		float timeRemaining = 30;
		float charging = 0;
		if(leafia$getCore(LeafiaConfig.dfcComponentRange) instanceof IMixinTileEntityCore mixin) {
			timeRemaining = 30 - mixin.getDetonationTimer() / 20f;
			if(mixin.getDetonation())
				charging = 1;
		}
		return ControllableLifecycle.values("active", isOn ? 1 : 0, "charging", charging, "timeRemaining", timeRemaining);
	}

	@Override
	public List<String> getInEvents() {
		return Arrays.asList("set_pulser_active", "set_pulser_detonation");
	}

	/**
	 * Upstream's two in-events, each behind the code the screen asks for (a panel that does not know the code changes nothing, and
	 * a fresh detonator's empty code matches an empty one, as the screen's own first-code rule does). Charging goes through the
	 * same {@link #isConditionMet} gate as the screen's button.
	 */
	@Override
	public void receiveEvent(BlockPos from, ControlEvent e) {
		DataValue codeVar = e.vars.get("code");
		if(codeVar == null || !codeVar.toString().equals(code))
			return;
		if(e.name.equals("set_pulser_active")) {
			DataValue active = e.vars.get("active");
			if(active != null)
				isOn = active.getNumber() >= 1f;
		} else if(e.name.equals("set_pulser_detonation")) {
			DataValue chargeVar = e.vars.get("charge");
			if(chargeVar == null)
				return;
			boolean charge = chargeVar.getNumber() >= 1f;
			if(leafia$getCore(LeafiaConfig.dfcComponentRange) instanceof IMixinTileEntityCore mixin) {
				if(charge) {
					if(isConditionMet())
						mixin.setDetonation(true);
				} else
					mixin.setDetonation(false);
			}
		}
	}

	@Override public BlockPos getControlPos() { return worldPosition; }
	@Override public Level getControlWorld() { return level; }
}
