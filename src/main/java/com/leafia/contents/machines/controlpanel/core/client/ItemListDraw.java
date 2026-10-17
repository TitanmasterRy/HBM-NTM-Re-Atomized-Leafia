package com.leafia.contents.machines.controlpanel.core.client;

import com.leafia.contents.machines.controlpanel.core.ItemList;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The client half of NTM-CE's {@code ItemList}: drawing a pop-up list and handing it a click (CCP-EDITOR, D-3066; the common half
 * is batch 2's {@link ItemList}). The list is drawn in whatever space the pose is in, with rows 6 units apart and the text at
 * 0.4 scale, as CE did; the node editor of batch 7 draws its dropdowns through this under its own zoom.
 *
 * <p>Colours are the list's own fields (CE's 0.5 grey at half alpha by default, lighter on the row under the mouse; a node's
 * dropdown restyles its list), which are plain data on the common {@link ItemList} since D-3067. The click is the common
 * list's too, now; it is kept here as a pass-through for the callers that already use this name.
 */
@OnlyIn(Dist.CLIENT)
public final class ItemListDraw {

	private ItemListDraw() {}

	public static final int TEXT_COLOR = 0xFFAFAFAF;

	public static void render(GuiGraphics g, ItemList list, float mouseX, float mouseY) {
		if(list.isClosed)
			return;
		if(list.child != null)
			render(g, list.child, mouseX, mouseY);

		GuiDraw.rect(g, list.posX, list.posY, list.posX + list.width, list.posY + list.itemNames.size() * 6 + 4,
				GuiDraw.argb(list.r, list.g, list.b, list.alpha));
		int idx = list.getMouseoverIndex(mouseX, mouseY);
		if(idx != -1)
			GuiDraw.rect(g, list.posX, list.posY + idx * 6 + 2, list.posX + list.width, list.posY + idx * 6 + 7,
					GuiDraw.argb(list.r + 0.1F, list.g + 0.1F, list.b + 0.1F, list.alpha));

		g.pose().pushPose();
		g.pose().translate(list.posX, list.posY, 0);
		g.pose().scale(0.4F, 0.4F, 1);
		g.pose().translate(-list.posX, -list.posY, 0);
		float y = list.posY + 7;
		Font font = Minecraft.getInstance().font;
		for(String s : list.itemNames) {
			if(s.startsWith("{expandable}")) {
				g.drawString(font, ">", list.posX + list.width * 2 - 6, y, list.textColor, false);
				s = s.substring(12);
			}
			g.drawString(font, s, list.posX + 2, y, list.textColor, false);
			y += 15;
		}
		g.flush();
		g.pose().popPose();
	}

	/** A click at this point in the list's space. True if it picked an entry that was final (no sub-list opened). */
	public static boolean mouseClicked(ItemList list, float x, float y) {
		return list.mouseClicked(x, y);
	}
}
