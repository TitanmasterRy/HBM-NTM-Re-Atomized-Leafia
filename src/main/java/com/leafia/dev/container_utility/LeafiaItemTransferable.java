package com.leafia.dev.container_utility;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A menu with upstream's shift-click chain: a small builder that says where a clicked stack may go,
 * in order, and gives back what vanilla's {@code quickMoveStack} contract wants.
 *
 * <p>Upstream: {@code com/leafia/dev/container_utility/LeafiaItemTransferable.java}, the superclass
 * of twelve of the addon's containers. It is {@code Container} in 1.12.2 and
 * {@link AbstractContainerMenu} here, and a thin adapter rather than a dissolve for one reason:
 * the base mod's {@code ContainerBase.quickMoveStack} does the simple case - a machine slot goes to
 * the inventory from the far end, anything else goes to the machine from slot zero - and eight of the
 * twelve chains upstream are exactly that (the slop reactor, the boiler, the Neutron Blaster, the
 * cabin, the regex filter, the APR, ZIRNOX and the DFC exchanger; D-1054, D-1077, D-1082), a ninth,
 * the detonator's, never moves anything. Those inherit {@code ContainerBase} and need none of
 * this. The other three are not that simple: the mixing vat
 * sends an inventory stack to <em>one of two slot ranges</em> depending on whether the vat is in
 * nuclear mode, the coolant heat exchanger fills its first three slots before the rest, and the PWR
 * terminal moves between a grid, a set of resource slots and the player's inventory in both
 * directions. This class is for menus whose chain is not "machine out, then machine in".
 *
 * <h2>Using it</h2>
 *
 * Extend it (it extends {@link AbstractContainerMenu}, so a menu that also wants {@code ContainerBase}'s
 * helpers builds them itself) and override {@code quickMoveStack}:
 *
 * <pre>{@code
 * LeafiaItemTransfer transfer = new LeafiaItemTransfer(3 + 10 + 8)._selected(index);
 * return transfer.__forSlots(0, 9999).__tryMoveToInventory(true)
 *         .__forInventory().__tryMoveToSlot(0, 2, false)
 *         .__tryMoveToSlot(3, 12, false)
 *         .__getReturn();
 * }</pre>
 *
 * <p>The numbers are upstream's own, <b>inclusive on both ends</b>: {@code __tryMoveToSlot(0, 2, ...)}
 * is slots 0, 1 and 2, which is {@code moveItemStackTo(stack, 0, 3, ...)}. The constructor takes the
 * machine's slot count, so its inventory begins at that index.
 *
 * <h2>Two upstream quirks kept, because the numbers depend on them</h2>
 *
 * <ul>
 * <li>{@code __forInventory} starts its source range at {@code __maxIndex} - the machine's <em>last</em>
 *     slot - not the first inventory slot. A stack clicked in that last machine slot is therefore
 *     accepted by the inventory branch as well as by the machine branch. Upstream's chains order
 *     their calls so this does not matter, and a change here would move which one runs first.</li>
 * <li>{@code __forSlots(0, 9999)} clamps the end to {@code __maxIndex}, which is how "every machine
 *     slot" is spelt.</li>
 * </ul>
 *
 * <p>Vanilla's {@code moveItemStackTo} is 1.12.2's {@code mergeItemStack}: same two-pass fill (stack
 * onto existing stacks first, then into empty slots) and the same {@code reverse} flag.
 */
public abstract class LeafiaItemTransferable extends AbstractContainerMenu {

	protected LeafiaItemTransferable(MenuType<?> type, int containerId) {
		super(type, containerId);
	}

	public class LeafiaItemTransfer {

		short slots;
		/** True once a move has changed the clicked stack. */
		boolean __changed = false;
		short selectedIndex;
		Slot selectedSlot;
		ItemStack backup;
		ItemStack stack;
		/** Non-null when the answer is already decided, before any move is tried. */
		ItemStack __return = null;
		short sourceSlotStart = 0;
		short sourceSlotEnd = 0;

		/** The highest index of this machine's own inventory. */
		public short __maxIndex = 0;

		/**
		 * @param slots the highest machine slot index plus one - that is, the machine's slot count
		 */
		public LeafiaItemTransfer(int slots) {
			this.slots = (short) slots;
			this.__maxIndex = (short) (slots - 1);
		}

		/** Picks the clicked slot. An empty or missing slot decides the answer: nothing to move. */
		public LeafiaItemTransfer _selected(int clickIndex) {

			this.selectedIndex = (short) clickIndex;
			this.selectedSlot = LeafiaItemTransferable.this.slots.get(clickIndex);

			if(selectedSlot == null || !selectedSlot.hasItem()) {
				__return = ItemStack.EMPTY;
			} else {
				this.stack = this.selectedSlot.getItem();
				this.backup = this.stack.copy();
			}

			return this;
		}

		boolean moveItem(int targetSlotStart, int targetSlotEnd, boolean reverse) {
			return LeafiaItemTransferable.this.moveItemStackTo(stack, targetSlotStart, targetSlotEnd + 1, reverse);
		}

		/** Only a click inside {@code [start, end]} (end clamped to the machine) will be moved by the next try. */
		public LeafiaItemTransfer __forSlots(int sourceSlotStart, int sourceSlotEnd) {
			this.sourceSlotStart = (short) Math.min(sourceSlotStart, __maxIndex);
			this.sourceSlotEnd = (short) Math.min(sourceSlotEnd, __maxIndex);
			return this;
		}

		/** Only a click in the player's inventory (and the machine's last slot - see the class note). */
		public LeafiaItemTransfer __forInventory() {
			this.sourceSlotStart = __maxIndex;
			this.sourceSlotEnd = (short) LeafiaItemTransferable.this.slots.size();
			return this;
		}

		/**
		 * Tries to move the clicked stack into {@code [targetSlotStart, targetSlotEnd]}, once: if an
		 * earlier call already moved it, or the answer is already decided, or the click is outside the
		 * range set by {@code __forSlots}/{@code __forInventory}, nothing happens.
		 */
		public LeafiaItemTransfer __tryMoveToSlot(int targetSlotStart, int targetSlotEnd, boolean reverse) {

			if((__return == null) && !__changed && !stack.isEmpty()
					&& (selectedIndex >= sourceSlotStart) && (selectedIndex <= sourceSlotEnd)) {
				__changed = moveItem(targetSlotStart, targetSlotEnd, reverse);
			}

			return this;
		}

		/** Into the player's inventory: from the first slot past the machine's to the end. */
		public LeafiaItemTransfer __tryMoveToInventory(boolean reverse) {
			return this.__tryMoveToSlot(this.slots, LeafiaItemTransferable.this.slots.size() - 1, reverse);
		}

		/**
		 * What {@code quickMoveStack} returns. Empty means "stop" - vanilla calls it again for as long
		 * as it returns something, so a move that changed the stack returns the pre-move copy and the
		 * caller loops until nothing more moves.
		 */
		public ItemStack __getReturn() {

			if(__return != null) return __return;

			if(!__changed) return ItemStack.EMPTY;

			if(stack.isEmpty())
				this.selectedSlot.set(ItemStack.EMPTY);
			else
				this.selectedSlot.setChanged();

			return this.backup;
		}
	}
}
