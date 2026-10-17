package com.leafia.contents.machines.controlpanel.core.client.configs;

import java.util.Map;

import com.leafia.contents.machines.controlpanel.core.client.ControlEditScreen;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;

import net.minecraft.client.gui.components.EditBox;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The dials' pane (both the square and the large dial use it): a text box for the {@code label}. Adapted from NTM-CE's
 * {@code SubElementDialSquare} (CCP-EDITOR, D-3066).
 */
@OnlyIn(Dist.CLIENT)
public class SubElementDialSquare extends SubElementBaseConfig {

	private static final int[] TRANSFORM = {18, 69, 88, 88};

	private final String text;
	private EditBox textField;

	public SubElementDialSquare(ControlEditScreen gui, Map<String, DataValue> map) {
		super(gui, map);
		this.text = text(map, "label", "");
	}

	@Override
	public void init() {
		int cX = gui.width / 2;
		textField = editBox(cX - 10, gui.getGuiTop() + 70, 120, 20, text);
		super.init();
	}

	@Override
	public void fillConfigs(Map<String, DataValue> configs) {
		putStringConfig(configs, "label", textField == null ? text : textField.getValue());
	}

	@Override
	public int[] getPreviewTransform() {
		return TRANSFORM;
	}
}
