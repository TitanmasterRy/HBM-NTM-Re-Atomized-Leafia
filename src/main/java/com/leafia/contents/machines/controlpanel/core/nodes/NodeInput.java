package com.leafia.contents.machines.controlpanel.core.nodes;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import org.jetbrains.annotations.NotNull;

import com.leafia.contents.machines.controlpanel.core.NodeConnection;
import com.leafia.contents.machines.controlpanel.core.NodeSystem;
import com.leafia.contents.machines.controlpanel.core.modular.StockNodesRegister;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;

import net.minecraft.nbt.CompoundTag;

/**
 * The incoming event's variables, one output per variable. Adapted from NTM-CE's {@code NodeInput} (D-3062); implements
 * {@link IInputNode}, which batch 1 introduced so the evaluation half of {@code NodeSystem} could compile without this class.
 *
 * <p>Evaluating it just answers the output's default, because {@link #setOutputFromVars} stores each variable there when the
 * event arrives. One hardening over CE: a variable the event does not carry leaves the output's last value alone, where CE
 * passed null to {@code setDefault} and threw through the sender's tick.
 */
public class NodeInput extends Node implements IInputNode {

	public @NotNull String name;

	public NodeInput(float x, float y, @NotNull String name) {
		super(x, y);
		this.name = name;
	}

	@Override
	public DataValue evaluate(int idx) {
		return outputs.get(idx).defaultValue;
	}

	@Override
	public CompoundTag writeToNBT(CompoundTag tag, NodeSystem sys) {
		tag.putString("nodeType", "input");
		tag.putString("name", name);
		return super.writeToNBT(tag, sys);
	}

	@Override
	public void readFromNBT(CompoundTag tag, NodeSystem sys) {
		this.name = tag.getString("name");
		super.readFromNBT(tag, sys);
	}

	@Override
	public float[] getColor() {
		return StockNodesRegister.colorInput;
	}

	@Override
	public String getDisplayName() {
		return name;
	}

	public NodeInput setVars(Map<String, DataValue> vars) {
		outputs.clear();
		for(Entry<String, DataValue> e : vars.entrySet()) {
			NodeConnection c = new NodeConnection(e.getKey(), this, outputs.size(), false, e.getValue().getType(), e.getValue().copy());
			outputs.add(c);
		}
		this.recalcSize();
		return this;
	}

	@Override
	public NodeInput setOutputFromVars(Map<String, DataValue> vars) {
		for(NodeConnection c : outputs) {
			if(Objects.equals(c.name, "from index")) {
				c.enumSelector = null;
				continue;
			}
			DataValue v = vars.get(c.name);
			if(v != null)
				c.setDefault(v);
		}
		return this;
	}
}
