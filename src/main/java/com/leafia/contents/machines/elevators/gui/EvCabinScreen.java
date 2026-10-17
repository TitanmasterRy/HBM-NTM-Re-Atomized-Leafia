package com.leafia.contents.machines.elevators.gui;

import java.util.ArrayList;
import java.util.List;

import com.hbm.util.i18n.I18nUtil;
import com.leafia.contents.machines.elevators.car.ElevatorEntity;
import com.leafia.contents.machines.elevators.car.ElevatorEntity.BellButton;
import com.leafia.contents.machines.elevators.car.ElevatorEntity.CloseButton;
import com.leafia.contents.machines.elevators.car.ElevatorEntity.ElevatorButton;
import com.leafia.contents.machines.elevators.car.ElevatorEntity.EvButtonModifyPacket;
import com.leafia.contents.machines.elevators.car.ElevatorEntity.FireButton;
import com.leafia.contents.machines.elevators.car.ElevatorEntity.FloorButton;
import com.leafia.contents.machines.elevators.car.ElevatorEntity.OpenButton;
import com.leafia.dev.custompacket.LeafiaCustomPacket;
import com.leafia.dev.gui.FiaUIRect;
import com.leafia.dev.gui.GuiScreenLeafia;
import com.leafia.lib.LeafiaRef;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The elevator cabin's editor: eight slots on the left, the button layout on the right, and three
 * text fields to rename the selected button.
 *
 * <p>Upstream: {@code .../elevators/gui/EvCabinGUI.java}, a {@code LCEGuiInfoContainer}. That base
 * is not ported and was not needed: nothing here uses what it adds over a plain container screen
 * (the tooltips are vanilla's, and the two sounds are {@link GuiScreenLeafia#click} and
 * {@link GuiScreenLeafia#denied}). It is an {@link AbstractContainerScreen} over
 * {@link EvCabinMenu}, 176 by 173, on {@code cab_002.png}; every coordinate is upstream's.
 *
 * <h2>How to read the right-hand side</h2>
 *
 * The cabin's wall panel is a grid three screen pixels to a button unit, with unit (0, 0) at
 * (92, 80) and y pointing <b>up</b>: {@link #getButtonUIX} and {@link #getButtonUIY} are the whole
 * mapping, and the drag handler inverts it with integer division (x) and a rounded float (y),
 * both as upstream wrote them. Click a button to select it, click it again to pick it up, and
 * release to drop it - the move is sent on release. The three fields edit the selected
 * <em>floor</em> button's floor number, its indicator text and its label, each saved by the 18
 * pixel square beside it; the add and remove buttons below add one of five button kinds (from an
 * overlay menu) or delete the selected one.
 *
 * <h2>Things carried over that look like mistakes</h2>
 *
 * <ul>
 * <li>The press highlights and the add-menu's ten-step close timer count down <b>per frame</b>,
 *     in the background layer, not per tick - upstream's arrangement.</li>
 * <li>{@code lastLength} is only ever written inside the guard that tests it, so that guard
 *     (reset a dragged button when the list shrinks under it) is dead code upstream and dead
 *     here. Kept so the file diffs against the original.</li>
 * <li>The three save buttons bound-check the selection with
 *     {@code buttons.size() < buttonSelection}, which lets a selection equal to the size through
 *     to {@code buttons.get}. Unreachable in practice, since a selection is only ever set from a
 *     button that is in the list.</li>
 * </ul>
 *
 * <h2>One deliberate change</h2>
 *
 * Upstream's {@code initGui} adds the five add-menu rectangles to a list that it never clears, so
 * resizing the window leaves the menu hit boxes at their old position. {@link #init} clears the
 * list first. It is client layout, not behaviour.
 */
@OnlyIn(Dist.CLIENT)
public class EvCabinScreen extends AbstractContainerScreen<EvCabinMenu> {

	static final ResourceLocation TEX = LeafiaRef.rl("textures/gui/elevators/cab_002.png");

	final ElevatorEntity entity;

	final List<FiaUIRect> addMenuRects = new ArrayList<>();
	static final String[] addMenuButtons = new String[]{"Floor", "Fire", "Open", "Close", "Bell"};
	FiaUIRect addBtn;
	FiaUIRect rmBtn;
	int addBtnTimer = 0;
	int rmBtnTimer = 0;
	boolean renderAddMenu = false;
	int addMenuTimer = 0;
	int addMenuSelection = -1;
	int buttonSelection = -1;
	boolean dragging = false;

	private final EditBox[] fields = new EditBox[3];
	private final FiaUIRect[] fieldButtons = new FiaUIRect[3];
	private final int[] fieldButtonTicks = new int[3];

	int lastLength = -1;
	int startX = 0;
	int startY = 0;
	ElevatorButton buttonToReset = null;

	public EvCabinScreen(EvCabinMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		this.entity = menu.entity;
		this.imageWidth = 176;
		this.imageHeight = 173;
	}

	@Override
	protected void init() {

		super.init();

		// The menu could not find its cabin on this client: there is nothing to edit.
		if(entity == null) {
			onClose();
			return;
		}

		int spacing = 15;
		int yOffset = addMenuButtons.length * spacing / 2;

		addMenuRects.clear();
		for(int i = 0; i < addMenuButtons.length; i++)
			addMenuRects.add(new FiaUIRect(this, imageWidth / 2 - 40, imageHeight / 2 - yOffset + spacing * i, 80, 13));

		for(int z = 0; z < 3; z++) {
			fields[z] = new EditBox(this.font, leftPos + 120, topPos + 11 + 19 * z, 29, 9, Component.empty());
			fields[z].setTextColor(0x5BBC00);
			fields[z].setTextColorUneditable(0x499500);
			fields[z].setBordered(false);
			// Not renderable widgets: renderBg draws them, at the point upstream's background layer did.
			addWidget(fields[z]);
			fieldButtons[z] = new FiaUIRect(this, 152, 5 + 19 * z, 18, 18);
		}

		addBtn = new FiaUIRect(this, 114, 62, 27, 13);
		rmBtn = new FiaUIRect(this, 143, 62, 27, 13);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {

		if(entity == null) return;

		if(!renderAddMenu) {

			super.render(graphics, mouseX, mouseY, partialTick);

			if(entity.buttons.size() < lastLength) {
				if(buttonToReset != null) {
					buttonToReset.x = startX;
					buttonToReset.y = startY;
					buttonToReset = null;
				}
				buttonSelection = -1;
				dragging = false;
				lastLength = entity.buttons.size();
			}

			if(dragging) {
				ElevatorButton button = entity.buttons.get(buttonSelection);
				int x = (mouseX - 92 - leftPos) / 3;
				int y = Math.round((78.5f - (mouseY - topPos)) / 3);
				button.x = x;
				button.y = y;
			} else {
				renderTooltip(graphics, mouseX, mouseY);

				if(fieldButtons[0].isMouseIn(mouseX, mouseY))
					hover(graphics, I18nUtil.resolveKey("gui.evcab.floor"), mouseX, mouseY);
				if(fieldButtons[1].isMouseIn(mouseX, mouseY))
					hover(graphics, I18nUtil.resolveKey("gui.evcab.floordisplay"), mouseX, mouseY);
				if(fieldButtons[2].isMouseIn(mouseX, mouseY))
					hover(graphics, I18nUtil.resolveKey("gui.evcab.label"), mouseX, mouseY);
				if(addBtn.isMouseIn(mouseX, mouseY))
					hover(graphics, I18nUtil.resolveKey("gui.evcab.add"), mouseX, mouseY);
				if(rmBtn.isMouseIn(mouseX, mouseY))
					hover(graphics, I18nUtil.resolveKey("gui.evcab.rm"), mouseX, mouseY);

				for(int i = 0; i < entity.buttons.size(); i++) {
					ElevatorButton button = entity.buttons.get(i);
					FiaUIRect rect = getButtonRect(button.x, button.y);
					if(rect.isMouseIn(mouseX, mouseY))
						hover(graphics, buttonName(button), mouseX, mouseY);
				}
			}

		} else {

			// Upstream: the background layer (which dims), a second dim, then the menu.
			renderBackground(graphics, mouseX, mouseY, partialTick);
			renderTransparentBackground(graphics);

			for(int i = 0; i < addMenuButtons.length; i++) {
				FiaUIRect rect = addMenuRects.get(i);
				String s = addMenuButtons[i];
				if(s.equals("Fire")) s = s + " Service";
				else s = s + " Button";
				graphics.blit(TEX, rect.absX(), rect.absY(), 176, addMenuSelection == i ? 13 : 0, rect.w, rect.h);
				graphics.drawString(this.font, s, rect.absX() + rect.w / 2 - this.font.width(s) / 2, rect.absY() + 3, 4210752, false);
			}

			if(addMenuSelection >= 0) {
				addMenuTimer++;
				if(addMenuTimer >= 10) {
					addMenuTimer = 0;
					addMenuSelection = -1;
					renderAddMenu = false;
				}
			}
		}
	}

	/** What a button's tooltip says. Upstream's strings, including "Error!" for a kind it does not know. */
	static String buttonName(ElevatorButton button) {
		if(button instanceof FloorButton floor) return floor.label;
		else if(button instanceof OpenButton) return "Open";
		else if(button instanceof CloseButton) return "Close";
		else if(button instanceof BellButton) return "Bell";
		else if(button instanceof FireButton) return "Fire Service";
		return "Error!";
	}

	private void hover(GuiGraphics graphics, String text, int mouseX, int mouseY) {
		graphics.renderTooltip(this.font, Component.literal(text), mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(double x, double y, int mouseButton) {

		int mouseX = (int) x;
		int mouseY = (int) y;

		if(entity == null) return super.mouseClicked(x, y, mouseButton);

		if(!renderAddMenu) {

			boolean handled = super.mouseClicked(x, y, mouseButton);

			if(mouseButton == 0) {
				if(!dragging) {

					if(addBtn.isMouseIn(mouseX, mouseY) && addBtnTimer <= 0) {
						addBtnTimer = 20;
						renderAddMenu = true;
						GuiScreenLeafia.click(1);
					}

					if(rmBtn.isMouseIn(mouseX, mouseY)) {
						if(buttonSelection != -1) {
							rmBtnTimer = 20;
							GuiScreenLeafia.click(1);
							EvButtonModifyPacket packet = new EvButtonModifyPacket();
							packet.localEntity = entity;
							packet.localMode = 2;
							packet.localTarget = buttonSelection;
							LeafiaCustomPacket.__start(packet).__sendToServer();
							buttonSelection = -1;
						} else GuiScreenLeafia.denied();
					}

					for(int i = 0; i < entity.buttons.size(); i++) {
						ElevatorButton button = entity.buttons.get(i);
						FiaUIRect rect = new FiaUIRect(this, getButtonUIX(button.x), getButtonUIY(button.y), 6, 3);
						if(rect.isMouseIn(mouseX, mouseY)) {
							if(buttonSelection != i) {
								buttonSelection = i;
								if(button instanceof FloorButton floor) {
									fields[0].setValue(Integer.toString(floor.floor));
									fields[1].setValue(entity.specialDisplayFloors.getOrDefault(floor.floor, Integer.toString(floor.floor)));
									fields[2].setValue(floor.label);
								} else {
									fields[0].setValue("");
									fields[1].setValue("");
									fields[2].setValue("");
								}
								GuiScreenLeafia.click(1.5f);
							} else {
								dragging = true;
								buttonToReset = button;
								startX = button.x;
								startY = button.y;
							}
							return true;
						}
					}

					boolean savePressed = false;
					for(int i = 0; i < 3; i++) {
						if(fieldButtons[i].isMouseIn(mouseX, mouseY)) {
							if(buttonSelection == -1 || entity.buttons.size() < buttonSelection
									|| !(entity.buttons.get(buttonSelection) instanceof FloorButton)) {
								GuiScreenLeafia.denied();
								break;
							}
							savePressed = true;
							EvButtonModifyPacket packet = new EvButtonModifyPacket();
							packet.localEntity = entity;
							packet.localMode = 3;
							packet.localTarget = buttonSelection;
							packet.localProperty = i;
							boolean doSend = true;
							if(i == 0) {
								try {
									packet.localFloor = Integer.parseInt(fields[0].getValue());
								} catch(NumberFormatException ignored) {
									doSend = false;
								}
							} else
								packet.localLabel = fields[i].getValue();
							if(doSend) {
								GuiScreenLeafia.click(1);
								fieldButtonTicks[i] = 20;
								LeafiaCustomPacket.__start(packet).__sendToServer();
							} else
								GuiScreenLeafia.denied();
						}
					}

					boolean fieldPressed = false;
					for(EditBox field : fields)
						fieldPressed = field.mouseClicked(x, y, mouseButton) || fieldPressed;

					if(!savePressed && !fieldPressed) {
						buttonSelection = -1;
						fields[0].setValue("");
						fields[1].setValue("");
						fields[2].setValue("");
					}
				}
			}

			return handled;

		} else if(mouseButton == 0 && addMenuSelection < 0) {

			for(int i = 0; i < addMenuButtons.length; i++) {
				if(addMenuRects.get(i).isMouseIn(mouseX, mouseY)) {
					addMenuSelection = i;
					GuiScreenLeafia.click(1);
					EvButtonModifyPacket packet = new EvButtonModifyPacket();
					packet.localEntity = entity;
					packet.localMode = 1;
					packet.localButtonType = i;
					LeafiaCustomPacket.__start(packet).__sendToServer();
					return true;
				}
			}

			if(addMenuSelection < 0)
				renderAddMenu = false;
		}

		return true;
	}

	@Override
	public boolean mouseReleased(double x, double y, int mouseButton) {

		boolean handled = super.mouseReleased(x, y, mouseButton);

		if(dragging) {
			dragging = false;
			buttonToReset = null;
			ElevatorButton button = entity.buttons.get(buttonSelection);
			EvButtonModifyPacket packet = new EvButtonModifyPacket();
			packet.localEntity = entity;
			packet.localMode = 0;
			packet.localTarget = buttonSelection;
			packet.localX = button.x;
			packet.localY = button.y;
			LeafiaCustomPacket.__start(packet).__sendToServer();
		}

		return handled;
	}

	/** Upstream draws no title and no inventory label. */
	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) { }

	int getButtonUIX(int btnX) {
		return 92 + btnX * 3 - 3;
	}

	int getButtonUIY(int btnY) {
		return 80 - btnY * 3 - 3;
	}

	FiaUIRect getButtonRect(int x, int y) {
		return new FiaUIRect(this, getButtonUIX(x), getButtonUIY(y), 6, 3);
	}

	void renderButtons(GuiGraphics graphics, int ox, int oy) {
		for(int i = 0; i < entity.buttons.size(); i++) {
			ElevatorButton button = entity.buttons.get(i);
			graphics.blit(TEX, ox + getButtonUIX(button.x), oy + getButtonUIY(button.y),
					176 + (i == buttonSelection ? 6 : 0), 39, 6, 3);
		}
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {

		graphics.blit(TEX, leftPos, topPos, 0, 0, imageWidth, imageHeight);

		for(int z = 0; z < 3; z++) {
			if(fields[z].isFocused())
				graphics.blit(TEX, leftPos + 116, topPos + 7 + 19 * z, 188, 39, 32, 14);
		}

		for(EditBox field : fields)
			field.render(graphics, mouseX, mouseY, partialTicks);

		for(int i = 0; i < 3; i++) {
			if(fieldButtonTicks[i] > 0) {
				fieldButtonTicks[i]--;
				graphics.blit(TEX, fieldButtons[i].absX(), fieldButtons[i].absY(), 230, 26, 18, 18);
			}
		}

		if(addBtnTimer > 0) {
			addBtnTimer--;
			graphics.blit(TEX, addBtn.absX(), addBtn.absY(), 176, 26, addBtn.w, addBtn.h);
		}

		if(rmBtnTimer > 0) {
			rmBtnTimer--;
			graphics.blit(TEX, rmBtn.absX(), rmBtn.absY(), 203, 26, rmBtn.w, rmBtn.h);
		}

		renderButtons(graphics, leftPos, topPos);
	}

	/**
	 * Upstream's {@code keyTyped}: a field gets the key first, and only then does the base close
	 * on the inventory key. A focused, editable field swallows the key either way, so typing an
	 * "e" does not close the screen.
	 */
	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {

		if(keyCode == 256 || entity == null) return super.keyPressed(keyCode, scanCode, modifiers);

		for(EditBox field : fields)
			if(field.keyPressed(keyCode, scanCode, modifiers)) return true;

		for(EditBox field : fields)
			if(field.canConsumeInput()) return true;

		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean charTyped(char codePoint, int modifiers) {

		if(entity != null)
			for(EditBox field : fields)
				if(field.charTyped(codePoint, modifiers)) return true;

		return super.charTyped(codePoint, modifiers);
	}
}
