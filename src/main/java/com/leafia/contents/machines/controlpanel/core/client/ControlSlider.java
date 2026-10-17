package com.leafia.contents.machines.controlpanel.core.client;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * A labelled slider over a range, standing in for Forge 1.12's {@code GuiSlider} as the editor's panes used it (CCP-EDITOR,
 * D-3066): shows {@code prefix + whole number}, no decimals and no suffix; {@link #fraction()} is GuiSlider's
 * {@code sliderValue} (0 to 1) and {@link #getValueInt()} its rounded value. The colour sliders run 0 to 100 and read the
 * fraction, which is how CE got a 0 to 1 channel out of them.
 */
@OnlyIn(Dist.CLIENT)
public class ControlSlider extends AbstractSliderButton {

	private final String prefix;
	private final double min;
	private final double max;
	private final boolean decimals;

	public ControlSlider(int x, int y, int width, int height, String prefix, double min, double max, double start) {
		this(x, y, width, height, prefix, min, max, start, false);
	}

	/** {@code decimals} is GuiSlider's {@code showDecimal}: the label shows one decimal place (the instruments' sizes are in tenths). */
	public ControlSlider(int x, int y, int width, int height, String prefix, double min, double max, double start, boolean decimals) {
		super(x, y, width, height, Component.empty(), 0);
		this.prefix = prefix;
		this.min = min;
		this.max = max;
		this.decimals = decimals;
		setValue(start);
	}

	/** The value in the slider's own units. */
	public double getValue() {
		return min + (max - min) * value;
	}

	/** GuiSlider rounded: half rounds up. */
	public int getValueInt() {
		return (int) Math.round(getValue());
	}

	/** 0 to 1 along the track. */
	public double fraction() {
		return value;
	}

	/** Moves the handle to this value, clamped to the range, without any callback. */
	public void setValue(double v) {
		value = max == min ? 0 : Math.max(0, Math.min(1, (v - min) / (max - min)));
		updateMessage();
	}

	/** Moves the handle to this fraction of the track. */
	public void setFraction(double f) {
		value = Math.max(0, Math.min(1, f));
		updateMessage();
	}

	@Override
	protected void updateMessage() {
		setMessage(Component.literal(prefix + (decimals ? String.format(java.util.Locale.ROOT, "%.1f", getValue()) : String.valueOf(getValueInt()))));
	}

	@Override
	protected void applyValue() {}
}
