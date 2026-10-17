package com.leafia.contents.machines.reactors.lftr.processing.separator.container;

import javax.annotation.Nullable;

import com.hbm.inventory.FluidContainerRegistry;
import com.hbm.inventory.FluidContainer;
import com.hbm.inventory.SlotCraftingOutput;
import com.hbm.inventory.SlotNonRetarded;
import com.hbm.inventory.container.ContainerBase;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemBlueprints;
import com.hbm.items.machine.ItemMachineUpgrade;
import com.hbm.util.InventoryUtil;
import com.leafia.contents.fluids.ff.LeafiaFF;
import com.leafia.contents.machines.reactors.lftr.processing.separator.SaltSeparatorTE;
import com.leafia.init.LeafiaMenuTypes;

import api.hbm.energymk2.IBatteryItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

/**
 * The salt separator's menu: battery, blueprint, two upgrades, then the five tank slot pairs.
 *
 * <p>Upstream: {@code .../separator/container/SaltSeparatorContainer.java}, a {@code ContainerBase}
 * with the chemical plant's layout minus its solid slots: the battery at (152, 81), the blueprint at
 * (35, 126), the upgrades in a column from (152, 108), the two input tanks' load slots at (26, 54) with
 * their take-only twins below at (26, 72), and the three output tanks' at (80, 54) and (80, 72). The
 * player's inventory starts at y = 174. The slots are indices 0 to 13 in that order.
 *
 * <h2>The shift-click chain is upstream's own</h2>
 *
 * Out of the machine goes to the player from the far end; into it, a battery goes to slot 0, a blueprint
 * to slot 1, a machine upgrade to the two upgrade slots, and anything else is sorted by what it is: a
 * <b>full</b> fluid container (NTM or Forge) into the input load slots 4 and 5, an <b>empty</b> one into
 * the output unload slots 8 to 10, and everything else goes nowhere. "NTM container" is upstream's
 * {@code NTMFluidCapabilityHandler.isNtmFluidContainer} - the item is any container the base mod's
 * {@code FluidContainerRegistry} knows, full or empty - and a "Forge container" is an item with a
 * NeoForge fluid handler capability. An item that is both is full if either says so.
 *
 * <p>Upstream builds this on {@code ContainerBase} directly, not on {@code LeafiaItemTransferable}, so
 * this does as well; the chain is not "machine out, then machine in" and is written out.
 *
 * <h2>The client finds the block entity</h2>
 *
 * As {@code HPBoilerMenu}: the payload is the core's position, and a failed lookup leaves a screen
 * over an empty inventory rather than a crash.
 */
public class SaltSeparatorMenu extends ContainerBase {

	/** The block entity the screen reads, or null if the client could not find it. */
	@Nullable
	public final SaltSeparatorTE separator;

	/** Client side: the payload carries the core's position. */
	public SaltSeparatorMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
		this(containerId, playerInventory, find(playerInventory, buf.readBlockPos()));
	}

	public SaltSeparatorMenu(int containerId, Inventory playerInventory, @Nullable SaltSeparatorTE separator) {

		super(LeafiaMenuTypes.SALT_SEPARATOR.get(), containerId, playerInventory,
				separator != null ? separator : new SimpleContainer(SaltSeparatorTE.SLOTS));

		this.separator = separator;

		// Battery
		this.addSlot(new SlotNonRetarded(this.tile, 0, 152, 81));
		// Schematic
		this.addSlot(new SlotNonRetarded(this.tile, 1, 35, 126));
		// Upgrades
		this.addSlots(this.tile, 2, 152, 108, 2, 1);
		// Fluid Input
		this.addSlots(this.tile, 4, 26, 54, 1, 2);
		this.addTakeOnlySlots(this.tile, 6, 26, 72, 1, 2);
		// Fluid Output
		this.addSlots(this.tile, 8, 80, 54, 1, 3);
		this.addTakeOnlySlots(this.tile, 11, 80, 72, 1, 3);

		this.playerInv(playerInventory, 8, 174);
	}

	@Nullable
	private static SaltSeparatorTE find(Inventory playerInventory, BlockPos pos) {
		return playerInventory.player.level().getBlockEntity(pos) instanceof SaltSeparatorTE separator ? separator : null;
	}

	/** Upstream's {@code isNtmFluidContainer}: an item the base mod's container registry lists, full or empty. */
	public static boolean isNtmContainer(ItemStack stack) {
		for(FluidContainer container : FluidContainerRegistry.allContainers) {
			if(container.fullContainer.getItem() == stack.getItem()) return true;
			if(!container.emptyContainer.isEmpty() && container.emptyContainer.getItem() == stack.getItem()) return true;
		}
		return false;
	}

	/** True for a container that holds fluid: by the NTM registry, or by a Forge handler holding any. */
	public static boolean isFluidFull(ItemStack stack) {
		boolean isNtm = isNtmContainer(stack);
		FluidType ntmType = isNtm ? FluidContainerRegistry.getFluidType(stack) : Fluids.NONE;
		int ntmAmount = (isNtm && ntmType != Fluids.NONE) ? FluidContainerRegistry.getFluidContent(stack, ntmType) : 0;
		int[] forge = forgeAmount(stack);
		return (isNtm && ntmAmount > 0) || (forge[0] == 1 && forge[1] > 0);
	}

	/** True for a container that holds none: an empty NTM container, or a Forge handler holding nothing. */
	public static boolean isFluidEmpty(ItemStack stack) {
		boolean isNtm = isNtmContainer(stack);
		FluidType ntmType = isNtm ? FluidContainerRegistry.getFluidType(stack) : Fluids.NONE;
		int ntmAmount = (isNtm && ntmType != Fluids.NONE) ? FluidContainerRegistry.getFluidContent(stack, ntmType) : 0;
		int[] forge = forgeAmount(stack);
		return (isNtm && (ntmType == Fluids.NONE || ntmAmount == 0)) || (forge[0] == 1 && forge[1] == 0);
	}

	/** {have a tank (1/0), total millibuckets} over a stack's NeoForge fluid handler. */
	private static int[] forgeAmount(ItemStack stack) {
		IFluidHandlerItem handler = LeafiaFF.itemHandler(stack);
		if(handler == null) return new int[] { 0, 0 };
		int amount = 0;
		boolean any = false;
		for(int i = 0; i < handler.getTanks(); i++) {
			any = true;
			FluidStack contents = handler.getFluidInTank(i);
			if(!contents.isEmpty() && contents.getAmount() > 0) amount += contents.getAmount();
		}
		return new int[] { any ? 1 : 0, amount };
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		ItemStack original = ItemStack.EMPTY;
		Slot slot = this.slots.get(index);

		if(slot != null && slot.hasItem()) {
			ItemStack stack = slot.getItem();
			original = stack.copy();

			int tileSize = tile.getContainerSize();

			if(index < tileSize) {
				SlotCraftingOutput.checkAchievements(player, stack);
				if(!InventoryUtil.mergeItemStack(this.slots, stack, tileSize, this.slots.size(), true)) {
					return ItemStack.EMPTY;
				}
			} else {
				if(original.getItem() instanceof IBatteryItem || original.is(ModItems.battery_creative.get())) {
					if(!InventoryUtil.mergeItemStack(this.slots, stack, 0, 1, false)) return ItemStack.EMPTY;
				} else if(original.getItem() instanceof ItemBlueprints) {
					if(!InventoryUtil.mergeItemStack(this.slots, stack, 1, 2, false)) return ItemStack.EMPTY;
				} else if(original.getItem() instanceof ItemMachineUpgrade) {
					if(!InventoryUtil.mergeItemStack(this.slots, stack, 2, 4, false)) return ItemStack.EMPTY;
				} else {
					if(isFluidFull(original)) {
						if(!InventoryUtil.mergeItemStack(this.slots, stack, 4, 6, false)) return ItemStack.EMPTY;
					} else if(isFluidEmpty(original)) {
						if(!InventoryUtil.mergeItemStack(this.slots, stack, 8, 11, false)) return ItemStack.EMPTY;
					} else {
						// Upstream's "fallback to solid input" is commented out: nothing else goes in.
						return ItemStack.EMPTY;
					}
				}
			}

			if(stack.isEmpty()) {
				slot.set(ItemStack.EMPTY);
			} else {
				slot.setChanged();
			}

			slot.onTake(player, stack);
		}

		return original;
	}
}
