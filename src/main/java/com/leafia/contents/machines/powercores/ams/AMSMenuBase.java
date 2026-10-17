package com.leafia.contents.machines.powercores.ams;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import com.hbm.inventory.container.ContainerBase;
import com.hbm.util.InventoryUtil;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * What the three AMC menus have in common: upstream's one-way shift-click, and the window
 * properties it syncs.
 *
 * <p>Not an upstream file: upstream repeats both in {@code AMSBaseContainer},
 * {@code AMSEmitterContainer} and {@code AMSStabilizerContainer} (D-3002).
 *
 * <h2>The shift-click, which is upstream's and is lopsided</h2>
 *
 * Upstream's {@code transferStackInSlot} handles slots 0 to 3 only - the first four slots of the
 * machine - and moves what is in them to <b>slots 4 onwards, filling from the end</b>. For the
 * emitter and the stabilizer, whose machine slots are 0 to 3, that is the player's inventory and
 * hotbar. For the base, slots 4 to 15 are still the machine's own (fuel, catalysts, the core,
 * the chips), so a full inventory sends the coolant canister there. Every other slot answers
 * "nothing": <b>nothing can be shift-clicked <em>into</em> an AMC</b>, and the base's slots 4 to 15
 * cannot be shift-clicked out of it. Kept exactly.
 *
 * <h2>The window properties</h2>
 *
 * Upstream's container sends {@code heat}, {@code warning} and (for two of the machines)
 * {@code mode} to the screen as window properties, because the block entity's packet carried
 * neither warning nor mode. They are {@link DataSlot}s here, which vanilla sends only when the
 * value changes, as upstream's {@code detectAndSendChanges} did.
 */
public abstract class AMSMenuBase extends ContainerBase {

	protected AMSMenuBase(MenuType<?> type, int containerId, Inventory playerInventory, Container tile) {
		super(type, containerId, playerInventory, tile);
	}

	/** One value mirrored between the block entity and the client's copy of it. */
	protected void track(IntSupplier getter, IntConsumer setter) {
		addDataSlot(new DataSlot() {
			@Override
			public int get() {
				return getter.getAsInt();
			}

			@Override
			public void set(int value) {
				setter.accept(value);
			}
		});
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {

		Slot slot = this.slots.get(index);

		if(slot == null || !slot.hasItem()) return ItemStack.EMPTY;

		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();

		if(index > 3) return ItemStack.EMPTY;

		if(!InventoryUtil.mergeItemStack(this.slots, stack, 4, this.slots.size(), true)) return ItemStack.EMPTY;

		if(stack.isEmpty()) slot.set(ItemStack.EMPTY);
		else slot.setChanged();

		return original;
	}
}
