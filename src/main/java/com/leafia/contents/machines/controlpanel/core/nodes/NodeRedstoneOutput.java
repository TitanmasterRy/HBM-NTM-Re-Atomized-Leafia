package com.leafia.contents.machines.controlpanel.core.nodes;

import java.util.Map;

import com.leafia.contents.machines.controlpanel.core.IControllable;
import com.leafia.contents.machines.controlpanel.core.IRedstonePanel;
import com.leafia.contents.machines.controlpanel.core.NodeConnection;
import com.leafia.contents.machines.controlpanel.core.NodeDropdown;
import com.leafia.contents.machines.controlpanel.core.NodeSystem;
import com.leafia.contents.machines.controlpanel.core.modular.StockNodesRegister;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

/**
 * Sets the redstone leaving one face of the panel, weak or strong. Adapted from NTM-CE's {@code NodeRedstoneOutput} (D-3063).
 *
 * <p>The strength is its input rounded to the nearest integer and clamped to 0..15 by the panel. It acts only when the owner is
 * an {@link IRedstonePanel}; it never cancels, so {@link #doOutput} is always true. An input that evaluates to nothing sets 0
 * where CE threw a {@code NullPointerException} through the sender's tick (D-3063).
 *
 * <p>Saved as {@code facing} and {@code mode} (0 weak, 1 strong), reduced with {@code floorMod} as the input node's are.
 */
public class NodeRedstoneOutput extends NodeOutput {

	private Direction facing = Direction.NORTH;
	private OutputMode mode = OutputMode.WEAK;

	public NodeRedstoneOutput(float x, float y) {
		super(x, y);
		this.inputs.add(new NodeConnection("Strength", this, inputs.size(), true, DataValue.DataType.NUMBER, new DataValueFloat(0)));

		NodeDropdown faceSelector = new NodeDropdown(this, otherElements.size(), s -> {
			for(Direction candidate : Direction.values()) {
				if(candidate.getName().equalsIgnoreCase(s)) {
					facing = candidate;
					break;
				}
			}
			return null;
		}, () -> facing.getName());
		for(Direction candidate : Direction.values()) {
			faceSelector.list.addItems(candidate.getName());
		}
		this.otherElements.add(faceSelector);

		NodeDropdown modeSelector = new NodeDropdown(this, otherElements.size(), s -> {
			for(OutputMode candidate : OutputMode.values()) {
				if(candidate.label.equals(s)) {
					mode = candidate;
					break;
				}
			}
			return null;
		}, () -> mode.label);
		for(OutputMode candidate : OutputMode.values()) {
			modeSelector.list.addItems(candidate.label);
		}
		this.otherElements.add(modeSelector);

		recalcSize();
	}

	@Override
	public CompoundTag writeToNBT(CompoundTag tag, NodeSystem sys) {
		tag.putString("nodeType", "redstoneOutput");
		tag.putInt("facing", facing.get3DDataValue());
		tag.putInt("mode", mode.ordinal());
		return super.writeToNBT(tag, sys);
	}

	@Override
	public void readFromNBT(CompoundTag tag, NodeSystem sys) {
		facing = Direction.from3DDataValue(tag.getInt("facing"));
		mode = OutputMode.values()[Math.floorMod(tag.getInt("mode"), OutputMode.values().length)];
		super.readFromNBT(tag, sys);
	}

	@Override
	public boolean doOutput(IControllable from, Map<String, NodeSystem> sendNodeMap, Map<String, BlockPos> positions) {
		if(from instanceof IRedstonePanel panel) {
			DataValue value = inputs.get(0).evaluate();
			int strength = value == null ? 0 : Math.round(value.getNumber());
			if(mode == OutputMode.WEAK) {
				panel.setWeakRedstoneOutput(facing, strength);
			} else {
				panel.setStrongRedstoneOutput(facing, strength);
			}
		}
		return true;
	}

	@Override
	public float[] getColor() {
		return StockNodesRegister.colorOutput;
	}

	@Override
	public String getDisplayName() {
		return "Redstone Output";
	}

	/** The face and mode this node writes, for tests and for the editor. */
	public Direction facing() {
		return facing;
	}

	public String modeLabel() {
		return mode.label;
	}

	private enum OutputMode {
		WEAK("Weak"),
		STRONG("Strong");

		private final String label;

		OutputMode(String label) {
			this.label = label;
		}
	}
}
