package com.leafia.contents.machines.controlpanel.instruments.types.starbound;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import com.leafia.contents.machines.controlpanel.CCPSyncPacketBase;
import com.leafia.contents.machines.controlpanel.core.Control;
import com.leafia.contents.machines.controlpanel.core.ControlEvent;
import com.leafia.contents.machines.controlpanel.core.ControlPanel;
import com.leafia.contents.machines.controlpanel.core.NodeSystem;
import com.leafia.contents.machines.controlpanel.core.controls.ControlType;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeGetVar;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeMath;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeMath.Operation;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeSetVar;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;
import com.leafia.dev.optimization.bitbyte.LeafiaBuf;
import com.leafia.init.LeafiaSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The large wall switch: six units square, a handle that swings through 65 degrees between its two stops
 * (CCP-INSTRUMENTS-1, D-3071). The common half of Leafia's {@code LargeSwitch}. A press ({@code ctrl_press}) plays the wall
 * switch's sound and flips {@code position} between 0 and 100 (the default graph: {@code 100 - position}); every tick the position is
 * clamped to 0..100, the drawn angle {@code curLevel} moves toward it by at most {@link #INCREMENT}, and the angle is sent to the
 * players near the panel as a {@link CCPLargeSwitchSyncPacket}.
 *
 * <p><b>Kept as upstream has it:</b> {@code curLevel} starts at 50 (the middle) where {@code position} starts at 0, so a fresh
 * switch swings from the middle to its low stop in its first two ticks. The angle drawn is {@code -65 * (1 - curLevel / 50)} degrees.
 *
 * <p><b>Hardening (D-1093 #4):</b> as for the lever, a NaN {@code position} reads as 0, and the sync packet's level is clamped to
 * 0..100 on read.
 */
public class LargeSwitch extends Control {

	/** How far the handle moves toward its target each tick: 200 / 7. */
	public static final float INCREMENT = 200 / 7f;

	private float curLevel = 50;

	public LargeSwitch(String name, String registryName, ControlPanel panel) {
		super(name, registryName, panel);
		vars.put("position", new DataValueFloat(0));
	}

	@Override
	public ControlType getControlType() {
		return ControlType.SWITCH;
	}

	@Override
	public float[] getSize() {
		return new float[]{6, 6, 0.5f};
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
		if(evt.name.equals("ctrl_press")) {
			Level world = panel == null || panel.parent == null ? null : panel.parent.getControlWorld();
			if(world != null && !world.isClientSide())
				world.playSound(null, panel.parent.getControlPos(), LeafiaSounds.sbWallSwitch.get(), SoundSource.BLOCKS, 0.5f, 1);
		} else if(evt.name.equals("tick")) {
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

	public CCPLargeSwitchSyncPacket syncPacket() {
		BlockPos pos = panel == null || panel.parent == null ? null : panel.parent.getControlPos();
		return new CCPLargeSwitchSyncPacket(pos, panel == null ? -1 : panel.controls.indexOf(this), curLevel);
	}

	/** The switch's drawn angle, server to client. */
	public static class CCPLargeSwitchSyncPacket extends CCPSyncPacketBase {

		private float level = 0;

		public CCPLargeSwitchSyncPacket() {}

		public CCPLargeSwitchSyncPacket(BlockPos pos, int idx, float level) {
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
			return apply(target, LargeSwitch.class, sw -> sw.setCurLevel(read));
		}
	}

	@Override
	public Control newControl(ControlPanel controlPanel) {
		return new LargeSwitch(name, registryName, controlPanel);
	}

	@Override
	public void populateDefaultNodes(List<ControlEvent> list) {
		NodeSystem ctrl_press = new NodeSystem(this);
		{
			NodeGetVar node0 = new NodeGetVar(170, 100, this).setData("position", false);
			ctrl_press.addNode(node0);

			NodeMath node1 = new NodeMath(230, 120).setData(Operation.SUB);
			node1.inputs.get(0).setDefault(new DataValueFloat(100));
			node1.inputs.get(1).setData(node0, 0, true);
			ctrl_press.addNode(node1);

			NodeSetVar node2 = new NodeSetVar(290, 130, this).setData("position", false);
			node2.inputs.get(0).setData(node1, 0, true);
			ctrl_press.addNode(node2);
		}
		receiveNodeMap.put("ctrl_press", ctrl_press);
	}
}
