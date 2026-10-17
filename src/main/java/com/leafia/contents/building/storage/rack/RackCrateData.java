package com.leafia.contents.building.storage.rack;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import com.hbm.blockentity.machine.storage.BlockEntityCrateBase;
import com.hbm.blocks.generic.BlockStorageCrate;

/**
 * A crate's contents as a dropped crate item carries them, read into a crate on a rack and written back out.
 *
 * <p>Upstream's rack reads and writes a stored crate with {@code IPersistentNBT} ({@code readNBT} on the way
 * in, {@code writeNBT} on the way out), the interface that is also how the crate keeps its contents when
 * picked up. This port's crate has no such interface: the format is {@code BlockStorageCrate.writeCrateData} and
 * {@code readCrateData}, which a broken crate's drop and a placed crate's {@code setPlacedBy} call and which the base
 * mod made public for this (D-2207); before that this class carried a copy of both. So a crate goes onto a rack with
 * what it held, comes off with it, and is the same item a broken crate would have been:
 *
 * <ul>
 * <li>{@code slotN}: the stack in slot N, saved whole;</li>
 * <li>{@code lock}, {@code lockMod}: the lock's pins and its modifier, only for a locked crate;</li>
 * <li>{@code spiders}: the spider trap, only if armed;</li>
 * <li>{@code extraData}: another mod's per-crate state (the labels), only if there is any.</li>
 * </ul>
 *
 * <p>{@code stacklock}, which the base adds to keep two filled crates from stacking, is not part of the data
 * but of the item: {@link RackTE#removeCrate} adds it.
 */
final class RackCrateData {

	private RackCrateData() {}

	/** The crate's state in the item format, or an empty tag for a crate with nothing to keep. */
	static CompoundTag write(BlockEntityCrateBase crate, HolderLookup.Provider registries) {
		return BlockStorageCrate.writeCrateData(crate, registries);
	}

	/** Upstream's {@code readNBT}, as the base's {@code setPlacedBy} reads an item: the same keys. */
	static void read(BlockEntityCrateBase crate, CompoundTag nbt, HolderLookup.Provider registries) {
		BlockStorageCrate.readCrateData(crate, nbt, registries);
	}

	/** The item's data, or an empty tag. */
	static CompoundTag of(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? new CompoundTag() : data.copyTag();
	}

	/** The crate's name off the item, or null. */
	static String nameOf(ItemStack stack) {
		Component name = stack.get(DataComponents.CUSTOM_NAME);
		return name == null ? null : name.getString();
	}
}
