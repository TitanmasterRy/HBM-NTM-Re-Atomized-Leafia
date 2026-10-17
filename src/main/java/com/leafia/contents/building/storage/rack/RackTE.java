package com.leafia.contents.building.storage.rack;

import java.util.Set;

import javax.annotation.Nullable;

import com.hbm.blockentity.machine.storage.BlockEntityCrateBase;
import com.leafia.init.LeafiaBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The storage rack's core: four places for a block, each of which may be a crate that opens.
 *
 * <p>Upstream: {@code .../building/storage/rack/RackTE.java}. Four places in a 2 by 2 - index 0 lower left,
 * 1 lower right, 2 upper left, 3 upper right, as seen from the front - each holding a <b>block</b> (any
 * block whose model is drawn as a model: a crate, but also a plain stone) and, for the base mod's crates
 * ({@link #supportedTEBlocks}), a <b>live crate</b>: a block entity of the crate's own kind that never joins the
 * world but carries the crate's inventory, which a player opens from the rack.
 *
 * <h2>What changed in the translation</h2>
 *
 * <ul>
 * <li><b>A crate's block entity is made by the crate block</b> ({@code newBlockEntity}), so while the
 *     {@code tungstenCrate} overhaul is on a racked tungsten crate is the addon's. It is given this rack's
 *     level and position, {@link BlockEntityCrateBase#setRemoteAccess remote access} (upstream's
 *     {@code leafia$setOnRack}, whose {@code isUseableByPlayer} only asks that the crate is not invalid),
 *     and is never ticked: a crate on a rack neither smelts nor takes SPK, as upstream's, whose rack never
 *     called its {@code update}.</li>
 * <li><b>A stored crate keeps what the item held</b>, in the item's own format ({@link RackCrateData}), and
 *     comes off the rack as the item a broken crate drops, labels and lock and all.</li>
 * <li><b>Sync is the block's update tag</b>, items only: upstream's per-slot {@code LeafiaPacket} carried the
 *     block's name and metadata and not the inventory either. The client draws the blocks; the contents travel
 *     with the menu.</li>
 * <li>The 1.12.2 metadata of a stored block is its own item here (D-034).</li>
 * </ul>
 *
 * <p>The live crates are built when the block entity is loaded into a level, not while its data is read, because
 * a crate needs the level.
 */
public class RackTE extends BlockEntity {

	/** The blocks a rack makes a live crate of; any other block is shown and stored, but opens nothing. */
	public static final Set<String> supportedTEBlocks = Set.of(
			"hbm:crate_iron", "hbm:crate_steel", "hbm:crate_tungsten", "hbm:crate_desh", "hbm:crate_template");

	public static final int PLACES = 4;

	/** Upstream's offsets for the four places in the frame's space: lower left, lower right, upper left, upper right. */
	public static final double[][] PLACES_AT = {
			{ -1.125, 0.375, 0.375 },
			{ 0.125, 0.375, 0.375 },
			{ -1.125, 1.75, 0.375 },
			{ 0.125, 1.75, 0.375 } };

	private final BlockEntityCrateBase[] tes = new BlockEntityCrateBase[PLACES];
	/** The block stored in each place, one of it; the crate's contents are in {@link #tes}. */
	public final ItemStack[] crates = { ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY };
	/** What was read from disk for a place and not yet built into a crate: the level was not there yet. */
	private final CompoundTag[] pending = new CompoundTag[PLACES];

	public RackTE(BlockPos pos, BlockState state) {
		super(LeafiaBlockEntities.RACK.get(), pos, state);
	}

	public boolean has(int index) {
		return !crates[index].isEmpty();
	}

	/** The live crate in a place, or null for a place with none (empty, an unsupported block, or on a client). */
	@Nullable
	public BlockEntityCrateBase crate(int index) {
		return tes[index];
	}

	/** The block in a place, or null. */
	@Nullable
	public Block block(int index) {
		return crates[index].getItem() instanceof BlockItem item ? item.getBlock() : null;
	}

	private static boolean supported(Block block) {
		return supportedTEBlocks.contains(BuiltInRegistries.BLOCK.getKey(block).toString()) && block instanceof EntityBlock;
	}

	/**
	 * Upstream's {@code storeCrate}: puts the block of this stack in a place, if it is a block drawn as a model.
	 * A crate item's data (a dropped crate's contents, lock and labels) goes into the live crate.
	 *
	 * @return whether the stack was a block that can be stored; the caller takes the item
	 */
	public boolean storeCrate(ItemStack item, int index, boolean sendUpdate) {

		if(!(item.getItem() instanceof BlockItem blockItem)) return false;
		Block block = blockItem.getBlock();
		BlockState state = block.defaultBlockState();
		if(state.getRenderShape() != RenderShape.MODEL) return false;

		tes[index] = null;
		pending[index] = null;
		ItemStack plain = new ItemStack(blockItem);
		crates[index] = plain;

		if(level != null && !level.isClientSide && supported(block)) {
			BlockEntity be = ((EntityBlock) block).newBlockEntity(worldPosition, state);
			if(be instanceof BlockEntityCrateBase crate) {
				crate.setLevel(level);
				crate.setRemoteAccess(true);
				RackCrateData.read(crate, RackCrateData.of(item), level.registryAccess());
				String name = RackCrateData.nameOf(item);
				if(name != null) crate.setCustomName(name);
				tes[index] = crate;
			}
		}

		changed(sendUpdate);
		return true;
	}

	/**
	 * Upstream's {@code removeCrate}: empties a place and returns the block as an item - with the crate's
	 * contents on it if it was a live one - or {@link ItemStack#EMPTY} for a place with none.
	 */
	public ItemStack removeCrate(int index, boolean sendUpdate) {

		ItemStack stack = crates[index];
		BlockEntityCrateBase crate = tes[index];
		CompoundTag waiting = pending[index];
		crates[index] = ItemStack.EMPTY;
		tes[index] = null;
		pending[index] = null;
		changed(sendUpdate);

		if(stack.isEmpty()) return ItemStack.EMPTY;

		ItemStack out = stack.copy();
		CompoundTag data = new CompoundTag();
		if(crate != null && level != null) {
			data = RackCrateData.write(crate, level.registryAccess());
			if(crate.hasCustomInventoryName()) out.set(DataComponents.CUSTOM_NAME, crate.getDisplayName());
			crate.setRemoved();
		} else if(waiting != null) {
			data = waiting.copy();
		}
		if(!data.isEmpty()) {
			data.putLong("stacklock", level == null ? 0L : level.random.nextLong());
			out.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
		}
		return out;
	}

	private void changed(boolean sendUpdate) {
		setChanged();
		if(sendUpdate && level != null && !level.isClientSide)
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
	}

	/** Upstream's {@code invalidate}: the live crates go with the rack. */
	@Override
	public void setRemoved() {
		for(BlockEntityCrateBase crate : tes)
			if(crate != null) crate.setRemoved();
		super.setRemoved();
	}

	@Override
	public void onLoad() {
		super.onLoad();
		if(level == null || level.isClientSide) return;
		for(int i = 0; i < PLACES; i++) {
			if(tes[i] != null || crates[i].isEmpty()) continue;
			Block block = block(i);
			if(block == null || !supported(block)) continue;
			BlockEntity be = ((EntityBlock) block).newBlockEntity(worldPosition, block.defaultBlockState());
			if(!(be instanceof BlockEntityCrateBase crate)) continue;
			crate.setLevel(level);
			crate.setRemoteAccess(true);
			if(pending[i] != null) RackCrateData.read(crate, pending[i].getCompound("data"), level.registryAccess());
			if(pending[i] != null && pending[i].contains("name")) crate.setCustomName(pending[i].getString("name"));
			tes[i] = crate;
			pending[i] = null;
		}
	}

	/// SAVE ///

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		for(int i = 0; i < PLACES; i++) {
			if(crates[i].isEmpty()) continue;
			CompoundTag place = new CompoundTag();
			place.put("item", crates[i].save(registries));
			place.putString("rsc", BuiltInRegistries.ITEM.getKey(crates[i].getItem()).toString());
			if(tes[i] != null) {
				place.put("data", RackCrateData.write(tes[i], registries));
				if(tes[i].hasCustomInventoryName()) place.putString("name", tes[i].getDisplayName().getString());
			} else if(pending[i] != null) {
				place.put("data", pending[i].getCompound("data").copy());
				if(pending[i].contains("name")) place.putString("name", pending[i].getString("name"));
			}
			tag.put("crate" + i, place);
		}
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		for(int i = 0; i < PLACES; i++) {
			tes[i] = null;
			pending[i] = null;
			crates[i] = ItemStack.EMPTY;
			if(!tag.contains("crate" + i)) continue;
			CompoundTag place = tag.getCompound("crate" + i);
			crates[i] = ItemStack.parseOptional(registries, place.getCompound("item"));
			if(place.contains("data")) pending[i] = place.copy();
			// a rack is only ever read into a level that is already there on a client, where the crates are not built
		}
	}

	/** Items only: a client draws the blocks and has no use for what is inside them. */
	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		CompoundTag tag = new CompoundTag();
		for(int i = 0; i < PLACES; i++) {
			if(crates[i].isEmpty()) continue;
			CompoundTag place = new CompoundTag();
			place.put("item", crates[i].save(registries));
			tag.put("crate" + i, place);
		}
		return tag;
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
		loadAdditional(pkt.getTag(), registries);
	}

	/** Upstream's label for a place, for the look-at panel and the tests. */
	public Component describe(int index) {
		return crates[index].isEmpty() ? Component.empty() : crates[index].getHoverName();
	}
}
