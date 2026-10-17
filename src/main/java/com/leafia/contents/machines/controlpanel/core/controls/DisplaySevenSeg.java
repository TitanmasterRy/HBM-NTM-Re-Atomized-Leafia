package com.leafia.contents.machines.controlpanel.core.controls;

import java.util.List;
import java.util.Map;

import com.leafia.contents.machines.controlpanel.core.Control;
import com.leafia.contents.machines.controlpanel.core.ControlEvent;
import com.leafia.contents.machines.controlpanel.core.ControlPanel;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;

import net.minecraft.world.phys.AABB;

/**
 * The seven-segment display: {@code digitCount} digits (0..8) of {@code value}, in hexadecimal or, with {@code isDecimal}, in
 * decimal, in one colour. The common half of NTM-CE's {@code DisplaySevenSeg} (CCP-CONTROLS, D-3064). No default graphs: it only
 * shows what a graph or a linked machine writes into {@code value}. It has no collision box, so a click never lands on it.
 *
 * <p>The box the editor selects it by is wider than the control's size: it grows leftwards by one digit minus an eighth for each
 * extra digit, as CE's {@code fillBox}. {@link #SEGMENTS} is CE's {@code chars} table, one {@code abcdefg} byte per hex digit,
 * for the batch-5 renderer.
 */
public class DisplaySevenSeg extends Control {

	/** The editor slider's range for {@code digitCount}. */
	public static final int MIN_DIGITS = 0, MAX_DIGITS = 8;

	/** {@code abcdefg} encoding of 0 to F: bit {@code j} is segment {@code seg_(6-j)} of the model. */
	public static final byte[] SEGMENTS = {
			0x7E, 0x30, 0x6D, 0x79, 0x33, 0x5B, 0x5F, 0x70, 0x7F, 0x7B, 0x77, 0x1F, 0x4E, 0x3D, 0x4F, 0x47
	};

	private float[] color = new float[]{1, 1, 1};
	private int digitCount = 1;
	private boolean isDecimal = false;

	public DisplaySevenSeg(String name, String registryName, ControlPanel panel) {
		super(name, registryName, panel);
		vars.put("value", new DataValueFloat(0));
		configMap.put("colorR", new DataValueFloat(color[0]));
		configMap.put("colorG", new DataValueFloat(color[1]));
		configMap.put("colorB", new DataValueFloat(color[2]));
		configMap.put("digitCount", new DataValueFloat(digitCount));
		configMap.put("isDecimal", new DataValueFloat(0));
	}

	@Override
	public ControlType getControlType() {
		return ControlType.DISPLAY;
	}

	@Override
	public float[] getSize() {
		return new float[]{.75F, 1.125F, .06F};
	}

	/** Red, green and blue in 0..1 (a copy). */
	public float[] getColor() {
		return color.clone();
	}

	public int getDigitCount() {
		return digitCount;
	}

	public boolean isDecimal() {
		return isDecimal;
	}

	@Override
	public void fillBox(float[] box) {
		float width = getSize()[0];
		float length = getSize()[1];
		box[0] = posX - (width * digitCount - ((digitCount - 1) * .125F)) + width;
		box[1] = posY;
		box[2] = posX + width;
		box[3] = posY + length;
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
			case "digitCount": {
				// The editor offers 0..8; a client-authored tree could name 2 billion digits for the renderer to loop over.
				digitCount = Math.max(MIN_DIGITS, Math.min(MAX_DIGITS, (int) e.getValue().getNumber()));
				break;
			}
			case "isDecimal": {
				isDecimal = e.getValue().getBoolean();
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
	public void populateDefaultNodes(List<ControlEvent> receiveEvents) {}

	@Override
	public Control newControl(ControlPanel panel) {
		return new DisplaySevenSeg(name, registryName, panel);
	}
}
