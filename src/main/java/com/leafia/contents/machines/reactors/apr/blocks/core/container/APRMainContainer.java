package com.leafia.contents.machines.reactors.apr.blocks.core.container;

import javax.annotation.Nullable;

import com.hbm.inventory.SlotNonRetarded;
import com.hbm.inventory.container.ContainerBase;
import com.leafia.contents.machines.reactors.apr.blocks.core.APRCoreTE;
import com.leafia.init.LeafiaMenuTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;

/**
 * The APR's menu: the capsule in and the empty capsule out, the coolant identifier's in and out, the selected
 * chamber's identifier in and out, and the player.
 *
 * <p>Upstream: {@code .../apr/blocks/core/container/APRMainContainer.java}. Slot coordinates are upstream's, on
 * {@code gui_main.png}: capsules at (117, 96) and (135, 96), coolant at (84, 25) and (84, 61), chamber fluid at
 * (8, 25) and (8, 61), the inventory from (25, 138) and the hotbar at 196. Upstream's shift-click chain is
 * "machine to inventory, back to front; inventory to the machine's six" - {@link ContainerBase}'s own (D-1054).
 */
public class APRMainContainer extends ContainerBase {

	@Nullable
	public final APRCoreTE te;

	/** Client side: the payload carries the core's position. */
	public APRMainContainer(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
		this(containerId, playerInventory, find(playerInventory, buf.readBlockPos()));
	}

	public APRMainContainer(int containerId, Inventory playerInventory, @Nullable APRCoreTE te) {
		super(LeafiaMenuTypes.APR_MAIN.get(), containerId, playerInventory, te != null ? te : new SimpleContainer(APRCoreTE.SLOTS));
		this.te = te;
		// particle io
		this.addSlot(new SlotNonRetarded(this.tile, 0, 117, 96));
		this.addSlot(new SlotNonRetarded(this.tile, 1, 135, 96));
		// main coolant
		this.addSlot(new SlotNonRetarded(this.tile, 2, 84, 25));
		this.addSlot(new SlotNonRetarded(this.tile, 3, 84, 61));
		// chamber fluids
		this.addSlot(new SlotNonRetarded(this.tile, 4, 8, 25));
		this.addSlot(new SlotNonRetarded(this.tile, 5, 8, 61));
		this.playerInv(playerInventory, 17 + 8, 138, 196);
	}

	@Nullable
	private static APRCoreTE find(Inventory playerInventory, BlockPos pos) {
		return playerInventory.player.level().getBlockEntity(pos) instanceof APRCoreTE te ? te : null;
	}
}
