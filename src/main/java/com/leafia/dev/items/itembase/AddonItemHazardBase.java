package com.leafia.dev.items.itembase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.hbm.hazard.modifier.HazardModifier;
import com.hbm.hazard.type.HazardTypeBase;
import com.leafia.init.hazards.ItemRads.MultiRadContainer;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/**
 * An addon item that declares its own hazards: a plain item plus eleven numbers.
 *
 * <p>Upstream: {@code com/leafia/dev/items/itembase/AddonItemHazardBase.java}. The class is a bag
 * of fields with a fluent setter each - {@code addRad}, {@code addDigamma}, {@code addFire},
 * {@code addCryogenic}, {@code addToxic}, {@code addBlinding}, {@code addAsbestos},
 * {@code addCoal}, {@code addAlkaline}, {@code addExplosive}, {@code addSharp} and
 * {@code addModifier} - and does nothing with them. {@code AddonHazards.register()} walks the
 * list of every instance and turns each into a {@code HazardData}; here that walk is
 * {@link com.leafia.init.LeafiaHazards#registerItemHazards}, over the addon's own registry rather
 * than over a static list the constructor fills (a {@code DeferredItem} is built lazily, and a
 * constructor that registered itself into a static list would run at a time the mod does not
 * choose).
 *
 * <p>Its subclass {@code AddonItemHazardBaked} is a 1.12.2 model bake and dissolves into an item
 * model JSON (D-1085), as {@code AddonItemBaked} did (D-1038).
 *
 * <p>Upstream's {@code Item.setContainerItem} survives as {@link #setContainerItem}, because
 * {@code pellet_rtg_fissium} hands its iron plate back when it is crafted with.
 *
 * <h2>Which of the fields do anything here</h2>
 *
 * Every field is kept, because an item's declaration should read as upstream's. The ones with a
 * hazard type are turned into entries: radiation (the addon's six types and the base's plain one),
 * digamma, {@code HOT} for {@link #fire}, {@code BLINDING}, {@code ASBESTOS}, {@code COAL} and
 * {@code EXPLOSIVE}, and - with {@code features.hazardOverhaul}, D-3023 - the addon's toxic, alkaline
 * and sharp types. Cryogenic has no type. {@link #unwired()} lists what an item asked for and did not
 * get, so a test (and a future contributor) can see it rather than discovering it in a world.
 */
public class AddonItemHazardBase extends Item {

	public Map<Class<? extends HazardTypeBase>, HazardModifier> mods;

	public MultiRadContainer radContainer = null;
	public double digamma;
	public int fire;
	public int cryogenic;
	public int toxic;
	public boolean blinding;
	public int asbestos;
	public int coal;
	public int alkaline;
	public double explosive;
	public double sharp;

	private Supplier<? extends ItemLike> container = null;

	public AddonItemHazardBase(Properties properties) {
		super(properties);
	}

	public AddonItemHazardBase addRad(MultiRadContainer container) { radContainer = container; return this; }
	public AddonItemHazardBase addDigamma(double value) { digamma = value; return this; }
	public AddonItemHazardBase addCryogenic(int value) { cryogenic = value; return this; }
	public AddonItemHazardBase addToxic(int value) { toxic = value; return this; }
	public AddonItemHazardBase addBlinding() { blinding = true; return this; }
	public AddonItemHazardBase addAsbestos(int value) { asbestos = value; return this; }
	public AddonItemHazardBase addCoal(int value) { coal = value; return this; }
	public AddonItemHazardBase addAlkaline(int value) { alkaline = value; return this; }
	public AddonItemHazardBase addExplosive(double value) { explosive = value; return this; }
	public AddonItemHazardBase addSharp(double value) { sharp = value; return this; }
	public AddonItemHazardBase addFire(int fire) { this.fire = fire; return this; }

	public AddonItemHazardBase addModifier(Class<? extends HazardTypeBase> type, HazardModifier modifier) {
		if(mods == null) mods = new HashMap<>();
		mods.put(type, modifier);
		return this;
	}

	/**
	 * What crafting leaves behind: upstream's {@code Item.setContainerItem}. A supplier, not an
	 * item, because an item that names another cannot be built until the other exists.
	 */
	public AddonItemHazardBase setContainerItem(Supplier<? extends ItemLike> remainder) {
		this.container = remainder;
		return this;
	}

	@Override
	public boolean hasCraftingRemainingItem(ItemStack stack) {
		return container != null;
	}

	@Override
	public ItemStack getCraftingRemainingItem(ItemStack stack) {
		return container == null ? ItemStack.EMPTY : new ItemStack(container.get());
	}

	/**
	 * The fields this item sets that no hazard type here can carry, by upstream's name for them.
	 * Empty for an item whose every hazard is wired.
	 */
	public List<String> unwired() {
		List<String> out = new ArrayList<>();
		// Coal and explosive are the base mod's own types and are always wired (D-3023); cryogenic has
		// none; toxic, alkaline and sharp are the addon's, registered with features.hazardOverhaul.
		boolean overhaul = com.leafia.config.LeafiaFeatures.hazardOverhaul;
		if(cryogenic != 0) out.add("cryogenic");
		if(toxic != 0 && !overhaul) out.add("toxic");
		if(alkaline != 0 && !overhaul) out.add("alkaline");
		if(sharp != 0 && !overhaul) out.add("sharp");
		return out;
	}
}
