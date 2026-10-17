package com.leafia.contents.machines.powercores.dfc.components.pulser;

import javax.annotation.Nullable;

import com.hbm.inventory.container.ContainerBase;
import com.leafia.init.LeafiaMenuTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The detonator's menu: no slots of its own, the player's inventory at y = 88 and the hotbar at 146.
 *
 * <p>Upstream: {@code .../components/pulser/CoreDetonatorContainer.java}, on {@code LeafiaItemTransferable}
 * but overriding the shift-click to do nothing at all - D-3000 names it as the one container behind that
 * base that never moves anything - so it is a plain {@link ContainerBase} here with the same override.
 */
public class CoreDetonatorMenu extends ContainerBase {

	@Nullable
	public final CoreDetonatorTE te;

	public CoreDetonatorMenu(int containerId, Inventory invPlayer, RegistryFriendlyByteBuf buf) {
		this(containerId, invPlayer, find(invPlayer, buf.readBlockPos()));
	}

	@Nullable
	private static CoreDetonatorTE find(Inventory invPlayer, BlockPos pos) {
		return invPlayer.player.level().getBlockEntity(pos) instanceof CoreDetonatorTE te ? te : null;
	}

	public CoreDetonatorMenu(int containerId, Inventory invPlayer, @Nullable CoreDetonatorTE te) {
		super(LeafiaMenuTypes.DFC_PULSER.get(), containerId, invPlayer, te != null ? te : new SimpleContainer(0));
		this.te = te;
		this.playerInv(invPlayer, 8, 88, 146);
	}

	/** Upstream's: nothing is ever shift-clicked anywhere. */
	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		return ItemStack.EMPTY;
	}
}
