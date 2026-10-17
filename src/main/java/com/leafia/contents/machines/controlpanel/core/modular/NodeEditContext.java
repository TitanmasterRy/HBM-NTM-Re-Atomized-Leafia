package com.leafia.contents.machines.controlpanel.core.modular;

import java.util.List;

import com.leafia.contents.machines.controlpanel.core.ControlEvent;
import com.leafia.contents.machines.controlpanel.core.NodeSystem;

/**
 * What the node editor tells a menu creator about the graph being edited (CCP-NODE-EDITOR, D-3067).
 *
 * <p>NTM-CE's {@code INodeMenuCreator} took the {@code SubElementNodeEditor} itself and read three fields off it:
 * {@code currentSystem}, {@code currentEvent} and {@code sendEvents}. The editor is a client page, and the creators build nodes
 * (common code, and Leafia's batch 10 and 13 categories are too), so the three fields are this plain holder that the page keeps
 * up to date. The field names are CE's, so a category written against the editor reads the same.
 */
public class NodeEditContext {

	/** The graph being edited: the current event's, or a function node's body under it. {@code currentSystem.parent} is the control. */
	public NodeSystem currentSystem;
	/** The event the graph is for. */
	public ControlEvent currentEvent;
	/** The events this control may broadcast, or null when the graph is a <b>send</b> graph (which cannot broadcast, and may cancel). */
	public List<ControlEvent> sendEvents;

	public NodeEditContext() {}

	public NodeEditContext(NodeSystem currentSystem, ControlEvent currentEvent, List<ControlEvent> sendEvents) {
		this.currentSystem = currentSystem;
		this.currentEvent = currentEvent;
		this.sendEvents = sendEvents;
	}

	/** True for a send graph (CE tested {@code sendEvents == null} everywhere). */
	public boolean isSendGraph() {
		return sendEvents == null;
	}
}
