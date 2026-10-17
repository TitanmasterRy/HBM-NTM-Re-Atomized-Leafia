package com.leafia.contents.machines.panel.controltorch;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.machines.controlpanel.core.ControlEvent;
import com.leafia.contents.machines.controlpanel.core.ControllableLifecycle;
import com.leafia.contents.machines.controlpanel.core.IControllable;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The control torch's block entity (CCP-INSTRUMENTS-2, D-3072): the panel's handle on it. Adapted from Leafia's {@code ControlTorchTE}.
 *
 * <p>One in-event, {@code torch_set_state} with an {@code isOn} variable: {@code >= 1} lights the torch and anything else puts it out,
 * by swapping its block for {@code control_torch} or {@code control_torch_unlit} with the same facing (nothing happens when it is
 * already so). One query key, {@code isOn}, 1 or 0. No out-events.
 *
 * <p><b>It keeps no state of its own.</b> Leafia's {@code isOn} field, and the {@code loadedByWorld} flag and queued function that
 * reconciled it with the block after a chunk load, existed because a 1.12 tile entity was rebuilt with no knowledge of its block; here the
 * answer is read from the block it is in every time, so there is nothing to reconcile and nothing to lose on a reload (the block entity
 * saves no data; the one it is rebuilt with is the same). A block swap replaces the block entity (the base class removes it as the
 * block changes), which leaves the panel's subscriptions where they were: they name a position, not an object.
 *
 * <p><b>Hardening (D-1093 #4):</b> an event with no {@code isOn} variable is ignored (Leafia dereferenced it); only the server acts.
 */
public class ControlTorchTE extends BlockEntity implements IControllable {

	public ControlTorchTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	/** Whether the torch is lit: whether the block it is in is {@code control_torch}. */
	public boolean isOn() {
		return getBlockState().getBlock() instanceof ControlTorchBlock torch && torch.isOn();
	}

	@Override
	public List<String> getInEvents() {
		return Collections.singletonList("torch_set_state");
	}

	@Override
	public Map<String, DataValue> getQueryData() {
		return ControllableLifecycle.values("isOn", isOn() ? 1 : 0);
	}

	@Override
	public void receiveEvent(BlockPos from, ControlEvent e) {
		if(!e.name.equals("torch_set_state") || level == null || level.isClientSide)
			return;
		double on = ControllableLifecycle.number(e, "isOn");
		if(Double.isNaN(on))
			return;
		boolean newState = on >= 1f;
		BlockState current = level.getBlockState(worldPosition);
		if(!(current.getBlock() instanceof ControlTorchBlock) || newState == isOn())
			return;
		level.setBlock(worldPosition, (newState ? LeafiaBlocks.control_torch : LeafiaBlocks.control_torch_unlit).get().defaultBlockState()
				.setValue(ControlTorchBlock.FACING, current.getValue(ControlTorchBlock.FACING)), 3);
	}

	@Override
	public BlockPos getControlPos() {
		return worldPosition;
	}

	@Override
	public Level getControlWorld() {
		return level;
	}

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
