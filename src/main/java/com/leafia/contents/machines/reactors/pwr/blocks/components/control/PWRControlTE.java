package com.leafia.contents.machines.reactors.pwr.blocks.components.control;

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

import java.util.HashMap;
import java.util.Map;

import com.custom_hbm.sound.LCEAudioProxy;
import com.custom_hbm.sound.LCEAudioWrapper;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.machines.reactors.pwr.blocks.components.PWRAssignableEntity;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.init.LeafiaSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A control rod's block entity, on the top block of its column: where the rod is
 * ({@link #position}, 0 fully inserted .. 1 fully withdrawn, as a fraction of the column), where it is
 * going ({@link #targetPosition}), and which group it answers to ({@link #name}).
 *
 * <p>Upstream: {@code com/leafia/contents/machines/reactors/pwr/blocks/components/control/PWRControlTE.java}.
 * The rod travels {@code speed / height} of its column per tick: {@code 0.22 / 20} blocks for the steel
 * {@code lwr_control}, {@code 0.1 / 20} for the lead {@code reactor_control} - so a taller rod takes
 * proportionally longer. Its debug {@code Tracker} trace ({@code bluk}) is not carried, and the CE control
 * panel's {@code IControllable} is stripped with its values kept (D-1093 #8):
 * {@link #receiveEvent(String, double)} and {@link #queryValues()}.
 */
public class PWRControlTE extends PWRAssignableEntity implements IControllable {

	public int height = 1;
	public double position = 0;
	public String name = defaultName;
	public static final String defaultName = "00.Unsorted";
	LCEAudioWrapper sound = null;

	public double speed = -1;
	public double targetPosition = 0;
	boolean initialTick = true;

	public PWRControlTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public void updateHeight() {
		if(!this.isRemoved() && level != null && level.isLoaded(worldPosition)) {
			BlockPos downPos = worldPosition.below();
			height = 1;
			while(level.isInWorldBounds(downPos)) {
				if(level.getBlockState(downPos).getBlock() instanceof PWRControlBlock) height++;
				else break;
				downPos = downPos.below();
			}
		}
	}

	/** Upstream's {@code connectUpper}: a rod with another rod block above it is not the top. */
	boolean underAnotherRod() {
		return level.getBlockState(worldPosition.above()).getBlock() instanceof PWRControlBlock;
	}

	@Override
	protected void loadAdditional(CompoundTag compound, HolderLookup.Provider registries) {
		if(compound.contains("rodP")) position = compound.getDouble("rodP");
		if(compound.contains("rodD")) targetPosition = compound.getDouble("rodD");
		if(compound.contains("name")) name = compound.getString("name");
		super.loadAdditional(compound, registries);
	}

	/** Upstream's name, typo and all. */
	public CompoundTag writeControlDateToNBT() {
		CompoundTag nbt = new CompoundTag();
		nbt.putDouble("rodP", position);
		nbt.putDouble("rodD", targetPosition);
		nbt.putString("name", name);
		nbt.putInt("x", worldPosition.getX());
		nbt.putInt("y", worldPosition.getY());
		nbt.putInt("z", worldPosition.getZ());
		return nbt;
	}

	public void readControlDataFromNBT(CompoundTag nbt) {
		position = nbt.getDouble("rodP");
		targetPosition = nbt.getDouble("rodD");
		name = nbt.getString("name");
	}

	@Override
	protected void saveAdditional(CompoundTag compound, HolderLookup.Provider registries) {
		compound.putDouble("rodP", position);
		compound.putDouble("rodD", targetPosition);
		compound.putString("name", name);
		super.saveAdditional(compound, registries);
	}

	@Override
	public void setRemoved() {
		ControllableLifecycle.leave(this);
		stopSound();
		super.setRemoved();
	}

	@Override
	public void onChunkUnloaded() {
		ControllableLifecycle.leave(this);
		stopSound();
		super.onChunkUnloaded();
	}

	private void stopSound() {
		if(sound != null) sound.stopSound();
		sound = null;
	}

	/** The rod's own speed, in blocks per tick, by its block. */
	public static double speedOf(Block block) {
		return LeafiaBlocks.lwr_control != null && block == LeafiaBlocks.lwr_control.get() ? 0.22 / 20 : 0.1 / 20;
	}

	@Override
	public void tick() {
		if(underAnotherRod()) return;
		if(initialTick) {
			initialTick = false;
			updateHeight();
		}
		super.tick();
		if(speed <= 0) speed = speedOf(getBlockState().getBlock());
		if(level.isClientSide) {
			if(sound != null) {
				if(targetPosition == position) stopSound();
			} else if(targetPosition != position) {
				sound = LCEAudioProxy.getLoopedSoundStartStop(level, LeafiaSounds.pwrRodLoop.get(), LeafiaSounds.pwrRodStart.get(), LeafiaSounds.pwrRodStop.get(),
						SoundSource.BLOCKS, worldPosition.getX() + 0.5f, worldPosition.getY() + 0.5f, worldPosition.getZ() + 0.5f, 0.0175f, 0.75f);
				sound.startSound();
			}
		} else {
			if(targetPosition != position) {
				if(Math.abs(targetPosition - position) < speed / height)
					position = targetPosition;
				else
					position += Math.signum(targetPosition - position) * speed / height;
				syncLocals();
				this.setChanged();
			}
		}
	}

	public LeafiaPacket.Builder generateSyncPacket() {
		return LeafiaPacket._start(this).__write(0, position).__write(1, targetPosition);
	}

	public void syncLocals() {
		generateSyncPacket().__sendToAffectedClients();
	}

	@Override
	public String getPacketIdentifier() {
		return "PWR_CTRL";
	}

	@Override
	public void onReceivePacketLocal(byte key, Object value) {
		switch(key) {
			case 0: position = (double) value; break;
			case 1: targetPosition = (double) value; break;
			case 2: name = (String) value; break;
		}
		super.onReceivePacketLocal(key, value);
	}

	@Override
	public void onPlayerValidate(Player plr) {
		LeafiaPacket.Builder packet = generateSyncPacket().__write(!name.equals(defaultName) ? 2 : -1, name);
		addDataToPacket(packet);
		packet.__sendToClient(plr);
	}

	/** The control panel's in-event {@code pwr_ctrl_set_level}, in percent. */
	public void receiveEvent(String event, double level) {
		if(event.equals("pwr_ctrl_set_level")) targetPosition = Mth.clamp(level / 100d, 0, 1);
	}

	/** The control panel's query value {@code level}: the rod's position in percent. */
	public Map<String, Float> queryValues() {
		Map<String, Float> map = new HashMap<>();
		map.put("level", (float) (position * 100));
		return map;
	}

	// ---- the Custom Control Panel (CCP-RETROFIT, D-3069) ----

	/** Upstream's one query key, from {@link #queryValues()}. */
	@Override
	public Map<String, DataValue> getQueryData() {
		return ControllableLifecycle.floats(queryValues());
	}

	@Override
	public List<String> getInEvents() {
		return Collections.singletonList("pwr_ctrl_set_level");
	}

	/** {@code level} is a percent, held to 0..100; a NaN changes nothing. */
	@Override
	public void receiveEvent(BlockPos from, ControlEvent e) {
		double level = ControllableLifecycle.number(e, "level");
		if(!Double.isNaN(level))
			receiveEvent(e.name, level);
	}

	@Override public BlockPos getControlPos() { return worldPosition; }
	@Override public Level getControlWorld() { return level; }

	@Override
	public void onLoad() {
		super.onLoad();
		ControllableLifecycle.join(this);
	}
}
