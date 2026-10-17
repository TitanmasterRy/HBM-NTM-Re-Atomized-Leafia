package com.leafia.contents.machines.powercores.dfc.components.injector;

import com.hbm.blocks.ModBlocks;
import com.hbm.inventory.SlotTakeOnly;
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
 * The overhauled injector's menu: two identifier slots with an output slot under each, and the player.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/powercores/dfc/components/injector/CoreInjectorContainer.java}.
 * Its slots sit at the screen's edges - (15, 16) over (15, 52), and (146, 17) over (146, 53) - where the
 * base mod's 1.7.10 menu has them at x 26 and 134, because the addon's screen puts the tanks inside and a
 * picture of the core between them. The lower two take only. Shift-click moves out of the machine, never
 * in, upstream's.
 */
public class CoreInjectorMenu extends ContainerBase {

	public final LeafiaCoreInjectorTE injector;

	public CoreInjectorMenu(int containerId, Inventory invPlayer, RegistryFriendlyByteBuf buf) {
		this(containerId, invPlayer, resolve(invPlayer, buf.readBlockPos()));
	}

	private static LeafiaCoreInjectorTE resolve(Inventory invPlayer, BlockPos pos) {
		if(invPlayer.player.level().getBlockEntity(pos) instanceof LeafiaCoreInjectorTE te) return te;
		return new LeafiaCoreInjectorTE(LeafiaBlockEntities.DFC_INJECTOR.get(), pos, ModBlocks.dfc_injector.get().defaultBlockState());
	}

	public CoreInjectorMenu(int containerId, Inventory invPlayer, LeafiaCoreInjectorTE injector) {
		super(LeafiaMenuTypes.DFC_INJECTOR.get(), containerId, invPlayer, injector);
		this.injector = injector;
		this.addSlot(new Slot(injector, 0, 15, 16));
		this.addSlot(new SlotTakeOnly(injector, 1, 15, 52));
		this.addSlot(new Slot(injector, 2, 146, 17));
		this.addSlot(new SlotTakeOnly(injector, 3, 146, 53));
		this.playerInv(invPlayer, 8, 84, 142);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int par2) {
		ItemStack var3 = ItemStack.EMPTY;
		Slot var4 = this.slots.get(par2);

		if(var4 != null && var4.hasItem()) {
			ItemStack var5 = var4.getItem();
			var3 = var5.copy();

			if(par2 <= 3) {
				if(!this.moveItemStackTo(var5, 4, this.slots.size(), true)) {
					return ItemStack.EMPTY;
				}
			} else {
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
