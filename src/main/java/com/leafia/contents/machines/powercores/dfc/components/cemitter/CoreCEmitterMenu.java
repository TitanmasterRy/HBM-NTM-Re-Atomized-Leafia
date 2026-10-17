package com.leafia.contents.machines.powercores.dfc.components.cemitter;

import com.hbm.inventory.container.ContainerBase;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.init.LeafiaBlockEntities;
import com.leafia.init.LeafiaMenuTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;

/**
 * The creative booster's menu: the player's inventory, and its on switch as a synced value.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/powercores/dfc/components/cemitter/CoreCEmitterContainer.java}:
 * the inventory from y = 88 and the hotbar at 146, and window property 3, {@code isOn}, sent on open and on
 * change - here a {@link DataSlot}, which is the same thing. Shift-click moves nothing, upstream's.
 */
public class CoreCEmitterMenu extends ContainerBase {

	public final CoreCEmitterTE emitter;

	public CoreCEmitterMenu(int containerId, Inventory invPlayer, RegistryFriendlyByteBuf buf) {
		this(containerId, invPlayer, resolve(invPlayer, buf.readBlockPos()));
	}

	private static CoreCEmitterTE resolve(Inventory invPlayer, BlockPos pos) {
		if(invPlayer.player.level().getBlockEntity(pos) instanceof CoreCEmitterTE te) return te;
		return new CoreCEmitterTE(LeafiaBlockEntities.DFC_CEMITTER.get(), pos, LeafiaBlocks.dfc_cemitter.get().defaultBlockState());
	}

	public CoreCEmitterMenu(int containerId, Inventory invPlayer, CoreCEmitterTE emitter) {
		super(LeafiaMenuTypes.DFC_CEMITTER.get(), containerId, invPlayer, emitter);
		this.emitter = emitter;
		this.playerInv(invPlayer, 8, 88, 146);
		this.addDataSlot(new DataSlot() {
			@Override public int get() { return emitter.isOn ? 1 : 0; }
			@Override public void set(int value) { emitter.isOn = value > 0; }
		});
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		return ItemStack.EMPTY;
	}
}
