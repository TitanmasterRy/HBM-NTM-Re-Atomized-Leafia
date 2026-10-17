package com.leafia.contents.machines.controlpanel.core.modular;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.leafia.contents.machines.controlpanel.core.ItemList;
import com.leafia.contents.machines.controlpanel.core.nodes.Node;

/**
 * The node editor's "add node" menu, as a tree of {@link ItemList}s (CCP-NODE-EDITOR, D-3067): the part of CE's
 * {@code SubElementNodeEditor.keyTyped} (Shift+A) that has no client type in it, so the menu can be driven in a test by clicking
 * on its rows exactly as the editor does.
 *
 * <p>The top level lists "Paste" (when the clipboard can be pasted here) and one expandable row per registered category
 * ({@link NTMControlPanelRegistry#addMenuCategories}). Choosing a category builds a sub-list from every creator registered for it
 * ({@link NTMControlPanelRegistry#addMenuControl}); choosing a node asks each creator in turn for it, the first one that knows
 * the name wins, and the node is handed to {@code onNode} where the editor adds and activates it. The grid position of a node is
 * read from {@code position} when the entry is <i>chosen</i> (CE's lambda read the mouse then), so it lands where the player
 * clicked the entry, not where the menu opened.
 */
public final class NodeAddMenu {

	private NodeAddMenu() {}

	/** The prefix that marks a row as a category that opens a sub-list. */
	public static final String EXPANDABLE = "{expandable}";

	/**
	 * @param screenX where the menu opens (screen space)
	 * @param position the grid position (x, y) a new node or a paste is placed at, read when an entry is chosen
	 * @param canPaste whether "Paste" is offered
	 * @param onPaste called with the grid position when "Paste" is chosen
	 * @param onNode called with the new node when a node entry is chosen
	 * @param onDone called after either, so the editor can close and forget the menu
	 */
	public static ItemList build(NodeEditContext editor, float screenX, float screenY, Supplier<float[]> position, boolean canPaste,
			Consumer<float[]> onPaste, Consumer<Node> onNode, Runnable onDone) {
		ItemList root = new ItemList(screenX, screenY, 32, s -> {
			float[] at = position.get();
			if("Paste".equals(s)) {
				onPaste.accept(at);
				onDone.run();
				return null;
			}
			if(!s.startsWith(EXPANDABLE))
				return null;
			List<INodeMenuCreator> controllers = NTMControlPanelRegistry.addMenuControl.get(s.substring(EXPANDABLE.length()));
			if(controllers == null)
				return null;
			ItemList list = new ItemList(0, 0, 32, s2 -> {
				float[] where = position.get();
				for(INodeMenuCreator controller : controllers) {
					Node node = controller.selectItem(s2, where[0], where[1], editor);
					if(node != null) {
						onNode.accept(node);
						onDone.run();
						break;
					}
				}
				return null;
			});
			for(INodeMenuCreator controller : controllers)
				controller.addItems(list, at[0], at[1], editor);
			return list;
		});
		if(canPaste)
			root.addItems("Paste");
		for(String category : NTMControlPanelRegistry.addMenuCategories)
			root.addItems(EXPANDABLE + category);
		return root;
	}
}
