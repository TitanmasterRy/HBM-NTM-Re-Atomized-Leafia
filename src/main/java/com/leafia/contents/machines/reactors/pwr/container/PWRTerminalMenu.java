package com.leafia.contents.machines.reactors.pwr.container;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.leafia.contents.machines.reactors.pwr.PWRData;
import com.leafia.contents.machines.reactors.pwr.blocks.components.PWRComponentEntity;
import com.leafia.contents.machines.reactors.pwr.blocks.components.element.PWRElementTE;
import com.leafia.contents.machines.reactors.pwr.blocks.components.terminal.PWRTerminalTE;
import com.leafia.dev.container_utility.LeafiaItemTransferable;
import com.leafia.init.LeafiaMenuTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * The PWR terminal's menu: the player's inventory, the core's three resource slots (a fluid identifier
 * that picks the coolant, and a canister in and out for the boiler's water), and one slot per fuel column
 * the reactor shows on the terminal - each the column's own rod slot, reached remotely.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/reactors/pwr/container/PWRTerminalContainer.java}, on
 * {@link LeafiaItemTransferable} with upstream's shift-click chain: from the inventory a stack goes into the
 * fuel columns (never the resource slots), and from any reactor slot back into the inventory, last slot
 * first. The slot order is upstream's: inventory 0-35, resources 36-38, columns from 39.
 *
 * <h2>The two sides agree on the columns by being told</h2>
 *
 * Upstream builds the column slots on each side from that side's own copy of the reactor's projection. Here
 * the server writes the column positions it built slots for into the opening packet ({@link #writeOpening})
 * and the client builds exactly those, over its own copy of each column - or over an empty stand-in where a
 * column has not reached the client, so the slot numbers still line up.
 *
 * <h2>Where a slot is drawn is the screen's business</h2>
 *
 * The coordinates given here are upstream's for the inventory and the resources, before the screen moves
 * them down by its computed offset; a column's are a distinct placeholder ({@link #remoteSlotX}), because
 * 1.21.1's slot coordinates are final and the screen only learns where the grid goes when it is laid out.
 * {@code PWRTerminalScreen} maps every slot to where it really is.
 *
 * <p>Still valid while the core keeps the same remote container (a re-diagnosis replaces it), its block
 * entity stands and still holds this core, and - not upstream's, which had no reach check, D-1094 - the
 * player is within reach of the terminal. Upstream's debug-wand chat line is not carried.
 */
public class PWRTerminalMenu extends LeafiaItemTransferable {

	public static final int RESOURCE_SLOTS = 3;
	public static final int FIRST_REMOTE = 9 * 4 + RESOURCE_SLOTS;

	static class ResourceSlot extends SlotItemHandler {
		ResourceSlot(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
			super(itemHandler, index, xPosition, yPosition);
		}
	}

	/** A fuel column's rod slot, at the column's place on the terminal's grid ({@link #localPos}). */
	public static class RemoteSlot extends SlotItemHandler {
		public final BlockPos localPos;

		RemoteSlot(IItemHandler itemHandler, int number, BlockPos localPos) {
			super(itemHandler, 0, remoteSlotX(number), REMOTE_SLOT_Y);
			this.localPos = localPos;
		}
	}

	/** The placeholder coordinates of the {@code number}th column slot (class note). */
	public static int remoteSlotX(int number) {
		return -10000 - number;
	}

	public static final int REMOTE_SLOT_Y = -10000;

	@Nullable
	public final PWRData core;
	@Nullable
	final ItemStackHandler remote;
	@Nullable
	final BlockEntity entity;
	public final BlockPos terminalPos;
	public final BlockState terminalState;
	/** The fuel columns, in slot order, in world coordinates. */
	public final List<BlockPos> fuels;
	final Player player;

	/** Server side. */
	public PWRTerminalMenu(int containerId, Inventory playerInventory, PWRTerminalTE terminal, PWRData core) {
		this(containerId, playerInventory, terminal.getBlockPos(), core, remoteFuels(core));
	}

	/** Client side: the payload is the terminal's position and the columns the server built slots for. */
	public PWRTerminalMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
		this(containerId, playerInventory, buf.readBlockPos(), null, readFuels(buf), playerInventory.player.level());
	}

	private PWRTerminalMenu(int containerId, Inventory playerInventory, BlockPos terminalPos, @Nullable PWRData core, List<BlockPos> fuels, Level clientLevel) {
		this(containerId, playerInventory, terminalPos, clientCore(clientLevel, terminalPos), fuels);
	}

	private PWRTerminalMenu(int containerId, Inventory playerInventory, BlockPos terminalPos, @Nullable PWRData core, List<BlockPos> fuels) {
		super(LeafiaMenuTypes.PWR_TERMINAL.get(), containerId);
		this.core = core;
		this.remote = core == null ? null : core.remoteContainer;
		this.entity = core == null ? null : core.companion;
		this.player = playerInventory.player;
		this.terminalPos = terminalPos;
		this.terminalState = player.level().getBlockState(terminalPos);
		this.fuels = fuels;

		// player slots
		for(int i = 0; i < 3; i++)
			for(int j = 0; j < 9; j++)
				this.addSlot(new Slot(playerInventory, j + i * 9 + 9, 53 + j * 18, 36 + i * 18));
		for(int i = 0; i < 9; i++)
			this.addSlot(new Slot(playerInventory, i, 53 + i * 18, 94));

		// pwr slots
		IItemHandler resources = core != null ? core.resourceContainer : new ItemStackHandler(RESOURCE_SLOTS);
		this.addSlot(new ResourceSlot(resources, 0, 5, 43));
		this.addSlot(new ResourceSlot(resources, 1, -22, 79));
		this.addSlot(new ResourceSlot(resources, 2, -22, 97));

		for(int i = 0; i < fuels.size(); i++) {
			BlockPos fuel = fuels.get(i);
			IItemHandler handler = player.level().getBlockEntity(fuel) instanceof PWRElementTE element ? element.inventory : new ItemStackHandler(1);
			BlockPos local = terminalState.hasProperty(HorizontalDirectionalBlock.FACING)
					? PWRData.terminalToLocal(terminalState, terminalPos, fuel) : BlockPos.ZERO;
			this.addSlot(new RemoteSlot(handler, i, local));
		}
	}

	/** The core the client's terminal links to, or null if it has not been told yet. */
	@Nullable
	private static PWRData clientCore(Level level, BlockPos terminalPos) {
		return level.getBlockEntity(terminalPos) instanceof PWRTerminalTE terminal ? terminal.getCoreByCorePos() : null;
	}

	/** The projection's fuel columns that have a column's block entity: what upstream builds slots for. */
	public static List<BlockPos> remoteFuels(PWRData core) {
		List<BlockPos> out = new ArrayList<>();
		for(BlockPos fuel : core.getProjectionFuelAndControlPositions().getKey())
			if(core.getWorld().getBlockEntity(fuel) instanceof PWRElementTE) out.add(fuel);
		return out;
	}

	/** The opening payload: the terminal, then the columns ({@link #remoteFuels}). */
	public static void writeOpening(RegistryFriendlyByteBuf buf, PWRTerminalTE terminal, PWRData core) {
		buf.writeBlockPos(terminal.getBlockPos());
		List<BlockPos> fuels = remoteFuels(core);
		buf.writeVarInt(fuels.size());
		for(BlockPos fuel : fuels) buf.writeBlockPos(fuel);
	}

	private static List<BlockPos> readFuels(RegistryFriendlyByteBuf buf) {
		int n = buf.readVarInt();
		List<BlockPos> out = new ArrayList<>(n);
		for(int i = 0; i < n; i++) out.add(buf.readBlockPos());
		return out;
	}

	/** Whether this menu is {@code terminal}'s, over {@code core}: what the terminal asks of a packet's sender. */
	public boolean isFor(PWRTerminalTE terminal, PWRData core) {
		return terminalPos.equals(terminal.getBlockPos()) && this.core == core;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		LeafiaItemTransfer transfer = new LeafiaItemTransfer(this.slots.size())._selected(index);
		return transfer.__forSlots(0, 9 * 4 - 1)
				.__tryMoveToSlot(9 * 4 + RESOURCE_SLOTS, this.slots.size() - 1, false)

				.__forSlots(9 * 4, this.slots.size() - 1)
				.__tryMoveToSlot(0, 9 * 4 - 1, true)

				.__getReturn();
	}

	@Override
	public boolean stillValid(Player player) {
		if(core == null) return true; // the client's view of a reactor that has not reached it yet
		if(core.remoteContainer != remote || entity == null || entity.isRemoved()) return false;
		if(!(entity instanceof PWRComponentEntity component) || component.getCore() != core) return false;
		return player.canInteractWithBlock(terminalPos, 4.0D);
	}
}
