package com.leafia.contents.miscellanous.regex_filter.pneumatic.container;

import com.leafia.contents.miscellanous.regex_filter.pneumatic.RegexFilterTE;
import com.leafia.init.LeafiaMenuTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * The filter's menu: its one slot at (8, 35), then the player's inventory and hotbar.
 *
 * <p>Upstream: {@code .../pneumatic/container/RegexFilterContainer.java}, a {@code Container}
 * built by {@code LeafiaItemTransferable}. Every coordinate is upstream's, because they have to
 * land on the screen's texture.
 *
 * <h2>The listener list and the first sync</h2>
 *
 * Upstream's constructor sent the filter list to the opening player, and
 * {@code IContainerOpenEventListener} added and removed the player from the block entity's
 * listener list. Here the <em>server</em> constructor does the first and the second together,
 * and {@link #removed} the third: this is the point where 1.21.1 tells a menu it was opened and
 * closed, and a menu has no other hook. The list reaches the client <b>before</b> the packet that
 * opens the screen (the menu is created, then announced), so the screen's first frame already
 * has it.
 *
 * <h2>The client constructor</h2>
 *
 * Built from a position, looked up in the client's world. If the block entity is not there (it
 * was broken between the click and the packet) the menu is empty-handed and the screen closes
 * itself.
 *
 * <h2>Shift-click</h2>
 *
 * Upstream's {@code LeafiaItemTransfer(1)}: the machine's slot goes to the inventory, the
 * inventory goes to the machine's slot - which only takes what the filters pass, because
 * {@code SlotItemHandler.mayPlace} asks the handler.
 */
public class RegexFilterMenu extends AbstractContainerMenu {

	public static final int MACHINE_SLOTS = 1;

	public final RegexFilterTE entity;
	private final IItemHandler inventory;

	/** Client side: opened from a packet that carries only the block's position. */
	public RegexFilterMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
		this(containerId, playerInventory, lookup(playerInventory, buf.readBlockPos()));
	}

	private static RegexFilterTE lookup(Inventory playerInventory, BlockPos pos) {
		return playerInventory.player.level().getBlockEntity(pos) instanceof RegexFilterTE filter ? filter : null;
	}

	/**
	 * Both sides: the server's, from {@code createMenu}, and the client's, from the position above
	 * (which may find nothing). Only the server registers a listener and sends the first sync.
	 */
	public RegexFilterMenu(int containerId, Inventory playerInventory, RegexFilterTE entity) {

		super(LeafiaMenuTypes.REGEX_FILTER.get(), containerId);
		this.entity = entity;
		this.inventory = entity != null ? entity.inventory : new ItemStackHandler(MACHINE_SLOTS);

		if(entity != null && !playerInventory.player.level().isClientSide) {
			entity.generateSyncPacket().__sendToClient(playerInventory.player);
			entity.listeners.add(playerInventory.player);
		}

		addSlot(new SlotItemHandler(inventory, 0, 8, 35));

		for(int i = 0; i < 3; i++)
			for(int j = 0; j < 9; j++)
				addSlot(new Slot(playerInventory, j + i * 9 + 9, 8 + j * 18, 103 + i * 18));

		for(int i = 0; i < 9; i++)
			addSlot(new Slot(playerInventory, i, 8 + i * 18, 161));
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {

		Slot slot = slots.get(index);
		if(!slot.hasItem()) return ItemStack.EMPTY;

		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();

		if(index < MACHINE_SLOTS) {
			if(!moveItemStackTo(stack, MACHINE_SLOTS, slots.size(), true)) return ItemStack.EMPTY;
		} else if(!moveItemStackTo(stack, 0, MACHINE_SLOTS, false)) {
			return ItemStack.EMPTY;
		}

		if(stack.isEmpty()) slot.set(ItemStack.EMPTY);
		else slot.setChanged();

		return original;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		if(entity != null && !player.level().isClientSide) entity.listeners.remove(player);
	}

	/** Upstream returns {@code true} unconditionally; kept, as the broof's is. */
	@Override
	public boolean stillValid(Player player) {
		return true;
	}

	public IItemHandler getInventory() {
		return inventory;
	}
}
