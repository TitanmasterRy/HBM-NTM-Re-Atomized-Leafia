package com.leafia.contents.machines.reactors.lftr.components.control;

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

import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.machines.reactors.lftr.components.element.MSRElementTE;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.dev.container_utility.LeafiaPacketReceiver;
import com.llib.group.LeafiaSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The LFTR control rod: a rod that slides out of the block's front into the reaction chambers in
 * line with it, and the extension tubes behind it that say how far it can go.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/reactors/lftr/components/control/MSRControlTE.java}.
 *
 * <h2>How far, and how fast</h2>
 *
 * The rod's travel ({@link #length}) is the number of {@code msr_control_extension} blocks stacked
 * straight <em>behind</em> the rod along its own axis, counted each tick. A screwdriver sets the
 * target in quarter metres (sneak to insert, otherwise withdraw), and the rod moves toward it at
 * {@link #speed} - 0.1 m a second, 0.005 a tick.
 *
 * <h2>What it restricts</h2>
 *
 * While the rod is moving, every reaction chamber in front of it within {@code length} blocks that is
 * free (or already this rod's) is claimed, and the chamber {@code i} blocks out is restricted by
 * {@code clamp(insertion - i + 1, 0, 1)} - so a rod 1.5 in fully restricts the first chamber and half
 * the second, and a reaction involving a chamber restricted to 1 adds no heat and burns no fuel. A chamber another rod holds stops
 * the walk; anything that is not a chamber is passed over. The insertion is then capped at the number
 * of chambers it reached. While the rod is still, nothing is re-evaluated, and a chamber keeps the
 * restriction it was last given.
 *
 * <h2>What is not here</h2>
 *
 * {@code IControllable} (the control panel's {@code lftr_ctrl_set_level} event and {@code level}
 * query) is stripped, per D-1093 #8. Upstream's own {@code invalidate} does not release the chambers
 * ("fuck off asshole", commented out), so breaking a rod leaves them restricted and claimed until
 * another rod moves past them; kept.
 */
public class MSRControlTE extends BlockEntity implements LeafiaPacketReceiver, IControllable {

	public double insertion = 0;
	public double targetInsertion = 0;
	public int length = 0;

	public static final double speed = 0.1 / 20;

	final LeafiaSet<MSRElementTE> TEs = new LeafiaSet<>();

	public MSRControlTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public String getPacketIdentifier() {
		return "LFTR_CTRL";
	}

	public Direction getDirection() {
		BlockState state = level.getBlockState(worldPosition);
		if(state.getBlock() instanceof MSRControlBlock)
			return state.getValue(MSRControlBlock.FACING);
		return Direction.NORTH;
	}

	/** Extension tubes straight behind the rod, along its own axis. */
	public int getExtensionLength(Direction facing) {
		facing = facing.getOpposite();
		BlockPos pos = this.worldPosition.relative(facing);
		int length = 0;
		while(level.isInWorldBounds(pos)) {
			BlockState state = level.getBlockState(pos);
			if(LeafiaBlocks.msr_control_extension != null && state.getBlock() == LeafiaBlocks.msr_control_extension.get()) {
				if(state.getValue(RotatedPillarBlock.AXIS).equals(facing.getAxis())) {
					pos = pos.relative(facing);
					length++;
					continue;
				}
			}
			break;
		}
		return length;
	}

	void clearTEs() {
		for(MSRElementTE te : TEs) {
			if(te.control != null && te.control.equals(worldPosition)) {
				te.control = null;
				te.restriction = 0;
			}
		}
		TEs.clear();
	}

	public static void tick(Level level, BlockPos pos, BlockState state, MSRControlTE control) {
		control.update();
	}

	public void update() {
		if(level == null || level.isClientSide) return;
		Direction direction = getDirection();
		length = getExtensionLength(direction);
		if(targetInsertion != insertion) {
			if(Math.abs(targetInsertion - insertion) < speed)
				insertion = targetInsertion;
			else {
				insertion += Math.signum(targetInsertion - insertion) * speed;
			}
			clearTEs();
			int actualLength = 0;
			for(int i = 1; i <= length; i++) {
				BlockPos pos1 = worldPosition.relative(direction, i);
				if(level.getBlockEntity(pos1) instanceof MSRElementTE te) {
					if(te.control == null || te.control.equals(worldPosition)) {
						if(Math.ceil(insertion) >= i) {
							te.control = worldPosition;
							TEs.add(te);
							te.restriction = Mth.clamp(insertion - i + 1, 0, 1);
						}
						actualLength++;
					} else
						break;
				}
			}
			if(insertion > actualLength) {
				insertion = actualLength;
			}
			this.setChanged();
		}
		LeafiaPacket._start(this).__write(0, insertion).__sendToAffectedClients();
	}

	@Override
	protected void loadAdditional(CompoundTag compound, HolderLookup.Provider registries) {
		super.loadAdditional(compound, registries);
		insertion = compound.getDouble("rodI");
		targetInsertion = compound.getDouble("rodD");
	}

	@Override
	protected void saveAdditional(CompoundTag compound, HolderLookup.Provider registries) {
		compound.putDouble("rodI", insertion);
		compound.putDouble("rodD", targetInsertion);
		super.saveAdditional(compound, registries);
	}

	@Override
	public void onReceivePacketLocal(byte key, Object value) {
		if(key == 0)
			insertion = (double) value;
	}

	@Override
	public void onReceivePacketServer(byte key, Object value, Player plr) {}

	@Override
	public void onPlayerValidate(Player plr) {}

	// ---- the Custom Control Panel (CCP-RETROFIT, D-3069) ----

	/** Upstream's one query key: how far the rod is out of the reactor, in percent of its length. */
	@Override
	public Map<String, DataValue> getQueryData() {
		double position = length > 0 ? 1 - insertion / length : 0;
		return ControllableLifecycle.values("level", (float) (position * 100));
	}

	@Override
	public List<String> getInEvents() {
		return Collections.singletonList("lftr_ctrl_set_level");
	}

	/** {@code level} is a percent of the rod's length, held to 0..100; a NaN changes nothing. */
	@Override
	public void receiveEvent(BlockPos from, ControlEvent e) {
		if(e.name.equals("lftr_ctrl_set_level")) {
			double position = ControllableLifecycle.fraction(e, "level");
			if(!Double.isNaN(position))
				targetInsertion = length - position * length;
		}
	}

	@Override public BlockPos getControlPos() { return worldPosition; }
	@Override public Level getControlWorld() { return level; }

	@Override
	public void onLoad() {
		super.onLoad();
		ControllableLifecycle.join(this);
	}

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
