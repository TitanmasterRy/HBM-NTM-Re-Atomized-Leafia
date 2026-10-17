package com.leafia.contents.machines.controlpanel.core.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.hbm.items.tool.ItemMultiDetonator;
import com.leafia.contents.machines.controlpanel.core.ControlLinks;
import com.leafia.contents.machines.controlpanel.core.IControllable;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * "Create Links": what the control talks to. A multi-detonator in the linker slot, accepted with "&gt;", puts every machine it
 * has stored on the list (a part of a multiblock stands for its core); each row has a tag, which is the name the control's
 * graphs use for that machine, and an "X" to drop it. Adapted from NTM-CE's {@code SubElementLinker} (CCP-EDITOR, D-3066).
 *
 * <p>What a link means to the server is not decided here. The server accepts a new link only if it is within the link radius of
 * the panel and the sender has a multi-detonator that names it (D-3063); the editor lists only what the detonator named.
 *
 * <p>Differences from CE: the machine at a stored position is found by the capability seam as well as by the block entity itself
 * ({@link ControlLinks#controllableAt}), so the adapters of batch 8 are linkable; "Continue" goes to the event editor (CCP-NODE-EDITOR,
 * D-3067), which a new control gets its default graphs from first, as in CE; a tag is
 * at most 32 characters (a text box's default, which CE also had).
 */
@OnlyIn(Dist.CLIENT)
public class SubElementLinker extends SubElement {

	public static final ResourceLocation inv_tex = GuiDraw.texture("gui_linker_add_element");

	public Button clear;
	public Button accept;
	public Button pageLeft;
	public Button pageRight;
	public Button cont;
	public Button back;

	public List<GuiLinkerButton> linkedButtons = new ArrayList<>();
	public Map<GuiLinkerButton, BlockPos> linkerToPosMap = new HashMap<>();
	public final Set<BlockPos> linkedPositions = new LinkedHashSet<>();
	public final Map<BlockPos, String> tags = new LinkedHashMap<>();
	public int numPages = 1;
	public int currentPage = 1;
	public boolean invalid = false;
	boolean lastEnable = true;

	public SubElementLinker(ControlEditScreen gui) {
		super(gui);
	}

	@Override
	public void init() {
		int cX = gui.width / 2;
		int cY = gui.height / 2;
		back = button(gui.getGuiLeft() + 219, gui.getGuiTop() + 13, 30, 20, "Back");
		clear = button(cX - 121, cY - 93, 40, 20, "Clear");
		accept = button(cX - 101, cY - 116, 20, 20, ">");
		pageLeft = button(cX - 60, cY - 16, 20, 20, "<");
		pageRight = button(cX + 90, cY - 16, 20, 20, ">");
		cont = button(cX - 60, cY + 6, 170, 20, "Continue");

		super.init();
		refreshButtons();
	}

	private ItemStack linkerStack() {
		return gui.getMenu().input.getItem();
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		int cX = gui.width / 2;
		int cY = gui.height / 2;

		ItemStack stack = linkerStack();
		accept.active = lastEnable && !stack.isEmpty() && stack.getItem() instanceof ItemMultiDetonator;

		String text = currentPage + "/" + numPages;
		g.drawString(gui.font(), text, cX + 16, cY - 10, 0xFF777777, false);
		text = "Create Links";
		g.drawString(gui.font(), text, cX - gui.font().width(text) / 2F + 10, cY - 110, 0xFF777777, false);
	}

	@Override
	public void renderBackground(GuiGraphics g) {
		g.blit(inv_tex, gui.getGuiLeft(), gui.getGuiTop(), 0, 0, gui.getXSize(), gui.getYSize());
	}

	private void recalculateVisibleButtons() {
		for(GuiLinkerButton b : linkedButtons)
			b.setShown(false);
		int idx = (currentPage - 1) * 3;
		for(int i = idx; i < idx + 3; i++) {
			if(i >= linkedButtons.size())
				break;
			linkedButtons.get(i).setShown(lastEnable);
		}
		boolean showPaging = numPages > 1;
		GuiDraw.show(pageLeft, lastEnable && showPaging && currentPage > 1);
		GuiDraw.show(pageRight, lastEnable && showPaging && currentPage < numPages);
	}

	@Override
	public void actionPerformed(Button button) {
		Level world = gui.control.getLevel();

		if(button == accept) {
			ItemStack stack = linkerStack();
			if(!stack.isEmpty() && stack.getItem() instanceof ItemMultiDetonator) {
				List<BlockPos> locs = ItemMultiDetonator.getLocations(stack);
				if(locs != null) {
					for(BlockPos stored : locs) {
						BlockPos core = ControlLinks.resolveCore(world, stored);
						IControllable controllable = ControlLinks.controllableAt(world, core);
						if(controllable != null) {
							BlockPos p = controllable.getControlPos();
							linkedPositions.add(p);
							tags.putIfAbsent(p, formatLinkLabel(p));
						}
					}
					refreshButtons();
					gui.returnControlInputToPlayerInventory();
				}
			}
		} else if(button == clear) {
			linkedPositions.clear();
			tags.clear();
			refreshButtons();
		} else if(button == back) {
			gui.returnControlInputToPlayerInventory();
			gui.popElement();
		} else if(button == cont) {
			if(!invalid) {
				syncCurrentEditControlConnections();
				gui.returnControlInputToPlayerInventory();
				// CE: the event editor reads what the linked machines can send and receive, and a new control gets its default graphs
				// now, so there is something to open in the node editor.
				gui.eventEditor.accumulateEventTypes(getLinked());
				gui.eventEditor.populateDefaultNodes();
				gui.pushElement(gui.eventEditor);
			}
		} else if(button == pageLeft) {
			currentPage = Math.max(1, currentPage - 1);
			recalculateVisibleButtons();
		} else if(button == pageRight) {
			currentPage = Math.min(numPages, currentPage + 1);
			recalculateVisibleButtons();
		} else {
			for(Entry<GuiLinkerButton, BlockPos> e : linkerToPosMap.entrySet()) {
				if(e.getKey() == button) {
					linkedPositions.remove(e.getValue());
					tags.remove(e.getValue());
					refreshButtons();
					return;
				}
			}
		}
	}

	/** The machines on the list that answer right now. */
	public List<IControllable> getLinked() {
		Level world = gui.control.getLevel();
		List<IControllable> list = new ArrayList<>();
		for(BlockPos p : linkedPositions) {
			IControllable c = ControlLinks.controllableAt(world, p);
			if(c != null)
				list.add(c);
		}
		return list;
	}

	public void reloadLinkedFromCurrentEditControl() {
		linkedPositions.clear();
		tags.clear();
		if(gui.currentEditControl == null) {
			refreshButtons();
			return;
		}

		for(Entry<String, BlockPos> entry : gui.currentEditControl.taggedLinks.entrySet()) {
			linkedPositions.add(entry.getValue());
			tags.put(entry.getValue(), entry.getKey());
		}
		refreshButtons();
	}

	public void syncCurrentEditControlConnections() {
		if(gui.currentEditControl == null)
			return;
		gui.currentEditControl.taggedLinks.clear();
		for(Entry<BlockPos, String> entry : tags.entrySet())
			gui.currentEditControl.taggedLinks.put(entry.getValue(), entry.getKey());
	}

	protected void refreshButtons() {
		for(GuiLinkerButton b : linkedButtons) {
			gui.removeWidgetFromScreen(b);
			gui.removeWidgetFromScreen(b.field);
		}
		linkedButtons.clear();
		linkerToPosMap.clear();
		int i = 0;
		int cX = gui.width / 2;
		int cY = gui.height / 2;
		for(BlockPos pos : linkedPositions) {
			GuiLinkerButton button = new GuiLinkerButton(gui.font(), cX - 73, cY - 90 + i * 22, 170, 20, tags.getOrDefault(pos, "ERROR"), b -> {
				SubElement top = gui.top();
				if(!top.lock)
					top.actionPerformed(b);
			});
			linkedButtons.add(button);
			linkerToPosMap.put(button, pos);
			i = (i + 1) % 3;
		}
		for(GuiLinkerButton b : linkedButtons) {
			gui.addWidget(b);
			gui.addWidget(b.field);
		}
		numPages = Math.max(1, (linkedPositions.size() + 2) / 3);
		currentPage = Mth.clamp(currentPage, 1, numPages);
		recalculateVisibleButtons();
	}

	private static String formatLinkLabel(BlockPos pos) {
		return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
	}

	@Override
	public void update() {
		invalid = false;
		tags.clear();
		Set<String> seenTags = new HashSet<>();
		Set<String> invalidTags = new HashSet<>();
		for(GuiLinkerButton b : linkedButtons) {
			BlockPos p = linkerToPosMap.get(b);
			String tag = b.field.getValue();
			b.field.setTextColor(0xFFFFFF);
			if(!seenTags.add(tag)) {
				invalid = true;
				invalidTags.add(tag);
			} else {
				tags.put(p, tag);
			}
		}
		for(GuiLinkerButton b : linkedButtons) {
			if(invalidTags.contains(b.field.getValue()))
				b.field.setTextColor(0xFF0000);
		}
		cont.active = lastEnable && !invalid;
	}

	@Override
	public void enableButtons(boolean enable) {
		lastEnable = enable;
		if(enable) {
			recalculateVisibleButtons();
		} else {
			for(GuiLinkerButton b : linkedButtons)
				b.setShown(false);
		}
		GuiDraw.show(clear, enable);
		GuiDraw.show(accept, enable);
		if(enable) {
			GuiDraw.show(pageLeft, numPages > 1 && currentPage > 1);
			GuiDraw.show(pageRight, numPages > 1 && currentPage < numPages);
		} else {
			GuiDraw.show(pageLeft, false);
			GuiDraw.show(pageRight, false);
		}
		GuiDraw.show(cont, enable);
		cont.active = enable && !invalid;
		GuiDraw.show(back, enable);
		gui.setSlotsEnabled(enable);
	}
}
