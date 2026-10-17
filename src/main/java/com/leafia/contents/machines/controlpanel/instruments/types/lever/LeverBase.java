package com.leafia.contents.machines.controlpanel.instruments.types.lever;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.leafia.contents.machines.controlpanel.CCPSyncPacketBase;
import com.leafia.contents.machines.controlpanel.core.Control;
import com.leafia.contents.machines.controlpanel.core.ControlEvent;
import com.leafia.contents.machines.controlpanel.core.ControlPanel;
import com.leafia.contents.machines.controlpanel.core.NodeSystem;
import com.leafia.contents.machines.controlpanel.core.controls.ControlType;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeConditional;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeGetVar;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeInput;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeMath;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeSetVar;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;
import com.leafia.contents.machines.controlpanel.instruments.AddonControlType;
import com.leafia.dev.optimization.bitbyte.LeafiaBuf;

import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The lever: a handle that is pushed along by clicks and swings toward its {@code position} (0 to 100) a step at a time
 * (CCP-INSTRUMENTS-1, D-3071). The common half of Leafia's {@code LeverBase}; {@link LeverHorizontal} and {@link LeverVertical} are
 * the two orientations and their drawing is {@code instruments.client.InstrumentRenderers}.
 *
 * <p>A click ({@code ctrl_press}) moves the position by 10 up, or by 10 down when the player was sneaking (the default graph:
 * {@code position + (isSneaking ? -10 : 10)}); the position is clamped to 0..100 every tick, and {@code curLevel}, the angle drawn,
 * is moved toward it by at most {@link #INCREMENT} a tick and sent to the players near the panel as a {@link CCPLeverSyncPacket}.
 * The variable {@code position} itself is a normal variable, so a panel's graphs can read it and write it and the panel's own
 * variable deltas carry it.
 *
 * <p><b>Kept as upstream has it:</b> {@code curLevel} starts at 0.5 where {@code position} starts at 50 (the drawn angle uses
 * {@code curLevel / 50}, so a fresh lever is drawn near its low stop for the one or two ticks it takes to swing to 50). The sync
 * packet carries the angle, not the target.
 *
 * <p><b>Hardening (D-1093 #4):</b> a NaN {@code position} reads as 0 (Leafia's {@code max(min(NaN, 100), 0)} left NaN in the
 * variable for ever, and the lever never moved again); the sync packet is bounded and its level clamped to 0..100 on read.
 */
public abstract class LeverBase extends Control {

	/** How far the handle moves toward its target each tick: 200 / 7. */
	public static final float INCREMENT = 200 / 7f;

	/** The angle being drawn, in the same 0..100 units as {@code position}. */
	private float curLevel = 0.5f;

	public LeverBase(String name, String registryName, ControlPanel panel) {
		super(name, registryName, panel);
		vars.put("position", new DataValueFloat(50f));
	}

	@Override
	public ControlType getControlType() {
		return AddonControlType.LEVER;
	}

	@Override
	public List<String> getOutEvents() {
		return Collections.singletonList("ctrl_press");
	}

	public float getCurLevel() {
		return curLevel;
	}

	/** Sets the drawn angle directly, clamped to 0..100; a NaN is ignored. For the sync packet and the tests. */
	public void setCurLevel(float level) {
		if(!Float.isNaN(level))
			curLevel = Math.max(0, Math.min(100, level));
	}

	@Override
	public void receiveEvent(ControlEvent evt) {
		if(evt.name.equals("tick")) {
			stepTowardPosition();
			CCPSyncPacketBase.sendFor(this, syncPacket());
		}
		super.receiveEvent(evt);
	}

	/** One tick of the handle's movement: the position clamped into the variable, the drawn angle moved toward it. */
	public void stepTowardPosition() {
		float p = getVar("position").getNumber();
		if(Float.isNaN(p))
			p = 0;
		float target = Math.max(Math.min(p, 100), 0);
		vars.put("position", new DataValueFloat(target));
		float delta = target - curLevel;
		float difference = Math.abs(delta);
		if(difference > 0) {
			if(difference <= INCREMENT)
				curLevel = target;
			else
				curLevel += Math.signum(delta) * INCREMENT;
		}
	}

	public CCPLeverSyncPacket syncPacket() {
		BlockPos pos = panel == null || panel.parent == null ? null : panel.parent.getControlPos();
		return new CCPLeverSyncPacket(pos, panel == null ? -1 : panel.controls.indexOf(this), curLevel);
	}

	/** A lever's drawn angle, server to client. */
	public static class CCPLeverSyncPacket extends CCPSyncPacketBase {

		private float level = 0;

		public CCPLeverSyncPacket() {}

		public CCPLeverSyncPacket(BlockPos pos, int idx, float level) {
			super(pos, idx);
			this.level = level;
		}

		@Override
		public Object signature() {
			return java.util.Arrays.asList(pos, idx, level);
		}

		@Override
		public void encode(LeafiaBuf buf) {
			super.encode(buf);
			buf.writeFloat(level);
		}

		@Override
		public Consumer<IPayloadContext> decode(LeafiaBuf buf) {
			Target target = readTarget(buf);
			float read = buf.readFloat();
			return apply(target, LeverBase.class, lever -> lever.setCurLevel(read));
		}
	}

	@Override
	public void populateDefaultNodes(List<ControlEvent> receiveEvents) {
		NodeSystem ctrl_press = new NodeSystem(this);
		{
			Map<String, DataValue> vars = new HashMap<>(receiveEvents.get(0).vars);
			vars.put("from index", new DataValueFloat(0));
			NodeInput node0 = new NodeInput(170, 100, "Event Data").setVars(vars);
			ctrl_press.addNode(node0);
			NodeGetVar node1 = new NodeGetVar(170, 150, this).setData("position", false);
			ctrl_press.addNode(node1);
			NodeConditional node2 = new NodeConditional(230, 110);
			node2.inputs.get(0).setData(node0, 1, true);
			node2.inputs.get(1).setDefault(new DataValueFloat(-10f));
			node2.inputs.get(2).setDefault(new DataValueFloat(10f));
			ctrl_press.addNode(node2);
			NodeMath node3 = new NodeMath(290, 130).setData(NodeMath.Operation.ADD);
			node3.inputs.get(0).setData(node2, 0, true);
			node3.inputs.get(1).setData(node1, 0, true);
			ctrl_press.addNode(node3);
			NodeSetVar node5 = new NodeSetVar(350, 130, this).setData("position", false);
			node5.inputs.get(0).setData(node3, 0, true);
			ctrl_press.addNode(node5);
		}
		receiveNodeMap.put("ctrl_press", ctrl_press);
	}
}
