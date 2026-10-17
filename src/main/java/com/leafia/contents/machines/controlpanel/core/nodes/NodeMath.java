package com.leafia.contents.machines.controlpanel.core.nodes;

import com.leafia.contents.machines.controlpanel.core.NodeConnection;
import com.leafia.contents.machines.controlpanel.core.NodeDropdown;
import com.leafia.contents.machines.controlpanel.core.NodeSystem;
import com.leafia.contents.machines.controlpanel.core.modular.StockNodesRegister;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValue.DataType;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

/**
 * Arithmetic, comparison and rounding on numbers, one operation chosen from a dropdown. Adapted from NTM-CE's {@code NodeMath}
 * (D-3062). Every operation is in float: comparisons answer 1 or 0, and a division by zero is infinity or NaN, as in CE.
 *
 * <p>Hardening over CE, for a graph the client wrote: the saved operation index is reduced with {@code floorMod} (CE's
 * {@code %} went negative and threw), and a node whose saved sockets are fewer than its operation needs evaluates to nothing
 * instead of reading past them.
 */
public class NodeMath extends Node {

	public Operation op = Operation.ADD;

	public NodeMath(float x, float y) {
		super(x, y);
		this.outputs.add(new NodeConnection("Output", this, outputs.size(), false, DataType.NUMBER, new DataValueFloat(0)));
		NodeDropdown opSelector = new NodeDropdown(this, otherElements.size(), s -> {
			Operation op = Operation.getByName(s);
			if(op != null) {
				setOperation(op);
			}
			return null;
		}, () -> op.name);
		for(Operation op : Operation.VALUES) {
			opSelector.list.addItems(op.name);
		}
		this.otherElements.add(opSelector);
		setOperation(Operation.ADD);
		evalCache = new DataValue[1];
	}

	@Override
	public CompoundTag writeToNBT(CompoundTag tag, NodeSystem sys) {
		tag.putString("nodeType", "math");
		tag.putInt("op", op.ordinal());
		return super.writeToNBT(tag, sys);
	}

	@Override
	public void readFromNBT(CompoundTag tag, NodeSystem sys) {
		op = Operation.VALUES[Math.floorMod(tag.getInt("op"), Operation.VALUES.length)];
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
		if(evals.length < op.arity)
			return null;
		switch(op) {
			case ADD:
				return evalCache[0] = new DataValueFloat(evals[0].getNumber() + evals[1].getNumber());
			case SUB:
				return evalCache[0] = new DataValueFloat(evals[0].getNumber() - evals[1].getNumber());
			case MULT:
				return evalCache[0] = new DataValueFloat(evals[0].getNumber() * evals[1].getNumber());
			case DIV:
				return evalCache[0] = new DataValueFloat(evals[0].getNumber() / evals[1].getNumber());
			case MOD:
				return evalCache[0] = new DataValueFloat(evals[0].getNumber() % evals[1].getNumber());
			case POW:
				return evalCache[0] = new DataValueFloat((float) Math.pow(evals[0].getNumber(), evals[1].getNumber()));
			case LOG:
				return evalCache[0] = new DataValueFloat((float) (Math.log(evals[0].getNumber()) / Math.log(evals[1].getNumber())));
			case EXP:
				return evalCache[0] = new DataValueFloat((float) Math.exp(evals[0].getNumber()));
			case SQRT:
				return evalCache[0] = new DataValueFloat((float) Math.sqrt(evals[0].getNumber()));
			case ABS:
				return evalCache[0] = new DataValueFloat(Math.abs(evals[0].getNumber()));
			case EQUAL:
				return evalCache[0] = new DataValueFloat(evals[0].getNumber() == evals[1].getNumber() ? 1 : 0);
			case GREATER:
				return evalCache[0] = new DataValueFloat(evals[0].getNumber() > evals[1].getNumber() ? 1 : 0);
			case LESS:
				return evalCache[0] = new DataValueFloat(evals[0].getNumber() < evals[1].getNumber() ? 1 : 0);
			case GEQUAL:
				return evalCache[0] = new DataValueFloat(evals[0].getNumber() >= evals[1].getNumber() ? 1 : 0);
			case LEQUAL:
				return evalCache[0] = new DataValueFloat(evals[0].getNumber() <= evals[1].getNumber() ? 1 : 0);
			case CLAMP:
				return evalCache[0] = new DataValueFloat(Mth.clamp(evals[0].getNumber(), evals[1].getNumber(), evals[2].getNumber()));
			case MAX:
				return evalCache[0] = new DataValueFloat(Math.max(evals[0].getNumber(), evals[1].getNumber()));
			case MIN:
				return evalCache[0] = new DataValueFloat(Math.min(evals[0].getNumber(), evals[1].getNumber()));
			case FLOOR:
				return evalCache[0] = new DataValueFloat((float) Math.floor(evals[0].getNumber()));
			case CEIL:
				return evalCache[0] = new DataValueFloat((float) Math.ceil(evals[0].getNumber()));
			case ROUND:
				return evalCache[0] = new DataValueFloat((float) Math.round(evals[0].getNumber()));
		}
		return evalCache[0] = null;
	}

	public NodeMath setData(Operation op) {
		setOperation(op);
		return this;
	}

	public void setOperation(Operation op) {
		this.op = op;
		for(NodeConnection c : inputs) {
			c.removeConnection();
		}
		this.inputs.clear();
		String s1 = "Input 1";
		String s2 = "Input 2";
		switch(op) {
			case EQUAL:
			case GREATER:
			case LESS:
			case GEQUAL:
			case LEQUAL:
			case ADD:
			case SUB:
			case MULT:
			case DIV:
			case MOD:
			case POW:
			case MIN:
			case MAX:
			case LOG:
				if(op == Operation.POW) {
					s1 = "Base";
					s2 = "Exponent";
				} else if(op == Operation.LOG) {
					s1 = "Value";
					s2 = "Base";
				}
				inputs.add(new NodeConnection(s1, this, inputs.size(), true, DataType.NUMBER, new DataValueFloat(0)));
				inputs.add(new NodeConnection(s2, this, inputs.size(), true, DataType.NUMBER, new DataValueFloat(0)));
				break;
			case EXP:
			case SQRT:
			case ABS:
				inputs.add(new NodeConnection("Input", this, inputs.size(), true, DataType.NUMBER, new DataValueFloat(0)));
				break;
			case FLOOR:
			case CEIL:
			case ROUND:
				inputs.add(new NodeConnection("Input", this, inputs.size(), true, DataType.NUMBER, new DataValueFloat(0)));
				break;
			case CLAMP:
				inputs.add(new NodeConnection("Value", this, inputs.size(), true, DataType.NUMBER, new DataValueFloat(0)));
				inputs.add(new NodeConnection("Min", this, inputs.size(), true, DataType.NUMBER, new DataValueFloat(0)));
				inputs.add(new NodeConnection("Max", this, inputs.size(), true, DataType.NUMBER, new DataValueFloat(0)));
				break;
		}
		recalcSize();
	}

	@Override
	public float[] getColor() {
		return StockNodesRegister.colorMath;
	}

	@Override
	public String getDisplayName() {
		return op.name;
	}

	/** The operations, in CE's order (the saved index is the ordinal), with their display names and how many inputs each reads. */
	public enum Operation {
		ADD("Add", 2),
		SUB("Subtract", 2),
		MULT("Multiply", 2),
		DIV("Divide", 2),
		MOD("Modulo", 2),
		POW("Power", 2),
		LOG("Logarithm", 2),
		EXP("Exponent", 1),
		SQRT("Square root", 1),
		ABS("Absolute", 1),
		EQUAL("Equal", 2),
		GREATER("Greater", 2),
		LESS("Less", 2),
		GEQUAL("Greater/equal", 2),
		LEQUAL("Less/equal", 2),
		CLAMP("Clamp", 3),
		MAX("Max", 2),
		MIN("Min", 2),
		FLOOR("Floor", 1),
		CEIL("Ceil", 1),
		ROUND("Round", 1);

		public static final Operation[] VALUES = values();

		public final String name;
		/** The number of inputs the operation reads. An addition of the port, for the guard in {@code evaluate}. */
		public final int arity;

		Operation(String name, int arity) {
			this.name = name;
			this.arity = arity;
		}

		public static Operation getByName(String name) {
			for(Operation o : Operation.VALUES) {
				if(o.name.equals(name)) {
					return o;
				}
			}
			return null;
		}
	}
}
