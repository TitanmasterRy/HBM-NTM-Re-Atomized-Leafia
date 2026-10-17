package com.leafia.overwrite_contents.difurnace;

import com.hbm.blockentity.machine.BlockEntityDiFurnaceRTG;
import com.hbm.blocks.ModBlocks;
import com.hbm.inventory.container.ContainerMachineDiFurnaceRTG;
import com.hbm.items.machine.ItemRTGPellet;
import com.hbm.util.InventoryUtil;
import com.leafia.contents.control.fuel.nuclearfuel.LeafiaRodItem;
import com.leafia.init.LeafiaBlockEntities;
import com.leafia.init.LeafiaMenuTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The RTG blast furnace's menu with upstream's fuel-rod slot at (4, 36), added after the player's.
 *
 * <p>Upstream: {@code com/leafia/overwrite_contents/mixin/mod/hbm/MixinContainerDiFurnaceRTG.java}, which
 * appends {@code new SlotItemHandler(te.inventory, 9, 4, 36)} at the tail of CE's constructor - so the rod
 * slot is menu index {@value #ROD_INDEX}, after the base furnace's nine and the player's 36. Built on the base
 * menu's protected constructor, under its own type.
 *
 * <p>The shift-click is the base furnace's with the rod slot fitted in: CE's generic {@code transferStack}
 * knows nothing of a slot past the player's, and the base mod's would send a stack shift-clicked out of the
 * furnace into the rod slot before the player's hotbar (it merges into {@code 9 .. end} from the end). Here
 * the furnace's slots and the rod slot empty into the player's inventory, an addon rod shift-clicked from the
 * inventory goes into the rod slot, and everything else is the base menu's rule. A port choice; upstream's
 * behaviour at index 45 was whatever CE's helper did with an index it did not expect.
 */
public class LeafiaDiFurnaceRTGMenu extends ContainerMachineDiFurnaceRTG {

	/** The rod slot's menu index. */
	public static final int ROD_INDEX = 9 + 36;

	public LeafiaDiFurnaceRTGMenu(int containerId, Inventory invPlayer, RegistryFriendlyByteBuf buf) {
		this(containerId, invPlayer, resolve(invPlayer, buf.readBlockPos()));
	}

	private static BlockEntityDiFurnaceRTG resolve(Inventory invPlayer, BlockPos pos) {
		if(invPlayer.player.level().getBlockEntity(pos) instanceof LeafiaDiFurnaceRTGTE furnace) return furnace;
		// A detached one, as the base menu's resolve does, so a desynced screen opens empty rather than crashing.
		return new LeafiaDiFurnaceRTGTE(LeafiaBlockEntities.DIFURNACE_RTG.get(), pos,
				ModBlocks.machine_difurnace_rtg_off.get().defaultBlockState());
	}

	public LeafiaDiFurnaceRTGMenu(int containerId, Inventory invPlayer, BlockEntityDiFurnaceRTG teIn) {
		super(LeafiaMenuTypes.DIFURNACE_RTG.get(), containerId, invPlayer, teIn);
		// Leafia rod
		this.addSlot(new Slot(teIn, LeafiaDiFurnaceRTGTE.ROD_SLOT, 4, 36));
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		ItemStack original = ItemStack.EMPTY;
		Slot slot = this.slots.get(index);

		if(slot != null && slot.hasItem()) {
			ItemStack stack = slot.getItem();
			original = stack.copy();

			if(index <= 8 || index == ROD_INDEX) {
				if(!InventoryUtil.mergeItemStack(this.slots, stack, 9, ROD_INDEX, true)) {
					return ItemStack.EMPTY;
				}
			} else if(stack.getItem() instanceof LeafiaRodItem) {
				if(!InventoryUtil.mergeItemStack(this.slots, stack, ROD_INDEX, ROD_INDEX + 1, false))
					return ItemStack.EMPTY;
			} else if(stack.getItem() instanceof ItemRTGPellet) {
				if(!InventoryUtil.mergeItemStack(this.slots, stack, 3, 9, false))
					return ItemStack.EMPTY;
			} else if(!InventoryUtil.mergeItemStack(this.slots, stack, 0, 3, false)) {
				return ItemStack.EMPTY;
			}

			if(stack.getCount() == 0) {
				slot.set(ItemStack.EMPTY);
			} else {
				slot.setChanged();
			}
		}

		return original;
	}
}
