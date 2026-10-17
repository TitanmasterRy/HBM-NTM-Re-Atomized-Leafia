package com.leafia.contents.machines.powercores.ams.base.container;

import javax.annotation.Nullable;

import com.hbm.inventory.SlotNonRetarded;
import com.leafia.contents.machines.powercores.ams.AMSMenuBase;
import com.leafia.contents.machines.powercores.ams.base.AMSBaseTE;
import com.leafia.init.LeafiaMenuTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;

/**
 * The AMC base's menu: eight canister slots down the sides, four catalysts round a core in the
 * middle, three satellite chips under it, and the player.
 *
 * <p>Upstream: {@code .../ams/base/container/AMSBaseContainer.java}. The slot coordinates are
 * upstream's and have to match {@code gui_base.png}: coolant in and out at x = 8 and x = 152
 * (y = 18 and 54), fuel under them (y = 72 and 108), the catalysts in a diamond at (80, 45),
 * (62, 63), (98, 63) and (80, 81), the core at (80, 63), the chips along y = 108 from x = 62, the
 * inventory at 140 and the hotbar at 198.
 *
 * <p>Slots take anything by hand - upstream's {@code SlotItemHandler} over an unfiltered handler -
 * and the base mod's {@code SlotNonRetarded} is only how this port asks the block entity, which
 * says yes. The shift-click and the three window properties (heat 0, warning 2, mode 4) are
 * {@link AMSMenuBase}'s. The client constructor reads the core's position from the payload and
 * finds the block entity the screen reads, which {@code LeafiaPacket} keeps up to date.
 */
public class AMSBaseMenu extends AMSMenuBase {

	/** The block entity the screen reads, or null if the client could not find it. */
	@Nullable
	public final AMSBaseTE base;

	/** Client side: the payload carries the core's position. */
	public AMSBaseMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
		this(containerId, playerInventory, find(playerInventory, buf.readBlockPos()));
	}

	public AMSBaseMenu(int containerId, Inventory playerInventory, @Nullable AMSBaseTE base) {

		super(LeafiaMenuTypes.AMS_BASE.get(), containerId, playerInventory,
				base != null ? base : new SimpleContainer(AMSBaseTE.SLOTS));

		this.base = base;

		// Cool 1 in, out; cool 2 in, out; fuel 1 in, out; fuel 2 in, out
		this.addSlot(new SlotNonRetarded(this.tile, 0, 8, 18));
		this.addSlot(new SlotNonRetarded(this.tile, 1, 8, 54));
		this.addSlot(new SlotNonRetarded(this.tile, 2, 152, 18));
		this.addSlot(new SlotNonRetarded(this.tile, 3, 152, 54));
		this.addSlot(new SlotNonRetarded(this.tile, 4, 8, 72));
		this.addSlot(new SlotNonRetarded(this.tile, 5, 8, 108));
		this.addSlot(new SlotNonRetarded(this.tile, 6, 152, 72));
		this.addSlot(new SlotNonRetarded(this.tile, 7, 152, 108));
		// Moderator (the catalysts)
		this.addSlot(new SlotNonRetarded(this.tile, 8, 80, 45));
		this.addSlot(new SlotNonRetarded(this.tile, 9, 62, 63));
		this.addSlot(new SlotNonRetarded(this.tile, 10, 98, 63));
		this.addSlot(new SlotNonRetarded(this.tile, 11, 80, 81));
		// Core
		this.addSlot(new SlotNonRetarded(this.tile, 12, 80, 63));
		// Sat chips
		this.addSlot(new SlotNonRetarded(this.tile, 13, 62, 108));
		this.addSlot(new SlotNonRetarded(this.tile, 14, 62 + 18, 108));
		this.addSlot(new SlotNonRetarded(this.tile, 15, 62 + 36, 108));

		this.playerInv(playerInventory, 8, 84 + 56, 142 + 56);

		if(base != null) {
			track(() -> base.heat, v -> base.heat = v);
			track(() -> base.warning, v -> base.warning = v);
			track(() -> base.mode, v -> base.mode = v);
		}
	}

	@Nullable
	private static AMSBaseTE find(Inventory playerInventory, BlockPos pos) {
		return playerInventory.player.level().getBlockEntity(pos) instanceof AMSBaseTE base ? base : null;
	}
}
