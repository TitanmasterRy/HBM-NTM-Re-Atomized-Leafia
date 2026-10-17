package com.leafia.contents.nonmachines.storage.items;

import javax.annotation.Nullable;

import com.hbm.blockentity.machine.storage.BlockEntityCrateBase;
import com.leafia.init.LeafiaMenuTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The labelling screen's menu: one icon slot and the player's inventory.
 *
 * <p>Upstream: {@code .../nonmachines/storage/items/CrateLabelContainer.java}, a container over the crate's
 * {@code leafia$getIconHandler()} - a one-slot item handler whose {@code insertItem} <b>keeps the item
 * offered as the icon and hands it back</b> (nothing is consumed) and whose {@code extractItem} clears the
 * icon and returns nothing. The slot is at (62, 35); the inventory is at (8, 103) and the hotbar at (8, 161).
 *
 * <h2>The icon slot is a click, not a container</h2>
 *
 * 1.21.1 moves stacks between a slot and the cursor with its own arithmetic, which cannot be made to "copy
 * and give back". So the slot refuses everything ({@link IconSlot}) and this menu answers a click on it
 * itself, in {@link #clicked}: an item on the cursor sets the icon to that item and stays on the cursor; an
 * empty cursor clears the icon. The slot's one-item container only shows the icon to the client, which
 * vanilla's slot sync carries. Number-key swaps with the slot behave as the hotbar stack being offered;
 * shift-click does nothing, as upstream's {@code transferStackInSlot} returns empty.
 *
 * <h2>The server writes the icon, the client sends the text</h2>
 *
 * Upstream's client edits its copy of the tile entity and sends the whole label state back, icon included.
 * Here the icon is the server's alone (set in {@link #clicked}, the server's run of it) and
 * {@link CrateLabelPacket} carries only the lines, so a client cannot name an icon it does not hold.
 *
 * <p>Stays open however far the player walks - upstream's {@code canInteractWith} is {@code true} - until
 * the crate itself is gone. The packet is reach-checked all the same (D-1094).
 */
public class CrateLabelMenu extends AbstractContainerMenu {

	@Nullable
	private final BlockEntityCrateBase crate;
	private final Direction side;
	private final Container icon = new SimpleContainer(1);

	/** Client side: the payload is the crate's position and the face that was clicked. */
	public CrateLabelMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
		this(containerId, inventory, find(inventory.player, buf.readBlockPos()), Direction.from2DDataValue(buf.readVarInt()));
	}

	public CrateLabelMenu(int containerId, Inventory inventory, @Nullable BlockEntityCrateBase crate, Direction side) {
		super(LeafiaMenuTypes.CRATE_LABEL.get(), containerId);
		this.crate = crate;
		this.side = side;

		if(crate != null && !inventory.player.level().isClientSide) {
			net.minecraft.world.item.Item shown = CrateLabels.of(crate).icon();
			if(shown != null) icon.setItem(0, new ItemStack(shown));
		}
		addSlot(new IconSlot(icon, 0, 62, 35));

		for(int i = 0; i < 3; i++)
			for(int j = 0; j < 9; j++)
				addSlot(new Slot(inventory, j + i * 9 + 9, 8 + j * 18, 103 + i * 18));

		for(int i = 0; i < 9; i++)
			addSlot(new Slot(inventory, i, 8 + i * 18, 161));
	}

	@Nullable
	private static BlockEntityCrateBase find(Player player, BlockPos pos) {
		return player.level().getBlockEntity(pos) instanceof BlockEntityCrateBase crate ? crate : null;
	}

	@Nullable
	public BlockEntityCrateBase crate() {
		return crate;
	}

	/** The face that was clicked: it decides which of the four vertical lines the first box edits. */
	public Direction side() {
		return side;
	}

	public Container iconContainer() {
		return icon;
	}

	/** The slot that only ever shows the icon. */
	private static final class IconSlot extends Slot {
		IconSlot(Container container, int slot, int x, int y) {
			super(container, slot, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return false;
		}

		@Override
		public boolean mayPickup(Player player) {
			return false;
		}
	}

	@Override
	public void clicked(int slotId, int button, ClickType clickType, Player player) {

		if(slotId == 0) {
			ItemStack offered = ItemStack.EMPTY;
			if(clickType == ClickType.PICKUP) offered = getCarried();
			else if(clickType == ClickType.SWAP && button >= 0 && button < 9) offered = player.getInventory().getItem(button);
			else return;

			setIcon(player, offered);
			return;
		}

		super.clicked(slotId, button, clickType, player);
	}

	/** Upstream's handler: a non-empty offer becomes the icon, an empty one clears it. */
	private void setIcon(Player player, ItemStack offered) {
		boolean clear = offered.isEmpty();
		icon.setItem(0, clear ? ItemStack.EMPTY : new ItemStack(offered.getItem()));
		if(crate != null && !player.level().isClientSide)
			CrateLabels.of(crate).setIcon(clear ? null : offered.getItem());
		broadcastChanges();
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(Player player) {
		return crate != null && !crate.isRemoved();
	}
}
