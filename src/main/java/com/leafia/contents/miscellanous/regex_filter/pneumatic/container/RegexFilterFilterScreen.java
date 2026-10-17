package com.leafia.contents.miscellanous.regex_filter.pneumatic.container;

import java.util.ArrayList;
import java.util.List;

import com.hbm.util.i18n.I18nUtil;
import com.leafia.contents.miscellanous.regex_filter.pneumatic.RegexFilterTE;
import com.leafia.contents.miscellanous.regex_filter.pneumatic.RegexFilterTE.FilterType;
import com.leafia.contents.miscellanous.regex_filter.pneumatic.RegexFilterTE.RegexFilter;
import com.leafia.dev.LeafiaUtil;
import com.leafia.dev.gui.FiaUIRect;
import com.leafia.dev.gui.GuiScreenLeafia;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The filter editor: a text box for the pattern, a button that cycles the type, and a button that
 * saves - or, with shift held on an existing filter, deletes it.
 *
 * <p>Upstream: {@code .../pneumatic/container/RegexFilterGUIFilter.java}, a plain
 * {@code GuiScreenLeafia} drawn over the same menu. The pattern is typed in green while it
 * compiles and red while it does not, and an invalid one cannot be saved (the denied sound plays
 * instead). That is the whole of the validation, and it lives on the client: the server accepts
 * what it is sent (D-1084).
 *
 * <p>There is no way to close the container from here. Escape, or the inventory key, goes back to
 * {@link RegexFilterScreen}, as upstream's did, and a focused box takes Escape and Enter first to
 * lose focus. If the server closes the menu, vanilla removes whatever screen is up.
 */
@OnlyIn(Dist.CLIENT)
public class RegexFilterFilterScreen extends GuiScreenLeafia implements IRegexFilterGUI {

	private final RegexFilterTE entity;
	private final RegexFilterMenu menu;
	private final Inventory playerInventory;
	private final Component menuTitle;

	/** The filter being edited, or null while a new one is being made. */
	public RegexFilter filter;
	FilterType filterType = FilterType.RESOURCE_ID;
	EditBox field;
	FiaUIRect modeRect;
	FiaUIRect saveRect;

	public RegexFilterFilterScreen(RegexFilterMenu menu, Inventory playerInventory, Component title,
			RegexFilterTE entity, RegexFilter filter) {
		this.menu = menu;
		this.playerInventory = playerInventory;
		this.menuTitle = title;
		this.entity = entity;
		this.filter = filter;
		if(filter != null) filterType = filter.type;
		this.xSize = 176;
		this.ySize = 41;
	}

	@Override
	protected void init() {
		super.init();

		entity.gui = this;
		modeRect = new FiaUIRect(this, 132, 16, 18, 18);
		saveRect = new FiaUIRect(this, 151, 16, 18, 18);

		field = new EditBox(font, guiLeft + 8 + 4, guiTop + 18 + 4, 117 - 8, 10, Component.empty());
		field.setTextColor(0x5BBC00);
		field.setTextColorUneditable(0x499500);
		field.setBordered(false);
		field.setMaxLength(50);
		if(filter != null) field.setValue(filter.regex);
		addRenderableWidget(field);
	}

	@Override
	public void removed() {
		if(entity.gui == this) entity.gui = null;
		super.removed();
	}

	/**
	 * The list changed under us. A new filter being made is not affected; an existing one is
	 * re-found by pattern and type, and if another player deleted it the editor has nothing left to
	 * edit and goes back.
	 */
	@Override
	public void onSyncFilters() {
		if(filter == null) return;
		for(RegexFilter other : entity.filters) {
			if(other.equals(filter)) {
				filter = other;
				return;
			}
		}
		exitConfig();
	}

	void exitConfig() {
		minecraft.setScreen(new RegexFilterScreen(menu, playerInventory, menuTitle));
	}

	@Override
	public void onClose() {
		exitConfig();
	}

	@Override
	public void tick() {
		super.tick();
		if(minecraft.player != null && !minecraft.player.isAlive()) minecraft.player.closeContainer();
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		// Escape, Enter and keypad Enter let go of a focused box before they leave the screen.
		if((keyCode == 256 || keyCode == 257 || keyCode == 335) && field.isFocused()) {
			field.setFocused(false);
			setFocused(null);
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	boolean isDelete() {
		return filter != null && hasShiftDown();
	}

	@Override
	public boolean mouseClicked(double x, double y, int button) {

		boolean handled = super.mouseClicked(x, y, button);

		int mouseX = (int) x;
		int mouseY = (int) y;

		if(button == 0) {
			if(modeRect.isMouseIn(mouseX, mouseY)) {
				playClick(1);
				filterType = FilterType.values()[(filterType.ordinal() + 1) % FilterType.values().length];
				return true;
			} else if(saveRect.isMouseIn(mouseX, mouseY)) {
				if(isDelete()) {
					playClick(1);
					entity.filters.remove(filter);
				} else {
					if(!LeafiaUtil.isRegexValid(field.getValue())) {
						playDenied();
						return true;
					}
					playClick(1);
					RegexFilter f = filter;
					if(f == null) f = new RegexFilter();
					f.type = filterType;
					f.regex = field.getValue();
					if(filter == null) entity.filters.add(f);
					filter = null;
				}
				entity.generateSyncPacket().__sendToServer();
				exitConfig();
				return true;
			}
		}
		return handled;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);

		if(modeRect.isMouseIn(mouseX, mouseY)) {
			graphics.renderTooltip(font, Component.literal(I18nUtil.resolveKey("tile.regex_filter.gui.config.filter",
					I18nUtil.resolveKey("tile.regex_filter.gui.filter." + filterType.name()))), mouseX, mouseY);
		} else if(saveRect.isMouseIn(mouseX, mouseY)) {
			List<Component> texts = new ArrayList<>();
			if(isDelete())
				texts.add(Component.literal(I18nUtil.resolveKey("tile.regex_filter.gui.config.delete")));
			else {
				texts.add(Component.literal(I18nUtil.resolveKey("tile.regex_filter.gui.config.save")));
				if(filter != null)
					texts.add(Component.literal(I18nUtil.resolveKey("tile.regex_filter.gui.config.shift")));
			}
			graphics.renderComponentTooltip(font, texts, mouseX, mouseY);
		}
	}

	@Override
	protected void drawGuiScreenForegroundLayer(GuiGraphics graphics, int mouseX, int mouseY) {
		graphics.drawString(font, menuTitle, xSize / 2 - font.width(menuTitle) / 2, 5, 4210752, false);
	}

	@Override
	protected void drawGuiScreenBackgroundLayer(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {

		drawDefaultBackground(graphics, mouseX, mouseY, partialTicks);
		graphics.blit(RegexFilterScreen.TEXTURE, guiLeft, guiTop, 0, 203, xSize, ySize);

		boolean valid = LeafiaUtil.isRegexValid(field.getValue());

		if(modeRect.isMouseIn(mouseX, mouseY)) graphics.setColor(0.9f, 0.9f, 0.9f, 1);
		graphics.blit(RegexFilterScreen.TEXTURE, guiLeft + 132, guiTop + 16, 200, filterType.ordinal() * 18, 18, 18);
		graphics.setColor(1, 1, 1, 1);

		if(saveRect.isMouseIn(mouseX, mouseY)) graphics.setColor(0.9f, 0.9f, 0.9f, 1);
		graphics.blit(RegexFilterScreen.TEXTURE, guiLeft + 151, guiTop + 16, isDelete() ? 194 : 176, 219, 18, 18);
		graphics.setColor(1, 1, 1, 1);

		if(field.isFocused())
			graphics.blit(RegexFilterScreen.TEXTURE, guiLeft + 8, guiTop + 18, 136, 185, 117, 14);

		field.setTextColor(valid ? 0x5BBC00 : 0xD84747);
		field.setTextColorUneditable(valid ? 0x499500 : 0xA33A3A);
	}
}
