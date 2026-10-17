package com.leafia.contents.machines.heat.hpboiler.container;

import javax.annotation.Nullable;

import com.hbm.inventory.container.ContainerBase;
import com.leafia.contents.machines.heat.hpboiler.HPBoilerTE;
import com.leafia.init.LeafiaMenuTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * The high-pressure boiler's menu: four canister slots, a fluid-identifier slot, and the player.
 *
 * <p>Upstream: {@code .../hpboiler/container/HPBoilerContainer.java}. The five slot positions are
 * upstream's own pixel coordinates, because they have to line up with {@code gui.png}.
 *
 * <h2>This is where the broof's note comes due, and the answer is not to hoist</h2>
 *
 * {@code BroofMenu}'s class doc said a shared addon menu base should hoist when a second addon
 * menu landed. This is that second one, and hoisting would be wrong: the broof's inventory is an
 * {@code ItemStackHandler} and this machine's is the base mod's {@code ItemStack[] slots}, so
 * they have <em>different</em> bases available. This one extends the base mod's
 * {@link ContainerBase} — which already does the slot helpers and the shift-click — and the
 * broof cannot, for the reason its own doc gives. Two menus, two right answers, and nothing to
 * share; the hoist waits for a third that actually matches one of them.
 *
 * <h2>The client has to find the block entity</h2>
 *
 * Everything the screen draws — heat, both tanks, the chain length — is synced to the
 * <em>block entity</em> by {@code LeafiaPacket}, not to the menu, so the client needs the real
 * {@link HPBoilerTE} rather than a stand-in. {@code ServerPlayer.openMenu(provider, pos)} writes
 * the position into the payload for exactly this, and the client constructor reads it back and
 * looks the block entity up. Where that fails the menu still opens on an empty container, so a
 * desync costs a blank screen rather than a crash.
 */
public class HPBoilerMenu extends ContainerBase {

	/** The block entity the screen reads, or null if the client could not find it. */
	@Nullable
	public final HPBoilerTE boiler;

	/** Client side: the payload carries the core's position. */
	public HPBoilerMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
		this(containerId, playerInventory, find(playerInventory, buf.readBlockPos()));
	}

	public HPBoilerMenu(int containerId, Inventory playerInventory, @Nullable HPBoilerTE boiler) {

		super(LeafiaMenuTypes.HP_BOILER.get(), containerId, playerInventory,
				boiler != null ? boiler : new SimpleContainer(HPBoilerTE.SLOTS));

		this.boiler = boiler;

		// Upstream's five, in upstream's order and at upstream's coordinates.
		this.addSlot(new Slot(this.tile, 0, 26, 17));
		this.addSlot(new Slot(this.tile, 1, 26, 53));
		this.addSlot(new Slot(this.tile, 2, 134, 17));
		this.addSlot(new Slot(this.tile, 3, 134, 53));
		this.addSlot(new Slot(this.tile, HPBoilerTE.SLOT_IDENTIFIER, 71, 53));

		// Upstream: three rows from y=103, hotbar at y=161.
		this.playerInv(playerInventory, 8, 103, 161);
	}

	@Nullable
	private static HPBoilerTE find(Inventory playerInventory, BlockPos pos) {
		return playerInventory.player.level().getBlockEntity(pos) instanceof HPBoilerTE boiler ? boiler : null;
	}
}
