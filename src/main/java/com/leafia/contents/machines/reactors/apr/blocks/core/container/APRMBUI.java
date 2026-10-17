package com.leafia.contents.machines.reactors.apr.blocks.core.container;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.hbm.util.i18n.I18nUtil;
import com.leafia.contents.machines.reactors.apr.blocks.core.APRCoreTE;
import com.leafia.dev.LeafiaUtil.ScrollUtil;
import com.leafia.dev.gui.FiaUIRect;
import com.leafia.dev.gui.GuiScreenLeafia;
import com.leafia.lib.LeafiaRef;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The APR's chamber editor, opened by a click on an unassembled core: a scrolling list of chamber radii, a row to
 * add one, and "Highlight" ("Auto-Build" for a creative player). Closing it sends the list to the core.
 *
 * <p>Upstream: {@code .../apr/blocks/core/container/APRMBUI.java}, a plain screen (no container), every coordinate
 * upstream's on {@code gui_assembly.png} (195 x 135): four rows of 25 pixels from y = 9, a 12-wide scroll bar at
 * (175, 9) over 98 pixels, the radius field at x = 115, the remove button at (148, row + 2) and the add button at
 * (80, row + 2). A new chamber starts at radius 6. A field turns red with its reason as its tooltip when it is not
 * a number, under 6, over 32, or within 3 of another (both of the pair); the list is still sent on close, and the
 * core empties its list on an intersection (see {@link APRCoreTE}). It closes itself when the core reports itself
 * assembled.
 *
 * <p>1.12.2's {@code GuiTextField} is an {@link EditBox} drawn and fed by hand, as upstream drew and fed its fields,
 * so only the four visible rows take clicks.
 */
@OnlyIn(Dist.CLIENT)
public class APRMBUI extends GuiScreenLeafia {

	public static final ResourceLocation TEXTURE = LeafiaRef.rl("textures/gui/apr/gui_assembly.png");

	public final APRCoreTE te;
	public List<Integer> chambers = new ArrayList<>();

	public APRMBUI(APRCoreTE te) {
		this.te = te;
		xSize = 195;
		ySize = 130 + 5;
		chambers.addAll(te.chambers);
	}

	public enum ErrorType {
		INTERSECTION, TOO_LARGE, TOO_SMALL, MALFORMED
	}

	public static class ScrollItem {
		public EditBox field;
		public int index;
		public FiaUIRect deleteOrAdd;
		public ErrorType errorType = null;
	}

	public Button highlight;
	public boolean scrollin = false;
	public FiaUIRect scrollRect;
	public boolean highlightOnClose = false;
	public final List<ScrollItem> items = new ArrayList<>();
	public int scrollPos = 0;

	@Override
	protected void init() {
		super.init();
		regenItems();
		clearWidgets();
		boolean creative = Minecraft.getInstance().player != null && Minecraft.getInstance().player.isCreative();
		highlight = Button.builder(Component.literal(I18nUtil.resolveKey(creative ? "desc.leafia.apr_mb.autobuild" : "desc.leafia.apr_mb.highlight")),
				b -> {
					highlightOnClose = true;
					onClose();
				}).bounds(guiLeft + xSize / 2 - 50, guiTop + 110, 100, 20).build();
		addRenderableWidget(highlight);
		scrollRect = new FiaUIRect(this, 175, 9, 12, 98);
	}

	public void regenItems() {
		items.clear();
		for(int i = 0; i < chambers.size(); i++) {
			int r = chambers.get(i);
			EditBox field = new EditBox(font, guiLeft + 113 + 2, 0, 30, 10, Component.empty());
			field.setTextColor(0x5BBC00);
			field.setTextColorUneditable(0x499500);
			field.setBordered(false);
			field.setMaxLength(2);
			field.setValue(Integer.toString(r));
			ScrollItem item = new ScrollItem();
			item.field = field;
			item.deleteOrAdd = new FiaUIRect(this, 148, 0, 19, 19);
			items.add(item);
		}
		ScrollItem item = new ScrollItem();
		item.deleteOrAdd = new FiaUIRect(this, 80, 0, 19, 19);
		items.add(item);
	}

	@FunctionalInterface
	public interface ScrollItemConsumer {
		void accept(int i, ScrollItem item, int mx, int my);
	}

	public void forItems(ScrollItemConsumer consumer, int mx, int my) {
		int size = items.size();
		for(int i = scrollPos; i < scrollPos + 4; i++) {
			if(items.size() != size) break;
			if(i >= items.size()) break;
			consumer.accept(i, items.get(i), mx, my);
		}
	}

	public void updatePosition(int i, ScrollItem item, int mouseX, int mouseY) {
		if(item.field != null)
			item.field.setY(guiTop + 13 + 4 - (scrollPos - i) * 25);
		item.deleteOrAdd.y = 11 - (scrollPos - i) * 25;
	}

	private GuiGraphics graphics;
	private float partial;

	public void renderItems(int i, ScrollItem item, int mouseX, int mouseY) {
		GuiGraphics g = graphics;
		if(item.field != null) {
			g.blit(TEXTURE, guiLeft + 9, guiTop + 9 - (scrollPos - i) * 25, 0, 130 + 5, 160, 23);
			g.drawString(font, I18nUtil.resolveKey("desc.leafia.apr_mb.radius"), guiLeft + 15, guiTop + 17 - (scrollPos - i) * 25, 4210752, false);
			item.field.setTextColor(0x5BBC00);
			item.field.setTextColorUneditable(0x499500);
			g.setColor(1, 1, 1, 1);
			if(item.errorType != null) {
				g.blit(TEXTURE, guiLeft + 113, guiTop + 13 - (scrollPos - i) * 25, 195, 15, 32, 15);
				item.field.setTextColor(0xFF0000);
				item.field.setTextColorUneditable(0x950000);
			} else if(item.field.isFocused())
				g.blit(TEXTURE, guiLeft + 113, guiTop + 13 - (scrollPos - i) * 25, 195, 0, 32, 15);
			item.field.render(g, mouseX, mouseY, partial);
			g.setColor(0.9f, 0.9f, 0.9f, 1);
			if(item.deleteOrAdd.isMouseIn(mouseX, mouseY))
				drawTexturedModalByFiaRect(g, TEXTURE, item.deleteOrAdd, 139, 132 + 5);
			g.setColor(1, 1, 1, 1);
		} else {
			if(item.deleteOrAdd.isMouseIn(mouseX, mouseY))
				g.setColor(0.9f, 0.9f, 0.9f, 1);
			drawTexturedModalByFiaRect(g, TEXTURE, item.deleteOrAdd, 160, 130 + 5);
			g.setColor(1, 1, 1, 1);
		}
	}

	public static boolean isHoveringOnField(EditBox field, int mouseX, int mouseY) {
		return mouseX >= field.getX() && mouseX < field.getX() + field.getWidth() && mouseY >= field.getY() && mouseY < field.getY() + field.getHeight();
	}

	public void renderHoveredInfo(int i, ScrollItem item, int mouseX, int mouseY) {
		GuiGraphics g = graphics;
		if(item.field != null) {
			if(isHoveringOnField(item.field, mouseX, mouseY)) {
				if(item.errorType != null)
					g.renderTooltip(font, Component.literal(I18nUtil.resolveKey("desc.leafia.apr_mb.error." + item.errorType.name().toLowerCase(java.util.Locale.ROOT))), mouseX, mouseY);
			}
			if(item.deleteOrAdd.isMouseIn(mouseX, mouseY))
				g.renderTooltip(font, Component.literal(I18nUtil.resolveKey("desc.leafia.apr_mb.delete")), mouseX, mouseY);
		} else {
			if(item.deleteOrAdd.isMouseIn(mouseX, mouseY))
				g.renderTooltip(font, Component.literal(I18nUtil.resolveKey("desc.leafia.apr_mb.add")), mouseX, mouseY);
		}
	}

	public void onClick(int i, ScrollItem item, int mouseX, int mouseY) {
		if(item.field != null) {
			if(item.deleteOrAdd.isMouseIn(mouseX, mouseY)) {
				chambers.remove(i);
				regenItems();
				playClick(1);
			}
		} else {
			if(item.deleteOrAdd.isMouseIn(mouseX, mouseY)) {
				chambers.add(APRCoreTE.minChamberRadius);
				regenItems();
				playClick(1);
			}
		}
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
		this.graphics = g;
		this.partial = partialTicks;
		super.render(g, mouseX, mouseY, partialTicks);
		forItems(this::renderHoveredInfo, mouseX, mouseY);
		if(te.assembled)
			onClose();
	}

	@Override
	public boolean mouseClicked(double mx, double my, int mouseButton) {
		boolean handled = super.mouseClicked(mx, my, mouseButton);
		int mouseX = (int) mx, mouseY = (int) my;
		for(ScrollItem item : items) {
			if(item.field != null) {
				boolean in = isHoveringOnField(item.field, mouseX, mouseY) && isVisible(item);
				item.field.setFocused(in);
				if(in) item.field.mouseClicked(mx, my, mouseButton);
			}
		}
		forItems(this::onClick, mouseX, mouseY);
		if(scrollRect.isMouseIn(mouseX, mouseY) && mouseButton == 0)
			scrollin = true;
		return true;
	}

	private boolean isVisible(ScrollItem item) {
		int i = items.indexOf(item);
		return i >= scrollPos && i < scrollPos + 4;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int state) {
		if(state == 0)
			scrollin = false;
		return super.mouseReleased(mouseX, mouseY, state);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		for(ScrollItem item : items) {
			if(item.field != null && item.field.isFocused()) {
				if(keyCode == 256) break; // escape still closes
				if(item.field.keyPressed(keyCode, scanCode, modifiers) || item.field.canConsumeInput())
					return true;
			}
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean charTyped(char codePoint, int modifiers) {
		for(ScrollItem item : items) {
			if(item.field != null && item.field.isFocused() && item.field.charTyped(codePoint, modifiers))
				return true;
		}
		return super.charTyped(codePoint, modifiers);
	}

	@Override
	public void removed() {
		super.removed();
		te.requestChambers(chambers, highlightOnClose);
	}

	public void putIntersectionError(int i) {
		ScrollItem item = items.get(i);
		if(item.errorType == null)
			item.errorType = ErrorType.INTERSECTION;
	}

	/** Upstream's per-frame validation: each field's number, then every pair of neighbours in sorted order. */
	public void validate() {
		for(int i = 0; i < items.size(); i++) {
			ScrollItem item = items.get(i);
			if(item.field != null) {
				item.errorType = validate(item.field.getValue());
				if(item.errorType == null) chambers.set(i, Integer.parseInt(item.field.getValue()));
			}
		}
		List<Integer> sorted = new ArrayList<>(chambers);
		sorted.sort(Comparator.naturalOrder());
		int lastR = 0;
		int lastI = 0;
		for(int i = 0; i < sorted.size(); i++) {
			int r = sorted.get(i);
			if(r - lastR <= 3) {
				putIntersectionError(i);
				putIntersectionError(lastI);
			}
			lastI = i;
			lastR = r;
		}
	}

	/** One field's error, or null: not a number, under 6, over 32 ({@link APRCoreTE#radiusError}). */
	public static ErrorType validate(String text) {
		String error = APRCoreTE.radiusError(text);
		return error == null ? null : ErrorType.valueOf(error);
	}

	@Override
	protected void drawGuiScreenBackgroundLayer(GuiGraphics g, float partialTicks, int mouseX, int mouseY) {
		drawDefaultBackground(g, mouseX, mouseY, partialTicks);
		g.setColor(1, 1, 1, 1);
		g.blit(TEXTURE, guiLeft, guiTop, 0, 0, xSize, ySize);
		int maxScroll = Math.max(0, items.size() - 4);
		int scrollBarPos = 0;
		if(maxScroll > 0) {
			if(scrollin)
				scrollPos = ScrollUtil._getScrollOffset(15, 98, mouseY - 9 - guiTop, maxScroll);
			scrollBarPos = ScrollUtil.getScrollBarPos(15, 98, ScrollUtil._getScrollRatio(scrollPos, maxScroll));
		}
		g.blit(TEXTURE, guiLeft + 175, guiTop + 9 + scrollBarPos, (maxScroll > 0) ? 232 : 244, 0, 12, 15);
		validate();
		forItems(this::updatePosition, mouseX, mouseY);
		forItems(this::renderItems, mouseX, mouseY);
	}
}
