package com.leafia.contents.machines.reactors.pwr.blocks.wreckage;

import javax.annotation.Nullable;

import com.hbm.blocks.ModBlocks;
import com.leafia.contents.LeafiaItems;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A metal wall's wreck, {@code lwrwreck_metal}: one intact mesh, two slightly bent, one wrecked, one rubble.
 * Breaks into shrapnel.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/reactors/pwr/blocks/wreckage/PWRWreckMetal.java}, every
 * mesh and hitbox as written. Placed by hand it shows a thorium block on a random normal variation at a
 * random scorch, upstream's own placeholder.
 */
public class PWRWreckMetal extends PWRMeshedWreck {

	public static final VariationGroup variations = new VariationGroupConstructor()
			.setErosion(Erosion.NONE)
			.addVariation("intact", (WreckBound[]) null)
			.setErosion(Erosion.SLIGHT)
			.addVariation("metal_slight_0", new WreckBound(0, 0, 2, 2, 0, 1.9))
			.addVariation("metal_slight_1", WreckBound.blend(-5, -5, 5, 5, 0, 9.5))
			.setErosion(Erosion.NORMAL)
			.addVariation("metal_wreck_0", WreckBound.blend(-5, -5, 5, 5, 0, 8))
			.setErosion(Erosion.RUBBLE)
			.addVariation("metal_rubble_0",
					WreckBound.blend(-5, 1, 4, 5, 0, 0.75),
					WreckBound.blend(-5, -5, 5, 1, 0, 0.5),
					WreckBound.blend(-2, -4, 4, 1, 0, 2))
			.compile();

	public PWRWreckMetal(Properties properties) {
		super(properties);
	}

	@Override
	public Item getDebrisItem() {
		return LeafiaItems.lwr_shrapnel.get();
	}

	@Override
	public VariationGroup getVariations() {
		return variations;
	}

	@Override
	public void setPlacedBy(Level world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(world, pos, state, placer, stack);
		if(!world.isClientSide) placedByHand(world, pos, ModBlocks.block_thorium.get(), variations.NORMAL.length);
	}
}
