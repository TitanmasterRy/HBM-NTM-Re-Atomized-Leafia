package com.leafia.contents.machines.controlpanel.core.controls;

import java.util.List;
import java.util.Map;

import com.leafia.contents.machines.controlpanel.core.Control;
import com.leafia.contents.machines.controlpanel.core.ControlEvent;
import com.leafia.contents.machines.controlpanel.core.ControlPanel;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValueEnum;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;
import com.leafia.contents.machines.controlpanel.core.types.DataValueString;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.AABB;

/**
 * The text display: the string in the {@code text} variable, in the dye colour in {@code color}, optionally lit. The common half
 * of NTM-CE's {@code DisplayText} (CCP-CONTROLS, D-3064). No default graphs, no collision box.
 *
 * <p><b>Its selection box depends on the font</b>: CE's {@code fillBox} uses {@code height}, which {@code render} sets from the
 * font renderer, and {@code textWidth}, which it measures. Neither can be known on a server, so both stay 0 until the client
 * draws it and calls {@link #setMeasured}; before that the box is the padded point the control stands on. The configured
 * {@code width} feeds the box width, the measured text width does not (CE computes it and uses it nowhere).
 */
public class DisplayText extends Control {

	/** The editor slider ranges for {@code scale} and {@code width}. */
	public static final int MIN_SCALE = 10, MAX_SCALE = 100, MIN_WIDTH = 10, MAX_WIDTH = 100;

	private int scale = 25;
	private float width = 20;
	private float textWidth = 0;
	private float height = 0;

	public DisplayText(String name, String registryName, ControlPanel panel) {
		super(name, registryName, panel);
		vars.put("isLit", new DataValueFloat(0));
		vars.put("text", new DataValueString("text"));
		vars.put("color", new DataValueEnum<>(DyeColor.WHITE));
		configMap.put("scale", new DataValueFloat(scale));
		configMap.put("width", new DataValueFloat(width));
	}

	@Override
	public ControlType getControlType() {
		return ControlType.DISPLAY;
	}

	@Override
	public float[] getSize() {
		return new float[]{0, 0, 0};
	}

	public int getScale() {
		return scale;
	}

	public float getConfiguredWidth() {
		return width;
	}

	public float getTextWidth() {
		return textWidth;
	}

	public float getTextHeight() {
		return height;
	}

	/** The client reports the text's pixel width and the font height, as CE's {@code render} stored them. */
	public void setMeasured(float textWidth, float height) {
		this.textWidth = textWidth;
		this.height = height;
	}

	@Override
	public void fillBox(float[] box) {
		float d = .1F;
		box[0] = posX - d;
		box[1] = posY - d;
		box[2] = posX + (width * 1.5F * scale / 500F) + d;
		box[3] = posY + (height * scale / 500F) + d;
	}

	@Override
	protected void onConfigMapChanged() {
		for(Map.Entry<String, DataValue> e : configMap.entrySet()) {
			switch(e.getKey()) {
			case "scale": {
				scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, (int) e.getValue().getNumber()));
				break;
			}
			case "width": {
				width = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, (int) e.getValue().getNumber()));
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
		return new DisplayText(name, registryName, panel);
	}

	@Override
	public void populateDefaultNodes(List<ControlEvent> receiveEvents) {}
}
