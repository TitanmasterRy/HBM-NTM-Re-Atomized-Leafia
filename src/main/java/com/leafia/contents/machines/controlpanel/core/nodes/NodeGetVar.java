package com.leafia.contents.machines.controlpanel.core.nodes;

import com.leafia.contents.machines.controlpanel.core.Control;
import com.leafia.contents.machines.controlpanel.core.NodeConnection;
import com.leafia.contents.machines.controlpanel.core.NodeDropdown;
import com.leafia.contents.machines.controlpanel.core.NodeSystem;
import com.leafia.contents.machines.controlpanel.core.modular.StockNodesRegister;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValue.DataType;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;

import net.minecraft.nbt.CompoundTag;

/**
 * Reads one of the control's own variables, or one of the panel's global variables. Adapted from NTM-CE (D-3062). An empty
 * name, or a name nobody has set, reads as the number 0. The output's declared type follows the variable's current type, so a
 * wire to a string variable is a string wire.
 */
public class NodeGetVar extends Node {

	public Control ctrl;
	public boolean global = false;
	public String varName;
	public NodeDropdown varSelector;

	public NodeGetVar(float x, float y, Control ctrl) {
		super(x, y);
		this.ctrl = ctrl;
		this.outputs.add(new NodeConnection("Output", this, outputs.size(), false, DataType.GENERIC, new DataValueFloat(0)));
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
			this.outputs.get(0).type = evaluate(0).getType();
			return null;
		}, () -> varName);
		varName = "";
		setVarSelector();
		this.otherElements.add(varSelector);
		recalcSize();
	}

	@Override
	public CompoundTag writeToNBT(CompoundTag tag, NodeSystem sys) {
		tag.putString("nodeType", "getVar");
		tag.putBoolean("global", global);
		tag.putString("varName", varName);
		return super.writeToNBT(tag, sys);
	}

	@Override
	public void readFromNBT(CompoundTag tag, NodeSystem sys) {
		global = tag.getBoolean("global");
		varName = tag.getString("varName");
		super.readFromNBT(tag, sys);
		refreshVariableReference();
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
	public DataValue evaluate(int idx) {
		if(varName.isEmpty())
			return new DataValueFloat(0);
		if(global) {
			return ctrl.getGlobalVar(varName);
		} else {
			return ctrl.getVar(varName);
		}
	}

	public NodeGetVar setData(String varName, boolean isGlobal) {
		this.varName = varName;
		this.global = isGlobal;
		refreshVariableReference();
		return this;
	}

	private void refreshVariableReference() {
		setVarSelector();
		if(!varName.isEmpty() && !outputs.isEmpty()) {
			this.outputs.get(0).type = evaluate(0).getType();
		}
	}

	@Override
	public float[] getColor() {
		return StockNodesRegister.colorInput;
	}

	@Override
	public String getDisplayName() {
		return "Get Variable";
	}
}
