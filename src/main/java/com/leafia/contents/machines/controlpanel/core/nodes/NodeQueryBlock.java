package com.leafia.contents.machines.controlpanel.core.nodes;

import com.leafia.contents.machines.controlpanel.core.Control;
import com.leafia.contents.machines.controlpanel.core.ControlEventSystem;
import com.leafia.contents.machines.controlpanel.core.IControllable;
import com.leafia.contents.machines.controlpanel.core.NodeConnection;
import com.leafia.contents.machines.controlpanel.core.NodeDropdown;
import com.leafia.contents.machines.controlpanel.core.NodeSystem;
import com.leafia.contents.machines.controlpanel.core.modular.StockNodesRegister;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

/**
 * Reads one value from a linked block. Adapted from NTM-CE (D-3062). The first dropdown picks one of the control's linked
 * positions by its tag, the second one of that block's {@link IControllable#getQueryData} keys.
 *
 * <p>The block is found with {@link ControlEventSystem#resolve}, the same lookup an event uses: it <b>refuses a position that
 * is not loaded</b> (a panel never loads a chunk, so the node reads 0 and the dropdown stays empty), and it finds a base-mod
 * machine through its {@link ControlEventSystem#CONTROLLABLE} adapter where CE tested {@code instanceof IControllable} on the
 * tile entity alone (D-3060 #4). A value the block does not have reads as 0, as in CE; a tag that is not linked reads 0 and
 * does not fall back to the origin, which CE's lookup did by accident (the link map's default is the zero position).
 */
public class NodeQueryBlock extends Node {

	public Control ctrl;

	public String tag = "";
	public String dataName = "";
	public NodeDropdown tagSelector;
	public NodeDropdown dataSelector;

	public NodeQueryBlock(float x, float y, Control ctrl) {
		super(x, y);
		this.ctrl = ctrl;

		this.outputs.add(new NodeConnection("Output", this, outputs.size(), false, DataValue.DataType.GENERIC, new DataValueFloat(0)));

		tagSelector = new NodeDropdown(this, otherElements.size(), s -> {
			tag = s;
			dataName = "";
			setDataSelector();
			return null;
		}, () -> tag);
		this.otherElements.add(tagSelector);
		setBlockPosSelector();

		dataSelector = new NodeDropdown(this, otherElements.size(), s -> {
			dataName = s;
			this.outputs.get(0).type = evaluate(0).getType();
			return null;
		}, () -> dataName);
		setDataSelector();
		this.otherElements.add(dataSelector);

		dataName = "";
		recalcSize();
	}

	/** The linked target for the chosen tag if it is linked, loaded and controllable; else null. */
	private IControllable target() {
		if(tag == null || tag.isEmpty() || !ctrl.taggedLinks.containsKey(tag))
			return null;
		if(ctrl.panel == null || ctrl.panel.parent == null)
			return null;
		Level world = ctrl.panel.parent.getControlWorld();
		if(world == null)
			return null;
		return ControlEventSystem.get(world).resolve(ctrl.taggedLinks.get(tag));
	}

	private void setBlockPosSelector() {
		tagSelector.list.itemNames.clear();
		for(String s : ctrl.taggedLinks.keySet()) {
			tagSelector.list.addItems(s);
		}
	}

	/** Refills the data dropdown with the linked block's query keys; empty if there is no tag or the block is not loaded. */
	public void setDataSelector() {
		dataSelector.list.itemNames.clear();
		IControllable te = target();
		if(te != null) {
			dataSelector.list.addItems(te.getQueryData().keySet());
		}
	}

	@Override
	public CompoundTag writeToNBT(CompoundTag tag, NodeSystem sys) {
		tag.putString("nodeType", "queryBlock");
		tag.putString("blockPos", this.tag);
		tag.putString("dataName", dataName);

		return super.writeToNBT(tag, sys);
	}

	@Override
	public void readFromNBT(CompoundTag tag, NodeSystem sys) {
		this.tag = tag.getString("blockPos");
		dataName = tag.getString("dataName");
		setBlockPosSelector();
		setDataSelector();
		super.readFromNBT(tag, sys);
	}

	@Override
	public DataValue evaluate(int inx) {
		if(!dataName.isEmpty() && ctrl.taggedLinks.containsKey(tag)) {
			IControllable te = target();
			if(te != null) {
				DataValue v = te.getQueryData().get(dataName);
				if(v != null)
					return v;
			}
			setDataSelector();
		}
		return new DataValueFloat(0);
	}

	public NodeQueryBlock setData(String blockPos) {
		this.tag = blockPos;
		return this;
	}

	/** The position the chosen tag is linked to, or null if the tag is not linked. */
	public BlockPos linkedPos() {
		return ctrl.taggedLinks.containsKey(tag) ? ctrl.taggedLinks.get(tag) : null;
	}

	@Override
	public float[] getColor() {
		return StockNodesRegister.colorInput;
	}

	@Override
	public String getDisplayName() {
		return "Query Block";
	}
}
