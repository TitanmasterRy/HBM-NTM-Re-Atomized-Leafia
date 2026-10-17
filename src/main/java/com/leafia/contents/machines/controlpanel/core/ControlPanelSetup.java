package com.leafia.contents.machines.controlpanel.core;

import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.machines.controlpanel.AddonNodesRegister;
import com.leafia.contents.machines.controlpanel.instruments.AddonInstrumentRegister;
import com.leafia.contents.machines.controlpanel.core.block.ControlPanelTE;
import com.leafia.contents.machines.controlpanel.core.modular.StockNodesRegister;

/**
 * What the Custom Control Panel needs done once, after the registries are built and before any panel is read: the event table,
 * the control registry, and the stock node loader (D-3063). Called from the mod's common setup, gated on
 * {@link LeafiaFeatures#controlPanel}; every step is idempotent, so a test may call it again.
 */
public final class ControlPanelSetup {

	private ControlPanelSetup() {}

	public static void commonSetup() {
		if(!LeafiaFeatures.controlPanel)
			return;
		ControlEvent.init();
		// Leafia's instruments go on the addon list before init folds it in (D-3071).
		AddonInstrumentRegister.register();
		ControlRegistry.init();
		StockNodesRegister.register();
		// Leafia's own nodes: the loader after the stock one, and their menu entries after the stock creators (D-3070).
		AddonNodesRegister.register();
		// The screwdriver opens the editor (D-3066). A screen is registered for the menu in LeafiaClient.
		ControlPanelTE.editorOpener = ControlEditMenu::open;
	}
}
