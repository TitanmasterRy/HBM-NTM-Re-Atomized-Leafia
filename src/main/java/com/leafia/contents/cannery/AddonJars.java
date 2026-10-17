package com.leafia.contents.cannery;

import java.util.Map;

import com.hbm.wiaj.JarConnections;
import com.hbm.wiaj.cannery.CanneryBase;
import com.hbm.wiaj.cannery.Jars;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.cannery.cannery.CanneryLFTR;
import com.leafia.contents.cannery.cannery.CanneryPWR;
import com.leafia.contents.cannery.cannery.CanneryTest;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredBlock;

/**
 * The addon's topics for the World In A Jar guide: hover one of these items in an inventory and press shift and the manual key.
 *
 * <p>Upstream: {@code com/leafia/contents/cannery/AddonJars.java}, which puts its three topics into the base mod's table with
 * {@code Jars.canneries.put}. The table is public and the base mod never clears it, so that is still how a second mod adds a topic
 * (no hand-off); the base mod fills it from its own client setup and this fills the same map from the addon's, so the order the two run
 * in does not matter. The map is keyed by item where upstream's was keyed by a stack (item and damage), which 1.21.1's per-variant
 * items make the same thing.
 *
 * <ul>
 * <li><b>The PWR topic</b> opens from the six fuel and control blocks: the three fuel rod skins, the coolant channel and both control
 * rod skins. Registered when {@link LeafiaFeatures#pwrReactor()} is, since those are the blocks.</li>
 * <li><b>The LFTR topic</b> opens from the reaction chamber, the control rod, the fuel plug and the arbitrary block. It builds a scene out
 * of the special duct as well as the reactor's blocks, so it needs both {@link LeafiaFeatures#lftrReactor()} and {@link LeafiaFeatures#ffDuct}.</li>
 * <li><b>The test topic</b> opens from the debug Forge-fluid source, upstream's {@code test_ff_source}, which this port has not
 * ported. It is registered if the block exists by that name, so porting the block lights it up without an edit here.</li>
 * </ul>
 */
public class AddonJars {

	private static boolean ductResolverAdded = false;

	private static void put(Map<Item, CanneryBase> canneries, DeferredBlock<? extends Block> block, CanneryBase cannery) {
		canneries.put(block.get().asItem(), cannery);
	}

	/** Adds the addon's topics to the base mod's table; called from the client's setup, when the items exist. */
	public static void initJars() {

		Map<Item, CanneryBase> canneries = Jars.canneries;

		// upstream: TestBlocks.ffsource, which is not ported
		Item ffsource = BuiltInRegistries.ITEM.getOptional(LeafiaRef.rl("test_ff_source")).orElse(null);
		if(ffsource != null) canneries.put(ffsource, new CanneryTest());

		if(LeafiaFeatures.pwrReactor()) {
			CanneryPWR pwr = new CanneryPWR();
			put(canneries, LeafiaBlocks.lwr_element, pwr);
			put(canneries, LeafiaBlocks.reactor_element, pwr);
			put(canneries, LeafiaBlocks.reactor_element_blank, pwr);
			put(canneries, LeafiaBlocks.lwr_channel, pwr);
			put(canneries, LeafiaBlocks.lwr_control, pwr);
			put(canneries, LeafiaBlocks.reactor_control, pwr);
		}

		if(LeafiaFeatures.lftrReactor() && LeafiaFeatures.ffDuct) {
			CanneryLFTR lftr = new CanneryLFTR();
			put(canneries, LeafiaBlocks.msr_element, lftr);
			put(canneries, LeafiaBlocks.msr_control, lftr);
			put(canneries, LeafiaBlocks.msr_plug, lftr);
			put(canneries, LeafiaBlocks.msr_arbitrary, lftr);
			// The special duct's arms are worked out against the jar as it is drawn (D-3041).
			if(!ductResolverAdded) {
				ductResolverAdded = true;
				JarConnections.addResolver(CanneryLFTR.DUCT_RESOLVER);
			}
		}
	}
}
