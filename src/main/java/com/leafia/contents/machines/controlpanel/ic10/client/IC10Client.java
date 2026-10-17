package com.leafia.contents.machines.controlpanel.ic10.client;

import com.leafia.contents.machines.controlpanel.core.client.SubElementNodeEditor;
import com.leafia.contents.machines.controlpanel.nodes.pack.NodeIC10;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The client half of the IC10 node (CCP-IC10, D-3073): what its two buttons do in the node editor.
 *
 * <p>D-3067 left a hook for each: a button's action is registered by name in {@link SubElementNodeEditor#BUTTON_ACTIONS}.
 * "Edit Code" (Leafia's {@code MixinSubElementNodeEditor}) points the editor page at the pressed node's own program and pushes it;
 * "Reset State" starts the node's state afresh, which Leafia did in the button's own {@code onClicked}. The node itself is a common
 * class and cannot name a page. The drawing needs nothing registered: the two buttons are {@code NodeButton}s, which
 * {@code NodeDraw} already draws.
 *
 * <p>{@code ControlEditScreen.init} calls {@link #register}, once per screen; the actions replace D-3067's placeholder for "Edit
 * Code", which pushed an editor with no program to edit.
 */
@OnlyIn(Dist.CLIENT)
public final class IC10Client {

	private IC10Client() {}

	public static void register() {
		SubElementNodeEditor.BUTTON_ACTIONS.put("Edit Code", (editor, button) -> {
			if(button.parent instanceof NodeIC10 node && editor.gui.ic10Editor != null) {
				editor.gui.ic10Editor.open(node.instructions);
				editor.gui.pushElement(editor.gui.ic10Editor);
			}
		});
		SubElementNodeEditor.BUTTON_ACTIONS.put("Reset State", (editor, button) -> {
			if(button.parent instanceof NodeIC10 node)
				node.resetState();
		});
	}
}
