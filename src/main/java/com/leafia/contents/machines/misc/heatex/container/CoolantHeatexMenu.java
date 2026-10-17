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
 * <h2>The chain (D-3076)</h2>
 *
 * <pre>
 *   LeafiaItemTransfer(2): any machine slot -&gt; the inventory, from the far end;
 *                          the inventory   -&gt; the battery slot (batteries only), then the identifier slot.
 * </pre>
 *
 * Upstream's chain asked for "slots 0 to 2" and then "3 to the last", counting the machine as three slots wide, so the
 * third was the first slot of the player's own inventory and a shift-click could shuffle within it. The chain stops at the
 * machine's two slots now.
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

				.__tryMoveToSlot(1, 1, false) // the battery slot takes only batteries, so it goes first
				.__tryMoveToSlot(0, 0, false);
		return transfer.__getReturn();
	}

	@Override
	public boolean stillValid(Player player) {
		return tile.stillValid(player);
	}
}
