package com.leafia.contents.machines.controlpanel.instruments.types.nixie;

import java.util.List;
import java.util.Map;

import com.leafia.contents.machines.controlpanel.core.Control;
import com.leafia.contents.machines.controlpanel.core.ControlEvent;
import com.leafia.contents.machines.controlpanel.core.ControlPanel;
import com.leafia.contents.machines.controlpanel.core.controls.ControlType;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;
import com.leafia.contents.machines.controlpanel.core.types.DataValueString;

import net.minecraft.world.phys.AABB;

/**
 * The nixie tube display: a row of glass tubes set into the panel, each glowing one character of {@code value} (CCP-INSTRUMENTS-2,
 * D-3072). The common half of Leafia's {@code NixieDisplay}; the drawing is {@code instruments.client.NixieDraw} (which marks the class
 * as an inset, drawn before the panel's slab, so the tubes show through a hole in it) and the pane is {@code NixieCSE}.
 *
 * <p>The one variable is {@code value}, a string: tube {@code i} shows the character at index {@code i} of its text, and a tube past the
 * end of the text is dark. A number written to it reads as its own text. The one configuration is {@code length}, how many tubes (0 to 8
 * in the editor; Leafia's constructor default is 1); the control's box is 1.75 wide for the first and a unit more for each further tube.
 * Display family, no collision box, no events of its own.
 *
 * <p><b>Hardening (D-1093 #4):</b> {@code length} is limited to the slider's 0 to 8 when read (a crafted panel asked for a tube per
 * integer, and the renderer loops over them each frame); the configuration map keeps the raw value.
 *
 * <p><b>Kept as Leafia has it:</b> a character is a UTF-16 unit, so a character outside the basic plane is split over two tubes.
 */
public class NixieDisplay extends Control {

	public static final int LENGTH_MIN = 0, LENGTH_MAX = 8;

	private int length = 1;

	public NixieDisplay(String name, String registryName, ControlPanel panel) {
		super(name, registryName, panel);
		vars.put("value", new DataValueString(""));
		configMap.put("length", new DataValueFloat(length));
	}

	@Override
	protected void onConfigMapChanged() {
		for(Map.Entry<String, DataValue> e : configMap.entrySet()) {
			if(e.getKey().equals("length")) {
				float v = e.getValue().getNumber();
				if(!Float.isNaN(v))
					length = (int) Math.max(LENGTH_MIN, Math.min(LENGTH_MAX, v));
			}
		}
	}

	public int getLength() {
		return length;
	}

	/** What each tube shows, left to right: the character of {@code value} at its index, or {@code ""} for a tube past the end. */
	public String[] getTubeTexts() {
		return tubeTexts(getVar("value").toString(), length);
	}

	/** {@link #getTubeTexts} for a given text and tube count. */
	public static String[] tubeTexts(String text, int tubes) {
		String[] out = new String[Math.max(0, tubes)];
		for(int i = 0; i < out.length; i++)
			out[i] = i < text.length() ? text.substring(i, i + 1) : "";
		return out;
	}

	@Override
	public ControlType getControlType() {
		return ControlType.DISPLAY;
	}

	/** Leafia's: "GUI resolution x10". */
	@Override
	public float[] getSize() {
		return new float[]{1.75F, 2.5F, 0.0625F};
	}

	@Override
	public void fillBox(float[] box) {
		float width = getSize()[0];
		float height = getSize()[1];
		box[0] = posX;
		box[1] = posY;
		box[2] = posX + width + length - 1;
		box[3] = posY + height;
	}

	@Override
	public AABB getBoundingBox() {
		return null;
	}

	@Override
	public void populateDefaultNodes(List<ControlEvent> receiveEvents) {}

	@Override
	public Control newControl(ControlPanel panel) {
		return new NixieDisplay(name, registryName, panel);
	}
}
