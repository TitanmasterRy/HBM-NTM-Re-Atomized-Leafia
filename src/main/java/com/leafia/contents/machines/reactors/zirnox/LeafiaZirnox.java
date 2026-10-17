package com.leafia.contents.machines.reactors.zirnox;

import com.hbm.blocks.BlockEntityOverrides;
import com.hbm.blocks.ModBlocks;
import com.leafia.config.LeafiaFeatures;
import com.leafia.init.LeafiaBlockEntities;

/**
 * How the ZIRNOX overhaul is put into the base mod: an override on the base reactor's block, read each time
 * its core makes a block entity.
 *
 * <p>Upstream applies {@code MixinTileEntityReactorZIRNOX} unconditionally. Here, as with the DFC (D-3011),
 * the base mod's {@code ReactorZirnox} asks {@link BlockEntityOverrides} first, and this answers with
 * {@link LeafiaZirnoxTE} while {@link LeafiaFeatures#zirnoxOverhaul} is on and declines otherwise, so the
 * base block makes the base reactor. A reactor already in a world keeps the type it was saved with; a
 * reactor saved as {@code leafia:zirnox} in a world later opened without the overhaul's type registered is
 * dropped by the chunk loader, inventory with it - the price of a switch that changes a base machine, and why
 * it defaults off (D-1002, D-3020).
 */
public final class LeafiaZirnox {

	private LeafiaZirnox() {}

	/** Common setup: the override, when its type exists. */
	public static void commonSetup() {
		if(LeafiaBlockEntities.ZIRNOX == null) return;
		BlockEntityOverrides.register(ModBlocks.reactor_zirnox.get(), LeafiaBlockEntities.ZIRNOX,
				(pos, state) -> LeafiaFeatures.zirnoxOverhaul ? new LeafiaZirnoxTE(LeafiaBlockEntities.ZIRNOX.get(), pos, state) : null);
	}
}
