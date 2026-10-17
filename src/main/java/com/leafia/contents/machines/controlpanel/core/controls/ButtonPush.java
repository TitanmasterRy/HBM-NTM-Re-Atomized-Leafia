package com.leafia.contents.machines.controlpanel.core.controls;

import java.util.Collections;
import java.util.List;

import com.leafia.contents.machines.controlpanel.core.Control;
import com.leafia.contents.machines.controlpanel.core.ControlEvent;
import com.leafia.contents.machines.controlpanel.core.ControlPanel;
import com.leafia.contents.machines.controlpanel.core.NodeSystem;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeBoolean;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeBuffer;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeFunction;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeGetVar;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeSetVar;
import com.leafia.contents.machines.controlpanel.core.types.DataValueEnum;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;

import net.minecraft.world.item.DyeColor;

/**
 * The push button: a momentary button with a lamp, whose press toggles {@code isPushed} and {@code isLit} and which lets go ten
 * ticks later. The common half of NTM-CE's {@code ButtonPush} (CCP-CONTROLS, D-3064): size, variables, events and the default
 * graphs. The model, texture and {@code render} are client (batch 5, {@code core.client}).
 */
public class ButtonPush extends Control {

	public ButtonPush(String name, String registryName, ControlPanel panel) {
		super(name, registryName, panel);
		vars.put("isPushed", new DataValueFloat(0));
		vars.put("isLit", new DataValueFloat(0));
		vars.put("color", new DataValueEnum<>(DyeColor.RED));
	}

	@Override
	public ControlType getControlType() {
		return ControlType.BUTTON;
	}

	@Override
	public float[] getSize() {
		return new float[]{1, 1, .4F};
	}

	@Override
	public List<String> getOutEvents() {
		return Collections.singletonList("ctrl_press");
	}

	@Override
	public void populateDefaultNodes(List<ControlEvent> receiveEvents) {
		NodeSystem ctrl_press = new NodeSystem(this);
		{
			NodeGetVar node0 = new NodeGetVar(170, 100, this).setData("isPushed", false);
			ctrl_press.addNode(node0);
			NodeBoolean node1 = new NodeBoolean(230, 120).setData(NodeBoolean.BoolOperation.NOT);
			node1.inputs.get(0).setData(node0, 0, true);
			ctrl_press.addNode(node1);
			NodeSetVar node2 = new NodeSetVar(290, 140, this).setData("isLit", false);
			node2.inputs.get(0).setData(node1, 0, true);
			ctrl_press.addNode(node2);
			NodeSetVar node3 = new NodeSetVar(290, 90, this).setData("isPushed", false);
			node3.inputs.get(0).setData(node1, 0, true);
			ctrl_press.addNode(node3);
		}
		receiveNodeMap.put("ctrl_press", ctrl_press);
		NodeSystem tick = new NodeSystem(this);
		{
			NodeGetVar node0 = new NodeGetVar(170, 100, this).setData("isPushed", false);
			tick.addNode(node0);
			NodeBuffer node1 = new NodeBuffer(230, 120);
			node1.inputs.get(0).setData(node0, 0, true);
			node1.inputs.get(1).setDefault(new DataValueFloat(10));
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

	@Override
	public Control newControl(ControlPanel panel) {
		return new ButtonPush(name, registryName, panel);
	}
}
