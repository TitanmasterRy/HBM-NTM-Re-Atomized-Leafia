package com.leafia.contents.machines.controlpanel.core;

import java.util.ArrayList;
import java.util.List;

import com.hbm.items.tool.ItemMultiDetonator;
import com.leafia.contents.machines.controlpanel.core.block.ControlPanelTE;
import com.leafia.contents.machines.controlpanel.core.block.IControlEditSession;
import com.leafia.init.LeafiaMenuTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * The control panel editor's menu: the one linker slot and the player's inventory. Adapted from NTM-CE's
 * {@code ContainerControlEdit} (CCP-EDITOR, D-3066), and it is the {@link IControlEditSession} that
 * {@code ControlPanelTE.applyFullSet} requires: a client's edit is accepted only from a player whose open container is one of
 * these, for that panel (D-3063).
 *
 * <h2>Differences from CE</h2>
 * <ul>
 * <li>{@link #stillValid} was {@code return true}; it is now "the panel is still there and the player is in reach of it". The
 * editor closes when the player walks away or the block is broken, and the close is what hands the linker back.</li>
 * <li>The linker slot takes <b>only a multi-detonator</b> (CE took any item and the screen then ignored it). A stack put there is
 * the only way the server can see a link was presented, and a slot that kept a stray item would be a free storage slot; one item
 * at a time, as the detonator stacks to one.</li>
 * <li>{@link #removed} hands the slot's stack back to the player, or drops it at their feet, <b>on the server only</b>
 * (CE ran it on both sides, so the client also tried to insert into its own inventory).</li>
 * <li>The slot's geometry is CE's: the linker at (8, 13), the inventory from (73, 162) and the hotbar at y 220. The slots are
 * switched on and off by the screen as the editor changes pages ({@link SlotDisableable#enabled}), which is
 * {@link Slot#isActive} here (CE's client-only {@code isEnabled}); only the client's screen consults it, the server does not.</li>
 * </ul>
 */
public class ControlEditMenu extends AbstractContainerMenu implements IControlEditSession {

	/** The slot the multi-detonator goes in. */
	public static final int LINKER_SLOT = 0;
	/** The linker slot plus 27 inventory slots plus the 9 of the hotbar. */
	public static final int SLOT_COUNT = 1 + 27 + 9;

	public final ControlPanelTE control;
	public final LinkerSlot input;
	public final List<SlotDisableable> invSlots = new ArrayList<>();

	/** The server's constructor, and the client's once it has found the panel. */
	public ControlEditMenu(int id, Inventory playerInventory, ControlPanelTE control) {
		super(LeafiaMenuTypes.CONTROL_EDIT.get(), id);
		this.control = control;
		this.input = (LinkerSlot) addSlot(new LinkerSlot(control, 8, 13));

		for(int i = 0; i < 3; i++)
			for(int j = 0; j < 9; j++)
				invSlots.add((SlotDisableable) addSlot(new SlotDisableable(playerInventory, j + i * 9 + 9, 73 + j * 18, 162 + i * 18)));

		for(int i = 0; i < 9; i++)
			invSlots.add((SlotDisableable) addSlot(new SlotDisableable(playerInventory, i, 73 + i * 18, 220)));
	}

	/** The client's, from the payload {@link #open} wrote: the panel's position. */
	public ControlEditMenu(int id, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
		this(id, playerInventory, find(playerInventory.player, buf.readBlockPos()));
	}

	private static ControlPanelTE find(Player player, BlockPos pos) {
		if(player.level().getBlockEntity(pos) instanceof ControlPanelTE te)
			return te;
		throw new IllegalStateException("the control panel editor was opened for " + pos.toShortString() + ", where there is no panel");
	}

	/** What the screwdriver does on a panel: opens this menu for the player. The registered {@code ControlPanelTE.editorOpener}. */
	public static void open(ServerPlayer player, ControlPanelTE panel) {
		if(!panel.hasPermission(player))
			return;
		BlockPos pos = panel.getBlockPos();
		player.openMenu(new SimpleMenuProvider((id, inv, p) -> new ControlEditMenu(id, inv, panel), Component.literal("Control Panel")),
				buf -> buf.writeBlockPos(pos));
	}

	@Override
	public ControlPanelTE editedPanel() {
		return control;
	}

	@Override
	public boolean stillValid(Player player) {
		return control.getLevel() != null && !control.isRemoved() && control.hasPermission(player);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		ItemStack moved = ItemStack.EMPTY;
		Slot slot = slots.get(index);
		if(slot != null && slot.hasItem()) {
			ItemStack stack = slot.getItem();
			moved = stack.copy();
			if(index == LINKER_SLOT) {
				if(!moveItemStackTo(stack, 1, slots.size(), true))
					return ItemStack.EMPTY;
			} else if(!moveItemStackTo(stack, LINKER_SLOT, 1, false)) {
				return ItemStack.EMPTY;
			}
			if(stack.isEmpty())
				slot.setByPlayer(ItemStack.EMPTY);
			else
				slot.setChanged();
		}
		return moved;
	}

	/** Gives back whatever was left in the linker slot. Server only: the client's inventory is not the authority. */
	@Override
	public void removed(Player player) {
		if(!player.level().isClientSide) {
			for(int i = 0; i < control.inventory.getSlots(); i++) {
				ItemStack stack = control.inventory.getStackInSlot(i);
				if(!stack.isEmpty()) {
					control.inventory.setStackInSlot(i, ItemStack.EMPTY);
					if(!player.getInventory().add(stack))
						player.drop(stack, false);
				}
			}
		}
		super.removed(player);
	}

	/** The linker slot: only a multi-detonator, one at a time. Switchable by the screen. */
	public static class LinkerSlot extends SlotItemHandler {

		public boolean enabled = false;

		public LinkerSlot(ControlPanelTE te, int x, int y) {
			super(te.inventory, 0, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return stack.getItem() instanceof ItemMultiDetonator && super.mayPlace(stack);
		}

		@Override
		public int getMaxStackSize() {
			return 1;
		}

		@Override
		public boolean isActive() {
			return enabled;
		}
	}

	/** A player-inventory slot the screen can switch off while a page that does not use the inventory is showing. */
	public static class SlotDisableable extends Slot {

		public boolean enabled = false;

		public SlotDisableable(Inventory inventory, int index, int x, int y) {
			super(inventory, index, x, y);
		}

		@Override
		public boolean isActive() {
			return enabled;
		}
	}
}
