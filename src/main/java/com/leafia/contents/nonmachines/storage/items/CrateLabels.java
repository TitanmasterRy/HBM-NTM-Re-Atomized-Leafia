package com.leafia.contents.nonmachines.storage.items;

import javax.annotation.Nullable;

import com.hbm.blockentity.machine.storage.BlockEntityCrateBase;
import com.hbm.blocks.ModBlocks;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/**
 * What is written on a crate: three lines, one line for each turn of the top and bottom faces, and an icon.
 *
 * <p>Upstream: {@code overwrite_contents/interfaces/IMixinTileEntityCrate.java} and the state the five crate
 * mixins (iron, steel, desh, tungsten, and the template crate) each carried a copy of - four {@code String[]}
 * and {@code String} fields, an {@code Item} and a metadata, written to the tile entity's NBT as
 * {@code leafia_label_upper}, {@code _middle}, {@code _lower}, {@code leafia_label_0..3} and
 * {@code leafia_icon_item}/{@code _meta}. All five dissolve into this one view over the base crate's
 * {@link BlockEntityCrateBase#getExtraData() extra data}, the tag D-2198 added for another mod's per-crate
 * state: it is saved with the crate, sent to a watching client, and rides the dropped item when a crate is
 * broken with its contents kept - which is how a labelled crate stays labelled when carried.
 *
 * <p>The keys are upstream's own. Two differences, both on purpose:
 * <ul>
 * <li><b>An empty line is not written.</b> Upstream writes all seven strings into every crate's NBT whether
 * or not anyone ever labelled it; here an unlabelled crate's extra data is empty, so it adds nothing to the
 * save and its dropped item keeps stacking with its kind.</li>
 * <li><b>No metadata.</b> The icon's 1.12.2 metadata is a different item here (D-034), so the icon is the
 * item's registry name alone.</li>
 * </ul>
 *
 * <p>The four blocks that can be labelled are {@link #labellable the base mod's four crates}. Upstream names
 * them in two places (the tooltip and the screwdriver) by identity, as does this.
 */
public final class CrateLabels {

	public static final String UPPER = "leafia_label_upper";
	public static final String MIDDLE = "leafia_label_middle";
	public static final String LOWER = "leafia_label_lower";
	public static final String ICON = "leafia_icon_item";

	/** Upstream's {@code setMaxStringLength(2)} on the middle line, which the server now also holds to. */
	public static final int MIDDLE_LENGTH = 2;
	/**
	 * Every other line: a text box's default maximum, which is what upstream's boxes have (it sets no other).
	 * The screen also trims a line to the width of "handle with care", as upstream's.
	 */
	public static final int LINE_LENGTH = 32;

	private final BlockEntityCrateBase crate;

	private CrateLabels(BlockEntityCrateBase crate) {
		this.crate = crate;
	}

	public static CrateLabels of(BlockEntityCrateBase crate) {
		return new CrateLabels(crate);
	}

	/** Whether this block is one of the four crates a label can be written on. */
	public static boolean labellable(Block block) {
		return block == ModBlocks.crate_iron.get()
				|| block == ModBlocks.crate_steel.get()
				|| block == ModBlocks.crate_tungsten.get()
				|| block == ModBlocks.crate_desh.get();
	}

	/**
	 * Which of the four vertical lines the labelling screen's first box edits for the face that was clicked:
	 * upstream's {@code positiveModulo(1 - horizontalIndex, 4)} (south 1, west 0, north 3, east 2).
	 */
	public static int verticalIndex(Direction side) {
		return Mth.positiveModulo(1 - side.get2DDataValue(), 4);
	}

	private static String verticalKey(int i) {
		return "leafia_label_" + i;
	}

	private CompoundTag tag() {
		return crate.getExtraData();
	}

	/** The line written on the top and bottom faces for one turn (0 to 3), or "". */
	public String vertical(int i) {
		return tag().getString(verticalKey(i));
	}

	public String[] verticals() {
		String[] out = new String[4];
		for(int i = 0; i < 4; i++) out[i] = vertical(i);
		return out;
	}

	public String upper() {
		return tag().getString(UPPER);
	}

	public String middle() {
		return tag().getString(MIDDLE);
	}

	public String lower() {
		return tag().getString(LOWER);
	}

	/** The icon item, or null for none; an unknown registry name reads as none. */
	@Nullable
	public Item icon() {
		if(!tag().contains(ICON)) return null;
		ResourceLocation id = ResourceLocation.tryParse(tag().getString(ICON));
		if(id == null || !BuiltInRegistries.ITEM.containsKey(id)) return null;
		Item item = BuiltInRegistries.ITEM.get(id);
		return item == Items.AIR ? null : item;
	}

	/** True when nothing is written: no line and no icon. */
	public boolean isBlank() {
		return tag().isEmpty();
	}

	private void put(String key, String value) {
		if(value == null || value.isEmpty()) tag().remove(key);
		else tag().putString(key, value);
	}

	/** Writes the three lines and the four vertical lines, leaving the icon, and tells the watchers. */
	public void setText(String[] vertical, String upper, String middle, String lower) {
		for(int i = 0; i < 4; i++) put(verticalKey(i), vertical[i]);
		put(UPPER, upper);
		put(MIDDLE, middle);
		put(LOWER, lower);
		crate.markExtraDataChanged();
	}

	/** Sets or (null) clears the icon, and tells the watchers. */
	public void setIcon(@Nullable Item item) {
		if(item == null || item == Items.AIR) tag().remove(ICON);
		else tag().putString(ICON, BuiltInRegistries.ITEM.getKey(item).toString());
		crate.markExtraDataChanged();
	}
}
