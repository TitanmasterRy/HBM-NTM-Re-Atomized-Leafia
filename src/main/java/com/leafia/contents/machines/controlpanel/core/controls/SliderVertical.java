package com.leafia.contents.machines.controlpanel.core.controls;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.leafia.contents.machines.controlpanel.core.Control;
import com.leafia.contents.machines.controlpanel.core.ControlEvent;
import com.leafia.contents.machines.controlpanel.core.ControlPanel;
import com.leafia.contents.machines.controlpanel.core.NodeSystem;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeConditional;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeGetVar;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeInput;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeMath;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeSetVar;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;

/**
 * The vertical slider, six notches (0 to 5): a plain press steps it up, a sneaking press steps it down. The common half of
 * NTM-CE's {@code SliderVertical} (CCP-CONTROLS, D-3064). The default graph clamps {@code value + (sneaking ? -1 : 1)} to 0..5.
 */
public class SliderVertical extends Control {

	public SliderVertical(String name, String registryName, ControlPanel panel) {
		super(name, registryName, panel);
		vars.put("value", new DataValueFloat(0));
	}

	@Override
	public ControlType getControlType() {
		return ControlType.SLIDER;
	}

	@Override
	public float[] getSize() {
		return new float[]{0.8125F, 2.3125F, .31F};
	}

	@Override
	public List<String> getOutEvents() {
		return Collections.singletonList("ctrl_press");
	}

	@Override
	public void populateDefaultNodes(List<ControlEvent> receiveEvents) {
		NodeSystem ctrl_press = new NodeSystem(this);
		{
			Map<String, DataValue> vars = new HashMap<>(receiveEvents.get(0).vars);
			vars.put("from index", new DataValueFloat(0));
			NodeInput node0 = new NodeInput(170, 100, "Event Data").setVars(vars);
			ctrl_press.addNode(node0);
			NodeGetVar node1 = new NodeGetVar(170, 150, this).setData("value", false);
			ctrl_press.addNode(node1);
			NodeConditional node2 = new NodeConditional(230, 110);
			node2.inputs.get(0).setData(node0, 1, true);
			node2.inputs.get(1).setDefault(new DataValueFloat(-1));
			node2.inputs.get(2).setDefault(new DataValueFloat(1));
			ctrl_press.addNode(node2);
			NodeMath node3 = new NodeMath(290, 130).setData(NodeMath.Operation.ADD);
			node3.inputs.get(0).setData(node2, 0, true);
			node3.inputs.get(1).setData(node1, 0, true);
			ctrl_press.addNode(node3);
			NodeMath node4 = new NodeMath(350, 130).setData(NodeMath.Operation.CLAMP);
			node4.inputs.get(0).setData(node3, 0, true);
			node4.inputs.get(1).setDefault(new DataValueFloat(0));
			node4.inputs.get(2).setDefault(new DataValueFloat(5));
			ctrl_press.addNode(node4);
			NodeSetVar node5 = new NodeSetVar(410, 110, this).setData("value", false);
			node5.inputs.get(0).setData(node4, 0, true);
			ctrl_press.addNode(node5);
		}
		receiveNodeMap.put("ctrl_press", ctrl_press);
	}

	@Override
	public Control newControl(ControlPanel panel) {
		return new SliderVertical(name, registryName, panel);
	}
}
