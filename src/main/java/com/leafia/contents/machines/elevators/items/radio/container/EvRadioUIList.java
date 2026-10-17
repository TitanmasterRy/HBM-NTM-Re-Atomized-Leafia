package com.leafia.contents.machines.elevators.items.radio.container;

import com.leafia.contents.LeafiaItems;
import com.leafia.contents.machines.elevators.items.radio.EvRadioItem.ElevatorMusic;
import com.leafia.dev.LeafiaUtil.ScrollUtil;
import com.leafia.dev.gui.FiaUIRect;
import com.leafia.dev.gui.GuiScreenLeafia;
import com.leafia.lib.LeafiaRef;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The radio's track list: "None" and every {@link ElevatorMusic}, as rows of 223 by 18, with a
 * scroll bar for when there are more than nine.
 *
 * <p>Upstream: {@code .../radio/container/EvRadioUIList.java}. Opened by one of the main screen's
 * three Change buttons with that button's {@code target} (0 up, 1 down, 2 idle). Clicking a row
 * stores its ordinal (row 0, "None", is -1; row {@code i} is ordinal {@code i - 1}) in the
 * target's slot and goes back; Escape and the inventory key go back without choosing.
 * <b>Going back always sends nothing</b> - the main screen is rebuilt with the choices in hand and
 * it is the one that sends them when it closes.
 *
 * <p>There are two tracks, so three rows and a maximum scroll of {@code 2 - 9 = -7}: the scroll
 * bar is drawn in its disabled frame and nothing can scroll. The scroll arithmetic is upstream's
 * all the same, <b>including a latent defect</b>: a row's hover and click area is computed at
 * {@code guiTop + 17 + i * 18} without the scroll offset the row is drawn at, so with more than
 * nine tracks a scrolled list would highlight and select the wrong row. Unreachable with two
 * tracks. Flagged in D-1088, not fixed.
 *
 * <p>Not upstream's: closing this screen by some other route (the player dies, the connection
 * drops) sends the choices directly. Upstream's {@code onGuiClosed} opens a new main screen for
 * the same effect, and in 1.21.1 opening a screen from inside another's {@code removed()} is
 * overwritten by the call that is still in progress.
 */
@OnlyIn(Dist.CLIENT)
public class EvRadioUIList extends GuiScreenLeafia {

	public static final ResourceLocation TEXTURE = LeafiaRef.rl("textures/gui/elevators/radio_change.png");

	public int upOrdinal = -1;
	public int downOrdinal = -1;
	public int idleOrdinal = -1;
	public final int target;

	private FiaUIRect scrollRect;
	private int scroll = 0;
	private boolean scrollin = false;
	private boolean screenSwitching = false;

	public EvRadioUIList(int target, int upOrdinal, int downOrdinal, int idleOrdinal) {
		xSize = 256;
		ySize = 184;
		this.target = target;
		this.upOrdinal = upOrdinal;
		this.downOrdinal = downOrdinal;
		this.idleOrdinal = idleOrdinal;
	}

	/** Upstream's {@code getMaxScroll}: nine rows fit. */
	static int getMaxScroll() {
		return ElevatorMusic.values().length - 9;
	}

	@Override
	protected void init() {
		super.init();
		scrollRect = new FiaUIRect(this, 236, 18, 12, 160);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int i = -(int) Math.signum(scrollY);
		if(getMaxScroll() > 0) {
			scroll += i;
			scroll = Mth.clamp(scroll, 0, getMaxScroll());
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int b) {
		boolean handled = super.mouseClicked(mouseX, mouseY, b);
		if(b == 0) {
			if(scrollRect.isMouseIn((int) mouseX, (int) mouseY))
				scrollin = true;
			for(int i = scroll; i <= scroll + 8; i++) {
				if(i < ElevatorMusic.values().length + 1) {
					int x = guiLeft + 8;
					int y = guiTop + 17 + i * 18;
					boolean isHovered = mouseX >= x && mouseY >= y && mouseX < x + 223 && mouseY < y + 18;
					if(isHovered) {
						if(target == 0)
							upOrdinal = i - 1;
						else if(target == 1)
							downOrdinal = i - 1;
						else if(target == 2)
							idleOrdinal = i - 1;
						playClick(1);
						goBack();
						return true;
					}
				}
			}
		}
		return handled;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if(button == 0)
			scrollin = false;
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public void removed() {
		if(screenSwitching) return;
		screenSwitching = true;
		EvRadioUI.instance = null;
		EvRadioUI.sendChoices(upOrdinal, downOrdinal, idleOrdinal);
		super.removed();
	}

	private void goBack() {
		screenSwitching = true;
		Minecraft.getInstance().setScreen(new EvRadioUI(upOrdinal, downOrdinal, idleOrdinal));
	}

	/** Upstream's {@code keyTyped}: Escape and the inventory key go back, and every other key does nothing. */
	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if(keyCode == 256 || this.minecraft.options.keyInventory.matches(keyCode, scanCode)) {
			goBack();
		}
		return true;
	}

	@Override
	protected void drawGuiScreenForegroundLayer(GuiGraphics graphics, int mouseX, int mouseY) {
		graphics.setColor(1, 1, 1, 1);
		Component name = LeafiaItems.ev_radio.get().getDescription();
		graphics.drawString(font, name, xSize / 2 - font.width(name) / 2, 5, 4210752, false);
	}

	@Override
	protected void drawGuiScreenBackgroundLayer(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
		drawDefaultBackground(graphics, mouseX, mouseY, partialTicks);
		graphics.setColor(1, 1, 1, 1);
		graphics.blit(TEXTURE, guiLeft, guiTop, 0, 0, xSize, ySize);

		int maxScroll = Math.max(getMaxScroll(), 0);
		scroll = Mth.clamp(scroll, 0, maxScroll);
		int scrollBarPos = 0;
		if(maxScroll > 0) {
			if(scrollin)
				scroll = ScrollUtil._getScrollOffset(15, 160, mouseY - 18 - guiTop, maxScroll);
			scrollBarPos = ScrollUtil.getScrollBarPos(15, 160, ScrollUtil._getScrollRatio(scroll, maxScroll));
		}
		graphics.blit(TEXTURE, guiLeft + 236, guiTop + 18 + scrollBarPos, (maxScroll > 0) ? 0 : 12, 220, 12, 15);

		for(int i = scroll; i <= scroll + 8; i++) {
			if(i < ElevatorMusic.values().length + 1) {
				int x = guiLeft + 8;
				int y = guiTop + 17 + i * 18;
				String s = "None";
				if(i >= 1) {
					ElevatorMusic element = ElevatorMusic.values()[i - 1];
					s = element.artist + " - " + element.title;
				}
				boolean isHovered = mouseX >= x && mouseY >= y && mouseX < x + 223 && mouseY < y + 18;
				graphics.blit(TEXTURE, x, y - scroll * 18, 0, isHovered ? 202 : 184, 223, 18);
				graphics.drawString(font, s, x + 5, y + 5 - scroll * 18, 0xFFFFFF, true);
			}
		}
	}
}
