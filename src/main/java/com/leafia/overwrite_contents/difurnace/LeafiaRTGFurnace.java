package com.leafia.overwrite_contents.difurnace;

import com.hbm.blocks.BlockEntityOverrides;
import com.hbm.blocks.ModBlocks;
import com.leafia.config.LeafiaFeatures;
import com.leafia.init.LeafiaBlockEntities;

/**
 * How the RTG blast furnace's rod slot is put into the base mod: overrides on both of the base furnace's
 * blocks, the unlit {@code machine_difurnace_rtg_off} and the lit {@code _on}, which the furnace swaps
 * between as it starts and stops (keeping its block entity), so the addon's type names both.
 *
 * <p>Upstream's three {@code DiFurnaceRTG} mixins apply unconditionally. They change a base machine, so here
 * they wait on {@link LeafiaFeatures#rtgFurnaceOverhaul}, which defaults off (D-1002, D-3020); the factory
 * reads the switch at each creation, as the DFC's does (D-3011). A furnace already in a world keeps the type
 * it was saved with.
 */
public final class LeafiaRTGFurnace {

	private LeafiaRTGFurnace() {}

	public static void commonSetup() {
		if(LeafiaBlockEntities.DIFURNACE_RTG == null) return;
		BlockEntityOverrides.register(ModBlocks.machine_difurnace_rtg_off.get(), LeafiaBlockEntities.DIFURNACE_RTG,
				(pos, state) -> LeafiaFeatures.rtgFurnaceOverhaul ? new LeafiaDiFurnaceRTGTE(LeafiaBlockEntities.DIFURNACE_RTG.get(), pos, state) : null);
		BlockEntityOverrides.register(ModBlocks.machine_difurnace_rtg_on.get(), LeafiaBlockEntities.DIFURNACE_RTG,
				(pos, state) -> LeafiaFeatures.rtgFurnaceOverhaul ? new LeafiaDiFurnaceRTGTE(LeafiaBlockEntities.DIFURNACE_RTG.get(), pos, state) : null);
	}
}
