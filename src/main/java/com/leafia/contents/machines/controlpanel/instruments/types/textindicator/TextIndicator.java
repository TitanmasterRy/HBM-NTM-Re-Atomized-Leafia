package com.leafia.contents.machines.controlpanel.instruments.types.textindicator;

import java.util.List;
import java.util.Map.Entry;
import java.util.function.Consumer;

import com.leafia.contents.machines.controlpanel.CCPSyncPacketBase;
import com.leafia.contents.machines.controlpanel.core.Control;
import com.leafia.contents.machines.controlpanel.core.ControlEvent;
import com.leafia.contents.machines.controlpanel.core.ControlPanel;
import com.leafia.contents.machines.controlpanel.core.controls.ControlType;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;
import com.leafia.contents.machines.controlpanel.core.types.DataValueString;
import com.leafia.dev.optimization.bitbyte.LeafiaBuf;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The text indicator: an annunciator window with a line of text, whose lens glows in a colour while {@code isLit} is set
 * (CCP-INSTRUMENTS-2, D-3072). The common half of Leafia's {@code TextIndicator}; the drawing is {@code instruments.client.InstrumentRenderers}
 * and the pane {@code TextIndicatorCSE}.
 *
 * <p>The glow does not snap: {@link #getCurrent} runs from 0 to {@link #DURATION} one step a server tick toward whichever end
 * {@code isLit} asks for, and is sent to the players near the panel as a {@link CCPTextIndicatorSyncPacket} every tick, as Leafia did; the
 * glow's strength is {@link #glow}, the square root of that fraction, so the lens swells quickly and settles.
 *
 * <p>Configuration: {@code red}, {@code green}, {@code blue} (0 to 255, the lens's colour; default red) and {@code text} (default
 * {@code A3-5}). Size 4.5 x 1.8 x 0.25, no collision box, Indicator family, no events of its own.
 *
 * <p><b>Hardening (D-1093 #4):</b> the colour is clamped to 0..255 when read (Leafia's own, kept); the step a packet carries is clamped to
 * 0..{@link #DURATION}. Leafia wrote the step as a signed byte and applied it unchecked.
 */
public class TextIndicator extends Control {

	/** How many ticks the glow takes to swell: Leafia's {@code duration}. */
	public static final int DURATION = 3;

	private float red = 255;
	private float green = 0;
	private float blue = 0;
	private String text = "A3-5";

	/** Where the glow is, 0 to {@link #DURATION}. */
	private int current = 0;

	public TextIndicator(String name, String registryName, ControlPanel panel) {
		super(name, registryName, panel);
		vars.put("isLit", new DataValueFloat(0));
		configMap.put("red", new DataValueFloat(red));
		configMap.put("green", new DataValueFloat(green));
		configMap.put("blue", new DataValueFloat(blue));
		configMap.put("text", new DataValueString(text));
	}

	private static float colour(float v, float fallback) {
		return Float.isNaN(v) ? fallback : Math.max(0, Math.min(255, v));
	}

	@Override
	protected void onConfigMapChanged() {
		for(Entry<String, DataValue> entry : configMap.entrySet()) {
			DataValue value = entry.getValue();
			switch(entry.getKey()) {
				case "red" -> red = colour(value.getNumber(), red);
				case "green" -> green = colour(value.getNumber(), green);
				case "blue" -> blue = colour(value.getNumber(), blue);
				case "text" -> text = value.toString();
				default -> {}
			}
		}
	}

	public float getRed() { return red; }
	public float getGreen() { return green; }
	public float getBlue() { return blue; }
	public String getText() { return text; }

	public int getCurrent() {
		return current;
	}

	/** Sets the glow step, limited to 0..{@link #DURATION}. For the sync packet and the tests. */
	public void setCurrent(int step) {
		current = Math.max(0, Math.min(DURATION, step));
	}

	/** The lens's glow alpha: {@code sqrt(current / DURATION)}. */
	public float glow() {
		return (float) Math.pow((float) current / DURATION, 0.5);
	}

	@Override
	public ControlType getControlType() {
		return ControlType.INDICATOR;
	}

	@Override
	public float[] getSize() {
		return new float[]{6.75F / 1.5f, 1.8F, 0.375F / 1.5f};
	}

	@Override
	public AABB getBoundingBox() {
		return null;
	}

	/** One tick of the glow: a step toward lit or unlit. */
	public void stepGlow() {
		int target = getVar("isLit").getBoolean() ? DURATION : 0;
		if(current != target) {
			if(current < target)
				current++;
			else
				current--;
		}
	}

	@Override
	public void receiveEvent(ControlEvent evt) {
		if(evt.name.equals("tick")) {
			stepGlow();
			CCPSyncPacketBase.sendFor(this, syncPacket());
		}
		super.receiveEvent(evt);
	}

	public CCPTextIndicatorSyncPacket syncPacket() {
		BlockPos pos = panel == null || panel.parent == null ? null : panel.parent.getControlPos();
		return new CCPTextIndicatorSyncPacket(pos, panel == null ? -1 : panel.controls.indexOf(this), current);
	}

	/** A text indicator's glow step, server to client. */
	public static class CCPTextIndicatorSyncPacket extends CCPSyncPacketBase {

		private int value;

		public CCPTextIndicatorSyncPacket() {}

		public CCPTextIndicatorSyncPacket(BlockPos pos, int idx, int value) {
			super(pos, idx);
			this.value = value;
		}

		@Override
		public Object signature() {
			return java.util.Arrays.asList(pos, idx, value);
		}

		@Override
		public void encode(LeafiaBuf buf) {
			super.encode(buf);
			buf.writeByte(value);
		}

		@Override
		public Consumer<IPayloadContext> decode(LeafiaBuf buf) {
			Target target = readTarget(buf);
			int read = buf.readByte();
			return apply(target, TextIndicator.class, indicator -> indicator.setCurrent(read));
		}
	}

	@Override
	public Control newControl(ControlPanel controlPanel) {
		return new TextIndicator(name, registryName, controlPanel);
	}

	@Override
	public void populateDefaultNodes(List<ControlEvent> list) {}
}
