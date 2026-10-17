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
 * The control knob, 2 by 2: a plain press turns it up one position, a sneaking press down, clamped to 0..10. The common half of
 * NTM-CE's {@code KnobControl} (CCP-CONTROLS, D-3064).
 *
 * <p>Its one config, {@code positions}, is read into a field and <b>nothing reads the field</b>: CE's renderer draws eleven
 * positions with a local constant, and the default graph clamps to 10, so the setting is inert upstream. Kept as it is (rule 4);
 * {@link #getPositions} exposes it for the editor's slider (1..11). A stored value outside that range is clamped when read into
 * the field and left as it is in the config map, so a panel round-trips unchanged.
 */
public class KnobControl extends Control {

	/** The editor slider's range for {@code positions}. */
	public static final int MIN_POSITIONS = 1, MAX_POSITIONS = 11;

	private int positions = 2;

	public KnobControl(String name, String registryName, ControlPanel panel) {
		super(name, registryName, panel);
		vars.put("value", new DataValueFloat(0));
		configMap.put("positions", new DataValueFloat(positions));
	}

	@Override
	public ControlType getControlType() {
		return ControlType.KNOB;
	}

	@Override
	public float[] getSize() {
		return new float[]{2, 2, .4F};
	}

	public int getPositions() {
		return positions;
	}

	@Override
	protected void onConfigMapChanged() {
		for(Map.Entry<String, DataValue> e : configMap.entrySet()) {
			switch(e.getKey()) {
			case "positions": {
				positions = Math.max(MIN_POSITIONS, Math.min(MAX_POSITIONS, (int) e.getValue().getNumber()));
				break;
			}
			}
		}
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
			NodeConditional node1 = new NodeConditional(230, 120);
			node1.inputs.get(0).setData(node0, 1, true);
			node1.inputs.get(1).setDefault(new DataValueFloat(-1));
			node1.inputs.get(2).setDefault(new DataValueFloat(1));
			ctrl_press.addNode(node1);
			NodeGetVar node2 = new NodeGetVar(170, 160, this).setData("value", false);
			ctrl_press.addNode(node2);
			NodeMath node3 = new NodeMath(290, 140).setData(NodeMath.Operation.ADD);
			node3.inputs.get(0).setData(node1, 0, true);
			node3.inputs.get(1).setData(node2, 0, true);
			ctrl_press.addNode(node3);
			NodeMath node4 = new NodeMath(350, 140).setData(NodeMath.Operation.CLAMP);
			node4.inputs.get(0).setData(node3, 0, true);
			node4.inputs.get(1).setDefault(new DataValueFloat(0));
			node4.inputs.get(2).setDefault(new DataValueFloat(10));
			ctrl_press.addNode(node4);
			NodeSetVar node5 = new NodeSetVar(410, 145, this).setData("value", false);
			node5.inputs.get(0).setData(node4, 0, true);
			ctrl_press.addNode(node5);
		}
		receiveNodeMap.put("ctrl_press", ctrl_press);
	}

	@Override
	public Control newControl(ControlPanel panel) {
		return new KnobControl(name, registryName, panel);
	}
}
