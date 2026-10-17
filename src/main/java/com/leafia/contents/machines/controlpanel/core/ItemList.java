package com.leafia.contents.machines.controlpanel.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * A pop-up list of names with an action for the one picked: the <b>common half</b> of NTM-CE's {@code ItemList} (D-3062).
 *
 * <p>The node dropdowns (math operation, event name, variable) keep their choices here, and a node writes
 * {@link #itemNames} into its NBT (the event broadcast does), so the list has to exist on a server. What is not here is CE's
 * {@code render} (colours, alpha, text colour, the GL calls) and {@code mouseClicked} (it walks the list in screen space and
 * calls {@link #action}); both are the client's (§2.2) and arrive with the editor in batches 6 and 7 as a class in
 * {@code core.client} that reads this one's public fields. {@link #getMouseoverIndex} and {@link #getBoundingBox}, the two pure
 * geometry methods that drawing needs, are kept.
 *
 * <p>A child list is the sub-menu an {@code {expandable}} entry opens: {@link #action} returns the list to show next, or null
 * when the pick was final.
 */
public class ItemList {

	public float posX;
	public float posY;
	public float width;

	public List<String> itemNames = new ArrayList<>();
	/** Called with the picked name. Returns a sub-list to open, or null if the pick was final. */
	public Function<String, ItemList> action;

	public ItemList child;

	public boolean isClosed;

	/** The list's look (CE's fields): the fill colour, its alpha and the text colour. A node's dropdown restyles its own. */
	public float r = 0.5F, g = 0.5F, b = 0.5F, alpha = 0.5F;
	public int textColor = 0xFFAFAFAF;

	public ItemList(float posX, float posY, float width, Function<String, ItemList> a) {
		this.posX = posX;
		this.posY = posY;
		this.width = width;
		this.action = a;
		isClosed = false;
	}

	public ItemList addItems(String item) {
		itemNames.add(item);
		return this;
	}

	public ItemList addItems(String item1, String item2) {
		itemNames.add(item1);
		itemNames.add(item2);
		return this;
	}

	public ItemList addItems(String... items) {
		Collections.addAll(itemNames, items);
		return this;
	}

	public ItemList addItems(Collection<String> items) {
		itemNames.addAll(items);
		return this;
	}

	/** The index of the entry under this point (6 units per row, 5 tall, starting 2 below the top), or -1. */
	public int getMouseoverIndex(float x, float y) {
		float yPos = posY + 2;
		for(int i = 0; i < itemNames.size(); i++) {
			if(x > posX && x < posX + width && y > yPos && y < yPos + 5) {
				return i;
			}
			yPos += 6;
		}
		return -1;
	}

	/** minX, minY, maxX, maxY of this list and, if one is open, its child. */
	public float[] getBoundingBox() {
		if(child != null) {
			float[] box = child.getBoundingBox();
			return new float[]{Math.min(posX, box[0]), Math.min(posY, box[1]), Math.max(posX + width, box[2]), Math.max(posY + itemNames.size() * 6 + 4, box[3])};
		}
		return new float[]{posX, posY, posX + width, posY + itemNames.size() * 6 + 4};
	}

	/**
	 * A click at this point in the list's space. True if it picked an entry that was final (no sub-list opened). CE's
	 * {@code mouseClicked}: a click inside an open child goes to the child; one outside closes it; then the row under the point
	 * runs {@link #action}, and a list it returns becomes the child, placed beside the row. (The draw half left this for the
	 * client in D-3062, but it names no client type, and the node editor's dropdowns need it in common code.)
	 */
	public boolean mouseClicked(float x, float y) {
		if(isClosed)
			return false;
		boolean didAction = false;
		if(child != null && ControlGeometry.intersects2DBox(x, y, child.getBoundingBox()))
			return child.mouseClicked(x, y);
		if(child != null) {
			child.close();
			child = null;
		}
		float yPos = posY + 2;
		for(String s : new ArrayList<>(itemNames)) {
			if(x > posX && x < posX + width && y > yPos && y < yPos + 6) {
				ItemList newList = action.apply(s);
				if(newList != null) {
					newList.posX = posX + width;
					newList.posY = yPos;
					child = newList;
				} else {
					didAction = true;
				}
			}
			yPos += 6;
		}
		return didAction;
	}

	public void close() {
		isClosed = true;
	}

	public void open() {
		isClosed = false;
	}
}
