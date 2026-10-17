package com.leafia.contents.bomb.missile.customnuke.container;

import com.leafia.contents.bomb.missile.customnuke.CustomNukeMissileItem;
import com.leafia.contents.bomb.missile.customnuke.CustomNukeMissileItem.CustomNukeMissileInventory;
import com.leafia.init.LeafiaMenuTypes;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * The custom nuke missile's 27 slots and the player's inventory (D-3022).
 *
 * <p>Upstream: {@code com/leafia/contents/bomb/missile/customnuke/container/CustomNukeMissileContainer.java} - three
 * rows of nine from (8, 18), the player's inventory 56 lower than a chest's. The missile in the main hand cannot be
 * moved while its own screen is open, neither clicked nor swapped onto with its hotbar number; one in the off hand
 * can (upstream checks the main hand only). Shift-click is the generic machine rule upstream's
 * {@code TransferStrategy.builder(27).genericMachineRange(0)} built: a missile slot to the player, anything else
 * into the missile's slots. The payload is the hand holding the missile.
 */
public class CustomNukeMissileContainer extends AbstractContainerMenu {

	public final CustomNukeMissileInventory nukeBoy;
	private final boolean isMainHand;

	public CustomNukeMissileContainer(int id, Inventory invPlayer, InteractionHand hand) {
		super(LeafiaMenuTypes.CUSTOM_NUKE_MISSILE.get(), id);
		ItemStack box = invPlayer.player.getItemInHand(hand);
		this.nukeBoy = new CustomNukeMissileInventory(box, invPlayer.player.registryAccess());
		this.isMainHand = hand == InteractionHand.MAIN_HAND;

		for(int row = 0; row < 3; row++)
			for(int col = 0; col < 9; col++)
				this.addSlot(new SlotItemHandler(nukeBoy, col + row * 9, 8 + col * 18, 18 + row * 18));

		for(int i = 0; i < 3; i++)
			for(int j = 0; j < 9; j++)
				this.addSlot(new Slot(invPlayer, j + i * 9 + 9, 8 + j * 18, 84 + i * 18 + 56));

		for(int i = 0; i < 9; i++)
			this.addSlot(new Slot(invPlayer, i, 8 + i * 18, 142 + 56));
	}

	public CustomNukeMissileContainer(int id, Inventory invPlayer, RegistryFriendlyByteBuf buf) {
		this(id, invPlayer, buf.readEnum(InteractionHand.class));
	}

	@Override
	public void clicked(int slotId, int dragType, ClickType clickType, Player player) {
		if(isMainHand) {
			if(clickType == ClickType.SWAP && dragType == player.getInventory().selected) return;
			int heldSlot = CustomNukeMissileItem.SLOTS + 27 + player.getInventory().selected;
			if(slotId == heldSlot) return;
		}
		super.clicked(slotId, dragType, clickType, player);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		ItemStack original = ItemStack.EMPTY;
		Slot slot = this.slots.get(index);
		if(slot != null && slot.hasItem()) {
			ItemStack stack = slot.getItem();
			original = stack.copy();
			int slots = CustomNukeMissileItem.SLOTS;
			if(index < slots) {
				if(!this.moveItemStackTo(stack, slots, this.slots.size(), true)) return ItemStack.EMPTY;
			} else {
				if(!this.moveItemStackTo(stack, 0, slots, false)) return ItemStack.EMPTY;
			}
			if(stack.isEmpty()) slot.set(ItemStack.EMPTY);
			else slot.setChanged();
		}
		return original;
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}
}
