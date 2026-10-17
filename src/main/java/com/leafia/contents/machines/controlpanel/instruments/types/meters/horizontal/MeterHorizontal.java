package com.leafia.contents.machines.controlpanel.instruments.types.meters.horizontal;

import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;

import com.leafia.contents.machines.controlpanel.core.Control;
import com.leafia.contents.machines.controlpanel.core.ControlEvent;
import com.leafia.contents.machines.controlpanel.core.ControlPanel;
import com.leafia.contents.machines.controlpanel.core.controls.ControlType;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;

import net.minecraft.world.phys.AABB;

/**
 * The horizontal meter: a flat gauge with a scale along its length and a needle that follows {@code value} (CCP-INSTRUMENTS-2, D-3072).
 * The common half of Leafia's {@code MeterHorizontal}; the drawing is {@code instruments.client.MeterDraw}, the pane
 * {@code MeterHorizontalCSE}. It listens for nothing and sends nothing, has no collision box and sits in the stock Meter family.
 *
 * <p>The variable {@code value} is read as it is: the needle sits at {@link #fraction} of the scale. Unlike the vertical meter there is no
 * smoothing and no packet, so the picture is whatever the panel's own variable deltas carry.
 *
 * <p>Configuration (editor ranges in brackets): {@code length} (3 to 10 units, the control's width; its box follows it),
 * {@code r}, {@code g}, {@code b} (0 to 255, the needle's glow), {@code minValue} and {@code maxValue} (the scale's ends, typed),
 * {@code divisions} (2 to 20 labelled marks), {@code subdivisions} (0 to 10 small marks between two labelled ones) and {@code decimals}
 * (0 to 3 places on a label).
 *
 * <p><b>Hardening (D-1093 #4):</b> every derived number is clamped to its slider when read, as the graph's are (D-3071); the configuration
 * map keeps the raw value so a panel round-trips byte for byte. {@code divisions} below 2 divided the scale by zero in the drawing, and
 * {@code decimals} reaches a {@code String.format} pattern, where a large one threw on every frame of every client in range. A bound that is
 * not a finite number leaves the previous bound.
 *
 * <p><b>Kept as Leafia has it:</b> {@link #ratio} of an empty range ({@code minValue == maxValue}) is the bound itself, not a ratio, so
 * the needle then sits where the clamped bound puts it; and the starting fields ({@code minValue} 0, {@code maxValue} 5) are not the
 * pane's own defaults ({@code maxValue} 1, {@code divisions} 5).
 */
public class MeterHorizontal extends Control {

	public static final int LENGTH_MIN = 3, LENGTH_MAX = 10;
	public static final int DIVISIONS_MIN = 2, DIVISIONS_MAX = 20;
	public static final int SUBDIVISIONS_MIN = 0, SUBDIVISIONS_MAX = 10;
	public static final int DECIMALS_MIN = 0, DECIMALS_MAX = 3;

	/** Where the scale starts and how far it stops short of the far end, in units: Leafia's {@code padding}. */
	public static final float PADDING = 0.4f;

	private int length = 5;
	private int r = 255;
	private int g = 153;
	private int b = 0;
	private float min = 0;
	private float max = 5;
	private int divisions = 6;
	private int subdivisions = 5;
	private int decimals = 0;

	public MeterHorizontal(String name, String registryName, ControlPanel panel) {
		super(name, registryName, panel);
		vars.put("value", new DataValueFloat(0));
		configMap.put("length", new DataValueFloat(length));
		configMap.put("r", new DataValueFloat(r));
		configMap.put("g", new DataValueFloat(g));
		configMap.put("b", new DataValueFloat(b));
		configMap.put("minValue", new DataValueFloat(min));
		configMap.put("maxValue", new DataValueFloat(max));
		configMap.put("divisions", new DataValueFloat(divisions));
		configMap.put("subdivisions", new DataValueFloat(subdivisions));
		configMap.put("decimals", new DataValueFloat(decimals));
	}

	/** {@code v} limited to {@code lo..hi}; a NaN is {@code fallback}. */
	static int sane(float v, int lo, int hi, int fallback) {
		if(Float.isNaN(v))
			return fallback;
		return (int) Math.max(lo, Math.min(hi, v));
	}

	@Override
	protected void onConfigMapChanged() {
		for(Entry<String, DataValue> entry : configMap.entrySet()) {
			float v = entry.getValue().getNumber();
			switch(entry.getKey()) {
				case "length" -> length = sane(v, LENGTH_MIN, LENGTH_MAX, length);
				case "r" -> r = sane(v, 0, 255, r);
				case "g" -> g = sane(v, 0, 255, g);
				case "b" -> b = sane(v, 0, 255, b);
				case "minValue" -> min = Float.isFinite(v) ? v : min;
				case "maxValue" -> max = Float.isFinite(v) ? v : max;
				case "decimals" -> decimals = sane(v, DECIMALS_MIN, DECIMALS_MAX, decimals);
				case "divisions" -> divisions = sane(v, DIVISIONS_MIN, DIVISIONS_MAX, divisions);
				case "subdivisions" -> subdivisions = sane(v, SUBDIVISIONS_MIN, SUBDIVISIONS_MAX, subdivisions);
				default -> {}
			}
		}
	}

	public int getLength() { return length; }
	public int getRed() { return r; }
	public int getGreen() { return g; }
	public int getBlue() { return b; }
	public float getMin() { return min; }
	public float getMax() { return max; }
	public int getDivisions() { return divisions; }
	public int getSubdivisions() { return subdivisions; }
	public int getDecimals() { return decimals; }

	/** Leafia's {@code ratio(a, b, t)}: where {@code t} sits between {@code a} and {@code b}; {@code a} itself for an empty range. */
	public static float ratio(float a, float b, float t) {
		float delta = b - a;
		if(delta == 0)
			return a;
		return (t - a) / delta;
	}

	public static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}

	/** The needle's place on the scale, 0 to 1: {@code clamp(ratio(min, max, value), 0, 1)}, and 0 for a value that is not a number. */
	public float fraction() {
		return fraction(getVar("value").getNumber());
	}

	public float fraction(float value) {
		float f = ratio(min, max, value);
		if(Float.isNaN(f))
			return 0;
		return Math.max(0, Math.min(1, f));
	}

	/** The needle's x in the control's own frame (0 at the left end, {@code length} units wide) for a fraction of the scale. */
	public float needleX(float fraction) {
		return lerp(PADDING, length - PADDING, fraction);
	}

	/** The value written under the {@code i}-th labelled mark (0 to {@code divisions - 1}), as Leafia formatted it. */
	public String label(int i) {
		return String.format(Locale.ROOT, "%01." + decimals + "f", lerp(min, max, i / (float) (divisions - 1)));
	}

	@Override
	public ControlType getControlType() {
		return ControlType.METER;
	}

	@Override
	public float[] getSize() {
		return new float[]{5, 1, 0.01f};
	}

	/** The box the editor selects it by: as wide as its length. */
	@Override
	public void fillBox(float[] box) {
		float height = getSize()[1];
		box[0] = posX;
		box[1] = posY;
		box[2] = posX + length;
		box[3] = posY + height;
	}

	@Override
	public AABB getBoundingBox() {
		return null;
	}

	@Override
	public Control newControl(ControlPanel controlPanel) {
		return new MeterHorizontal(name, registryName, controlPanel);
	}

	@Override
	public void populateDefaultNodes(List<ControlEvent> list) {}
}
