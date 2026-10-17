package com.leafia.contents.machines.controlpanel.core.controls;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.leafia.contents.machines.controlpanel.core.Control;
import com.leafia.contents.machines.controlpanel.core.ControlEvent;
import com.leafia.contents.machines.controlpanel.core.ControlPanel;
import com.leafia.contents.machines.controlpanel.core.NodeSystem;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeBoolean;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeBuffer;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeFunction;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeGetVar;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeInput;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeSetVar;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;

/**
 * The covered push button: a red button under a hinged cover. A sneaking press flips the cover ({@code isCoverOpen}); a plain
 * press with the cover open pushes the button and lights it. The common half of NTM-CE's {@code ButtonEncasedPush}
 * (CCP-CONTROLS, D-3064).
 *
 * <p>Its default press graph reads the {@code isSneaking} variable by position in the event list the editor hands in, as CE does:
 * {@code receiveEvents.get(0)} is the control's first out-event, {@code ctrl_press}.
 */
public class ButtonEncasedPush extends Control {

	public ButtonEncasedPush(String name, String registryName, ControlPanel panel) {
		super(name, registryName, panel);
		vars.put("isPushed", new DataValueFloat(0));
		vars.put("isLit", new DataValueFloat(0));
		vars.put("isCoverOpen", new DataValueFloat(0));
	}

	@Override
	public ControlType getControlType() {
		return ControlType.BUTTON;
	}

	@Override
	public float[] getSize() {
		return new float[]{1.5F, 1.5F, 1F};
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
			NodeFunction node1 = new NodeFunction(230, 80);
			NodeSystem node1_subsystem = new NodeSystem(this);
			{
				NodeGetVar node1_0 = new NodeGetVar(170, 100, this).setData("isCoverOpen", false);
				node1_subsystem.addNode(node1_0);
				NodeBoolean node1_1 = new NodeBoolean(230, 120).setData(NodeBoolean.BoolOperation.NOT);
				node1_1.inputs.get(0).setData(node1_0, 0, true);
				node1_subsystem.addNode(node1_1);
				NodeSetVar node1_2 = new NodeSetVar(290, 140, this).setData("isCoverOpen", false);
				node1_2.inputs.get(0).setData(node1_1, 0, true);
				node1_subsystem.addNode(node1_2);
			}
			node1.inputs.get(0).setData(node0, 1, true);
			ctrl_press.subSystems.put(node1, node1_subsystem);
			ctrl_press.addNode(node1);
			NodeBoolean node2 = new NodeBoolean(230, 120).setData(NodeBoolean.BoolOperation.NOT);
			node2.inputs.get(0).setData(node0, 1, true);
			ctrl_press.addNode(node2);
			NodeGetVar node3 = new NodeGetVar(170, 160, this).setData("isCoverOpen", false);
			ctrl_press.addNode(node3);
			NodeBoolean node4 = new NodeBoolean(290, 130).setData(NodeBoolean.BoolOperation.AND);
			node4.inputs.get(0).setData(node2, 0, true);
			node4.inputs.get(1).setData(node3, 0, true);
			ctrl_press.addNode(node4);
			NodeFunction node5 = new NodeFunction(350, 140);
			NodeSystem node5_subsystem = new NodeSystem(this);
			{
				NodeGetVar node5_0 = new NodeGetVar(170, 100, this).setData("isPushed", false);
				node5_subsystem.addNode(node5_0);
				NodeBoolean node5_1 = new NodeBoolean(230, 120).setData(NodeBoolean.BoolOperation.NOT);
				node5_1.inputs.get(0).setData(node5_0, 0, true);
				node5_subsystem.addNode(node5_1);
				NodeSetVar node5_2 = new NodeSetVar(290, 140, this).setData("isPushed", false);
				node5_2.inputs.get(0).setData(node5_1, 0, true);
				node5_subsystem.addNode(node5_2);
				NodeSetVar node5_3 = new NodeSetVar(290, 100, this).setData("isLit", false);
				node5_3.inputs.get(0).setDefault(new DataValueFloat(1));
				node5_subsystem.addNode(node5_3);
			}
			node5.inputs.get(0).setData(node4, 0, true);
			ctrl_press.subSystems.put(node5, node5_subsystem);
			ctrl_press.addNode(node5);
		}
		receiveNodeMap.put("ctrl_press", ctrl_press);
		NodeSystem tick = new NodeSystem(this);
		{
			NodeGetVar node0 = new NodeGetVar(170, 100, this).setData("isPushed", false);
			tick.addNode(node0);
			NodeBuffer node1 = new NodeBuffer(230, 120);
			node1.inputs.get(0).setData(node0, 0, true);
			node1.inputs.get(1).setDefault(new DataValueFloat(15));
			tick.addNode(node1);
			NodeFunction node2 = new NodeFunction(290, 140);
			NodeSystem node2_subsystem = new NodeSystem(this);
			{
				NodeSetVar node2_0 = new NodeSetVar(290, 100, this).setData("isPushed", false);
				node2_subsystem.addNode(node2_0);
				NodeSetVar node2_1 = new NodeSetVar(290, 140, this).setData("isLit", false);
				node2_subsystem.addNode(node2_1);
			}
			node2.inputs.get(0).setData(node1, 0, true);
			tick.subSystems.put(node2, node2_subsystem);
			tick.addNode(node2);
		}
		receiveNodeMap.put("tick", tick);
	}

	@Override
	public Control newControl(ControlPanel panel) {
		return new ButtonEncasedPush(name, registryName, panel);
	}
}
