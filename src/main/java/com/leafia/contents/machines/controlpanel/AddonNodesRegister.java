package com.leafia.contents.machines.controlpanel;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.leafia.contents.machines.controlpanel.categories.NCLeafiaBoolean;
import com.leafia.contents.machines.controlpanel.categories.NCLeafiaInput;
import com.leafia.contents.machines.controlpanel.categories.NCLeafiaOutput;
import com.leafia.contents.machines.controlpanel.categories.NCLeafiaPack;
import com.leafia.contents.machines.controlpanel.categories.NCLeafiaText;
import com.leafia.contents.machines.controlpanel.categories.NCLeafiaUtility;
import com.leafia.contents.machines.controlpanel.core.ControlPanelGuard;
import com.leafia.contents.machines.controlpanel.core.modular.INodeLoader;
import com.leafia.contents.machines.controlpanel.core.modular.INodeMenuCreator;
import com.leafia.contents.machines.controlpanel.core.modular.NTMControlPanelRegistry;
import com.leafia.contents.machines.controlpanel.ic10.IC10Program;

/**
 * Registers Leafia's own nodes with the Custom Control Panel: the loader that recreates them from a saved graph and the menu
 * categories that offer them (CCP-LEAFIA-NODES, D-3070). Adapted from Leafia's {@code AddonNodesRegister}.
 *
 * <p>Leafia's version appended to CE's categories by {@code putIfAbsent}/{@code get}/{@code add}; this one does the same on the
 * port's registry (D-3067), idempotently, and <b>after</b> {@code StockNodesRegister.register()} so the stock creator is first in
 * every category and the loader order is stock then addon, as in 1.12.2. Two categories are Leafia's own: "Text" and "Utility",
 * appended to the menu's top level after the stock six.
 *
 * <p>The graph instruments' two nodes are in the Input and Output creators (CCP-INSTRUMENTS-1, D-3071). The "Pack" creator (the
 * IC10 node, CCP-IC10, D-3073) is appended to the stock "Pack" category; it offers the node only while
 * {@code LeafiaFeatures.controlPanelIC10} is on, and the same call installs the guard's check of an IC10 program.
 */
public final class AddonNodesRegister {

	/** The colour of the string nodes. */
	public static final float[] colorString = new float[]{0.2F, 0.8F, 1};
	/** The colour of the utility nodes. */
	public static final float[] colorUtility = new float[]{1, 0.614F, 0.189F};

	private AddonNodesRegister() {}

	private static INodeMenuCreator creator(String category) {
		return switch(category) {
			case "Input" -> new NCLeafiaInput();
			case "Output" -> new NCLeafiaOutput();
			case "Boolean" -> new NCLeafiaBoolean();
			case "Pack" -> new NCLeafiaPack();
			case "Text" -> new NCLeafiaText();
			default -> new NCLeafiaUtility();
		};
	}

	/** Registers the addon's loader and menu entries once; a second call changes nothing. */
	public static synchronized void register() {
		for(String category : new String[]{"Text", "Utility"}) {
			if(!NTMControlPanelRegistry.addMenuCategories.contains(category))
				NTMControlPanelRegistry.addMenuCategories.add(category);
		}
		for(String category : new String[]{"Input", "Output", "Boolean", "Pack", "Text", "Utility"}) {
			List<INodeMenuCreator> list = NTMControlPanelRegistry.addMenuControl.computeIfAbsent(category, k -> new CopyOnWriteArrayList<>());
			INodeMenuCreator c = creator(category);
			boolean present = false;
			for(INodeMenuCreator have : list) {
				if(have.getClass() == c.getClass()) {
					present = true;
					break;
				}
			}
			if(!present)
				list.add(c);
		}
		// The guard measures an IC10 program wherever one arrives (CCP-IC10, D-3073).
		ControlPanelGuard.NODE_SCANNERS.put("leafia_ic10", IC10Program::problem);
		boolean hasLoader = false;
		for(INodeLoader l : NTMControlPanelRegistry.nbtNodeLoaders) {
			if(l instanceof AddonNodeLoader) {
				hasLoader = true;
				break;
			}
		}
		if(!hasLoader)
			NTMControlPanelRegistry.nbtNodeLoaders.add(new AddonNodeLoader());
	}
}
