package com.leafia.contents.machines.controlpanel.core;

import java.util.function.Function;

import org.jetbrains.annotations.NotNull;

import com.leafia.contents.machines.controlpanel.core.nodes.Node;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValue.DataType;
import com.leafia.contents.machines.controlpanel.core.types.DataValueEnum;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;
import com.leafia.contents.machines.controlpanel.core.types.DataValueString;

import net.minecraft.nbt.CompoundTag;

/**
 * A socket on a node: a named, typed input or output, with the wire to the socket it is connected to. The common half of
 * NTM-CE's {@code NodeConnection} (D-3061).
 *
 * <p>Only an <b>input</b> keeps its connection; an output holds {@code null} and {@code -1}, and the line is drawn from the
 * input's side. An unconnected input answers its {@link #defaultValue}, which is what the editor's typing box edits.
 *
 * <h2>What moved out</h2>
 * {@code render} and {@code drawLine} (CE draws a straight line between the two ports, not a curve) are the client's (§2.2) and
 * are {@code client.NodeDraw}'s since CCP-NODE-EDITOR (D-3067). The socket is an {@link ITypableNode}, with the typing protocol
 * in terms of {@code typeChar}/{@code backspace} instead of CE's LWJGL {@code keyTyped}; the typing state, the hit boxes, the
 * wiring and the NBT are kept.
 *
 * <h2>The enum selector</h2>
 * CE built a {@code NodeDropdown} here for an enum-typed socket. The dropdown is a widget, so the common class holds only a
 * {@link NodeElement} slot and a {@link #enumSelectorFactory} the client sets once ({@link #newEnumSelector} is the factory it
 * sets); with no factory (a server) the slot stays null and {@link #selectEnum} is the whole behaviour a selector's callback needs.
 *
 * <p>Hardening over CE, all invisible to a well-formed panel: the stored socket type is reduced with {@code floorMod}, and
 * the typed-ordinal fallback cannot overflow on {@code Integer.MIN_VALUE}.
 */
public class NodeConnection extends NodeElement implements ITypableNode {

	/**
	 * Set by the client to attach a selector widget to an enum socket; given the connection, it returns the element to keep in
	 * {@link #enumSelector}. Null on a server.
	 */
	public static Function<NodeConnection, NodeElement> enumSelectorFactory = null;

	public String name;
	// These should always be null and -1 for output nodes.
	public Node connection;
	public int connectionIndex;

	public DataValue defaultValue;
	public boolean drawsLine = false;
	public boolean isInput;
	public DataType type;
	public StringBuilder builder;
	public boolean isTyping;

	public NodeElement enumSelector = null;

	public NodeConnection(String name, Node p, int idx, boolean isInput, DataType type, @NotNull DataValue defaultVal) {
		super(p, idx);
		this.name = name;
		this.connection = null;
		setDefault(defaultVal);
		this.connectionIndex = -1;
		this.isInput = isInput;
		this.type = type;
	}

	@Override
	public CompoundTag writeToNBT(CompoundTag tag, NodeSystem sys) {
		super.writeToNBT(tag, sys);
		tag.putString("name", name);
		tag.putInt("Ci", connectionIndex);
		tag.putInt("Ni", sys.nodes.indexOf(connection));
		tag.putBoolean("in", isInput);
		tag.putInt("T", type.ordinal());
		tag.put("D", defaultValue.writeToNBT());
		tag.putBoolean("DL", drawsLine);
		return tag;
	}

	@Override
	public void readFromNBT(CompoundTag tag, NodeSystem sys) {
		super.readFromNBT(tag, sys);
		name = tag.getString("name");
		connectionIndex = tag.getInt("Ci");
		int nodeIdx = tag.getInt("Ni");
		if(nodeIdx == -1) {
			connection = null;
		} else {
			connection = sys.nodes.get(nodeIdx);
		}
		buildFromNBT(tag);
	}

	/** Reads the part of the tag that does not depend on the other nodes: direction, type, default and line flag. */
	public void buildFromNBT(CompoundTag tag) {
		isInput = tag.getBoolean("in");
		type = DataType.VALUES[Math.floorMod(tag.getInt("T"), DataType.VALUES.length)];
		defaultValue = DataValue.newFromNBT(tag.get("D"));
		if(defaultValue == null) {
			type = DataType.NUMBER;
			defaultValue = new DataValueFloat(0);
		}
		drawsLine = tag.getBoolean("DL");
		builder = null;
		isTyping = false;
	}

	public void setDefault(@NotNull DataValue defaultVal) {
		this.defaultValue = defaultVal;
		type = defaultVal.getType();
		if(type == DataType.ENUM && enumSelectorFactory != null) {
			enumSelector = enumSelectorFactory.apply(this);
			if(enumSelector != null)
				enumSelector.setOffset(offsetX, offsetY);
		} else {
			enumSelector = null;
		}
	}

	/** The names of the constants an enum-typed default can take, in ordinal order; empty for any other type. */
	public String[] enumNames() {
		if(!(defaultValue instanceof DataValueEnum<?> e))
			return new String[0];
		Enum<?>[] vals = e.getPossibleValues();
		String[] names = new String[vals.length];
		for(int i = 0; i < vals.length; i++)
			names[i] = vals[i].name();
		return names;
	}

	/** Makes the default the constant of this name, as the selector's callback did. An unknown name changes nothing. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public void selectEnum(String constantName) {
		if(!(defaultValue instanceof DataValueEnum<?> e))
			return;
		for(Enum<?> c : e.getPossibleValues()) {
			if(c.name().equals(constantName)) {
				defaultValue = new DataValueEnum(c);
				return;
			}
		}
	}

	public NodeConnection removeConnection() {
		// Will only run for input nodes as well, since the output node doesn't maintain a connection
		if(connection != null) {
			NodeConnection n = connection.outputs.get(connectionIndex);
			drawsLine = false;
			connection = null;
			connectionIndex = -1;
			return n;
		}
		return null;
	}

	public NodeConnection setData(Node connection, int connectionIndex, boolean drawsLine) {
		this.connection = connection;
		this.connectionIndex = connectionIndex;
		this.drawsLine = drawsLine;
		return this;
	}

	@Override
	public void resetOffset() {
		super.resetOffset();
		offsetY += parent.otherElements.size() * 8;
		if(isInput) {
			offsetY += parent.outputs.size() * 8;
		}
		if(enumSelector != null) {
			enumSelector.setOffset(offsetX, offsetY);
		}
	}

	// minX, minY, maxX, maxY
	public float[] getPortBox() {
		float oX = offsetX;
		if(!isInput)
			oX += 40;
		return new float[]{-2 + oX, -2 + offsetY + 10, 2 + oX, 2 + offsetY + 10};
	}

	public float[] getValueBox() {
		if(enumSelector != null)
			return enumSelector.getBox();
		return new float[]{3 + offsetX, -3 + offsetY + 10, 37 + offsetX, 3 + offsetY + 10};
	}

	/** The value this socket carries: the connected output's, or the default when nothing is connected. */
	public DataValue evaluate() {
		if(connection == null || connectionIndex == -1) {
		} else {
			return connection.evaluate(connectionIndex);
		}
		return defaultValue;
	}

	@Override
	public boolean isTyping() {
		return isTyping;
	}

	@Override
	public void startTyping() {
		isTyping = true;
		builder = new StringBuilder();
		builder.append(defaultValue.toString());
	}

	/** Commits what was typed to the default, reading it as this socket's type. */
	@Override
	@SuppressWarnings({"unchecked", "rawtypes"})
	public void stopTyping() {
		if(builder == null) {
			isTyping = false;
			return;
		}
		DataValue val = new DataValueString(builder.toString());
		builder = null;
		isTyping = false;
		switch(type) {
			case GENERIC:
			case STRING:
				defaultValue = val;
				break;
			case ENUM:
				DataValueEnum<?> def = (DataValueEnum<?>) defaultValue;
				Enum<?>[] possibleVals = def.getPossibleValues();
				boolean matched = false;
				for(Enum<?> e : possibleVals) {
					if(e.name().equalsIgnoreCase(val.toString())) {
						defaultValue = new DataValueEnum(e);
						matched = true;
						break;
					}
				}
				if(!matched) {
					int idx = (int) (Math.abs((long) ((int) val.getNumber())) % possibleVals.length);
					defaultValue = new DataValueEnum(possibleVals[idx]);
				}
				break;
			case NUMBER:
				defaultValue = new DataValueFloat(val.getNumber());
				break;
			default:
				break;
		}
	}

	@Override
	public void typeChar(char c) {
		if(builder != null && ITypableNode.isTypable(c) && builder.length() < MAX_TYPED)
			builder.append(c);
	}

	@Override
	public void backspace() {
		if(builder != null && builder.length() > 0)
			builder.deleteCharAt(builder.length() - 1);
	}

	/**
	 * The selector the client attaches to an enum socket ({@link #enumSelectorFactory}): a dropdown of the constants' names whose
	 * pick makes that constant the default. CE built this in {@code setDefault} itself; the widget is the same, only who builds
	 * it moved, so a server never makes one.
	 */
	public static NodeElement newEnumSelector(NodeConnection c) {
		NodeDropdown selector = new NodeDropdown(c.parent, c.index, s -> {
			c.selectEnum(s);
			return null;
		}, () -> c.defaultValue.toString());
		for(String name : c.enumNames())
			selector.list.addItems(name);
		return selector;
	}
}
