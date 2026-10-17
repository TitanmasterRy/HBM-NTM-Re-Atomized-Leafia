package com.leafia.contents.machines.controlpanel.core;

import com.leafia.contents.machines.controlpanel.core.nodes.Node;

import net.minecraft.nbt.CompoundTag;

/**
 * A row of a node that is not a socket: a dropdown, a button, a text box. The common half of NTM-CE's {@code NodeElement}
 * (D-3061): where it sits inside its node, hit-testing, and NBT.
 *
 * <p>CE's {@code render(mX, mY)} and {@code onClicked(SubElement)} are the client's and are not here (§2.2); batch 7 draws a
 * node's elements from {@code core.client} by element type. {@link #getBox} and {@link #setOffset} are additions: CE's dropdown
 * and button each had them, and the common {@link NodeConnection} needs them for an enum socket's selector, so they live on the
 * base with the box CE's two subclasses share as the default.
 */
public class NodeElement {

	public String name = "default";
	public Node parent;
	public int index;
	public float offsetX;
	public float offsetY;

	public NodeElement(Node parent, int idx) {
		this.parent = parent;
		this.index = idx;
		resetOffset();
	}

	public NodeElement(String name, Node parent, int idx) {
		this.name = name;
		this.parent = parent;
		this.index = idx;
		resetOffset();
	}

	public void resetOffset() {
		offsetX = parent.posX;
		offsetY = parent.posY + index * 8;
	}

	/** Moves the element to an absolute offset, as a node does not (a node moves its elements with {@link #resetOffset}). */
	public void setOffset(float x, float y) {
		this.offsetX = x;
		this.offsetY = y;
	}

	/** True if the click at this node-space position was consumed. The base consumes nothing. */
	public boolean onClick(float x, float y) {
		return false;
	}

	/** minX, minY, maxX, maxY of the element's clickable value area: the 34 by 6 strip CE's dropdown and button both use. */
	public float[] getBox() {
		return new float[]{3 + offsetX, -3 + offsetY + 10, 37 + offsetX, 3 + offsetY + 10};
	}

	public CompoundTag writeToNBT(CompoundTag tag, NodeSystem sys) {
		tag.putFloat("oX", offsetX);
		tag.putFloat("oY", offsetY);
		tag.putInt("idx", index);
		tag.putInt("pIdx", sys.nodes.indexOf(parent));
		return tag;
	}

	public void readFromNBT(CompoundTag tag, NodeSystem sys) {
		offsetX = tag.getFloat("oX");
		offsetY = tag.getFloat("oY");
		index = tag.getInt("idx");
		parent = sys.nodes.get(tag.getInt("pIdx"));
	}
}
