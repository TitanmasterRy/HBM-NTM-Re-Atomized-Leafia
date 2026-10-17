package com.leafia.contents.miscellanous.slop.container;

import javax.annotation.Nullable;

import com.hbm.inventory.container.ContainerBase;
import com.leafia.contents.miscellanous.slop.SlopTE;
import com.leafia.init.LeafiaMenuTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;

/**
 * The slop reactor's menu: an ingot slot, a snowball slot, and the player.
 *
 * <p>Upstream: {@code .../slop/container/SlopContainer.java}. The two slots are upstream's pixel
 * coordinates - (8, 17) and (80, 53) - because they have to line up with {@code slop.png}; the
 * player's inventory is the usual one, 84 down and a hotbar at 142.
 *
 * <p>Two of its jobs change owner. {@code transferStackInSlot} was a {@code LeafiaItemTransferable}
 * chain that moves a stack out of the machine into the inventory, and out of the inventory into the
 * machine's slots: that is exactly what {@link ContainerBase#quickMoveStack} does, so it is
 * inherited, as the boiler's was (D-1054). And {@code IContainerOpenEventListener} - the callback
 * that put the player on the block entity's listener list - is the constructor here, with
 * {@link #removed} to take them off; vanilla opens the menu on the server by constructing it, so
 * there is nothing to listen for. The client builds the same menu from a payload and must <em>not</em>
 * register itself, or the client's copy of the block entity would "send" to a player it is not
 * connected to.
 */
public class SlopMenu extends ContainerBase {

	/** The block entity the screen reads, or null if the client could not find it. */
	@Nullable
	public final SlopTE slop;

	/** Client side: the payload carries the reactor's position. */
	public SlopMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
		this(containerId, playerInventory, find(playerInventory, buf.readBlockPos()));
	}

	public SlopMenu(int containerId, Inventory playerInventory, @Nullable SlopTE slop) {

		super(LeafiaMenuTypes.SLOP.get(), containerId, playerInventory,
				slop != null ? slop : new SimpleContainer(SlopTE.SLOTS));

		this.slop = slop;

		// Upstream's two, in upstream's order and at upstream's coordinates.
		this.addSlot(new Slot(this.tile, SlopTE.SLOT_FUEL, 8, 35 - 18));
		this.addSlot(new Slot(this.tile, SlopTE.SLOT_COOLANT, 80, 53));

		this.playerInv(playerInventory, 8, 84, 142);

		// Upstream's onContainerOpened. Only the server's menu has a real listener list to join.
		if(slop != null && !playerInventory.player.level().isClientSide)
			slop.listeners.add(playerInventory.player);
	}

	/** Upstream's {@code onContainerClosed}. */
	@Override
	public void removed(Player player) {
		if(slop != null) slop.listeners.remove(player);
		super.removed(player);
	}

	@Nullable
	private static SlopTE find(Inventory playerInventory, BlockPos pos) {
		return playerInventory.player.level().getBlockEntity(pos) instanceof SlopTE slop ? slop : null;
	}
}
