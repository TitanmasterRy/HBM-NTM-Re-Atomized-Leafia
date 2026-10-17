package com.leafia.contents.machines.processing.mixingvat.container;

import javax.annotation.Nullable;

import com.hbm.inventory.SlotNonRetarded;
import com.hbm.inventory.SlotTakeOnly;
import com.leafia.contents.machines.processing.mixingvat.MixingVatTE;
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
 * The mixing vat's menu: a battery, two upgrade slots, an identifier in and out, six ingredient slots, and the player.
 *
 * <p>Upstream: {@code .../mixingvat/container/MixingVatContainer.java}, on {@link LeafiaItemTransferable} - one of the
 * three menus that need its builder, because its shift-click goes to one of two ranges by mode. The slot positions are
 * upstream's, to match {@code mixingvat.png}: the battery at (186, 55) and two upgrades at (186, 86) and (186, 104); in
 * nuclear mode the salt-in slot at (44, 17), the take-only one at (44, 53), the 3 x 2 ingredients from (71, 17), and the
 * identifier slot (7, 17) with its take-only partner (7, 53); the inventory at 121 and the hotbar at 179. Without nuclear
 * mode there are only the first three.
 *
 * <h2>Upstream's shift-click, kept (D-021): it never reaches the ingredients</h2>
 *
 * <pre>
 *   LeafiaItemTransfer(3 + 10 + 8): a click in slots 0 to 20 -&gt; the inventory (slots 21 up), from the far end;
 *                                    a click from slot 20 on     -&gt; slots 0 to 2, then (non-nuclear) 3 to 12,
 *                                                                  or (nuclear) 13 to 20.
 * </pre>
 *
 * Upstream counts the machine as <b>21 slots</b> - its inventory has 23 - but the menu holds only the slots
 * it added: 13 in nuclear mode (and only 3 without it), with the player's 36 after them. So 21 is a slot of the player's
 * inventory, "13 to 20" are eight more of them, and a stack shift-clicked from the inventory goes to slots 0 to 2 (a battery
 * slot and two upgrade slots, which take anything) and otherwise shuffles inside the inventory. A click in the first eight
 * inventory slots is treated as a click in the machine and goes to the rest of the inventory. The ingredients, the identifier
 * and the salt slot are filled by hand. The numbers are the whole of the chain, so they are kept, and pinned.
 *
 * <p>The client builds the same menu from the position the server sent; where it cannot find the vat it opens on an empty
 * container.
 */
public class MixingVatMenu extends LeafiaItemTransferable {

	/** The block entity the screen reads, or null if the client could not find it. */
	@Nullable
	public final MixingVatTE vat;

	private final Container tile;

	/** Client side: the payload carries the core's position. */
	public MixingVatMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
		this(containerId, playerInventory, find(playerInventory, buf.readBlockPos()));
	}

	public MixingVatMenu(int containerId, Inventory playerInventory, @Nullable MixingVatTE vat) {

		super(LeafiaMenuTypes.MIXING_VAT.get(), containerId);

		this.vat = vat;
		this.tile = vat != null ? vat : new SimpleContainer(MixingVatTE.SLOTS);

		// Battery
		this.addSlot(new SlotNonRetarded(tile, 0, 186, 55));

		// Upgrades
		this.addSlot(new SlotNonRetarded(tile, 1, 186, 86));
		this.addSlot(new SlotNonRetarded(tile, 2, 186, 104));

		if(vat != null && !vat.nuclearMode) {
			// Upstream's other mode has no slots of its own.
		} else {
			// Fluid IO
			this.addSlot(new SlotNonRetarded(tile, 13, 44, 17));
			this.addSlot(new SlotTakeOnly(tile, 14, 44, 53));
			for(int x = 0; x <= 2; x++) {
				for(int y = 0; y <= 1; y++)
					this.addSlot(new SlotNonRetarded(tile, 15 + x + y * 3, 71 + x * 18, 17 + y * 18));
			}
			this.addSlot(new SlotNonRetarded(tile, 21, 7, 17));
			this.addSlot(new SlotTakeOnly(tile, 22, 7, 53));
		}

		// Inventory
		for(int i = 0; i < 3; i++) {
			for(int j = 0; j < 9; j++)
				this.addSlot(new SlotNonRetarded(playerInventory, j + i * 9 + 9, 8 + j * 18, 121 + i * 18));
		}
		for(int i = 0; i < 9; i++)
			this.addSlot(new SlotNonRetarded(playerInventory, i, 8 + i * 18, 179));
	}

	@Nullable
	private static MixingVatTE find(Inventory playerInventory, BlockPos pos) {
		return playerInventory.player.level().getBlockEntity(pos) instanceof MixingVatTE vat ? vat : null;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		LeafiaItemTransfer transfer = new LeafiaItemTransfer(3 + 10 + 8)._selected(index);
		transfer.__forSlots(0, 9999)
				.__tryMoveToInventory(true)

				.__forInventory()

				.__tryMoveToSlot(0, 2, false);
		if(vat != null && !vat.nuclearMode)
			return transfer.__tryMoveToSlot(3, 12, false)
					.__getReturn();
		else
			return transfer.__tryMoveToSlot(13, transfer.__maxIndex, false)
					.__getReturn();
	}

	@Override
	public boolean stillValid(Player player) {
		return tile.stillValid(player);
	}
}
