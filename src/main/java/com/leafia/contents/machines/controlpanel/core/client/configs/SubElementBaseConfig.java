package com.leafia.contents.machines.controlpanel.core.client.configs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.leafia.contents.machines.controlpanel.core.client.ControlEditScreen;
import com.leafia.contents.machines.controlpanel.core.client.ControlSlider;
import com.leafia.contents.machines.controlpanel.core.client.GuiDraw;
import com.leafia.contents.machines.controlpanel.core.client.SubElement;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;
import com.leafia.contents.machines.controlpanel.core.types.DataValueString;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The configuration pane of a control that has nothing to configure, and the base of the five that do (CCP-EDITOR, D-3066).
 * Adapted from NTM-CE's {@code SubElementBaseConfig}: {@link #fillConfigs} writes what the pane's widgets say into a map under
 * the control's own config keys, {@link #getPreviewTransform} says where on the screen the control's picture goes.
 *
 * <p>A pane reads its starting values from the control's config map; a key that is missing (a panel saved by an addon that
 * since changed, a hostile tag) reads as the pane's default rather than failing, which CE's {@code map.get(key).getNumber()}
 * did with a null pointer. The numbers the widgets can produce are the editor's slider ranges, which are also the ranges the
 * controls clamp their derived fields to when they read them back (D-3064).
 */
@OnlyIn(Dist.CLIENT)
public class SubElementBaseConfig extends SubElement {

	protected static final int[] DEFAULT_PREVIEW_RECT = new int[]{84, 80, 88, 88};

	public SubElementBaseConfig(ControlEditScreen gui, Map<String, DataValue> map) {
		super(gui);
	}

	public Map<String, DataValue> getConfigs() {
		Map<String, DataValue> configs = new HashMap<>();
		fillConfigs(configs);
		return configs;
	}

	/** Writes this pane's values into the map, keeping the value objects already there. */
	public void fillConfigs(Map<String, DataValue> configs) {}

	/** {x, y, width, height} of the control's picture, relative to the screen's top left corner. */
	public int[] getPreviewTransform() {
		return DEFAULT_PREVIEW_RECT;
	}

	protected static void putFloatConfig(Map<String, DataValue> configs, String key, float value) {
		DataValue dataValue = configs.get(key);
		if(dataValue instanceof DataValueFloat f) {
			f.num = value;
		} else {
			configs.put(key, new DataValueFloat(value));
		}
	}

	protected static void putStringConfig(Map<String, DataValue> configs, String key, String value) {
		DataValue dataValue = configs.get(key);
		if(dataValue instanceof DataValueString s) {
			s.set(value);
		} else {
			configs.put(key, new DataValueString(value));
		}
	}

	protected static float num(Map<String, DataValue> map, String key, float fallback) {
		DataValue v = map == null ? null : map.get(key);
		return v == null ? fallback : v.getNumber();
	}

	protected static boolean flag(Map<String, DataValue> map, String key, boolean fallback) {
		DataValue v = map == null ? null : map.get(key);
		return v == null ? fallback : v.getBoolean();
	}

	protected static String text(Map<String, DataValue> map, String key, String fallback) {
		DataValue v = map == null ? null : map.get(key);
		return v == null ? fallback : v.toString();
	}

	/** Every widget the pane made, shown and hidden together. */
	private final List<AbstractWidget> widgets = new ArrayList<>();

	protected ControlSlider slider(int x, int y, int width, String prefix, double min, double max, double start) {
		ControlSlider s = add(new ControlSlider(x, y, width, 15, prefix, min, max, start));
		widgets.add(s);
		return s;
	}

	protected ControlSlider slider(int x, int y, int width, String prefix, double min, double max, double start, boolean decimals) {
		ControlSlider s = add(new ControlSlider(x, y, width, 15, prefix, min, max, start, decimals));
		widgets.add(s);
		return s;
	}

	protected EditBox editBox(int x, int y, int width, int height, String text) {
		EditBox box = add(new EditBox(Minecraft.getInstance().font, x, y, width, height, Component.literal("Text")));
		box.setValue(text);
		widgets.add(box);
		return box;
	}

	protected Checkbox checkbox(int x, int y, String label, boolean selected) {
		Checkbox box = add(Checkbox.builder(Component.literal(label), Minecraft.getInstance().font).pos(x, y).selected(selected).build());
		widgets.add(box);
		return box;
	}

	@Override
	public void enableButtons(boolean enable) {
		for(AbstractWidget w : widgets)
			GuiDraw.show(w, enable);
	}

	/** The colour swatch the colour sliders drive, drawn at the screen-relative corner given. */
	protected void swatch(GuiGraphics g, int x, int y, float r, float gr, float b) {
		g.fill(gui.getGuiLeft() + x, gui.getGuiTop() + y, gui.getGuiLeft() + x + 15, gui.getGuiTop() + y + 15, GuiDraw.argb(r, gr, b, 1F));
	}
}
