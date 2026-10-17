package com.leafia.contents.machines.heat.rtheater.container;

import javax.annotation.Nullable;

import com.hbm.inventory.container.ContainerBase;
import com.leafia.contents.machines.heat.rtheater.HeaterRTGTE;
import com.leafia.init.LeafiaMenuTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The RT heater's menu: fifteen pellets in a five-by-three grid, the fuel rod at the left, and the player.
 *
 * <p>Upstream: {@code .../rtheater/container/HeaterRTGContainer.java}, slot for slot at upstream's
 * coordinates. Plain {@link Slot}s, as upstream's unvalidated {@code SlotItemHandler}s.
 *
 * <h2>Upstream's shift-click, kept with its two oddities</h2>
 *
 * Out of a pellet slot (0-14) it merges into {@code 15 .. end} - the rod slot first in line before the
 * player's, though from the far end, so in practice the rod slot is reached only when the inventory is
 * full. Anything else - the player's inventory <em>and the rod slot</em> - merges into the pellet slots
 * 0-14. So a rod cannot be shift-clicked into its own slot, and shift-clicking it out of that slot moves it
 * into a pellet slot rather than to the player. {@code LeafiaRTGTests} pins both.
 */
public class HeaterRTGMenu extends ContainerBase {

	@Nullable
	public final HeaterRTGTE heater;

	public HeaterRTGMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
		this(containerId, playerInventory, find(playerInventory, buf.readBlockPos()));
	}

	public HeaterRTGMenu(int containerId, Inventory playerInventory, @Nullable HeaterRTGTE heater) {

		super(LeafiaMenuTypes.HEATER_RT.get(), containerId, playerInventory,
				heater != null ? heater : new SimpleContainer(HeaterRTGTE.SLOTS));

		this.heater = heater;

		for(int row = 0; row < 3; row++)
			for(int col = 0; col < 5; col++)
				this.addSlot(new Slot(this.tile, row * 5 + col, 26 + col * 18, 22 + row * 18));
		this.addSlot(new Slot(this.tile, HeaterRTGTE.ROD_SLOT, 4, 40));

		this.playerInv(playerInventory, 8, 94, 152);
	}

	@Nullable
	private static HeaterRTGTE find(Inventory playerInventory, BlockPos pos) {
		return playerInventory.player.level().getBlockEntity(pos) instanceof HeaterRTGTE heater ? heater : null;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int par2) {
		ItemStack var3 = ItemStack.EMPTY;
		Slot var4 = this.slots.get(par2);

		if(var4 != null && var4.hasItem()) {
			ItemStack var5 = var4.getItem();
			var3 = var5.copy();

			if(par2 <= 14) {
				if(!this.moveItemStackTo(var5, 15, this.slots.size(), true)) {
					return ItemStack.EMPTY;
				}
			} else if(!this.moveItemStackTo(var5, 0, 15, false)) {
				return ItemStack.EMPTY;
			}

			if(var5.isEmpty()) {
				var4.set(ItemStack.EMPTY);
			} else {
				var4.setChanged();
			}
		}

		return var3;
	}
}
