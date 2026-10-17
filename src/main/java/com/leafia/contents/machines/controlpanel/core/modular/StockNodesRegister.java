package com.leafia.contents.machines.controlpanel.core.modular;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.leafia.contents.machines.controlpanel.core.modular.categories.NCStockBoolean;
import com.leafia.contents.machines.controlpanel.core.modular.categories.NCStockComposite;
import com.leafia.contents.machines.controlpanel.core.modular.categories.NCStockInput;
import com.leafia.contents.machines.controlpanel.core.modular.categories.NCStockLogic;
import com.leafia.contents.machines.controlpanel.core.modular.categories.NCStockMath;
import com.leafia.contents.machines.controlpanel.core.modular.categories.NCStockOutput;

/**
 * The stock nodes' colours, their loader's registration and the stock categories of the node menu. Adapted from NTM-CE's
 * {@code StockNodesRegister}; complete since CCP-NODE-EDITOR (D-3067), which added the menu half.
 *
 * <p>{@link #register()} adds the loader (all a server needs to read a saved graph) and the six menu categories with their
 * {@code NCStock*} creators, in CE's order: Input, Output, Math, Boolean, Logic, Pack. Every step is idempotent, and a stock
 * creator is put <b>first</b> in its category so an addon's entries (Leafia's batch 10 and 13 categories) follow it, as in CE
 * where the stock registration ran first. The panel's common setup calls it, so a graph can be read on a server whether or not an
 * editor has ever opened.
 */
public class StockNodesRegister {

	//Input for the system, such as the in variables for the event or a single value
	public static final float[] colorInput = new float[]{0, 1, 0};
	//Intermediate node for math. Has a dropdown menu for operations like add, multiply, sin, abs, etc.
	public static final float[] colorMath = new float[]{1, 1, 0};
	//for boolean functions: and, not, xor etc.
	public static final float[] colorBoolean = new float[]{1, 0, 1};
	//Intermediate nodes for logic such as for loops and if statements. Each contains child node systems, which possibly produce outputs.
	public static final float[] colorLogic = new float[]{.4F, .4F, 1};
	//The outputs that get evaluated, such as broadcasting a new event or setting an arbitrary variable
	public static final float[] colorOutput = new float[]{1, 0, 0};
	//Composite series
	public static final float[] colorComposite = new float[]{1F, 0.399F, 0.842F};

	/** The categories and the creators that fill them, in menu order. */
	private static final String[] CATEGORIES = {"Input", "Output", "Math", "Boolean", "Logic", "Pack"};

	private static INodeMenuCreator[] creators() {
		return new INodeMenuCreator[]{new NCStockInput(), new NCStockOutput(), new NCStockMath(), new NCStockBoolean(), new NCStockLogic(), new NCStockComposite()};
	}

	/** Registers the stock node loader and menu once; a second call changes nothing. */
	public static synchronized void register() {
		boolean hasLoader = false;
		for(INodeLoader l : NTMControlPanelRegistry.nbtNodeLoaders) {
			if(l instanceof StockNodeLoader) {
				hasLoader = true;
				break;
			}
		}
		if(!hasLoader)
			NTMControlPanelRegistry.nbtNodeLoaders.add(new StockNodeLoader());

		INodeMenuCreator[] stock = creators();
		for(int i = 0; i < CATEGORIES.length; i++) {
			String category = CATEGORIES[i];
			if(!NTMControlPanelRegistry.addMenuCategories.contains(category))
				NTMControlPanelRegistry.addMenuCategories.add(category);
			List<INodeMenuCreator> list = NTMControlPanelRegistry.addMenuControl.computeIfAbsent(category, k -> new CopyOnWriteArrayList<>());
			boolean present = false;
			for(INodeMenuCreator c : list) {
				if(c.getClass() == stock[i].getClass()) {
					present = true;
					break;
				}
			}
			if(!present)
				list.add(0, stock[i]);
		}
	}
}
