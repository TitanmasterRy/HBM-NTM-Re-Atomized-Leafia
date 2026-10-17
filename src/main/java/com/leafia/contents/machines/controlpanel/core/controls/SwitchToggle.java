package com.leafia.contents.machines.controlpanel.core.controls;

import java.util.Collections;
import java.util.List;

import com.leafia.contents.machines.controlpanel.core.Control;
import com.leafia.contents.machines.controlpanel.core.ControlEvent;
import com.leafia.contents.machines.controlpanel.core.ControlPanel;
import com.leafia.contents.machines.controlpanel.core.NodeSystem;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeBoolean;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeGetVar;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeSetVar;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;

/** The toggle switch: a press flips {@code isOn}. The common half of NTM-CE's {@code SwitchToggle} (CCP-CONTROLS, D-3064). */
public class SwitchToggle extends Control {

	public SwitchToggle(String name, String registryName, ControlPanel panel) {
		super(name, registryName, panel);
		vars.put("isOn", new DataValueFloat(0));
	}

	@Override
	public ControlType getControlType() {
		return ControlType.SWITCH;
	}

	@Override
	public float[] getSize() {
		return new float[]{1, 1, .62F};
	}

	@Override
	public List<String> getOutEvents() {
		return Collections.singletonList("ctrl_press");
	}

	@Override
	public void populateDefaultNodes(List<ControlEvent> receiveEvents) {
		NodeSystem ctrl_press = new NodeSystem(this);
		{
			NodeGetVar node0 = new NodeGetVar(170, 100, this).setData("isOn", false);
			ctrl_press.addNode(node0);
			NodeBoolean node1 = new NodeBoolean(230, 120).setData(NodeBoolean.BoolOperation.NOT);
			node1.inputs.get(0).setData(node0, 0, true);
			ctrl_press.addNode(node1);
			NodeSetVar node2 = new NodeSetVar(290, 140, this).setData("isOn", false);
			node2.inputs.get(0).setData(node1, 0, true);
			ctrl_press.addNode(node2);
		}
		receiveNodeMap.put("ctrl_press", ctrl_press);
	}

	@Override
	public Control newControl(ControlPanel panel) {
		return new SwitchToggle(name, registryName, panel);
	}
}
