package com.leafia.contents.machines.elevators.gui;

import javax.annotation.Nullable;

import com.leafia.contents.machines.elevators.car.ElevatorEntity;
import com.leafia.init.LeafiaMenuTypes;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * The elevator cabin's menu: its eight slots (chip, floor, ceiling, four walls, radio) and the
 * player.
 *
 * <p>Upstream: {@code .../elevators/gui/EvCabinContainer.java}. Slot positions are upstream's own
 * pixel coordinates - a plus of five around (30, 35), 21 apart - because they have to land on
 * {@code cab_002.png}, and the order is upstream's: the centre is slot 0, then
 * {@code (-21,+21)}, {@code (-21,-21)}, {@code (0,-21)}, {@code (-21,0)}, {@code (0,+21)},
 * {@code (+21,0)}, {@code (+21,+21)}.
 *
 * <h2>The {@code tempContainer} field is gone</h2>
 *
 * Upstream built this container by hand in {@code processInitialInteract}, stored it on the
 * entity as {@code tempContainer} and assigned it to {@code player.openContainer} on both sides,
 * because 1.12.2's {@code GuiHandler} could not be given the entity. A {@code MenuType} can: the
 * server opens it with the entity's id in the payload and the client constructor below finds the
 * entity again, so nothing is parked on the entity and nothing is assigned to the player.
 *
 * <h2>Shift-click, kept exactly - including a defect</h2>
 *
 * Upstream's {@code LeafiaItemTransfer(8)} is two moves, and they are reproduced rather than
 * rewritten as the usual "machine to player, player to machine" pair, because they are not
 * quite that:
 *
 * <ol>
 * <li>a click on a cabin slot (0-7) merges into the player's inventory, back to front;</li>
 * <li>a click on <em>any</em> slot from <b>7</b> up merges into slots 0-7, front to back.</li>
 * </ol>
 *
 * <p>The second range starts at the highest cabin slot's index rather than the first player
 * slot's. So when the radio slot (7) cannot move into a full inventory, the second move runs on
 * it too, and slot 7 is one of its own targets: a stackable stack merges into itself, and
 * vanilla's merge sets the count to the doubled value. Vanilla's shift-click loop repeats while
 * the slot still holds that item, so one shift-click doubles it up to the stack limit.
 * <b>Upstream has the same defect</b> (the same two arithmetic lines, the same merge), it needs
 * a full inventory and an item in the slot that accepts anything, and it is reachable only once
 * the screwdriver that opens this menu is ported. Flagged, not fixed (D-021); D-1077 and
 * {@code LeafiaEvCabinGuiTests.shiftClickingTheRadioSlotWithAFullInventoryDoublesIt}.
 *
 * <h2>{@link #stillValid} is true unconditionally</h2>
 *
 * Upstream's {@code canInteractWith} returns true: a cabin screen stays open however far you
 * walk, and survives the cabin being removed. Kept, as the broof's is.
 */
public class EvCabinMenu extends AbstractContainerMenu {

	/** Eight cabin slots, then 27 inventory and 9 hotbar. */
	public static final int CABIN_SLOTS = 8;

	/** The cabin, or null if the client could not find it - the screen then draws an empty panel. */
	@Nullable
	public final ElevatorEntity entity;

	private final IItemHandler inventory;

	/** Client side: the payload carries the cabin's entity id. */
	public EvCabinMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
		this(containerId, playerInventory, find(playerInventory, buf.readVarInt()));
	}

	public EvCabinMenu(int containerId, Inventory playerInventory, @Nullable ElevatorEntity entity) {

		super(LeafiaMenuTypes.EV_CABIN.get(), containerId);
		this.entity = entity;
		this.inventory = entity != null ? entity.inventory : new ItemStackHandler(CABIN_SLOTS);

		this.addSlot(new SlotItemHandler(inventory, 0, 30, 35));
		this.addSlot(new SlotItemHandler(inventory, 1, 30 - 21, 35 + 21));
		this.addSlot(new SlotItemHandler(inventory, 2, 30 - 21, 35 - 21));
		this.addSlot(new SlotItemHandler(inventory, 3, 30, 35 - 21));
		this.addSlot(new SlotItemHandler(inventory, 4, 30 - 21, 35));
		this.addSlot(new SlotItemHandler(inventory, 5, 30, 35 + 21));
		this.addSlot(new SlotItemHandler(inventory, 6, 30 + 21, 35));
		this.addSlot(new SlotItemHandler(inventory, 7, 30 + 21, 35 + 21));

		for(int i = 0; i < 3; i++)
			for(int j = 0; j < 9; j++)
				this.addSlot(new Slot(playerInventory, j + i * 9 + 9, 8 + j * 18, 91 + i * 18));

		for(int i = 0; i < 9; i++)
			this.addSlot(new Slot(playerInventory, i, 8 + i * 18, 149));
	}

	@Nullable
	private static ElevatorEntity find(Inventory playerInventory, int id) {
		Entity entity = playerInventory.player.level().getEntity(id);
		return entity instanceof ElevatorEntity cabin ? cabin : null;
	}

	/** Upstream's {@code transferStackInSlot}; see the class note for what it really does. */
	@Override
	public ItemStack quickMoveStack(Player player, int index) {

		if(index < 0 || index >= slots.size()) return ItemStack.EMPTY;

		Slot selected = slots.get(index);
		if(selected == null || !selected.hasItem()) return ItemStack.EMPTY;

		ItemStack stack = selected.getItem();
		ItemStack backup = stack.copy();
		boolean changed = false;

		// __forSlots(0, 9999).__tryMoveToInventory(true): the range ends at the highest cabin slot.
		if(index <= CABIN_SLOTS - 1)
			changed = moveItemStackTo(stack, CABIN_SLOTS, slots.size(), true);

		// __forInventory().__tryMoveToSlot(0, maxIndex, false): note it starts AT maxIndex (7).
		if(!changed && !stack.isEmpty() && index >= CABIN_SLOTS - 1 && index <= slots.size())
			changed = moveItemStackTo(stack, 0, CABIN_SLOTS, false);

		if(!changed) return ItemStack.EMPTY;

		if(stack.isEmpty()) selected.set(ItemStack.EMPTY);
		else selected.setChanged();

		return backup;
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}

	/** Exposed for the tests. */
	public IItemHandler getInventory() {
		return inventory;
	}
}
