package com.leafia.contents.machines.processing.solblaster.container;

import javax.annotation.Nullable;

import com.hbm.inventory.SlotNonRetarded;
import com.hbm.inventory.container.ContainerBase;
import com.leafia.contents.machines.processing.solblaster.SolBlasterTE;
import com.leafia.init.LeafiaMenuTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

/**
 * The Neutron Blaster's menu: the core, four lenses, a 5x3 grid of inputs, and the player.
 *
 * <p>Upstream: {@code .../solblaster/container/SolBlasterContainer.java}, on
 * {@code LeafiaItemTransferable}, whose one job is the shift-click chain: everything in the
 * machine goes to the inventory from the far end, everything else goes to the machine from slot
 * zero. That is {@link ContainerBase#quickMoveStack} exactly (as the boiler's and the slop
 * reactor's menus found, D-1054, D-1082), and it asks each slot whether it will take the item, so
 * a solinium core can only land in slot 0 and a lens in slots 1 to 4. One difference worth
 * stating: upstream's slot limit of one item is the block entity's {@code getMaxStackSize}, and
 * the slots read it, so a stack of ten ingots shift-clicks in one at a time.
 *
 * <p>The slots are the base mod's {@code SlotNonRetarded}, which asks the container whether it takes
 * an item - upstream's {@code SlotItemHandler} did through {@code isItemValid}, and vanilla's plain
 * {@code Slot} never does, so a hand could have put dirt in the core's slot.
 *
 * <p>The slot coordinates are upstream's and have to match {@code gui.png}: the core at (23, 23),
 * the lenses in a 2x2 at (8, 77), the inputs from (80, 59), the inventory at 145 and the hotbar
 * at 203. {@code IContainerOpenEventListener} is the constructor and {@link #removed}: vanilla
 * opens a menu on the server by building it, and the client builds the same menu from a payload
 * and must not register itself or its copy of the block entity would "send" to itself.
 */
public class SolBlasterMenu extends ContainerBase {

	/** The block entity the screen reads, or null if the client could not find it. */
	@Nullable
	public final SolBlasterTE blaster;

	/** Client side: the payload carries the core's position. */
	public SolBlasterMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
		this(containerId, playerInventory, find(playerInventory, buf.readBlockPos()));
	}

	public SolBlasterMenu(int containerId, Inventory playerInventory, @Nullable SolBlasterTE blaster) {

		super(LeafiaMenuTypes.SOL_BLASTER.get(), containerId, playerInventory,
				blaster != null ? blaster : new SimpleContainer(SolBlasterTE.SLOTS));

		this.blaster = blaster;

		this.addSlot(new SlotNonRetarded(this.tile, SolBlasterTE.SLOT_CORE, 23, 23));
		this.addSlot(new SlotNonRetarded(this.tile, 1, 8, 77));
		this.addSlot(new SlotNonRetarded(this.tile, 2, 8 + 18, 77));
		this.addSlot(new SlotNonRetarded(this.tile, 3, 8, 77 + 18));
		this.addSlot(new SlotNonRetarded(this.tile, 4, 8 + 18, 77 + 18));

		for(int x = 0; x < 5; x++)
			for(int y = 0; y < 3; y++)
				this.addSlot(new SlotNonRetarded(this.tile, SolBlasterTE.FIRST_INPUT + x + y * 5, 80 + x * 18, 59 + y * 18));

		this.playerInv(playerInventory, 8, 145, 203);

		// Upstream's onContainerOpened. Only the server's menu has a real listener list to join.
		if(blaster != null && !playerInventory.player.level().isClientSide)
			blaster.listeners.add(playerInventory.player);
	}

	/** Upstream's {@code onContainerClosed}. */
	@Override
	public void removed(Player player) {
		if(blaster != null) blaster.listeners.remove(player);
		super.removed(player);
	}

	@Nullable
	private static SolBlasterTE find(Inventory playerInventory, BlockPos pos) {
		return playerInventory.player.level().getBlockEntity(pos) instanceof SolBlasterTE blaster ? blaster : null;
	}
}
