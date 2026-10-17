package com.leafia.contents.machines.processing.solblaster;

import com.leafia.passive.effects.LeafiaShakecam;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.hbm.blockentity.BlockEntityMachineBase;
import com.hbm.items.ModItems;
import com.hbm.packet.toclient.AuxParticlePacketNT;
import com.hbm.particle.helper.VanillaExtCreator;
import com.leafia.contents.machines.processing.solblaster.container.SolBlasterMenu;
import com.leafia.contents.machines.processing.solblaster.recipes.SolBlasterRecipes;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.dev.container_utility.LeafiaPacketReceiver;
import com.leafia.init.LeafiaBlockEntities;
import com.leafia.init.LeafiaSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Neutron Blaster's block entity: twenty single-item slots, a door that opens while anyone has
 * the screen up, and a detonation that transmutes what is in the fifteen input slots.
 *
 * <p>Upstream: {@code .../solblaster/SolBlasterTE.java}.
 *
 * <h2>How it fires</h2>
 *
 * <ul>
 * <li><b>Armed</b> is a solinium core in slot 0 and an explosive lens in each of slots 1 to 4 -
 *     either lens, in any mix.</li>
 * <li><b>Powered</b> is any block of the 3x3x3 (or of the two-block column above it) having a
 *     redstone signal on a neighbour. {@link SolBlasterBlock} reports each such block through
 *     {@link #updateRedstonePower}, and {@link #activePoses} is the set of those that are.</li>
 * <li><b>The door.</b> {@link #doorPos} climbs one a tick, to {@link #doorDuration}, while any
 *     player has the screen open ({@link #listeners}), and falls one a tick otherwise. While it is
 *     above zero {@link #cooldown} is held at 15; only at zero does the cooldown count down, and
 *     only when it has reached zero does a powered, armed blaster go off. So the sequence is the
 *     one a player would expect - close the screen, the door swings shut over fifty ticks, wait
 *     fifteen more, bang - but <b>a blaster that is never opened fires the tick it is powered</b>.
 *     Hoppers and droppers can load it without a door ever moving.</li>
 * <li><b>The detonation</b> destroys the core and the four lenses, plays upstream's two sounds and
 *     its burst of particles, and replaces each of the fifteen input slots with
 *     {@link SolBlasterRecipes#processItem}.</li>
 * </ul>
 *
 * <h2>The inventory</h2>
 *
 * <ul>
 * <li><b>One item a slot.</b> Upstream builds its handler with {@code getNewInventory(20, 1)}, a
 *     slot limit of 1; {@link #getMaxStackSize} is 1 here.</li>
 * <li><b>The rules.</b> Slot 0 takes only the solinium core and slots 1-4 only lenses
 *     ({@link #canPlaceItem}); slots 5-19 take anything by hand. A hopper is stricter: it may put
 *     into the fifteen only an item that has a <em>recipe</em>, and take out of them only an item
 *     that has not - so what went in comes out once it has been transmuted. It can never take
 *     from slots 0-4.</li>
 * <li><b>Every side reaches every slot</b> - {@code getAccessibleSlotsFromSide} is all twenty.</li>
 * <li><b>The comparator</b> is told whenever a slot changes, from every block of the machine, so
 *     that one beside any corner of it can read it.</li>
 * </ul>
 *
 * <p><b>The camera shake</b> when the door finishes closing is upstream's: a smooth shake (intensity
 * 0.15, duration 0.5 s, speed 8, blur and bloom dulling 50, range 12, curve 0.5) to everyone within
 * ten blocks, through {@link LeafiaShakecam} (D-1089), wired after both merged.
 *
 * <h2>What is not here</h2>
 *
 * <ul>
 * <li><b>{@code LeafiaPacket} every tick.</b> Upstream sends the door's position to everyone
 *     within 256 blocks on every tick whether or not it moved. Here it is sent when it changes,
 *     and also rides the block entity's update tag, which is what a client that was out of range
 *     reads on arrival. Nothing visible differs: the client already smooths from one value to the
 *     next.</li>
 * </ul>
 */
public class SolBlasterTE extends BlockEntityMachineBase implements LeafiaPacketReceiver {

	/** Upstream's door-shut shake parameters and the radius it is sent to (D-1087). */
	public static final String[] DOOR_SHAKE = {"type=smooth", "intensity=0.15", "duration=0.5", "speed=8",
			"blurDulling=50", "bloomDulling=50", "range=12", "curve=0.5"};
	public static final double DOOR_SHAKE_RANGE = 10;

	/** Upstream's {@code super(20)}: core, four lenses, fifteen inputs. */
	public static final int SLOTS = 20;
	public static final int SLOT_CORE = 0;
	public static final int FIRST_LENS = 1;
	public static final int LAST_LENS = 4;
	public static final int FIRST_INPUT = 5;

	/** Upstream's {@code doorDuration}: ticks the door takes to open. */
	public static int doorDuration = 50;

	/** Ticks the blaster waits after the door has shut. */
	public static final int COOLDOWN = 15;

	/** The door's position in ticks, 0 shut. On the client, the value it eases to. */
	public int doorPos = 0;
	public int prevDoorPos = 0;
	public int nextDoorPos = 0;

	/** Every block of the machine that has a redstone signal beside it. */
	public final Set<BlockPos> activePoses = new HashSet<>();

	public int cooldown = 0;

	/** The players with the screen up. */
	public final List<Player> listeners = new ArrayList<>();

	/** What was last sent, so a packet goes out only when the door has moved. */
	private int sentDoorPos = 0;

	private static final int[] ALL_SLOTS = new int[SLOTS];

	static {
		for(int i = 0; i < SLOTS; i++) ALL_SLOTS[i] = i;
	}

	public SolBlasterTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state, SLOTS);
	}

	public SolBlasterTE(BlockPos pos, BlockState state) {
		this(LeafiaBlockEntities.SOL_BLASTER.get(), pos, state);
	}

	// ---- inventory -------------------------------------------------------------------------

	@Override
	public int getMaxStackSize() {
		return 1;
	}

	@Override
	public int[] getSlotsForFace(Direction side) {
		return ALL_SLOTS;
	}

	/** Upstream's {@code isItemValidForSlot}. */
	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		if(slot == SLOT_CORE) return stack.is(ModItems.solinium_core.get());
		if(slot >= FIRST_LENS && slot <= LAST_LENS) return isLens(stack);
		return true;
	}

	/** Upstream's {@code canInsertItemHopper} and {@code canInsertItem}, together. */
	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
		if(slot >= FIRST_INPUT) return SolBlasterRecipes.isValidInput(stack) && canPlaceItem(slot, stack);
		return canPlaceItem(slot, stack);
	}

	/** Upstream's {@code canExtractItemHopper}: the fifteen give up whatever is not an input. */
	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
		if(slot >= FIRST_INPUT) return !SolBlasterRecipes.isValidInput(stack);
		return false;
	}

	private static boolean isLens(ItemStack stack) {
		return stack.is(ModItems.early_explosive_lenses.get()) || stack.is(ModItems.explosive_lenses.get());
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		super.setItem(slot, stack);
		slotContentsChanged(slot);
	}

	@Override
	public ItemStack removeItem(int slot, int amount) {
		ItemStack removed = super.removeItem(slot, amount);
		if(!removed.isEmpty()) slotContentsChanged(slot);
		return removed;
	}

	/**
	 * Upstream's {@code slotContentsChanged}: tell every block of the machine's 3x3x3, and the two
	 * above it, that something changed, so a comparator beside any of them re-reads.
	 *
	 * <p>The comparator's own output is computed in the block; this is only the nudge.
	 */
	public void slotContentsChanged(int slot) {

		setChanged();

		if(level == null || level.isClientSide) return;

		// Not upstream's: a lens loaded by a hopper shows its cover at once, not after a reload.
		level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);

		var block = getBlockState().getBlock();

		for(int x = -1; x <= 1; x++)
			for(int z = -1; z <= 1; z++)
				for(int y = 0; y <= 2; y++)
					level.updateNeighborsAt(worldPosition.offset(x, y, z), block);

		level.updateNeighborsAt(worldPosition.above(3), block);
		level.updateNeighborsAt(worldPosition.above(4), block);
	}

	// ---- the state of the machine ----------------------------------------------------------

	public boolean checkForItem(Item item, int slot) {
		return slots[slot].is(item);
	}

	/** A core in slot 0 and a lens, of either kind, in each of slots 1-4. */
	public boolean isArmed() {
		if(!slots[SLOT_CORE].is(ModItems.solinium_core.get())) return false;
		for(int i = FIRST_LENS; i <= LAST_LENS; i++)
			if(!isLens(slots[i])) return false;
		return true;
	}

	/**
	 * Upstream's {@code updateRedstonePower}, which {@link SolBlasterBlock} calls from every
	 * block of the machine that is told a neighbour changed.
	 */
	public void updateRedstonePower(BlockPos pos) {

		boolean changed = level.hasNeighborSignal(pos) ? activePoses.add(pos.immutable()) : activePoses.remove(pos);

		if(changed) setChanged();
	}

	// ---- the tick --------------------------------------------------------------------------

	@Override
	public void updateEntity() {

		if(level.isClientSide) {
			prevDoorPos = doorPos;
			doorPos = nextDoorPos;
			return;
		}

		// A player who disconnected with the screen up never closed it.
		listeners.removeIf(Player::isRemoved);

		if(listeners.isEmpty()) {

			if(doorPos > 0) {

				if(doorPos == 20) playDoorSound(false);
				doorPos--;

				if(doorPos == 0 && level instanceof ServerLevel server) {
					// Upstream's ShakecamPacket, at this block, to everyone within ten (D-1087, D-1089).
					LeafiaShakecam.sendNear(server, worldPosition, DOOR_SHAKE_RANGE, DOOR_SHAKE);
				}
			}

		} else {
			if(doorPos == 0) playDoorSound(true);
			doorPos = Math.min(doorDuration, doorPos + 1);
		}

		if(doorPos != sentDoorPos) {
			sentDoorPos = doorPos;
			LeafiaPacket._start(this).__write(SolBlasterPackets.DOOR_SYNC.ordinal(), doorPos).__sendToClients(256);
		}

		if(doorPos > 0) {
			cooldown = COOLDOWN;
		} else {
			if(cooldown == 0 && !activePoses.isEmpty() && isArmed()) detonate();
			cooldown = Math.max(0, cooldown - 1);
		}
	}

	private void playDoorSound(boolean open) {
		level.playSound(null, worldPosition,
				(open ? LeafiaSounds.reactor_door_open : LeafiaSounds.reactor_door_close).get(),
				SoundSource.BLOCKS, 0.75F, 1F);
	}

	/**
	 * The bang: the core and lenses are spent, and each input slot is replaced by what it turns into.
	 *
	 * <p>The sounds are at the block three above the core, where the tower is; the burst is five
	 * puffs and a flash of size four, which is {@code VanillaExt.LargeExplode(5, 4)}'s own, drawn by
	 * the base mod's {@code "largeexplode"} creator, to everyone within 512 blocks.
	 */
	public void detonate() {

		for(int i = 0; i <= LAST_LENS; i++) setItem(i, ItemStack.EMPTY);

		double x = worldPosition.getX() + 0.5D;
		double y = worldPosition.getY() + 3D;
		double z = worldPosition.getZ() + 0.5D;

		level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 4F, 1F);
		level.playSound(null, x, y, z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.BLOCKS, 20F, 1F);

		if(level instanceof ServerLevel server)
			PacketDistributor.sendToPlayersNear(server, null, x, y, z, 512D,
					new AuxParticlePacketNT(explosionTag(), x, y, z));

		for(int i = FIRST_INPUT; i < SLOTS; i++)
			setItem(i, SolBlasterRecipes.processItem(slots[i]));
	}

	/**
	 * Upstream's {@code VanillaExt.LargeExplode(5, 4)} emitted with motion (0, 1, 0): the base mod's
	 * {@code "largeexplode"} effect, whose keys are {@code count} (a byte) and {@code size}.
	 */
	public static CompoundTag explosionTag() {
		CompoundTag data = VanillaExtCreator.tag(VanillaExtCreator.MODE_LARGE_EXPLODE, 0D, 1D, 0D);
		data.putByte("count", (byte) 5);
		data.putFloat("size", 4F);
		return data;
	}

	public static void tick(Level level, BlockPos pos, BlockState state, SolBlasterTE blaster) {
		blaster.updateEntity();
	}

	// ---- persistence -----------------------------------------------------------------------

	@Override
	protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.saveAdditional(nbt, registries);

		ListTag active = new ListTag();
		for(BlockPos pos : activePoses)
			active.add(new IntArrayTag(new int[] { pos.getX(), pos.getY(), pos.getZ() }));
		nbt.put("activePoses", active);
	}

	@Override
	protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.loadAdditional(nbt, registries);

		activePoses.clear();
		for(Tag tag : nbt.getList("activePoses", Tag.TAG_INT_ARRAY)) {
			if(tag instanceof IntArrayTag array && array.getAsIntArray().length >= 3) {
				int[] a = array.getAsIntArray();
				activePoses.add(new BlockPos(a[0], a[1], a[2]));
			}
		}
	}

	/**
	 * What a client that loads the chunk reads: everything that is saved - the lenses the renderer
	 * draws covers over come out of the slots - and where the door stands.
	 */
	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		CompoundTag tag = saveWithoutMetadata(registries);
		tag.putInt("doorPos", doorPos);
		return tag;
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
		super.handleUpdateTag(tag, registries);
		int pos = tag.getInt("doorPos");
		prevDoorPos = pos;
		doorPos = pos;
		nextDoorPos = pos;
	}

	// ---- menu and packets ------------------------------------------------------------------

	@Override
	public String getName() {
		return "tile.sol_blaster.name";
	}

	@Override
	public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
		return new SolBlasterMenu(containerId, playerInventory, this);
	}

	@Override
	public String getPacketIdentifier() {
		return "SOL_BLAST";
	}

	/** Upstream's {@code SolBlasterPackets}. */
	public enum SolBlasterPackets {
		DOOR_SYNC
	}

	@Override
	public void onReceivePacketLocal(byte key, Object value) {
		if(key == SolBlasterPackets.DOOR_SYNC.ordinal()) nextDoorPos = (int) value;
	}

	@Override
	public void onReceivePacketServer(byte key, Object value, Player plr) { }

	@Override
	public void onPlayerValidate(Player plr) { }

	@Override
	public List<Player> getListeners() {
		return listeners;
	}

	/** Upstream's box: three out on every side, six up. */
	public AABB getRenderBoundingBox() {
		return new AABB(worldPosition.getX() - 3, worldPosition.getY(), worldPosition.getZ() - 3,
				worldPosition.getX() + 4, worldPosition.getY() + 6, worldPosition.getZ() + 4);
	}
}
