package com.leafia.database;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/**
 * Which tier of reactor each reactor block belongs to: ZIRNOX, PWR, RBMK, Watz, APR, fusion, ICF, AMS
 * and DFC, in that order, each a "fission", "particle", "fusion" or "core" class.
 *
 * <p>Upstream: {@code com/leafia/database/ReactorTiers.java}. It exists for one thing, the line
 * {@code MachineTooltip.addInfoASM} puts under a reactor block's name - "RBMK (Tier 3 / Fission
 * Reactor)" - and, with shift held, the tiers either side. {@link #redirection} is the lookup that
 * line reads: an item to its tier index.
 *
 * <h2>Names, not blocks</h2>
 *
 * Upstream passes the blocks themselves ({@code PWR.element}, {@code ModBlocks.rbmk_rod}). Half the
 * reactors named here are not registered yet in this port - the PWR, the APR, the AMS - and a Java
 * field cannot name a block that does not exist. So the table is of <em>registry names</em>,
 * {@code hbm:rbmk_rod} and {@code leafia:pwr_element}, resolved by {@link #register} once every block
 * is registered, and a name that does not resolve is skipped and listed in {@link #unresolved}. When
 * a later batch registers the PWR, its blocks join their tier with no edit here. The table is
 * generated from upstream's nine {@code addTier} calls.
 *
 * <p>The tier number is the index of the call, so it does not depend on which blocks resolved: the
 * RBMK is tier 3 whether or not the PWR is present.
 */
public class ReactorTiers {

	/** Tier names, by index: the lang key suffix of {@code desc.leafia._reactortier.name.*}. */
	public static final List<String> names = new ArrayList<>();
	/** Tier classes, by index: the suffix of {@code desc.leafia._reactortier.class.*}. */
	public static final List<String> classes = new ArrayList<>();
	/** A reactor block's item to the index of its tier. */
	public static final Map<Item, Integer> redirection = new HashMap<>();
	/** The registry names the table lists that are not registered (yet), by tier key. */
	public static final Map<String, List<String>> unresolved = new HashMap<>();

	// BEGIN GENERATED
	private static final String[][] TABLE = {
		{"zirnox", "fission", "hbm:machine_zirnox"},
		{"pwr", "fission", "leafia:lwr_element", "leafia:reactor_element", "leafia:reactor_element_blank", "leafia:lwr_control", "leafia:reactor_control", "leafia:lwr_channel", "leafia:reactor_conductor", "leafia:lwr_exchanger", "leafia:lwr_terminal", "leafia:reactor_hatch", "leafia:reactor_hatch_alt", "leafia:lwr_port", "leafia:lwr_occs_in", "leafia:lwr_occs_out", "leafia:lwr_signal"},
		{"rbmk", "fission", "hbm:rbmk_absorber", "hbm:rbmk_autoloader", "hbm:rbmk_blank", "hbm:rbmk_boiler", "hbm:rbmk_control", "hbm:rbmk_control_mod", "hbm:rbmk_control_auto", "hbm:rbmk_autoloader", "hbm:rbmk_console", "hbm:rbmk_crane_console", "hbm:rbmk_cooler", "hbm:rbmk_heater", "hbm:rbmk_storage", "hbm:rbmk_rod", "hbm:rbmk_rod_mod", "hbm:rbmk_rod_reasim", "hbm:rbmk_rod_reasim_mod", "leafia:rbmk_rod_realersim", "leafia:rbmk_rod_realersim_mod"},
		{"watz", "fission", "hbm:watz", "hbm:struct_watz_core", "hbm:watz_casing", "hbm:watz_cooler", "hbm:watz_element", "hbm:watz_pump"},
		{"apr", "particle", "leafia:apr_core", "leafia:apr_port", "leafia:apr_component", "leafia:apr_outlet"},
		{"fusion", "fusion", "hbm:fusion_torus", "hbm:fusion_klystron", "hbm:fusion_torus", "hbm:fusion_component", "hbm:struct_torus_core", "hbm:fusion_klystron", "hbm:fusion_boiler", "hbm:fusion_mhdt", "hbm:fusion_breeder", "hbm:fusion_collector", "hbm:fusion_coupler"},
		{"icf", "fusion", "hbm:icf", "hbm:struct_icf_core", "hbm:icf_component", "hbm:icf_controller", "hbm:icf_laser_component"},
		{"ams", "core", "leafia:ams_base", "leafia:ams_emitter", "leafia:ams_limiter"},
		{"dfc", "core", "hbm:dfc_core", "hbm:dfc_emitter", "hbm:dfc_receiver", "hbm:dfc_stabilizer", "hbm:dfc_injector", "leafia:dfc_exchanger", "leafia:dfc_reinforced"},
	};
	// END GENERATED

	/**
	 * Fills the lookup. Idempotent: it starts by clearing, so a test (or a reload) can run it again.
	 * Called from common setup, after every registry is complete.
	 */
	public static void register() {
		names.clear();
		classes.clear();
		redirection.clear();
		unresolved.clear();
		int tier = 0;
		for(String[] row : TABLE) {
			addTier(tier, row[0], row[1], row, 2);
			tier++;
		}
	}

	static void addTier(int tier, String translationKey, String reactorClass, String[] blocks, int from) {
		names.add(translationKey);
		classes.add(reactorClass);
		for(int i = from; i < blocks.length; i++) {
			ResourceLocation id = ResourceLocation.parse(blocks[i]);
			Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
			if(item == null || item == net.minecraft.world.item.Items.AIR) {
				// a block registered one item per variant (IBlockMulti): upstream's one Item covered every meta,
				// so every item placing that block joins the tier
				boolean any = false;
				var block = BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
				if(block != null && block != net.minecraft.world.level.block.Blocks.AIR) {
					for(Item candidate : BuiltInRegistries.ITEM) {
						if(candidate instanceof net.minecraft.world.item.BlockItem bi && bi.getBlock() == block) {
							redirection.put(candidate, tier);
							any = true;
						}
					}
				}
				if(!any)
					unresolved.computeIfAbsent(translationKey, k -> new ArrayList<>()).add(blocks[i]);
				continue;
			}
			redirection.put(item, tier);
		}
	}
}
