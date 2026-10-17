package com.leafia.contents.machines.misc.heatex.container;

import javax.annotation.Nullable;

import com.hbm.inventory.SlotNonRetarded;
import com.leafia.contents.machines.misc.heatex.CoolantHeatexTE;
import com.leafia.dev.container_utility.LeafiaItemTransferable;
import com.leafia.init.LeafiaMenuTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The coolant heat exchanger's menu: a fluid identifier, a battery and the player.
 *
 * <p>Upstream: {@code .../heatex/container/CoolantHeatexContainer.java}, on
 * {@link LeafiaItemTransferable} - one of the three menus that need its builder, because its shift-click chain is
 * not "machine out, then machine in". The slot positions are upstream's: the identifier at (17, 72), the battery
 * at (152, 72), the inventory at 122 and the hotbar at 180, to line up with {@code coolant_heatex.png}.
 *
 * <h2>The chain, upstream's numbers (D-021)</h2>
 *
 * <pre>
 *   LeafiaItemTransfer(2): any machine slot -&gt; the inventory, from the far end;
 *                          the inventory   -&gt; slots 0 to 2, then 3 to the machine's last slot (1: nothing).
 * </pre>
 *
 * Slots 0 and 1 are the machine's; <b>slot 2 is the first slot of the player's inventory</b>, which upstream's
 * "first three" reaches because it counted the machine as three slots wide. So a stack shift-clicked in the inventory
 * goes to the identifier slot if it will go there (anything is accepted), else to the battery slot (batteries only), else
 * onto the first inventory slot, which is a move within the inventory. Kept as it is: the numbers are the only thing the chain is.
 *
 * <p>The client builds the same menu from a payload naming the block entity's position, and where it cannot find it
 * the menu opens on an empty container: a desync costs a blank screen rather than a crash.
 */
public class CoolantHeatexMenu extends LeafiaItemTransferable {

	/** The block entity the screen reads, or null if the client could not find it. */
	@Nullable
	public final CoolantHeatexTE heatex;

	private final Container tile;

	/** Client side: the payload carries the core's position. */
	public CoolantHeatexMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
		this(containerId, playerInventory, find(playerInventory, buf.readBlockPos()));
	}

	public CoolantHeatexMenu(int containerId, Inventory playerInventory, @Nullable CoolantHeatexTE heatex) {

		super(LeafiaMenuTypes.COOLANT_HEATEX.get(), containerId);

		this.heatex = heatex;
		this.tile = heatex != null ? heatex : new SimpleContainer(CoolantHeatexTE.SLOTS);

		// Fluid identifier
		this.addSlot(new SlotNonRetarded(tile, CoolantHeatexTE.SLOT_IDENTIFIER, 17, 72));
		// Battery
		this.addSlot(new SlotNonRetarded(tile, CoolantHeatexTE.SLOT_BATTERY, 152, 72));

		// Inventory
		for(int i = 0; i < 3; i++) {
			for(int j = 0; j < 9; j++)
				this.addSlot(new SlotNonRetarded(playerInventory, j + i * 9 + 9, 8 + j * 18, 122 + i * 18));
		}
		for(int i = 0; i < 9; i++)
			this.addSlot(new SlotNonRetarded(playerInventory, i, 8 + i * 18, 180));
	}

	@Nullable
	private static CoolantHeatexTE find(Inventory playerInventory, BlockPos pos) {
		return playerInventory.player.level().getBlockEntity(pos) instanceof CoolantHeatexTE heatex ? heatex : null;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		LeafiaItemTransfer transfer = new LeafiaItemTransfer(2)._selected(index);
		transfer.__forSlots(0, 9999)
				.__tryMoveToInventory(true)

				.__forInventory()

				.__tryMoveToSlot(0, 2, false);
		return transfer.__tryMoveToSlot(3, transfer.__maxIndex, false)
				.__getReturn();
	}

	@Override
	public boolean stillValid(Player player) {
		return tile.stillValid(player);
	}
}
