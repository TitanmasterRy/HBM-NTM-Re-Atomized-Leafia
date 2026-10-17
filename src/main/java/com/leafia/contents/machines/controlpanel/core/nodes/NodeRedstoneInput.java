package com.leafia.contents.machines.controlpanel.core.nodes;

import com.leafia.contents.machines.controlpanel.core.Control;
import com.leafia.contents.machines.controlpanel.core.IRedstonePanel;
import com.leafia.contents.machines.controlpanel.core.NodeConnection;
import com.leafia.contents.machines.controlpanel.core.NodeDropdown;
import com.leafia.contents.machines.controlpanel.core.NodeSystem;
import com.leafia.contents.machines.controlpanel.core.modular.StockNodesRegister;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

/**
 * Reads the redstone arriving at one face of the panel. Adapted from NTM-CE's {@code NodeRedstoneInput} (D-3063).
 *
 * <p>Six modes: the effective power, the weak and the strong part of it, and a 0/1 for each. What it reads is the owner's
 * {@link IRedstonePanel}; while a {@code redstone_input} event is being handled that is the snapshot taken when the change was
 * seen, not the live world, so two changes in one tick are handled one after the other, each with its own picture. A panel
 * whose owner is not a redstone panel reads 0.
 *
 * <p>Saved as {@code facing} (the direction's 3D data value, which is the 1.12.2 index) and {@code mode} (the ordinal). Hardening
 * over CE for a graph the client wrote: a negative or oversized {@code mode} is reduced with {@code floorMod}, where CE's
 * {@code %} went negative and threw.
 */
public class NodeRedstoneInput extends Node {

	private final Control ctrl;
	private Direction facing = Direction.NORTH;
	private ReadMode mode = ReadMode.POWER;

	public NodeRedstoneInput(float x, float y, Control ctrl) {
		super(x, y);
		this.ctrl = ctrl;
		this.outputs.add(new NodeConnection("Output", this, outputs.size(), false, DataValue.DataType.NUMBER, new DataValueFloat(0)));

		NodeDropdown faceSelector = new NodeDropdown(this, otherElements.size(), s -> {
			for(Direction candidate : Direction.values()) {
				if(candidate.getName().equalsIgnoreCase(s)) {
					facing = candidate;
					cacheValid = false;
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
			for(ReadMode candidate : ReadMode.values()) {
				if(candidate.label.equals(s)) {
					mode = candidate;
					cacheValid = false;
					break;
				}
			}
			return null;
		}, () -> mode.label);
		for(ReadMode candidate : ReadMode.values()) {
			modeSelector.list.addItems(candidate.label);
		}
		this.otherElements.add(modeSelector);

		evalCache = new DataValue[1];
		recalcSize();
	}

	@Override
	public CompoundTag writeToNBT(CompoundTag tag, NodeSystem sys) {
		tag.putString("nodeType", "redstoneInput");
		tag.putInt("facing", facing.get3DDataValue());
		tag.putInt("mode", mode.ordinal());
		return super.writeToNBT(tag, sys);
	}

	@Override
	public void readFromNBT(CompoundTag tag, NodeSystem sys) {
		facing = Direction.from3DDataValue(tag.getInt("facing"));
		mode = ReadMode.values()[Math.floorMod(tag.getInt("mode"), ReadMode.values().length)];
		super.readFromNBT(tag, sys);
	}

	@Override
	public DataValue evaluate(int idx) {
		if(cacheValid) {
			return evalCache[0];
		}
		cacheValid = true;
		if(!(ctrl.panel.parent instanceof IRedstonePanel panel)) {
			return evalCache[0] = new DataValueFloat(0);
		}

		switch(mode) {
			case POWER:
				return evalCache[0] = new DataValueFloat(panel.getRedstoneInputPower(facing));
			case WEAK:
				return evalCache[0] = new DataValueFloat(panel.getRedstoneInputWeak(facing));
			case STRONG:
				return evalCache[0] = new DataValueFloat(panel.getRedstoneInputStrong(facing));
			case IS_POWERED:
				return evalCache[0] = new DataValueFloat(panel.getRedstoneInputPower(facing) > 0);
			case IS_WEAKLY_POWERED:
				return evalCache[0] = new DataValueFloat(panel.getRedstoneInputWeak(facing) > 0);
			case IS_STRONGLY_POWERED:
				return evalCache[0] = new DataValueFloat(panel.getRedstoneInputStrong(facing) > 0);
			default:
				return evalCache[0] = new DataValueFloat(0);
		}
	}

	@Override
	public float[] getColor() {
		return StockNodesRegister.colorInput;
	}

	@Override
	public String getDisplayName() {
		return "Redstone Input";
	}

	/** The face and mode this node reads, for tests and for the editor. */
	public Direction facing() {
		return facing;
	}

	public String modeLabel() {
		return mode.label;
	}

	private enum ReadMode {
		POWER("Power"),
		WEAK("Weak Power"),
		STRONG("Strong Power"),
		IS_POWERED("Is Powered"),
		IS_WEAKLY_POWERED("Is Weak"),
		IS_STRONGLY_POWERED("Is Strong");

		private final String label;

		ReadMode(String label) {
			this.label = label;
		}
	}
}
