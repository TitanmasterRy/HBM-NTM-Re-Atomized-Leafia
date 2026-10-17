package com.leafia.contents.machines.controlpanel.core.modular;

import com.leafia.contents.machines.controlpanel.core.ItemList;
import com.leafia.contents.machines.controlpanel.core.nodes.Node;

/**
 * One source of entries for a category of the node editor's "add node" menu. Adapted from NTM-CE's {@code INodeMenuCreator}
 * (CCP-NODE-EDITOR, D-3067), with the editor passed as a {@link NodeEditContext} so that a creator is common code.
 *
 * <p>{@link #addItems} puts the category's entries on a list; {@link #selectItem} makes the node for the entry that was picked,
 * or returns null if the name is not this creator's (a category can have several creators, and each is asked in turn).
 */
public interface INodeMenuCreator {

	Node selectItem(String s2, float x, float y, NodeEditContext editor);

	void addItems(ItemList list, float x, float y, NodeEditContext editor);
}
