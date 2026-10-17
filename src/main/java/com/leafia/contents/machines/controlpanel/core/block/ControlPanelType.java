package com.leafia.contents.machines.controlpanel.core.block;

import java.util.Locale;

import net.minecraft.util.StringRepresentable;

/**
 * Which panel a {@link ControlPanelTE} belongs to: the sloped desk panel, or the upright front panel. Adapted from NTM-CE's
 * {@code BlockControlPanelType} (D-3063), an {@code IStringSerializable} there and a {@link StringRepresentable} here.
 */
public enum ControlPanelType implements StringRepresentable {
	CUSTOM_PANEL,
	FRONT_PANEL;

	@Override
	public String getSerializedName() {
		return name().toLowerCase(Locale.ENGLISH);
	}
}
