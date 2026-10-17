package com.leafia.contents.machines.controlpanel.core.nodes;

import com.leafia.contents.machines.controlpanel.core.NodeConnection;
import com.leafia.contents.machines.controlpanel.core.NodeDropdown;
import com.leafia.contents.machines.controlpanel.core.NodeSystem;
import com.leafia.contents.machines.controlpanel.core.modular.StockNodesRegister;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValue.DataType;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;

import net.minecraft.nbt.CompoundTag;

/**
 * Boolean logic on two numbers (one for NOT), read as true when non-zero. Adapted from NTM-CE's {@code NodeBoolean} (D-3062).
 * The answer is 1 or 0. The saved operation index is reduced with {@code floorMod} and a node with too few saved sockets
 * evaluates to nothing, as for {@link NodeMath}.
 */
public class NodeBoolean extends Node {

	public BoolOperation op = BoolOperation.AND;

	public NodeBoolean(float x, float y) {
		super(x, y);
		this.outputs.add(new NodeConnection("Output", this, outputs.size(), false, DataType.NUMBER, new DataValueFloat(0)));
		NodeDropdown opSelector = new NodeDropdown(this, otherElements.size(), s -> {
			BoolOperation op = BoolOperation.getByName(s);
			if(op != null)
				setOperation(op);
			return null;
		}, () -> op.name());
		for(BoolOperation op : BoolOperation.VALUES) {
			opSelector.list.addItems(op.name());
		}
		this.otherElements.add(opSelector);
		setOperation(BoolOperation.AND);
		evalCache = new DataValue[1];
	}

	@Override
	public float[] getColor() {
		return StockNodesRegister.colorBoolean;
	}

	@Override
	public String getDisplayName() {
		return op.name();
	}

	@Override
	public CompoundTag writeToNBT(CompoundTag tag, NodeSystem sys) {
		tag.putString("nodeType", "boolean");
		tag.putInt("op", op.ordinal());
		return super.writeToNBT(tag, sys);
	}

	@Override
	public void readFromNBT(CompoundTag tag, NodeSystem sys) {
		op = BoolOperation.VALUES[Math.floorMod(tag.getInt("op"), BoolOperation.VALUES.length)];
		super.readFromNBT(tag, sys);
	}

	@Override
	public DataValue evaluate(int idx) {
		if(cacheValid)
			return evalCache[0];
		cacheValid = true;

		DataValue[] evals = new DataValue[inputs.size()];
		for(int i = 0; i < evals.length; i++) {
			evals[i] = inputs.get(i).evaluate();
			if(evals[i] == null)
				return null;
		}
		if(evals.length < op.arity())
			return null;

		switch(op) {
			case AND: return evalCache[0] = new DataValueFloat(evals[0].getBoolean() && evals[1].getBoolean());
			case OR: return evalCache[0] = new DataValueFloat(evals[0].getBoolean() || evals[1].getBoolean());
			case NOT: return evalCache[0] = new DataValueFloat(!evals[0].getBoolean());
			case XOR: return evalCache[0] = new DataValueFloat(evals[0].getBoolean() ^ evals[1].getBoolean());
			case NAND: return evalCache[0] = new DataValueFloat(!(evals[0].getBoolean() && evals[1].getBoolean()));
			case NOR: return evalCache[0] = new DataValueFloat(!(evals[0].getBoolean() || evals[1].getBoolean()));
			case XNOR: return evalCache[0] = new DataValueFloat(evals[0].getBoolean() == evals[1].getBoolean());
		}

		return evalCache[0] = null;
	}

	public void setOperation(BoolOperation op) {
		this.op = op;
		for(NodeConnection c : inputs) {
			c.removeConnection();
		}
		this.inputs.clear();
		String s1 = "Input 1";
		String s2 = "Input 2";

		if(op == BoolOperation.NOT) {
			inputs.add(new NodeConnection("Input", this, inputs.size(), true, DataType.NUMBER, new DataValueFloat(0)));
		} else {
			inputs.add(new NodeConnection(s1, this, inputs.size(), true, DataType.NUMBER, new DataValueFloat(0)));
			inputs.add(new NodeConnection(s2, this, inputs.size(), true, DataType.NUMBER, new DataValueFloat(0)));
		}

		recalcSize();
	}

	public NodeBoolean setData(BoolOperation op) {
		setOperation(op);
		return this;
	}

	public enum BoolOperation {
		AND,
		OR,
		NOT,
		XOR,
		NAND,
		NOR,
		XNOR;

		public static final BoolOperation[] VALUES = values();

		/** How many inputs the operation reads: one for NOT, two for the rest. */
		public int arity() {
			return this == NOT ? 1 : 2;
		}

		public static BoolOperation getByName(String name) {
			for(BoolOperation o : BoolOperation.VALUES) {
				if(o.name().equals(name)) {
					return o;
				}
			}
			return null;
		}
	}
}
