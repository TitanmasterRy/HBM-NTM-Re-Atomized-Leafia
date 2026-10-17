package com.leafia.contents.machines.powercores.ams.stabilizer.container;

import javax.annotation.Nullable;

import com.hbm.inventory.SlotNonRetarded;
import com.leafia.contents.machines.powercores.ams.AMSMenuBase;
import com.leafia.contents.machines.powercores.ams.stabilizer.AMSStabilizerTE;
import com.leafia.init.LeafiaMenuTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;

/**
 * The AMC stabilizer's menu: a fluid identifier in and out, the focus, a battery, and the player.
 *
 * <p>Upstream: {@code .../ams/stabilizer/container/AMSStabilizerContainer.java}, the emitter's
 * layout slot for slot. Window properties: heat (0), warning (2) and mode (3). See
 * {@link AMSMenuBase} for the shift-click and the slot rule.
 */
public class AMSStabilizerMenu extends AMSMenuBase {

	@Nullable
	public final AMSStabilizerTE stabilizer;

	/** Client side: the payload carries the core's position. */
	public AMSStabilizerMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
		this(containerId, playerInventory, find(playerInventory, buf.readBlockPos()));
	}

	public AMSStabilizerMenu(int containerId, Inventory playerInventory, @Nullable AMSStabilizerTE stabilizer) {

		super(LeafiaMenuTypes.AMS_STABILIZER.get(), containerId, playerInventory,
				stabilizer != null ? stabilizer : new SimpleContainer(AMSStabilizerTE.SLOTS));

		this.stabilizer = stabilizer;

		// Fluid in, out; the focus; a battery
		this.addSlot(new SlotNonRetarded(this.tile, 0, 44, 17));
		this.addSlot(new SlotNonRetarded(this.tile, 1, 44, 53));
		this.addSlot(new SlotNonRetarded(this.tile, 2, 80, 53));
		this.addSlot(new SlotNonRetarded(this.tile, 3, 116, 53));

		this.playerInv(playerInventory, 8, 84, 142);

		if(stabilizer != null) {
			track(() -> stabilizer.heat, v -> stabilizer.heat = v);
			track(() -> stabilizer.warning, v -> stabilizer.warning = v);
			track(() -> stabilizer.mode, v -> stabilizer.mode = v);
		}
	}

	@Nullable
	private static AMSStabilizerTE find(Inventory playerInventory, BlockPos pos) {
		return playerInventory.player.level().getBlockEntity(pos) instanceof AMSStabilizerTE stabilizer ? stabilizer : null;
	}
}
