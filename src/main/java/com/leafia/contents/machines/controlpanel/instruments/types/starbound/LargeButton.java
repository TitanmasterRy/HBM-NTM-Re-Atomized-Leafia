package com.leafia.contents.machines.controlpanel.instruments.types.starbound;

import java.util.Collections;
import java.util.List;

import com.leafia.contents.machines.controlpanel.core.Control;
import com.leafia.contents.machines.controlpanel.core.ControlEvent;
import com.leafia.contents.machines.controlpanel.core.ControlPanel;
import com.leafia.contents.machines.controlpanel.core.NodeSystem;
import com.leafia.contents.machines.controlpanel.core.controls.ControlType;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeBuffer;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeFunction;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeGetVar;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeMath;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeMath.Operation;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeSetVar;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;
import com.leafia.contents.machines.controlpanel.nodes.NodeSounder;
import com.leafia.contents.machines.controlpanel.nodes.NodeSounder.NodeSnd;

/**
 * The large wall button: six units square, a push that lights and presses a lens in and lets go 45 ticks later
 * (CCP-INSTRUMENTS-1, D-3071). The common half of Leafia's {@code LargeButton} (its package, {@code starbound}, is upstream's).
 * A press ({@code ctrl_press}) plays the {@code BUTTON} sound node and sets both {@code isPushed} and {@code isLit} to 1; the tick
 * graph watches {@code isPushed} through a 45-tick buffer and clears both.
 *
 * <p>Leafia's comment in the press graph: "the set variable node values doesn't get saved. BRUH" - a Math node feeding the literal 1
 * to both Set Variable nodes is how it works around {@code NodeSetVar} forgetting a typed literal across a save (flagged under
 * D-3064), so this control, unlike the stock encased button, still lights after a reload. Kept as it is.
 */
public class LargeButton extends Control {

	/** The ticks before the button lets go: the buffer's length in the default tick graph. */
	public static final int HOLD_TICKS = 45;

	public LargeButton(String name, String registryName, ControlPanel panel) {
		super(name, registryName, panel);
		vars.put("isPushed", new DataValueFloat(0));
		vars.put("isLit", new DataValueFloat(0));
	}

	@Override
	public ControlType getControlType() {
		return ControlType.BUTTON;
	}

	@Override
	public float[] getSize() {
		return new float[]{6, 6, 0.5f};
	}

	@Override
	public List<String> getOutEvents() {
		return Collections.singletonList("ctrl_press");
	}

	@Override
	public Control newControl(ControlPanel controlPanel) {
		return new LargeButton(name, registryName, controlPanel);
	}

	/** Leafia: "mostly stolen code uwu". */
	@Override
	public void populateDefaultNodes(List<ControlEvent> receiveEvents) {
		NodeSystem ctrl_press = new NodeSystem(this);
		{
			NodeSounder node0 = new NodeSounder(230, 90 - (140 - 90));
			node0.snd = NodeSnd.BUTTON;
			ctrl_press.addNode(node0);

			NodeMath nodeBruh = new NodeMath(170, 100).setData(Operation.ADD);
			nodeBruh.inputs.get(0).setDefault(new DataValueFloat(1));
			ctrl_press.addNode(nodeBruh);

			NodeSetVar node2 = new NodeSetVar(230, 140, this).setData("isLit", false);
			node2.inputs.get(0).setData(nodeBruh, 0, true);
			ctrl_press.addNode(node2);
			NodeSetVar node3 = new NodeSetVar(230, 90, this).setData("isPushed", false);
			node3.inputs.get(0).setData(nodeBruh, 0, true);
			ctrl_press.addNode(node3);
		}
		receiveNodeMap.put("ctrl_press", ctrl_press);
		NodeSystem tick = new NodeSystem(this);
		{
			NodeGetVar node0 = new NodeGetVar(170, 100, this).setData("isPushed", false);
			tick.addNode(node0);
			NodeBuffer node1 = new NodeBuffer(230, 120);
			node1.inputs.get(0).setData(node0, 0, true);
			node1.inputs.get(1).setDefault(new DataValueFloat(HOLD_TICKS));
			tick.addNode(node1);
			NodeFunction node2 = new NodeFunction(290, 130);
			NodeSystem node2_subsystem = new NodeSystem(this);
			{
				node2_subsystem.addNode(new NodeSetVar(290, 90, this).setData("isPushed", false));
				node2_subsystem.addNode(new NodeSetVar(290, 140, this).setData("isLit", false));
			}
			node2.inputs.get(0).setData(node1, 0, true);
			tick.subSystems.put(node2, node2_subsystem);
			tick.addNode(node2);
		}
		receiveNodeMap.put("tick", tick);
	}
}
