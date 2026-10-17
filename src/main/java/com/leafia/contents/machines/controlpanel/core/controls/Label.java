package com.leafia.contents.machines.controlpanel.core.controls;

import java.util.List;
import java.util.Map;

import com.leafia.contents.machines.controlpanel.core.Control;
import com.leafia.contents.machines.controlpanel.core.ControlEvent;
import com.leafia.contents.machines.controlpanel.core.ControlPanel;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;
import com.leafia.contents.machines.controlpanel.core.types.DataValueString;

import net.minecraft.world.phys.AABB;

/**
 * A fixed text label, in an RGB colour and a scale, optionally lit ({@code isLit}). The common half of NTM-CE's {@code Label}
 * (CCP-CONTROLS, D-3064). No default graphs, no collision box.
 *
 * <p>Like {@link DisplayText}, its selection box depends on the font: {@code width} and {@code height} are the string's pixel
 * width and the font height, stored by CE's {@code render}. They stay 0 on a server until the client reports them with
 * {@link #setMeasured}.
 */
public class Label extends Control {

	/** The editor slider's range for {@code scale}. */
	public static final int MIN_SCALE = 10, MAX_SCALE = 100;

	private float[] color = new float[]{1, 1, 1};
	private String text = "label";
	private int scale = 25;

	float width = 0;
	float height = 0;

	public Label(String name, String registryName, ControlPanel panel) {
		super(name, registryName, panel);
		vars.put("isLit", new DataValueFloat(0));
		configMap.put("colorR", new DataValueFloat(color[0]));
		configMap.put("colorG", new DataValueFloat(color[1]));
		configMap.put("colorB", new DataValueFloat(color[2]));
		configMap.put("text", new DataValueString(text));
		configMap.put("scale", new DataValueFloat(scale));
	}

	@Override
	public ControlType getControlType() {
		return ControlType.LABEL;
	}

	@Override
	public float[] getSize() {
		return new float[]{0, 0, 0};
	}

	/** Red, green and blue in 0..1 (a copy). */
	public float[] getColor() {
		return color.clone();
	}

	public String getText() {
		return text;
	}

	public int getScale() {
		return scale;
	}

	/** The client reports the text's pixel width and the font height, as CE's {@code render} stored them. */
	public void setMeasured(float width, float height) {
		this.width = width;
		this.height = height;
	}

	@Override
	public void fillBox(float[] box) {
		box[0] = posX;
		box[1] = posY;
		box[2] = posX + (width * scale / 500F);
		box[3] = posY + (height * scale / 500F);
	}

	@Override
	protected void onConfigMapChanged() {
		for(Map.Entry<String, DataValue> e : configMap.entrySet()) {
			switch(e.getKey()) {
			case "colorR": {
				color[0] = e.getValue().getNumber();
				break;
			}
			case "colorG": {
				color[1] = e.getValue().getNumber();
				break;
			}
			case "colorB": {
				color[2] = e.getValue().getNumber();
				break;
			}
			case "text": {
				text = e.getValue().toString();
				break;
			}
			case "scale": {
				scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, (int) e.getValue().getNumber()));
				break;
			}
			}
		}
	}

	@Override
	public AABB getBoundingBox() {
		return null;
	}

	@Override
	public Control newControl(ControlPanel panel) {
		return new Label(name, registryName, panel);
	}

	@Override
	public void populateDefaultNodes(List<ControlEvent> receiveEvents) {}
}
