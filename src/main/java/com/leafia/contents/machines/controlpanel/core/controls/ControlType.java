package com.leafia.contents.machines.controlpanel.core.controls;

import java.util.ArrayList;
import java.util.List;

/**
 * The family a control belongs to, which decides what the placement list groups it under. Adapted from NTM-CE's
 * {@code ControlType} (D-3061), a class standing in for an enum so that an addon can add a family: Leafia's
 * {@code AddonControlType} (batch 11) calls {@link #registerOrGet}, which is what its {@code MixinControlType} forced a
 * class-load for.
 */
public class ControlType {

	public final String name;
	public static final List<ControlType> ALL_VALUES = new ArrayList<>();

	/** Do not use this, use {@link #registerOrGet}. */
	private ControlType(String name) {
		this.name = name;
		ALL_VALUES.add(this);
	}

	public static synchronized ControlType registerOrGet(String name) {
		ControlType ret = getByName(name);
		if(ret == null)
			ret = new ControlType(name);
		return ret;
	}

	public static synchronized ControlType getByName(String name) {
		for(ControlType o : ALL_VALUES) {
			if(o.name.equals(name)) {
				return o;
			}
		}
		return null;
	}

	public static final ControlType BUTTON = registerOrGet("Button");
	public static final ControlType SWITCH = registerOrGet("Switch");
	public static final ControlType SLIDER = registerOrGet("Slider");
	public static final ControlType KNOB = registerOrGet("Knob");
	public static final ControlType DIAL = registerOrGet("Dial");
	public static final ControlType DISPLAY = registerOrGet("Display");
	public static final ControlType INDICATOR = registerOrGet("Indicator");
	public static final ControlType METER = registerOrGet("Meter"); // unused
	public static final ControlType LABEL = registerOrGet("Label");
}
