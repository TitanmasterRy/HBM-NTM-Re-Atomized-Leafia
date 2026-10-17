package com.leafia.contents.machines.controlpanel.core.modular;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Where node loaders and the node menu register. Adapted from NTM-CE's {@code NTMControlPanelRegistry} (D-3061, completed by
 * CCP-NODE-EDITOR, D-3067).
 *
 * <p>Order matters for the loaders: {@code Node.nodeFromNBT} takes the first loader that answers, so the stock loader goes in
 * before an addon's, as in CE. {@link #addMenuCategories} is the order of the "add node" menu's top level, and
 * {@link #addMenuControl} maps a category to the creators that fill it (an addon adds to a stock category with
 * {@code computeIfAbsent}/{@code putIfAbsent} and {@code add}, as Leafia's did). All three are concurrent: they are appended to at
 * setup and read by the editor and by the server thread while panels load.
 */
public class NTMControlPanelRegistry {

	public static List<INodeLoader> nbtNodeLoaders = new CopyOnWriteArrayList<>();

	/** The categories of the node editor's add menu, in menu order. */
	public static List<String> addMenuCategories = new CopyOnWriteArrayList<>();

	/** The creators that fill each category. */
	public static Map<String, List<INodeMenuCreator>> addMenuControl = new ConcurrentHashMap<>();
}
