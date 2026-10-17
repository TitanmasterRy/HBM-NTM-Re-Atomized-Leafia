package com.leafia.contents.miscellanous.regex_filter.pneumatic.container;

import java.util.ArrayList;
import java.util.List;

import com.hbm.util.i18n.I18nUtil;
import com.leafia.contents.miscellanous.regex_filter.pneumatic.RegexFilterTE;
import com.leafia.contents.miscellanous.regex_filter.pneumatic.RegexFilterTE.RegexFilter;
import com.leafia.dev.LeafiaUtil.ScrollUtil;
import com.leafia.dev.gui.FiaUIRect;
import com.leafia.dev.gui.GuiScreenLeafia;
import com.leafia.lib.LeafiaRef;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The filter's main screen: its one slot, the player's inventory, and a three-row scrolling list
 * of the filters with a row at the end to add one.
 *
 * <p>Upstream: {@code .../pneumatic/container/RegexFilterGUI.java}. Every coordinate is its own,
 * and so is the arithmetic - including the way the list is hit-tested, which moves the
 * <em>mouse</em> down by the scroll offset instead of moving the rectangles up.
 *
 * <p>Each row has a toggle between whitelist and blacklist (the small square left of the pencil)
 * and a pencil that opens {@link RegexFilterFilterScreen} on that filter. The last row is a plus
 * that opens it on a new one. Both screens share this menu, which stays open on the server while
 * they swap: neither one closes it when it is replaced, and each releases the block entity's
 * hook only if it is still the one holding it.
 */
@OnlyIn(Dist.CLIENT)
public class RegexFilterScreen extends AbstractContainerScreen<RegexFilterMenu> implements IRegexFilterGUI {

	static final ResourceLocation TEXTURE = LeafiaRef.rl("textures/gui/regex_filter.png");

	private final RegexFilterTE entity;
	private final Inventory playerInventory;

	int truncatorLength = 0;
	FiaUIRect scrollRect;
	int scroll = 0;
	boolean scrollin = false;
	final List<RegexEntry> list = new ArrayList<>();

	public RegexFilterScreen(RegexFilterMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		this.entity = menu.entity;
		this.playerInventory = playerInventory;
		this.imageWidth = 176;
		this.imageHeight = 185;
		this.inventoryLabelY = imageHeight - 96 + 2;
	}

	public static class RegexEntry {
		FiaUIRect rect;
		FiaUIRect blacklistRect;
		RegexFilter filter;
	}

	@Override
	protected void init() {
		super.init();

		// The block entity was gone when the menu looked for it: nothing to edit.
		if(entity == null) {
			onClose();
			return;
		}

		entity.gui = this;
		truncatorLength = font.width("...");
		scrollRect = new FiaUIRect(this, 156, 17, 12, 52);
		generateRegexEntries();
	}

	@Override
	public void removed() {
		if(entity != null && entity.gui == this) entity.gui = null;
		super.removed();
	}

	@Override
	public void onSyncFilters() {
		if(scrollRect == null) return; // the first sync can beat init()
		generateRegexEntries();
	}

	int getMaxScroll() {
		return entity.filters.size() - 2;
	}

	void generateRegexEntries() {
		list.clear();
		for(int i = 0; i < entity.filters.size() + 1; i++) {
			RegexEntry entry = new RegexEntry();
			if(i < entity.filters.size()) {
				entry.filter = entity.filters.get(i);
				entry.rect = new FiaUIRect(this, 133, 18 + i * 18, 14, 14);
				entry.blacklistRect = new FiaUIRect(this, 122, 19 + i * 18, 10, 12);
			} else
				entry.rect = new FiaUIRect(this, 86, 18 + i * 18, 14, 14);
			list.add(entry);
		}
		int maxScroll = Math.max(getMaxScroll(), 0);
		scroll = Mth.clamp(scroll, 0, maxScroll);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if(entity != null && getMaxScroll() > 0) {
			scroll += -Mth.clamp((int) Math.signum(scrollY), -1, 1);
			scroll = Mth.clamp(scroll, 0, getMaxScroll());
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		renderTooltip(graphics, mouseX, mouseY);
		if(entity == null) return;

		for(int i = scroll; i <= scroll + 2; i++) {
			if(i < list.size()) {
				RegexEntry entry = list.get(i);
				if(entry.rect.isMouseIn(mouseX, mouseY + scroll * 18)) {
					tip(graphics, entry.filter == null ? "tile.regex_filter.gui.main.add" : "tile.regex_filter.gui.main.edit", mouseX, mouseY);
					break;
				}
				if(entry.filter != null && entry.blacklistRect.isMouseIn(mouseX, mouseY + scroll * 18)) {
					tip(graphics, "tile.regex_filter.gui.main." + (entry.filter.blacklist ? "blacklist" : "whitelist"), mouseX, mouseY);
				}
			} else break;
		}
	}

	private void tip(GuiGraphics graphics, String key, int mouseX, int mouseY) {
		graphics.renderTooltip(font, Component.literal(I18nUtil.resolveKey(key)), mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(double x, double y, int b) {

		boolean handled = super.mouseClicked(x, y, b);
		if(entity == null) return handled;

		int mouseX = (int) x;
		int mouseY = (int) y;

		if(b == 0) {
			if(scrollRect.isMouseIn(mouseX, mouseY)) scrollin = true;
			for(int i = scroll; i <= scroll + 2; i++) {
				if(i < list.size()) {
					RegexEntry entry = list.get(i);
					if(entry.filter != null && entry.blacklistRect.isMouseIn(mouseX, mouseY + scroll * 18)) {
						GuiScreenLeafia.click(1);
						entry.filter.blacklist = !entry.filter.blacklist;
						entity.generateSyncPacket().__sendToServer();
					}
					if(entry.rect.isMouseIn(mouseX, mouseY + scroll * 18)) {
						GuiScreenLeafia.click(1);
						minecraft.setScreen(new RegexFilterFilterScreen(menu, playerInventory, title, entity, entry.filter));
						return true;
					}
				} else break;
			}
		}
		return handled;
	}

	@Override
	public boolean mouseReleased(double x, double y, int button) {
		if(button == 0) scrollin = false;
		return super.mouseReleased(x, y, button);
	}

	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		graphics.drawString(font, title, imageWidth / 2 - font.width(title) / 2, 5, 4210752, false);
		graphics.drawString(font, playerInventoryTitle, 8, imageHeight - 96 + 2, 4210752, false);
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {

		graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
		if(entity == null) return;

		int maxScroll = Math.max(getMaxScroll(), 0);
		int scrollBarPos = 0;
		if(maxScroll > 0) {
			if(scrollin)
				scroll = ScrollUtil._getScrollOffset(15, 52, mouseY - 17 - topPos, maxScroll);
			scrollBarPos = ScrollUtil.getScrollBarPos(15, 52, ScrollUtil._getScrollRatio(scroll, maxScroll));
		}
		graphics.blit(TEXTURE, leftPos + 156, topPos + 17 + scrollBarPos, maxScroll > 0 ? 176 : 188, 0, 12, 15);

		int pos = 0;
		for(int i = scroll; i <= scroll + 2; i++) {
			if(i < list.size()) {
				RegexEntry entry = list.get(i);
				if(entry.filter != null) {
					graphics.blit(TEXTURE, leftPos + 37, topPos + 16 + pos * 18, 0, 185, 112, 18);

					String text = entry.filter.regex;
					if(font.width(text) > 88) {
						text = "";
						for(char c : entry.filter.regex.toCharArray()) {
							if(font.width(text + c) + truncatorLength <= 77)
								text = text + c;
							else {
								text = text + "...";
								break;
							}
						}
					}
					graphics.drawString(font, text, leftPos + 37 + 5, topPos + 16 + pos * 18 + 5, 0xa6a6a6, true);

					float color = entry.filter.blacklist ? 0 : 1;
					if(entry.blacklistRect.isMouseIn(mouseX, mouseY + scroll * 18))
						color = entry.filter.blacklist ? 0.1f : 0.9f;
					graphics.setColor(color, color, color, 1);
					graphics.blit(TEXTURE,
							entry.blacklistRect.guiLeft + entry.blacklistRect.x,
							entry.blacklistRect.guiTop + entry.blacklistRect.y - scroll * 18,
							112, 185, 10, 12);
					graphics.setColor(1, 1, 1, 1);
				}
				if(entry.rect.isMouseIn(mouseX, mouseY + scroll * 18))
					graphics.setColor(0.9f, 0.9f, 0.9f, 1);
				graphics.blit(TEXTURE,
						entry.rect.guiLeft + entry.rect.x, entry.rect.guiTop + entry.rect.y - scroll * 18,
						entry.filter != null ? 225 : 239, 171,
						14, 14);
				graphics.setColor(1, 1, 1, 1);
			} else break;
			pos++;
		}
	}
}
