package com.leafia.init;

import java.util.EnumMap;
import java.util.List;

import com.leafia.config.LeafiaFeatures;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The addon's armor materials (D-3026): one, the radiation goggles'.
 *
 * <p>Upstream builds the goggles from {@code ArmorMaterial.IRON}, the vanilla iron armour (durability multiplier 15, defence 2/5/6/2,
 * enchantability 9, the iron equip sound, repaired with iron ingots, no toughness). 1.21.1 wants a registered material, so this is
 * iron's numbers under the addon's name - with <b>no layers</b>: the goggles are drawn by their own render layer from their own model,
 * and a layer here would draw an iron helmet as well.
 */
public final class LeafiaArmorMaterials {

	private LeafiaArmorMaterials() {}

	public static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, LeafiaRef.MODID);

	/** Vanilla iron, less its layers. Null with {@code features.radGlasses} off. */
	public static DeferredHolder<ArmorMaterial, ArmorMaterial> RADGLASSES;

	public static void register(IEventBus modBus) {
		if(LeafiaFeatures.radGlasses) {
			RADGLASSES = MATERIALS.register("radglasses", () -> {
				EnumMap<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
				defense.put(ArmorItem.Type.BOOTS, 2);
				defense.put(ArmorItem.Type.LEGGINGS, 5);
				defense.put(ArmorItem.Type.CHESTPLATE, 6);
				defense.put(ArmorItem.Type.HELMET, 2);
				defense.put(ArmorItem.Type.BODY, 5);
				return new ArmorMaterial(defense, 9, SoundEvents.ARMOR_EQUIP_IRON, () -> Ingredient.of(Items.IRON_INGOT), List.of(), 0.0F, 0.0F);
			});
		}
		MATERIALS.register(modBus);
	}

	/** Vanilla's durability multiplier for iron: a helmet of it lasts 11 x 15 = 165 uses. */
	public static final int IRON_DURABILITY = 15;
}
