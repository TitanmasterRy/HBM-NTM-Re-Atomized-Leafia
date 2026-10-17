package com.leafia.contents.gear.advisor.container;

import java.util.ArrayList;
import java.util.List;

import com.hbm.util.i18n.I18nUtil;
import com.leafia.contents.LeafiaItems;
import com.leafia.contents.gear.advisor.AdvisorItem;
import com.leafia.contents.gear.advisor.AdvisorItem.AdvisorPacket;
import com.leafia.contents.gear.advisor.AdvisorItem.AdvisorSignalType;
import com.leafia.dev.LeafiaUtil.ScrollUtil;
import com.leafia.dev.custompacket.LeafiaCustomPacket;
import com.leafia.dev.gui.FiaUIRect;
import com.leafia.dev.gui.GuiScreenLeafia;
import com.leafia.init.LeafiaSounds;
import com.leafia.lib.LeafiaRef;
import com.leafia.LeafiaMod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The advisor's main screen: a scrolling list of everything it has warned about, newest first,
 * with a Dismiss button and a Read button.
 *
 * <p>Upstream: {@code com/leafia/contents/gear/advisor/container/AdvisorUI.java}, a
 * {@code GuiScreenLeafia} with no container behind it - upstream tried a {@code DummyContainer}
 * and left the attempt commented out ("this sucks"), so this is a plain screen opened by the item
 * and the server never hears about it except through the packets below.
 *
 * <h2>The conversation</h2>
 *
 * Opening sends {@code SYNC_REQUEST}; the list is a "Loading..." panel until the server's
 * {@code SYNC_LOCAL} arrives. Dismiss sends {@code DISMISS} with the entry's index in the
 * server's oldest-first list, which is {@code size - 1 - row}. Leaving the screen for anything but
 * the Read screen sends {@code CLOSE}, so the server folds the lid back.
 *
 * <h2>Ported as upstream draws it</h2>
 *
 * 195 by 145, six rows of 162 by 18 at (8, 17), a scroll bar at (175, 18) that is 12 by 15 on a
 * 106-pixel track, and two 48 by 13 buttons at (90, 127) and (140, 127). Every coordinate and
 * both hover tints (0.9 grey) are upstream's. Two things changed because the toolkit did:
 * a bound texture is an argument to {@code blit}, and a shader colour (which is what
 * {@code GlStateManager.color} became) also tints text, so it is put back to white before every
 * string where 1.12.2's font renderer would have set its own.
 *
 * <p>Dead upstream and kept: {@code dismissCooldown} is only ever read, never set, so the
 * "pressed" frame of the Dismiss button (the sprite at 0,181) is never drawn.
 */
@OnlyIn(Dist.CLIENT)
public class AdvisorUI extends GuiScreenLeafia implements IAdvisorUI {

	/** Whichever of the three advisor screens is open, or null. Upstream's {@code instance}. */
	public static IAdvisorUI instance = null;

	public static final ResourceLocation TEXTURE = LeafiaRef.rl("textures/gui/advisor/main.png");

	public boolean loading = true;

	public AdvisorUI() {
		Minecraft.getInstance().getSoundManager()
				.play(SimpleSoundInstance.forUI(LeafiaSounds.advisor_activate.get(), 1));
		instance = this;
		xSize = 195;
		ySize = 145;
		requestSync();
	}

	/** Coming back from the Read screen: the list scrolled to where it was, no fanfare. */
	public AdvisorUI(int scroll) {
		this.scroll = scroll;
		instance = this;
		xSize = 195;
		ySize = 145;
		requestSync();
	}

	private static void requestSync() {
		send(new AdvisorPacket(AdvisorSignalType.SYNC_REQUEST.id));
	}

	/**
	 * Sends one of this screen's packets, unless there is no connection to send it on.
	 *
	 * <p>Upstream's screens send unconditionally. 1.21.1 throws when asked to send with no
	 * connection, and {@code removed()} is called as a screen is torn down on a disconnect - which
	 * is exactly when there is none - so the screens would turn a dropped connection into a
	 * second exception.
	 */
	static void send(AdvisorPacket packet) {
		if(Minecraft.getInstance().getConnection() == null) return;
		try {
			LeafiaCustomPacket.__start(packet).__sendToServer();
		} catch(RuntimeException e) {
			LeafiaMod.LOGGER.warn("Advisor packet {} not sent: {}", packet.signalType, e.toString());
		}
	}

	/** Leaving the advisor entirely: the server closes the cover. */
	static void sendClose() {
		send(new AdvisorPacket(AdvisorSignalType.CLOSE.id));
	}

	boolean screenSwitching = false;

	@Override
	public void removed() {
		if(screenSwitching) return;
		instance = null;
		sendClose();
		super.removed();
	}

	/** Moves every row to where the panel now is. */
	public void updateRects() {
		for(HistoryElement warning : warnings) {
			warning.rect.updateGuiPosition(this);
			warning.dirty = false;
		}
	}

	/** Replaces the list with the server's, which arrives oldest first. */
	public void setHistory(List<String> oldestFirst) {
		warnings.clear();
		List<String> rows = AdvisorItem.displayOrder(oldestFirst);
		for(int i = 0; i < rows.size(); i++)
			warnings.add(new HistoryElement(rows.get(i), i));
		updateRects();
		loading = false;
	}

	FiaUIRect scrollRect;
	FiaUIRect dismissRect;
	FiaUIRect readRect;
	int dismissCooldown = 0;

	private void drawButton(GuiGraphics graphics, String s, FiaUIRect rect) {
		int width = font.width(s);
		graphics.drawString(font, s, rect.guiLeft + rect.x + rect.w / 2 - width / 2, rect.guiTop + rect.y + 3, 4210752, false);
	}

	@Override
	protected void init() {
		super.init();
		scrollRect = new FiaUIRect(this, 175, 18, 12, 106);
		dismissRect = new FiaUIRect(this, 90, 127, 48, 13);
		readRect = new FiaUIRect(this, 140, 127, 48, 13);
		updateRects();
	}

	/** One notch of the wheel is one row, and only when there is somewhere to go. */
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int i = -(int) Math.clamp(scrollY, -1, 1);
		if(getMaxScroll() > 0) {
			scroll += i;
			scroll = Mth.clamp(scroll, 0, getMaxScroll());
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	boolean scrollin = false;

	@Override
	public boolean mouseClicked(double mouseXd, double mouseYd, int b) {
		super.mouseClicked(mouseXd, mouseYd, b);
		int x = (int) mouseXd;
		int y = (int) mouseYd;
		if(b == 0) {
			if(scrollRect.isMouseIn(x, y))
				scrollin = true;
			else if(dismissRect.isMouseIn(x, y)) {
				if(selection != -1) {
					playClick(1);
					AdvisorPacket packet = new AdvisorPacket(AdvisorSignalType.DISMISS.id);
					packet.dismissIndex = warnings.size() - 1 - selection;
					send(packet);
					selection = -1;
				} else
					playDenied();
			} else if(readRect.isMouseIn(x, y)) {
				if(selection != -1) {
					playClick(1);
					HistoryElement element = warnings.get(selection);
					screenSwitching = true;
					Minecraft.getInstance().setScreen(
							new AdvisorUIRead(I18nUtil.resolveKey(AdvisorItem.MSG_ROOT + element.key + ".message"), scroll));
				} else
					playDenied();
			} else
				selection = -1;
			for(int i = scroll; i <= scroll + 5; i++) {
				if(i < warnings.size()) {
					HistoryElement element = warnings.get(i);
					if(element.rect.isMouseIn(x, y + 18 * scroll)) {
						selection = i;
						playClick(1);
					}
				}
			}
		}
		return true;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if(button == 0)
			scrollin = false;
		return super.mouseReleased(mouseX, mouseY, button);
	}

	/** One row: the message key, and its position in the list (0 is the newest). */
	public static class HistoryElement {
		public final String key;
		public final FiaUIRect rect;
		public final int index;
		boolean dirty = true;

		public HistoryElement(String key, int index) {
			this.key = key;
			this.index = index;
			rect = new FiaUIRect(8, 17 + 18 * index, 162, 18);
		}
	}

	/** Newest first. */
	public List<HistoryElement> warnings = new ArrayList<>();
	int scroll = 0;
	int selection = -1;

	int getMaxScroll() {
		return warnings.size() - 6;
	}

	@Override
	protected void drawGuiScreenForegroundLayer(GuiGraphics graphics, int mouseX, int mouseY) {
		graphics.setColor(1, 1, 1, 1);
		String name = LeafiaItems.advisor.get().getDescription().getString();
		graphics.drawString(font, name, xSize / 2 - font.width(name) / 2, 5, 4210752, false);
	}

	@Override
	protected void drawGuiScreenBackgroundLayer(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {

		drawDefaultBackground(graphics, mouseX, mouseY, partialTicks);

		if(loading) graphics.setColor(0.9f, 0.9f, 0.9f, 1f);
		graphics.blit(TEXTURE, guiLeft, guiTop, 0, 0, xSize, ySize);
		graphics.setColor(1, 1, 1, 1);

		if(loading) {
			// Upstream draws at a half-pixel offset; a translation keeps it.
			float x = guiLeft + xSize / 2f - 0.5f - font.width("Loading...") / 2f;
			float y = guiTop + ySize / 2f - 6.5f;
			graphics.pose().pushPose();
			graphics.pose().translate(x, y, 0);
			graphics.drawString(font, "Loading...", 0, 0, 0xFFFFFF, true);
			graphics.pose().popPose();
			return;
		}

		int maxScroll = Math.max(getMaxScroll(), 0);
		scroll = Mth.clamp(scroll, 0, maxScroll);
		int scrollBarPos = 0;
		if(maxScroll > 0) {
			if(scrollin)
				scroll = ScrollUtil._getScrollOffset(15, 106, mouseY - 18 - guiTop, maxScroll);
			scrollBarPos = ScrollUtil.getScrollBarPos(15, 106, ScrollUtil._getScrollRatio(scroll, maxScroll));
		}
		graphics.blit(TEXTURE, guiLeft + 175, guiTop + 18 + scrollBarPos, (maxScroll > 0) ? 195 : 207, 0, 12, 15);

		if(dismissRect.isMouseIn(mouseX, mouseY))
			graphics.setColor(0.9f, 0.9f, 0.9f, 1f);
		if(dismissCooldown > 0) {
			dismissCooldown--;
			drawTexturedModalByFiaRect(graphics, TEXTURE, dismissRect, 0, 181);
		} else if(dismissRect.isMouseIn(mouseX, mouseY))
			drawTexturedModalByFiaRect(graphics, TEXTURE, dismissRect, 90, 127);

		graphics.setColor(1, 1, 1, 1);
		if(readRect.isMouseIn(mouseX, mouseY)) {
			graphics.setColor(0.9f, 0.9f, 0.9f, 1f);
			drawTexturedModalByFiaRect(graphics, TEXTURE, readRect, 140, 127);
		}

		graphics.setColor(1, 1, 1, 1);
		for(int i = scroll; i <= scroll + 5; i++) {
			if(i < warnings.size()) {
				HistoryElement element = warnings.get(i);
				if(element.rect.isMouseIn(mouseX, mouseY + 18 * scroll))
					graphics.setColor(0.9f, 0.9f, 0.9f, 1f);
				graphics.blit(TEXTURE,
						element.rect.guiLeft + element.rect.x, element.rect.guiTop + element.rect.y - scroll * 18,
						0, (selection == i) ? 163 : 145, 162, 18);
				graphics.setColor(1, 1, 1, 1);
				graphics.drawString(font,
						I18nUtil.resolveKey(AdvisorItem.MSG_ROOT + element.key + ".name"),
						element.rect.guiLeft + element.rect.x + 5, element.rect.guiTop + element.rect.y + 5 - scroll * 18,
						0xa6a6a6, true);
			}
		}
		drawButton(graphics, I18nUtil.resolveKey("item.advisor.gui.main.dismiss"), dismissRect);
		drawButton(graphics, I18nUtil.resolveKey("item.advisor.gui.main.read"), readRect);
	}
}
