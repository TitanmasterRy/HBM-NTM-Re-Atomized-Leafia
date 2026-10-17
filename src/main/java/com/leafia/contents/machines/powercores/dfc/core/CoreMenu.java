package com.leafia.contents.machines.powercores.dfc.core;

import com.hbm.blocks.ModBlocks;
import com.hbm.inventory.container.ContainerBase;
import com.leafia.init.LeafiaBlockEntities;
import com.leafia.init.LeafiaMenuTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The addon core's menu: the two catalysts above and below the core in one column, and the player.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/powercores/dfc/core/CoreContainer.java}, the same
 * slots as NTM-CE's {@code ContainerCore} (its screen builds that one): slot 0 at (80, 7), the core at
 * (80, 39), slot 2 at (80, 71), the inventory from y = 122 and the hotbar at 180. The base mod's
 * 1.7.10 core puts its three in a row at y = 53, so this is a menu of its own, under its own type.
 *
 * <p>Plain {@link Slot}s, as upstream's {@code SlotItemHandler} over an unfiltered handler: a player
 * may put anything in, and automation still asks the block entity (D-967).
 *
 * <p><b>The shift-click (D-3076).</b> Out of the machine it merges into the player's slots, {@code 3 .. end};
 * upstream's range began at 2 and so included the machine's own slot 2, where a catalyst shifted out of
 * slot 0 landed. Into the machine it tries slot 0, then 1, then 2.
 */
public class CoreMenu extends ContainerBase {

	public final LeafiaCoreTE core;

	public CoreMenu(int containerId, Inventory invPlayer, RegistryFriendlyByteBuf buf) {
		this(containerId, invPlayer, resolve(invPlayer, buf.readBlockPos()));
	}

	private static LeafiaCoreTE resolve(Inventory invPlayer, BlockPos pos) {
		if(invPlayer.player.level().getBlockEntity(pos) instanceof LeafiaCoreTE core) return core;
		return new LeafiaCoreTE(LeafiaBlockEntities.DFC_CORE.get(), pos, ModBlocks.dfc_core.get().defaultBlockState());
	}

	public CoreMenu(int containerId, Inventory invPlayer, LeafiaCoreTE core) {
		super(LeafiaMenuTypes.DFC_CORE.get(), containerId, invPlayer, core);
		this.core = core;

		this.addSlot(new Slot(core, 0, 80, 7));
		this.addSlot(new Slot(core, 1, 80, 39));
		this.addSlot(new Slot(core, 2, 80, 71));

		this.playerInv(invPlayer, 8, 122, 180);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int par2) {
		ItemStack var3 = ItemStack.EMPTY;
		Slot var4 = this.slots.get(par2);

		if(var4 != null && var4.hasItem()) {
			ItemStack var5 = var4.getItem();
			var3 = var5.copy();

			if(par2 <= 2) {
				if(!this.moveItemStackTo(var5, 3, this.slots.size(), true)) { // D-3076: the player slots (upstream began at the machine's own slot 2)
					return ItemStack.EMPTY;
				}
			} else if(!this.moveItemStackTo(var5, 0, 1, false))
				if(!this.moveItemStackTo(var5, 1, 2, false))
					if(!this.moveItemStackTo(var5, 2, 3, false))
						return ItemStack.EMPTY;

			if(var5.isEmpty()) {
				var4.set(ItemStack.EMPTY);
			} else {
				var4.setChanged();
			}
		}

		return var3;
	}
}
