package com.leafia.contents.machines.controlpanel.core;

import java.util.function.Function;
import java.util.function.Supplier;

import com.leafia.contents.machines.controlpanel.core.nodes.Node;

import net.minecraft.nbt.CompoundTag;

/**
 * A node's dropdown row: the <b>common half</b> of NTM-CE's {@code NodeDropdown} (D-3062). It owns the {@link ItemList} of
 * choices and the getter for the name shown on the closed row; the node reads and writes those, so a server needs them.
 *
 * <p>CE's {@code render} is the client's (§2.2), {@code NodeDraw}'s; {@link #onClick} (open, close, forward the click to the
 * list) is here, as pure geometry, and so is CE's colour setup on the list (alpha 0.95, grey 0.3, text colour 0xFFDFDFDF), which
 * {@code ItemListDraw} reads (CCP-NODE-EDITOR, D-3067).
 *
 * <p>A dropdown is never saved: a node writes its own selection (an enum ordinal, a name) and rebuilds the dropdown when it is
 * created. CE threw if asked to serialise one; so does this.
 */
public class NodeDropdown extends NodeElement {

	public ItemList list;
	public Supplier<String> nameGetter;

	public NodeDropdown(Node parent, int idx, Function<String, ItemList> func, Supplier<String> nameGetter) {
		super(parent, idx);
		list = new ItemList(0, 0, 32, func);
		list.alpha = 0.95F;
		list.r = list.g = list.b = 0.3F;
		list.textColor = 0xFFDFDFDF;
		list.close();
		resetOffset();
		this.nameGetter = nameGetter;
	}

	@Override
	public CompoundTag writeToNBT(CompoundTag tag, NodeSystem sys) {
		throw new IllegalStateException("Dropdown node should not be serialized!");
	}

	@Override
	public void readFromNBT(CompoundTag tag, NodeSystem sys) {
		throw new IllegalStateException("Dropdown node should not be serialized!");
	}

	@Override
	public void resetOffset() {
		super.resetOffset();
		if(list != null) {
			list.posX = this.offsetX + 4;
			list.posY = this.offsetY + 14;
		}
	}

	@Override
	public void setOffset(float x, float y) {
		this.offsetX = x;
		this.offsetY = y;
		list.posX = x + 4;
		list.posY = y + 14;
	}

	/**
	 * CE's click: a click on an open list's entry picks it and closes the list; a click on the row opens a closed list and
	 * closes an open one. True if the click was used. (Moved to the common half in D-3067: it is pure geometry.)
	 */
	@Override
	public boolean onClick(float x, float y) {
		if(!list.isClosed && ControlGeometry.intersects2DBox(x, y, list.getBoundingBox())) {
			if(list.mouseClicked(x, y)) {
				list.close();
				return true;
			}
		}
		if(ControlGeometry.intersects2DBox(x, y, getBox())) {
			if(list.isClosed)
				list.open();
			else
				list.close();
			return true;
		}
		return false;
	}

	/** The name on the closed row. */
	public String currentName() {
		return nameGetter.get();
	}

	/** Picks an entry as a click on it would: runs the list's action with the name. Returns the sub-list it opened, if any. */
	public ItemList choose(String name) {
		return list.action.apply(name);
	}
}
