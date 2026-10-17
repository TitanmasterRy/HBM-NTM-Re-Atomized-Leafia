package com.leafia.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.hbm.blocks.ModBlocks;
import com.hbm.items.machine.ItemPWRFuel.EnumPWRFuel;
import com.hbm.lib.RefStrings;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.LeafiaResourceItems;
import com.leafia.contents.control.fuel.nuclearfuel.LeafiaRods;
import com.leafia.init.LeafiaMats;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/**
 * What the addon hides from the recipe viewer (D-3032): upstream's {@code _JEIBlacklist.blacklistRecipes},
 * as the list of stacks. {@code AddonJeiPlugin} removes them from JEI's ingredient list once its runtime is
 * up, which is what upstream's {@code IIngredientBlacklist.addIngredientToBlacklist} did and what the base
 * mod does for its own hide list ({@code ViewHideList}). This class names no JEI type.
 *
 * <p>In upstream's order:
 *
 * <ol>
 * <li><b>The base mod's PWR, while the addon's replaces it</b> - the three fuel stages of every grade, and
 *     eleven blocks. Upstream hides these unless {@code disableAddonPWR}, because its PWR always replaces the
 *     base one; here the replacement is the overhaul {@link LeafiaFeatures#pwr}, which is off by default and
 *     is what hides the base PWR's recipes ({@code LeafiaBaseRecipes}). With it off nothing of the base is
 *     hidden. Upstream's other branch - hide the <em>addon</em> PWR when it is disabled - has no counterpart:
 *     a disabled PWR registers nothing (D-1002), so there is nothing to hide.</li>
 * <li><b>The joke and test items</b>: the Kys-3000 billet and rod, the elevator light, the three DNA items,
 *     the catsmilium, confusium and shitfuckium rods, the mysticite cell (both states) and the mysticite
 *     light receiver.</li>
 * </ol>
 *
 * <p><b>Not here.</b> {@code Reserved6TE.ateupd} (the first line upstream) is a list of the blocks of a
 * block entity upstream reserved and never used (D-3030); and {@code AddonItems.digammaRecord}, the hidden
 * record, is not an item here: its song is a datapack entry played by {@code LeafiaDigammaRecord}.
 *
 * <p>An item whose feature is off is not registered, and is left out.
 */
public final class _JEIBlacklist {

	private _JEIBlacklist() {}

	/** Every stack to hide, in upstream's order. */
	public static List<ItemStack> hidden() {
		List<ItemStack> out = new ArrayList<>();

		if(LeafiaFeatures.pwr) {
			for(EnumPWRFuel grade : EnumPWRFuel.values()) {
				String name = grade.name().toLowerCase(Locale.ROOT);
				add(out, base("pwr_fuel_" + name));
				add(out, base("pwr_fuel_hot_" + name));
				add(out, base("pwr_fuel_depleted_" + name));
			}
			add(out, ModBlocks.pwr_block.get());
			add(out, ModBlocks.pwr_casing.get());
			add(out, ModBlocks.pwr_channel.get());
			add(out, ModBlocks.pwr_control.get());
			add(out, ModBlocks.pwr_fuel.get()); // upstream's pwr_fuelrod
			add(out, ModBlocks.pwr_controller.get());
			add(out, ModBlocks.pwr_port.get());
			add(out, ModBlocks.pwr_reflector.get());
			add(out, ModBlocks.pwr_neutron_source.get());
			add(out, ModBlocks.pwr_heatex.get());
			add(out, ModBlocks.pwr_heatsink.get());
		}

		add(out, resource("billet_kys"));
		add(out, LeafiaRods.get("leafRodKys"));
		add(out, LeafiaBlocks.elevator_light == null ? null : LeafiaBlocks.elevator_light.get());
		// AddonItems.digammaRecord: not an item here (class doc)
		add(out, resource("dna_felid"));
		add(out, resource("dna_canid"));
		add(out, resource("dna_eevee"));
		add(out, LeafiaRods.get("leafRodCatsmile"));
		add(out, LeafiaRods.get("leafRodConfusion"));
		add(out, LeafiaRods.get("leafRodShitfuckium"));
		add(out, resource("cells_mysticite")); // upstream's am_rifle_cell_mysticite
		add(out, resource("cells_mysticite_filled"));
		add(out, LeafiaMats.item("part_receiver_light_mysticite"));

		return out;
	}

	private static void add(List<ItemStack> out, ItemLike item) {
		if(item == null) return;
		ItemStack stack = new ItemStack(item);
		if(!stack.isEmpty()) out.add(stack);
	}

	private static Item base(String name) {
		return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(RefStrings.MODID, name));
	}

	private static Item resource(String name) {
		var item = LeafiaResourceItems.get(name);
		return item == null ? null : item.get();
	}
}
