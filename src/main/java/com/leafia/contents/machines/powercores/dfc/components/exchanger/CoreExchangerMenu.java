package com.leafia.contents.machines.powercores.dfc.components.exchanger;

import com.hbm.inventory.container.ContainerBase;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.init.LeafiaBlockEntities;
import com.leafia.init.LeafiaMenuTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The DFC heat exchanger's menu: its one identifier slot at (62, 67), the inventory from y = 102 and the
 * hotbar at 160.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/powercores/dfc/components/exchanger/CoreExchangerContainer.java}.
 * Opening it puts the player on the exchanger's listener list, which is who its tank packet goes to, and
 * closing it takes them off - upstream's {@code IContainerOpenEventListener}. Shift-click is
 * {@code LeafiaItemTransferable}'s: out of the slot into the inventory, from the end; into the slot from
 * the inventory.
 */
public class CoreExchangerMenu extends ContainerBase {

	public final CoreExchangerTE te;

	public CoreExchangerMenu(int containerId, Inventory invPlayer, RegistryFriendlyByteBuf buf) {
		this(containerId, invPlayer, resolve(invPlayer, buf.readBlockPos()));
	}

	private static CoreExchangerTE resolve(Inventory invPlayer, BlockPos pos) {
		if(invPlayer.player.level().getBlockEntity(pos) instanceof CoreExchangerTE te) return te;
		return new CoreExchangerTE(LeafiaBlockEntities.DFC_EXCHANGER.get(), pos, LeafiaBlocks.dfc_exchanger.get().defaultBlockState());
	}

	public CoreExchangerMenu(int containerId, Inventory invPlayer, CoreExchangerTE te) {
		super(LeafiaMenuTypes.DFC_EXCHANGER.get(), containerId, invPlayer, te);
		this.te = te;
		this.addSlot(new Slot(te, 0, 62, 67));
		this.playerInv(invPlayer, 8, 102, 160);
		if(!invPlayer.player.level().isClientSide) te.listeners.add(invPlayer.player);
	}

	@Override
	public void removed(Player player) {
		te.listeners.remove(player);
		super.removed(player);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		ItemStack copy = ItemStack.EMPTY;
		Slot slot = this.slots.get(index);
		if(slot != null && slot.hasItem()) {
			ItemStack stack = slot.getItem();
			copy = stack.copy();
			if(index == 0) {
				if(!this.moveItemStackTo(stack, 1, this.slots.size(), true)) return ItemStack.EMPTY;
			} else if(!this.moveItemStackTo(stack, 0, 1, false)) {
				return ItemStack.EMPTY;
			}
			if(stack.isEmpty()) slot.set(ItemStack.EMPTY);
			else slot.setChanged();
		}
		return copy;
	}
}
