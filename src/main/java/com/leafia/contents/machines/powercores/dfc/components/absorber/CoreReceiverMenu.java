package com.leafia.contents.machines.powercores.dfc.components.absorber;

import com.hbm.blocks.ModBlocks;
import com.hbm.inventory.container.ContainerBase;
import com.leafia.init.LeafiaBlockEntities;
import com.leafia.init.LeafiaMenuTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The overhauled absorber's menu: the player's inventory and nothing else, and a live feed of what the
 * absorber was paid, its power and its level for as long as it is open.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/powercores/dfc/components/absorber/CoreReceiverContainer.java}.
 * The inventory sits 14 to the right of the usual column and 2 lower (x 22, y 86, the hotbar at 144),
 * because the screen is 205 wide. Upstream's {@code addListener} and {@code detectAndSendChanges} both send
 * {@code leafia$sendToPlayer}; here the first {@link #broadcastChanges()} after opening is that first send.
 * Shift-click moves nothing, upstream's.
 */
public class CoreReceiverMenu extends ContainerBase {

	public final LeafiaCoreReceiverTE receiver;
	private final Player player;

	public CoreReceiverMenu(int containerId, Inventory invPlayer, RegistryFriendlyByteBuf buf) {
		this(containerId, invPlayer, resolve(invPlayer, buf.readBlockPos()));
	}

	private static LeafiaCoreReceiverTE resolve(Inventory invPlayer, BlockPos pos) {
		if(invPlayer.player.level().getBlockEntity(pos) instanceof LeafiaCoreReceiverTE te) return te;
		return new LeafiaCoreReceiverTE(LeafiaBlockEntities.DFC_RECEIVER.get(), pos, ModBlocks.dfc_receiver.get().defaultBlockState());
	}

	public CoreReceiverMenu(int containerId, Inventory invPlayer, LeafiaCoreReceiverTE receiver) {
		super(LeafiaMenuTypes.DFC_RECEIVER.get(), containerId, invPlayer, receiver);
		this.receiver = receiver;
		this.player = invPlayer.player;
		this.playerInv(invPlayer, 8 + 14, 84 + 2, 142 + 2);
	}

	@Override
	public void broadcastChanges() {
		if(!player.level().isClientSide) receiver.leafia$sendToPlayer(player);
		super.broadcastChanges();
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		return ItemStack.EMPTY;
	}
}
