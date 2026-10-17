package com.leafia.contents.machines.powercores.ams.emitter.container;

import javax.annotation.Nullable;

import com.hbm.inventory.SlotNonRetarded;
import com.leafia.contents.machines.powercores.ams.AMSMenuBase;
import com.leafia.contents.machines.powercores.ams.emitter.AMSEmitterTE;
import com.leafia.init.LeafiaMenuTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;

/**
 * The AMC emitter's menu: a fluid identifier in and out, the muzzle, a battery, and the player.
 *
 * <p>Upstream: {@code .../ams/emitter/container/AMSEmitterContainer.java}. Slots at (44, 17),
 * (44, 53), (80, 53) and (116, 53), the inventory at 84 and the hotbar at 142, to match
 * {@code gui_emitter.png}. Window properties: heat (0) and warning (2). See {@link AMSMenuBase}
 * for the shift-click and the slot rule.
 */
public class AMSEmitterMenu extends AMSMenuBase {

	@Nullable
	public final AMSEmitterTE emitter;

	/** Client side: the payload carries the core's position. */
	public AMSEmitterMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
		this(containerId, playerInventory, find(playerInventory, buf.readBlockPos()));
	}

	public AMSEmitterMenu(int containerId, Inventory playerInventory, @Nullable AMSEmitterTE emitter) {

		super(LeafiaMenuTypes.AMS_EMITTER.get(), containerId, playerInventory,
				emitter != null ? emitter : new SimpleContainer(AMSEmitterTE.SLOTS));

		this.emitter = emitter;

		// Fluid in, out; the muzzle; a battery
		this.addSlot(new SlotNonRetarded(this.tile, 0, 44, 17));
		this.addSlot(new SlotNonRetarded(this.tile, 1, 44, 53));
		this.addSlot(new SlotNonRetarded(this.tile, 2, 80, 53));
		this.addSlot(new SlotNonRetarded(this.tile, 3, 116, 53));

		this.playerInv(playerInventory, 8, 84, 142);

		if(emitter != null) {
			track(() -> emitter.heat, v -> emitter.heat = v);
			track(() -> emitter.warning, v -> emitter.warning = v);
		}
	}

	@Nullable
	private static AMSEmitterTE find(Inventory playerInventory, BlockPos pos) {
		return playerInventory.player.level().getBlockEntity(pos) instanceof AMSEmitterTE emitter ? emitter : null;
	}
}
