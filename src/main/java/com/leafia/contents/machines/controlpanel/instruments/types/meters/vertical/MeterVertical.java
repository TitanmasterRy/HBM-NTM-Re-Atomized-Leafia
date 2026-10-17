package com.leafia.contents.machines.controlpanel.instruments.types.meters.vertical;

import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.function.Consumer;

import com.leafia.contents.machines.controlpanel.CCPSyncPacketBase;
import com.leafia.contents.machines.controlpanel.core.Control;
import com.leafia.contents.machines.controlpanel.core.ControlEvent;
import com.leafia.contents.machines.controlpanel.core.ControlPanel;
import com.leafia.contents.machines.controlpanel.core.controls.ControlType;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;
import com.leafia.dev.optimization.bitbyte.LeafiaBuf;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The vertical meter: a drum with a scale printed round it that turns behind a fixed needle (CCP-INSTRUMENTS-2, D-3072). The common half
 * of Leafia's {@code MeterVertical}; the drawing is {@code instruments.client.MeterDraw}, the pane {@code MeterVerticalCSE}.
 *
 * <p>The drum's angle, {@link #getCurAngle}, is eased toward the angle {@code value} asks for on the server's {@code tick} and sent to
 * the players near the panel as a {@link CCPMeterVerticalSyncPacket} every tick, as Leafia did: within {@link #THRESHOLD} degrees it
 * arrives, otherwise it moves by a 2.5th of the distance or by the threshold, whichever is more, so a long way is covered in a
 * decaying run and the last few degrees at a steady two a tick. With {@code clamp} set the value is first limited to the scale's ends
 * (either way round).
 *
 * <p>Configuration (editor ranges in brackets): {@code needleOffset} (-3 to 3 steps of 11.25 degrees, where the needle sits against the
 * drum), {@code minValue} and {@code maxValue} (typed), {@code decimals} (0 to 3), {@code divisions} (5 to 30 labelled marks),
 * {@code subdivisions} (0 to 10) and {@code clamp}. Size 1.7 x 5 x 1.25, no collision box, Meter family.
 *
 * <p><b>Hardening (D-1093 #4):</b> the derived numbers are clamped to their sliders when read (the configuration map keeps the raw value),
 * so {@code divisions} 0 no longer divides the drum by zero and {@code decimals} cannot reach a {@code String.format} pattern as a huge
 * number; a bound that is not a finite number leaves the previous bound; a value that is not a number counts as the scale's start
 * (Leafia's NaN made the drum's angle NaN for ever); the angle a packet sets is clamped to {@code +-}{@link #MAX_ANGLE} and a NaN
 * ignored.
 *
 * <p><b>Kept as Leafia has it:</b> the unclamped value may ask for any angle, and an empty range ({@code minValue == maxValue}) asks for
 * the angle of the bound itself as if it were a ratio (see {@link #ratio}).
 */
public class MeterVertical extends Control {

	public static final int NEEDLE_MIN = -3, NEEDLE_MAX = 3;
	public static final int DIVISIONS_MIN = 5, DIVISIONS_MAX = 30;
	public static final int SUBDIVISIONS_MIN = 0, SUBDIVISIONS_MAX = 10;
	public static final int DECIMALS_MIN = 0, DECIMALS_MAX = 3;

	/** Within this many degrees the drum arrives. Leafia's own number. */
	public static final float THRESHOLD = 2;

	/** The most a sync packet may turn the drum to, either way; well past anything a scale asks for. */
	public static final float MAX_ANGLE = 10_000;

	/** One step of the needle offset, in degrees: {@code 360 / 32}. */
	public static final float NEEDLE_STEP = 360 / 32f;

	private int needleOffset = 0;
	private float max = 1;
	private float min = -1;
	private int divisions = 20;
	private int subdivisions = 3;
	private int decimals = 1;
	private boolean clamp = false;

	/** The drum's angle in degrees, the thing drawn. */
	private float curAngle = 0;

	public MeterVertical(String name, String registryName, ControlPanel panel) {
		super(name, registryName, panel);
		vars.put("value", new DataValueFloat(0));
		configMap.put("needleOffset", new DataValueFloat(needleOffset));
		configMap.put("minValue", new DataValueFloat(min));
		configMap.put("maxValue", new DataValueFloat(max));
		configMap.put("decimals", new DataValueFloat(decimals));
		configMap.put("divisions", new DataValueFloat(divisions));
		configMap.put("subdivisions", new DataValueFloat(subdivisions));
		configMap.put("clamp", new DataValueFloat(0));
	}

	private static int sane(float v, int lo, int hi, int fallback) {
		if(Float.isNaN(v))
			return fallback;
		return (int) Math.max(lo, Math.min(hi, v));
	}

	@Override
	protected void onConfigMapChanged() {
		for(Entry<String, DataValue> entry : configMap.entrySet()) {
			DataValue value = entry.getValue();
			float v = value.getNumber();
			switch(entry.getKey()) {
				case "needleOffset" -> needleOffset = sane(v, NEEDLE_MIN, NEEDLE_MAX, needleOffset);
				case "minValue" -> min = Float.isFinite(v) ? v : min;
				case "maxValue" -> max = Float.isFinite(v) ? v : max;
				case "decimals" -> decimals = sane(v, DECIMALS_MIN, DECIMALS_MAX, decimals);
				case "divisions" -> divisions = sane(v, DIVISIONS_MIN, DIVISIONS_MAX, divisions);
				case "subdivisions" -> subdivisions = sane(v, SUBDIVISIONS_MIN, SUBDIVISIONS_MAX, subdivisions);
				case "clamp" -> clamp = value.getBoolean();
				default -> {}
			}
		}
	}

	public int getNeedleOffset() { return needleOffset; }
	public float getMin() { return min; }
	public float getMax() { return max; }
	public int getDivisions() { return divisions; }
	public int getSubdivisions() { return subdivisions; }
	public int getDecimals() { return decimals; }
	public boolean isClamp() { return clamp; }

	public float getCurAngle() {
		return curAngle;
	}

	/** Sets the drum's angle directly, limited to {@code +-MAX_ANGLE}; a NaN is ignored. For the sync packet and the tests. */
	public void setCurAngle(float angle) {
		if(!Float.isNaN(angle))
			curAngle = Math.max(-MAX_ANGLE, Math.min(MAX_ANGLE, angle));
	}

	@Override
	public ControlType getControlType() {
		return ControlType.METER;
	}

	@Override
	public float[] getSize() {
		return new float[]{1.7f, 5f, 1.25f};
	}

	@Override
	public AABB getBoundingBox() {
		return null;
	}

	/** Leafia's {@code ratio}: where {@code t} sits between {@code a} and {@code b}, and {@code a} itself for an empty range. */
	public static float ratio(float a, float b, float t) {
		float delta = b - a;
		if(delta == 0)
			return a;
		return (t - a) / delta;
	}

	public static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}

	/** The drum's angle, in degrees, for a ratio of the scale. Leafia's {@code getAngleForRatio}. */
	public float getAngleForRatio(float ratio) {
		ratio -= 0.5f;
		return -360 / (divisions + 1f) * ratio * divisions;
	}

	/** The value limited to the scale when {@code clamp} is set, either way round; a value that is not a number is the scale's start. */
	public float effectiveValue() {
		float val = getVar("value").getNumber();
		if(!Float.isFinite(val))
			return min;
		if(clamp) {
			if(min < max)
				val = Math.min(Math.max(val, min), max);
			else
				val = Math.min(Math.max(val, max), min);
		}
		return val;
	}

	/** The angle the drum is being turned to for the present value. */
	public float desiredAngle() {
		return -getAngleForRatio(ratio(min, max, effectiveValue()));
	}

	/** The value written next to the {@code i}-th mark (0 to {@code divisions}). */
	public String label(int i) {
		return String.format(Locale.ROOT, "%01." + decimals + "f", lerp(min, max, i / (float) divisions));
	}

	/** One tick of the drum's movement, exactly Leafia's. */
	public void stepTowardValue() {
		float desired = desiredAngle();
		if(!Float.isFinite(desired))
			return;
		float delta = desired - curAngle;
		if(Math.abs(delta) <= THRESHOLD)
			curAngle = desired;
		else {
			float move = Math.max(Math.abs(delta / 2.5f), THRESHOLD) * Math.signum(delta);
			curAngle += move;
		}
	}

	@Override
	public void receiveEvent(ControlEvent evt) {
		if(evt.name.equals("tick")) {
			stepTowardValue();
			CCPSyncPacketBase.sendFor(this, syncPacket());
		}
		super.receiveEvent(evt);
	}

	public CCPMeterVerticalSyncPacket syncPacket() {
		BlockPos pos = panel == null || panel.parent == null ? null : panel.parent.getControlPos();
		return new CCPMeterVerticalSyncPacket(pos, panel == null ? -1 : panel.controls.indexOf(this), curAngle);
	}

	/** A vertical meter's drawn angle, server to client. */
	public static class CCPMeterVerticalSyncPacket extends CCPSyncPacketBase {

		private float value;

		public CCPMeterVerticalSyncPacket() {}

		public CCPMeterVerticalSyncPacket(BlockPos pos, int idx, float value) {
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
			buf.writeFloat(value);
		}

		@Override
		public Consumer<IPayloadContext> decode(LeafiaBuf buf) {
			Target target = readTarget(buf);
			float read = buf.readFloat();
			return apply(target, MeterVertical.class, meter -> meter.setCurAngle(read));
		}
	}

	@Override
	public Control newControl(ControlPanel controlPanel) {
		return new MeterVertical(name, registryName, controlPanel);
	}

	@Override
	public void populateDefaultNodes(List<ControlEvent> list) {}
}
