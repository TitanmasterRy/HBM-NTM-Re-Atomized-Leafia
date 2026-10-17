package com.leafia.contents.machines.reactors.zirnox;

import javax.annotation.Nullable;

import com.hbm.inventory.SlotTakeOnly;
import com.leafia.dev.container_utility.LeafiaItemTransferable;
import com.leafia.init.LeafiaMenuTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The overhauled ZIRNOX's menu: the 24 fuel channels in upstream's staggered lattice, the CO2 and
 * feedwater canister slots, and the player.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/reactors/zirnox/ZirnoxContainer.java}. The channels are
 * placed by upstream's own arithmetic ({@link #channelX}/{@link #channelY}): two slot-widths apart along a
 * row, rows of three and four alternating, each row of four set back half a step. Slots 24 and 26 take a
 * CO2 and a water canister, 25 and 27 are take-only - CE's layout, which the block entity's
 * {@code loadTanks} reads.
 *
 * <p>Its shift-click is upstream's {@code LeafiaItemTransfer} chain, unchanged: anything in the machine goes
 * to the player's inventory from the far end, and anything in the player's inventory goes into the first
 * machine slot that will take it - which, for an addon rod or a base rod, is the first empty fuel channel.
 * The chain's class note describes its one quirk (the machine's last slot counts as the inventory).
 */
public class ZirnoxMenu extends LeafiaItemTransferable {

	/** The machine's slot count: 24 channels and four canister slots. */
	public static final int MACHINE_SLOTS = 28;

	@Nullable
	public final LeafiaZirnoxTE reactor;
	private final Container container;

	public ZirnoxMenu(int containerId, Inventory invPlayer, RegistryFriendlyByteBuf buf) {
		this(containerId, invPlayer, find(invPlayer, buf.readBlockPos()));
	}

	public ZirnoxMenu(int containerId, Inventory invPlayer, @Nullable LeafiaZirnoxTE reactor) {
		super(LeafiaMenuTypes.ZIRNOX.get(), containerId);
		this.reactor = reactor;
		this.container = reactor != null ? reactor : new SimpleContainer(MACHINE_SLOTS);

		// fuel slots
		for(int i = 0; i < 24; i++) {
			this.addSlot(new Slot(container, i, channelX(i), channelY(i)));
		}
		// coolant io
		this.addSlot(new Slot(container, 24, 6, 81));
		this.addSlot(new SlotTakeOnly(container, 25, 6, 99));
		// feedwater io
		this.addSlot(new Slot(container, 26, 193, 81));
		this.addSlot(new SlotTakeOnly(container, 27, 193, 99));

		// player slots
		for(int i = 0; i < 3; i++) {
			for(int j = 0; j < 9; j++) {
				this.addSlot(new Slot(invPlayer, j + i * 9 + 9, 27 + j * 18, 161 + i * 18));
			}
		}
		for(int i = 0; i < 9; i++) {
			this.addSlot(new Slot(invPlayer, i, 27 + i * 18, 219));
		}
	}

	/** Upstream's channel column: {@code 67 + xoffset * 16}. */
	public static int channelX(int i) {
		int xoffset = i % 7 * 2;
		if(i % 7 >= 3) xoffset -= 7;
		return 67 + xoffset * 16;
	}

	/** Upstream's channel row: {@code 20 + yoffset * 16}. */
	public static int channelY(int i) {
		int yoffset = i / 7 * 2;
		if(i % 7 >= 3) yoffset++;
		return 20 + yoffset * 16;
	}

	@Nullable
	private static LeafiaZirnoxTE find(Inventory invPlayer, BlockPos pos) {
		return invPlayer.player.level().getBlockEntity(pos) instanceof LeafiaZirnoxTE reactor ? reactor : null;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int clickIndex) {
		LeafiaItemTransfer transfer = new LeafiaItemTransfer(MACHINE_SLOTS)._selected(clickIndex);
		return transfer.__forSlots(0, 9999)
				.__tryMoveToInventory(true)

				.__forInventory()
				.__tryMoveToSlot(0, transfer.__maxIndex, false)

				.__getReturn();
	}

	@Override
	public boolean stillValid(Player player) {
		return container.stillValid(player);
	}
}
