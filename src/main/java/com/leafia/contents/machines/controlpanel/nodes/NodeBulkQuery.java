package com.leafia.contents.machines.controlpanel.nodes;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.leafia.contents.machines.controlpanel.core.Control;
import com.leafia.contents.machines.controlpanel.core.ControlEventSystem;
import com.leafia.contents.machines.controlpanel.core.IControllable;
import com.leafia.contents.machines.controlpanel.core.NodeConnection;
import com.leafia.contents.machines.controlpanel.core.NodeDropdown;
import com.leafia.contents.machines.controlpanel.core.NodeSystem;
import com.leafia.contents.machines.controlpanel.core.modular.StockNodesRegister;
import com.leafia.contents.machines.controlpanel.core.nodes.Node;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

/**
 * One number read from every block the control is linked to and combined (CCP-LEAFIA-NODES, D-3070). Adapted from Leafia's
 * {@code NodeBulkQuery}.
 *
 * <p>Where {@code NodeQueryBlock} reads one linked block by tag, this reads <b>all</b> of them: for each linked position that
 * answers {@link IControllable#getQueryData} with a number under the chosen key, the numbers are combined by the chosen
 * {@link CombineMethod}. A block that has no such key, or has it as text, is skipped. The dropdown lists the number keys any
 * linked block offers.
 *
 * <p><b>It reaches the world only as {@code NodeQueryBlock} does:</b> {@link ControlEventSystem#resolve}, which refuses a
 * position that is not loaded (a panel never loads a chunk) and finds a base-mod machine through its capability adapter, where
 * Leafia's tested {@code instanceof IControllable} on the tile entity. A control has at most 64 links (the guard), so a read is
 * bounded.
 *
 * <p>Combining: {@code ADD} sums (empty is 0); {@code MULTIPLY} multiplies from 1 (empty is 1); {@code AVERAGE} sums and divides
 * by the count (empty is 0); {@code HIGHEST} and {@code LOWEST} take the extreme (empty is 0). With no key chosen the node reads 0.
 */
public class NodeBulkQuery extends Node {

	public enum CombineMethod { ADD, MULTIPLY, AVERAGE, HIGHEST, LOWEST }

	public Control ctrl;
	public CombineMethod combineMethod = CombineMethod.ADD;
	public String dataName = "";
	public NodeDropdown dataSelector;

	public NodeBulkQuery(float x, float y, Control ctrl) {
		super(x, y);
		this.ctrl = ctrl;

		this.outputs.add(new NodeConnection("Output", this, outputs.size(), false, DataValue.DataType.GENERIC, new DataValueFloat(0)));

		NodeDropdown combineSelector = new NodeDropdown(this, otherElements.size(), s -> {
			try {
				combineMethod = CombineMethod.valueOf(s);
			} catch(IllegalArgumentException ignored) {}
			return null;
		}, () -> combineMethod.name());
		this.otherElements.add(combineSelector);
		for(CombineMethod value : CombineMethod.values())
			combineSelector.list.addItems(value.name());

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

	/** The block linked at {@code pos} if it is loaded and controllable; else null. Never loads a chunk. */
	private IControllable target(Level world, BlockPos pos) {
		return ControlEventSystem.get(world).resolve(pos);
	}

	private Level world() {
		return AddonNodeSupport.levelOf(ctrl);
	}

	/** Refills the data dropdown with the number keys the linked blocks offer, each once, in link order. */
	public void setDataSelector() {
		dataSelector.list.itemNames.clear();
		Level world = world();
		if(world == null)
			return;
		Set<String> alreadyAdded = new HashSet<>();
		for(BlockPos pos : ctrl.taggedLinks.values()) {
			IControllable te = target(world, pos);
			if(te == null)
				continue;
			for(Map.Entry<String, DataValue> var : te.getQueryData().entrySet()) {
				if(alreadyAdded.add(var.getKey())) {
					if(var.getValue() instanceof DataValueFloat)
						dataSelector.list.addItems(var.getKey());
				}
			}
		}
	}

	@Override
	public CompoundTag writeToNBT(CompoundTag tag, NodeSystem sys) {
		tag.putString("nodeType", "bulkQuery");
		tag.putString("combineMethod", combineMethod.name());
		tag.putString("dataName", dataName);

		return super.writeToNBT(tag, sys);
	}

	@Override
	public void readFromNBT(CompoundTag tag, NodeSystem sys) {
		try {
			combineMethod = CombineMethod.valueOf(tag.getString("combineMethod"));
		} catch(IllegalArgumentException ignored) {}
		dataName = tag.getString("dataName");
		setDataSelector();
		super.readFromNBT(tag, sys);
	}

	@Override
	public DataValue evaluate(int inx) {
		if(!dataName.isEmpty()) {
			Level world = world();
			float value = 0;
			if(combineMethod == CombineMethod.MULTIPLY)
				value = 1;
			int total = 0;
			boolean firstTime = true;
			if(world != null) {
				for(BlockPos pos : ctrl.taggedLinks.values()) {
					IControllable te = target(world, pos);
					if(te == null)
						continue;
					DataValue data = te.getQueryData().get(dataName);
					if(data instanceof DataValueFloat) {
						float curValue = data.getNumber();
						switch(combineMethod) {
							case ADD -> value += curValue;
							case MULTIPLY -> value *= curValue;
							case AVERAGE -> {
								value += curValue;
								total++;
							}
							case HIGHEST -> {
								if(firstTime)
									value = curValue;
								else
									value = Math.max(curValue, value);
								firstTime = false;
							}
							case LOWEST -> {
								if(firstTime)
									value = curValue;
								else
									value = Math.min(curValue, value);
								firstTime = false;
							}
						}
					}
				}
			}
			if(total > 0 && combineMethod == CombineMethod.AVERAGE)
				value /= total;
			return new DataValueFloat(value);
		}
		return new DataValueFloat(0);
	}

	@Override
	public float[] getColor() {
		return StockNodesRegister.colorInput;
	}

	@Override
	public String getDisplayName() {
		return "Bulk Query";
	}
}
