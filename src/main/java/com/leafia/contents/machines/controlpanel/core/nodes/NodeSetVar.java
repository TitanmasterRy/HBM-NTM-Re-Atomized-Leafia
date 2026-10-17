package com.leafia.contents.machines.controlpanel.core.nodes;

import java.util.Map;

import com.leafia.contents.machines.controlpanel.core.Control;
import com.leafia.contents.machines.controlpanel.core.IControllable;
import com.leafia.contents.machines.controlpanel.core.NodeConnection;
import com.leafia.contents.machines.controlpanel.core.NodeDropdown;
import com.leafia.contents.machines.controlpanel.core.NodeSystem;
import com.leafia.contents.machines.controlpanel.core.modular.StockNodesRegister;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValue.DataType;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * Writes its input to one of the control's own variables, or to a panel global. Adapted from NTM-CE (D-3062). It never cancels:
 * {@link #doOutput} is always true. An empty name writes nothing, and, a hardening over CE, so does an input that evaluates to
 * nothing (CE stored the null, which failed later when the variable was saved).
 */
public class NodeSetVar extends NodeOutput {

	public Control ctrl;
	public boolean global = false;
	public NodeDropdown varSelector;
	public String varName;

	public NodeSetVar(float x, float y, Control ctrl) {
		super(x, y);
		this.ctrl = ctrl;
		this.inputs.add(new NodeConnection("Input", this, 0, true, DataType.GENERIC, new DataValueFloat(0)));
		NodeDropdown globalSelector = new NodeDropdown(this, otherElements.size(), s -> {
			if(s.equals("Global") && !global) {
				global = true;
				varName = "";
			}
			if(s.equals("Local") && global) {
				global = false;
				varName = "";
			}
			setVarSelector();
			return null;
		}, () -> (global ? "Global" : "Local"));
		globalSelector.list.addItems("Global", "Local");
		this.otherElements.add(globalSelector);

		varSelector = new NodeDropdown(this, otherElements.size(), s -> {
			varName = s;
			DataValue val = global ? ctrl.getGlobalVar(s) : ctrl.getVar(s);
			this.inputs.get(0).type = val.getType();
			this.inputs.get(0).setDefault(val);
			return null;
		}, () -> varName);
		varName = "";
		setVarSelector();
		this.otherElements.add(varSelector);

		recalcSize();
	}

	@Override
	public CompoundTag writeToNBT(CompoundTag tag, NodeSystem sys) {
		tag.putString("nodeType", "setVar");
		tag.putBoolean("global", global);
		tag.putString("varName", varName);
		return super.writeToNBT(tag, sys);
	}

	@Override
	public void readFromNBT(CompoundTag tag, NodeSystem sys) {
		global = tag.getBoolean("global");
		varName = tag.getString("varName");
		super.readFromNBT(tag, sys);
		// Owner-approved divergence from CE (D-3066): CE overwrote the input's default with the variable's current value here,
		// so a typed literal was lost on every load. A saved default is kept; the variable's value is the fallback only when
		// nothing was saved (the type still follows the variable).
		boolean saved = tag.getCompound("in").getCompound("con0").contains("D");
		refreshVariableReference(saved);
	}

	/** Refills the variable dropdown with the names of the variables in the chosen scope. */
	public void setVarSelector() {
		varSelector.list.itemNames.clear();
		if(global) {
			for(String name : ctrl.panel.globalVars.keySet()) {
				varSelector.list.addItems(name);
			}
		} else {
			for(String name : ctrl.vars.keySet()) {
				varSelector.list.addItems(name);
			}
		}
	}

	@Override
	public boolean doOutput(IControllable from, Map<String, NodeSystem> sendNodeMap, Map<String, BlockPos> positions) {
		if(varName.isEmpty())
			return true;
		DataValue val = inputs.get(0).evaluate();
		if(val == null)
			return true;
		if(global) {
			ctrl.panel.globalVars.put(varName, val);
		} else {
			ctrl.vars.put(varName, val);
		}
		return true;
	}

	public NodeSetVar setData(String varName, boolean isGlobal) {
		this.varName = varName;
		this.global = isGlobal;
		refreshVariableReference(false);
		return this;
	}

	private void refreshVariableReference(boolean keepSavedDefault) {
		setVarSelector();
		if(varName.isEmpty() || inputs.isEmpty()) {
			return;
		}
		DataValue val = global ? ctrl.panel.globalVars.get(varName) : ctrl.vars.get(varName);
		if(val == null) {
			return;
		}
		if(keepSavedDefault)
			return;
		this.inputs.get(0).type = val.getType();
		this.inputs.get(0).setDefault(val);
	}

	@Override
	public float[] getColor() {
		return StockNodesRegister.colorOutput;
	}

	@Override
	public String getDisplayName() {
		return "Set Variable";
	}
}
