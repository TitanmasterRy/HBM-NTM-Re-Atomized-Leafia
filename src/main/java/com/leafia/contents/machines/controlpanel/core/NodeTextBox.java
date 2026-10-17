package com.leafia.contents.machines.controlpanel.core;

import java.util.function.Consumer;

import com.leafia.contents.machines.controlpanel.core.nodes.Node;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValueString;

import net.minecraft.nbt.CompoundTag;

/**
 * A one-line text field inside a node: the common half of NTM-CE's {@code NodeTextBox} (CCP-NODE-EDITOR, D-3067); the picture
 * is {@code NodeDraw}'s. No stock node uses it; it is the base of the field Leafia's string nodes put in their own box
 * (batches 10 and 13). Distinct from Leafia's {@code elements/NodeTextBox}, which is a different class in another package
 *.
 *
 * <p>The text is committed to {@link #value} and reported to {@link #changed} when typing stops, not on every key. CE called
 * {@code changed} without checking it; a box built without a callback commits quietly here.
 */
public class NodeTextBox extends NodeElement implements ITypableNode {

	public String name;

	public String value;
	public StringBuilder builder;
	public boolean isTyping;
	public Consumer<String> changed = null;

	public NodeTextBox(String name, Node p, int idx, String defaultVal, Consumer<String> func) {
		super(p, idx);
		this.name = name;
		this.changed = func;
		value = defaultVal;
	}

	@Override
	public CompoundTag writeToNBT(CompoundTag tag, NodeSystem sys) {
		super.writeToNBT(tag, sys);
		tag.putString("name", name);
		tag.putString("D", value);
		return tag;
	}

	@Override
	public void readFromNBT(CompoundTag tag, NodeSystem sys) {
		super.readFromNBT(tag, sys);
		name = tag.getString("name");
		value = tag.getString("D");
		builder = null;
		isTyping = false;
	}

	@Override
	public void resetOffset() {
		super.resetOffset();
		offsetY += parent.otherElements.size() * 8;
	}

	/** minX, minY, maxX, maxY of the port; unused by the editor, kept from CE. */
	public float[] getPortBox() {
		return new float[]{-2 + offsetX, -2 + offsetY + 10, 2 + offsetX, 2 + offsetY + 10};
	}

	@Override
	public float[] getValueBox() {
		return new float[]{3 + offsetX, -3 + offsetY + 10, 37 + offsetX, 3 + offsetY + 10};
	}

	public DataValue evaluate() {
		return new DataValueString(value);
	}

	@Override
	public boolean isTyping() {
		return isTyping;
	}

	@Override
	public void startTyping() {
		isTyping = true;
		builder = new StringBuilder();
		builder.append(value);
	}

	@Override
	public void stopTyping() {
		if(builder != null)
			value = builder.toString();
		builder = null;
		isTyping = false;
		if(changed != null)
			changed.accept(value);
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
}
