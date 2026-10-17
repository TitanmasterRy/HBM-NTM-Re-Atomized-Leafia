package com.leafia.contents.machines.controlpanel.core.block;

/**
 * Implemented by the menu that edits a panel (batch 6's {@code ControlEditMenu}). The server accepts a client's whole-panel edit
 * only from a player whose open container is one of these, for <i>this</i> panel (D-3063): CE
 * applied the edit whether or not any editor had been opened.
 *
 * <p>It is an interface in batch 3 so that the trust check exists, and is tested with a stand-in menu, before the menu does.
 */
public interface IControlEditSession {

	/** The panel this menu edits. */
	ControlPanelTE editedPanel();
}
