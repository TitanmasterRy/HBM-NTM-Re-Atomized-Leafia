package com.leafia.contents.machines.controlpanel.instruments.types.drawing;

import java.util.List;
import java.util.Map.Entry;

import com.leafia.contents.machines.controlpanel.core.Control;
import com.leafia.contents.machines.controlpanel.core.ControlEvent;
import com.leafia.contents.machines.controlpanel.core.ControlPanel;
import com.leafia.contents.machines.controlpanel.core.controls.ControlType;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;
import com.leafia.contents.machines.controlpanel.core.types.DataValueString;

import net.minecraft.world.phys.AABB;

/**
 * The boxed label: a flat coloured rectangle with a line of text on it, for dressing a panel (CCP-INSTRUMENTS-1, D-3071). The common
 * half of Leafia's {@code Drawing}; the drawing is {@code instruments.client.InstrumentRenderers}, and the pane is {@code DrawingCSE}.
 * It listens for nothing and sends nothing, has no collision box (a click passes through it) and sits in the stock Label family.
 *
 * <p>Configuration (editor ranges in brackets): {@code red}, {@code green}, {@code blue} (0..100 per cent), {@code width} and
 * {@code height} (0.1..10 units, the box's size - the control's declared size is a unit and its box follows these), {@code scale}
 * (10..100, the text size over 500), {@code text} and {@code white} (white text rather than black).
 *
 * <p><b>Hardening (D-1093 #4, invisible to anything the editor can make):</b> width, height and scale are clamped to their slider
 * ranges when read, as {@code Graph}'s are, and the configuration map keeps the raw value. The fields start at the configuration's
 * defaults (0.2 units square) where Leafia's started at 0 until the first configuration was applied.
 */
public class Drawing extends Control {

	private int r = 100;
	private int g = 100;
	private int b = 100;
	private float width = 0.2f;
	private float height = 0.2f;
	private int scale = 25;
	private String text = "";
	private boolean white = false;

	public Drawing(String name, String registryName, ControlPanel panel) {
		super(name, registryName, panel);
		configMap.put("red", new DataValueFloat(r));
		configMap.put("green", new DataValueFloat(g));
		configMap.put("blue", new DataValueFloat(b));
		configMap.put("width", new DataValueFloat(0.2f));
		configMap.put("height", new DataValueFloat(0.2f));
		configMap.put("scale", new DataValueFloat(scale));
		configMap.put("text", new DataValueString(text));
		configMap.put("white", new DataValueFloat(0));
	}

	private static float sane(float v, float lo, float hi, float fallback) {
		if(Float.isNaN(v))
			return fallback;
		return Math.max(lo, Math.min(hi, v));
	}

	@Override
	protected void onConfigMapChanged() {
		for(Entry<String, DataValue> entry : configMap.entrySet()) {
			DataValue value = entry.getValue();
			switch(entry.getKey()) {
				case "red" -> r = (int) sane(value.getNumber(), 0, 100, r);
				case "green" -> g = (int) sane(value.getNumber(), 0, 100, g);
				case "blue" -> b = (int) sane(value.getNumber(), 0, 100, b);
				case "width" -> width = sane(value.getNumber(), 0.1f, 10, width);
				case "height" -> height = sane(value.getNumber(), 0.1f, 10, height);
				case "scale" -> scale = (int) sane(value.getNumber(), 10, 100, scale);
				case "text" -> text = value.toString();
				case "white" -> white = value.getNumber() >= 1;
				default -> {}
			}
		}
	}

	/** Red, green and blue as 0..1 fractions. */
	public float[] getColor() {
		return new float[]{r / 100f, g / 100f, b / 100f};
	}

	public float getWidth() { return width; }
	public float getHeight() { return height; }
	public int getScale() { return scale; }
	public String getText() { return text; }
	public boolean isWhiteText() { return white; }

	@Override
	public ControlType getControlType() {
		return ControlType.LABEL;
	}

	@Override
	public float[] getSize() {
		return new float[]{1, 1, 0.01f};
	}

	/** The box the editor selects it by: its own width and height from its position. */
	@Override
	public void fillBox(float[] box) {
		box[0] = posX;
		box[1] = posY;
		box[2] = posX + width;
		box[3] = posY + height;
	}

	@Override
	public AABB getBoundingBox() {
		return null;
	}

	@Override
	public Control newControl(ControlPanel controlPanel) {
		return new Drawing(name, registryName, controlPanel);
	}

	@Override
	public void populateDefaultNodes(List<ControlEvent> list) {}
}
