package com.leafia.contents.machines.controlpanel.core.client;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.leafia.contents.machines.controlpanel.core.Control;
import com.leafia.contents.machines.controlpanel.core.ControlRegistry;
import com.leafia.contents.machines.controlpanel.core.client.configs.ConfigPanes;
import com.leafia.contents.machines.controlpanel.core.client.configs.SubElementBaseConfig;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Picks a variant of the chosen control family and configures it. Adapted from NTM-CE's {@code SubElementItemConfig}
 * (CCP-EDITOR, D-3066) with Leafia's {@code MixinSubElementItemConfig} folded in.
 *
 * <p>The page shows the variant's name, "n/m", "&lt;" and "&gt;" to change variant (not when editing an existing control), the
 * control's configuration pane (one of the five in {@code client.configs}, chosen by {@link ConfigPanes}) and a picture of the
 * control as configured. "Done" applies the pane's values to the control and goes to the linker.
 *
 * <p><b>The mixin:</b> it injected, at the tail of {@code actionPerformed}, a call to the pane's own {@code actionPerformed} so
 * that a button a pane made reached the pane. That is {@link #actionPerformed}'s last line here. Every button the editor makes
 * reports to the top page, so a pane's button gets there through this page.
 *
 * <p>Differences from CE: the variant is built when it changes, not every frame; coming back from the linker keeps the control
 * being made, with its settings and its links (CE built a fresh one on "Back", which lost both);
 * "Done" overlays the pane's values on the control's existing configuration, so a setting no pane edits survives (CE replaced
 * the whole map, which was fine for the fourteen stock controls and would have wiped an addon control's hidden settings);
 * and the picture is {@link ControlPreview}'s. CE's developer overlay for positioning the picture (
 * {@code ControlPanelViewModelPositonDebugger}) is not ported.
 */
@OnlyIn(Dist.CLIENT)
public class SubElementItemConfig extends SubElement {

	public static final ResourceLocation bg_tex = GuiDraw.texture("gui_base");
	private final float[] previewBox = new float[4];

	public Button btn_done;
	public Button btn_next;
	public Button btn_prev;

	public List<String> variants = Collections.emptyList();
	private int curr_variant = 0;
	private int num_variants = 1;

	SubElementBaseConfig config_gui;
	private Map<String, DataValue> configs;

	/** The variant shown, rebuilt when the choice changes; null forces the pane to be built again. */
	public Control last_control = null;
	private Control variant = null;
	Map<String, DataValue> existing_configs;

	public SubElementItemConfig(ControlEditScreen gui) {
		super(gui);
	}

	@Override
	public void init() {
		int cX = gui.width / 2;
		int cY = gui.height / 2;
		btn_prev = button(cX - 30, gui.getGuiTop() + 24, 15, 20, "<");
		btn_next = button(cX + 15, gui.getGuiTop() + 24, 15, 20, ">");
		btn_done = button(cX - 85, cY + 92, 170, 20, "Done");

		this.config_gui = new SubElementBaseConfig(gui, Collections.emptyMap());
		this.config_gui.init();
		super.init();
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		int cX = gui.width / 2;
		int gLeft = gui.getGuiLeft();
		int gTop = gui.getGuiTop();
		if(variants.isEmpty() || gui.currentEditControl == null)
			return;

		num_variants = variants.size() - 1;

		if(gui.isEditMode) {
			existing_configs = gui.currentEditControl.getConfigs();
			curr_variant = variants.indexOf(gui.currentEditControl.registryName);
		}

		if(curr_variant < 0 || curr_variant > variants.size() - 1)
			curr_variant = 0;
		String variantName = variants.get(curr_variant);
		if(variant == null || !variant.registryName.equals(variantName)) {
			// Coming back from the linker, the control being made is the same variant: keep it (and the links it holds).
			if(!gui.isEditMode && gui.currentEditControl.registryName.equals(variantName))
				variant = gui.currentEditControl;
			else
				variant = ControlRegistry.getNew(variantName, gui.panel);
		}

		boolean canChangeVariant = !gui.isEditMode && num_variants > 0;
		GuiDraw.show(btn_prev, canChangeVariant && curr_variant > 0);
		GuiDraw.show(btn_next, canChangeVariant && curr_variant < num_variants);

		String text = variant.name;
		g.drawString(gui.font(), text, cX - (gui.font().width(text) / 2F), gTop + 11, 0xFF777777, false);
		text = (curr_variant + 1) + "/" + (num_variants + 1);
		g.drawString(gui.font(), text, cX - (gui.font().width(text) / 2F), gTop + 30, 0xFF777777, false);

		if(last_control == null || !variant.name.equals(last_control.name)) {
			config_gui.enableButtons(false);
			config_gui.removeWidgets();
			Control proto = ControlRegistry.registry.get(variantName);
			if(proto != null) {
				config_gui = ConfigPanes.create(proto, gui, gui.isEditMode ? existing_configs : variant.getConfigs());
			}
			if(!gui.isEditMode)
				gui.currentEditControl = variant;

			config_gui.init();
			config_gui.enableButtons(true);

			variants = ControlRegistry.getAllControlsOfType(gui.currentEditControl.getControlType());
		}
		int[] previewRect = config_gui.getPreviewTransform();
		renderPreview(g, gLeft + previewRect[0], gTop + previewRect[1], previewRect[2], previewRect[3], variant);
		config_gui.render(g, mouseX, mouseY, partialTick);

		this.last_control = variant;
	}

	@Override
	public void renderBackground(GuiGraphics g) {
		g.blit(bg_tex, gui.getGuiLeft(), gui.getGuiTop(), 0, 0, gui.getXSize(), gui.getYSize());
	}

	@Override
	public void update() {
		config_gui.update();
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int state) {
		return config_gui.mouseReleased(mouseX, mouseY, state);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		return config_gui.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		return config_gui.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean charTyped(char codePoint, int modifiers) {
		return config_gui.charTyped(codePoint, modifiers);
	}

	@Override
	public void actionPerformed(Button button) {
		if(button == btn_done) {
			// The pane's values over the control's own settings, so one no pane edits is kept.
			configs = new LinkedHashMap<>(gui.currentEditControl.getConfigs());
			config_gui.fillConfigs(configs);
			gui.currentEditControl.applyConfigs(configs);
			gui.linker.reloadLinkedFromCurrentEditControl();

			config_gui.enableButtons(false);
			last_control = null;
			variant = null;
			gui.pushElement(gui.linker);
		} else if(button == btn_next) {
			curr_variant = Math.min(num_variants, curr_variant + 1);
		} else if(button == btn_prev) {
			curr_variant = Math.max(0, curr_variant - 1);
		} else {
			// Leafia's MixinSubElementItemConfig: whatever else was pressed is the pane's.
			config_gui.actionPerformed(button);
		}
	}

	@Override
	public void enableButtons(boolean enable) {
		GuiDraw.show(btn_done, enable);
		if(!enable) {
			GuiDraw.show(btn_next, false);
			GuiDraw.show(btn_prev, false);
		} else {
			gui.setSlotsEnabled(false);
		}
	}

	@Override
	public void onClose() {
		config_gui.onClose();
	}

	private void renderPreview(GuiGraphics g, int x, int y, int width, int height, Control variant) {
		Map<String, DataValue> previewConfigs = variant.getConfigs();
		config_gui.fillConfigs(previewConfigs);
		variant.refreshConfigs();
		variant.posX = 0;
		variant.posY = 0;

		GuiDraw.rect(g, x, y, x + width, y + height, GuiDraw.argb(0.09F, 0.11F, 0.10F, 0.85F));
		GuiDraw.rect(g, x + 1, y + 1, x + width - 1, y + height - 1, GuiDraw.argb(0.16F, 0.18F, 0.17F, 0.95F));

		ControlPreview.measure(variant);
		variant.fillBox(previewBox);
		float boxWidth = Math.max(previewBox[2] - previewBox[0], 0.1F);
		float boxHeight = Math.max(previewBox[3] - previewBox[1], 0.1F);
		float scale = Math.min((width - 12F) / boxWidth, (height - 12F) / boxHeight);
		float renderX = x + (width - boxWidth * scale) / 2F - previewBox[0] * scale;
		float renderY = y + (height - boxHeight * scale) / 2F - previewBox[1] * scale;

		g.enableScissor(x, y, x + width, y + height);
		g.pose().pushPose();
		g.pose().translate(renderX, renderY, 0);
		g.pose().scale(scale, scale, 1F);
		gui.placement.renderControl(g, variant, false);
		g.pose().popPose();
		g.disableScissor();
	}
}
