package com.leafia.contents.building.storage.broof.container;

import com.leafia.contents.building.storage.broof.BroofTE;
import com.leafia.init.LeafiaMenuTypes;

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
 * The broof's menu — <b>this addon's first</b>.
 *
 * <p>Upstream: {@code .../broof/container/BroofContainer.java}. Six rows of nine, then the
 * player's inventory and hotbar, all at upstream's own pixel coordinates because those have to
 * line up with the GUI texture.
 *
 * <h2>A naming trap, stated once</h2>
 *
 * 1.12.2's {@code Container} — the thing with slots — is {@link AbstractContainerMenu} here,
 * and 1.12.2's {@code IInventory} is {@code Container}. The base mod's {@code ContainerBase}
 * records the same trap; this file is named {@code BroofMenu} rather than
 * {@code BroofContainer} so the two never have to be told apart by context.
 *
 * <h2>Why this does not extend the base mod's {@code ContainerBase}</h2>
 *
 * That base is built over a vanilla {@code Container} and sizes its shift-click from
 * {@code tile.getContainerSize()}. The broof's inventory is an {@link ItemStackHandler}, which
 * is what upstream uses and what its capability exposes, so the base does not fit. A
 * {@code LeafiaContainerBase} for one consumer would be scaffolding invented ahead of its second
 * user; when a second addon menu lands, the shared half hoists then — the pattern this campaign
 * has used four times already.
 *
 * <h2>The client constructor, which 1.12.2 did not need</h2>
 *
 * 1.12.2 built the menu on both sides from a shared reference to the tile entity. Here the
 * client builds it from a network payload and has no block entity at all, so it gets an
 * unattached handler of the same size; the server then syncs the contents into it. That is why
 * the two constructors exist and why neither is dead code.
 */
public class BroofMenu extends AbstractContainerMenu {

	private final IItemHandler inventory;

	/** Client side: opened from a packet that carries no block entity. */
	public BroofMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
		this(containerId, playerInventory, new ItemStackHandler(BroofTE.SLOTS));
	}

	public BroofMenu(int containerId, Inventory playerInventory, BroofTE broof) {
		this(containerId, playerInventory, broof.inventory);
	}

	private BroofMenu(int containerId, Inventory playerInventory, IItemHandler inventory) {

		super(LeafiaMenuTypes.BROOF.get(), containerId);
		this.inventory = inventory;

		// Upstream's coordinates, unchanged: they line up with vanilla's generic_54 texture,
		// which is the one the screen draws.
		for(int i = 0; i < 6; i++)
			for(int j = 0; j < 9; j++)
				addSlot(new SlotItemHandler(inventory, j + i * 9, 8 + j * 18, 18 + i * 18));

		for(int i = 0; i < 3; i++)
			for(int j = 0; j < 9; j++)
				addSlot(new Slot(playerInventory, j + i * 9 + 9, 8 + j * 18, 84 + i * 18 + (18 * 3) + 2));

		for(int i = 0; i < 9; i++)
			addSlot(new Slot(playerInventory, i, 8 + i * 18, 142 + (18 * 3) + 2));
	}

	/**
	 * Shift-click moves a stack between the broof and the player, whichever way round.
	 *
	 * <p>Upstream routes this through the base mod's {@code TransferStrategy}, configured as
	 * {@code builder(54).genericMachineRange(0)} — <b>one generic range covering all fifty-four
	 * slots</b>, which is the plain behaviour spelled out in a framework this port has not
	 * ported. Written plainly here rather than porting a strategy engine to express "no
	 * strategy": nothing is lost, because there was nothing in it.
	 */
	@Override
	public ItemStack quickMoveStack(Player player, int index) {

		Slot slot = slots.get(index);
		if(!slot.hasItem()) return ItemStack.EMPTY;

		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();

		if(index < BroofTE.SLOTS) {
			if(!moveItemStackTo(stack, BroofTE.SLOTS, slots.size(), true)) return ItemStack.EMPTY;
		} else if(!moveItemStackTo(stack, 0, BroofTE.SLOTS, false)) {
			return ItemStack.EMPTY;
		}

		if(stack.isEmpty()) slot.set(ItemStack.EMPTY);
		else slot.setChanged();

		return original;
	}

	/**
	 * Upstream returns {@code true} unconditionally — a broof stays open however far you walk.
	 *
	 * <p>Kept. Vanilla's {@code stillValid} would close the screen at eight blocks, and
	 * tightening it is a behaviour change to a shipped block (D-021).
	 */
	@Override
	public boolean stillValid(Player player) {
		return true;
	}

	/** Exposed for the tests, which assert the slot count rather than the layout. */
	public IItemHandler getInventory() {
		return inventory;
	}
}
