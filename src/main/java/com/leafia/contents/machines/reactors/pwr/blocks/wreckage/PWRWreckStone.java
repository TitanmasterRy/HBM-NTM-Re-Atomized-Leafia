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
 * A stone wall's wreck, {@code lwrwreck_stone}, and the default wreck of anything the meltdown scorched
 * that has no waste form: one intact mesh, three slightly worn, two wrecked, four rubble. Breaks into pieces.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/reactors/pwr/blocks/wreckage/PWRWreckStone.java}, every
 * mesh and hitbox as written, including the commented-out {@code wreck_stone_2} (left out, as upstream
 * left it out). Placed by hand it shows concrete bricks on a random normal variation at a random scorch.
 */
public class PWRWreckStone extends PWRMeshedWreck {

	public static final VariationGroup variations = new VariationGroupConstructor()
			.setErosion(Erosion.NONE)
			.addVariation("intact", (WreckBound[]) null)
			.setErosion(Erosion.SLIGHT)
			.addVariation("stone_slight_0", new WreckBound(0, 0, 2, 2, 0, 1.9))
			.addVariation("stone_slight_1", new WreckBound(0, 0, 2, 2, 0, 1.9))
			.addVariation("stone_slight_2", new WreckBound(0, 0, 2, 2, 0, 1.9))
			.setErosion(Erosion.NORMAL)
			.addVariation("wreck_stone", new WreckBound(0, 0, 2, 2, 0, 1.75))
			.addVariation("wreck_stone_3", new WreckBound(0, 0, 2, 2, 0, 1.6))
			.setErosion(Erosion.RUBBLE)
			.addVariation("stone_rubble_0_flip",
					WreckBound.blend(-5, -5, -1, -1, 0, 0.75),
					WreckBound.blend(-5, -1, -1, 3, 0, 0.5),
					WreckBound.blend(-1, -5, 3, -3, 0, 0.5),
					WreckBound.blend(2, -3, 5, 1, 0, 0.75),
					WreckBound.blend(-2, 0, 2, 3, 0, 0.75),
					WreckBound.blend(1, 3, 5, 5, 0, 0.5))
			.addVariation("stone_rubble_1",
					WreckBound.blend(-5, -4, 5, 5, 0, 0.5))
			.addVariation("stone_rubble_2",
					WreckBound.blend(-5, 1, 4, 5, 0, 0.75),
					WreckBound.blend(-5, -5, 5, 1, 0, 0.5),
					WreckBound.blend(-2, -4, 4, 1, 0, 2))
			.addVariation("stone_rubble_3",
					new WreckBound(0.7, 0.41, 0.6, 0.8, 0, 0.15),
					WreckBound.blend(0, -1, 4, 3.5, 0, 0.75),
					WreckBound.blend(-3, 1, 4, 3.5, 0, 0.75))
			.compile();

	public PWRWreckStone(Properties properties) {
		super(properties);
	}

	@Override
	public Item getDebrisItem() {
		return LeafiaItems.lwr_piece.get();
	}

	@Override
	public VariationGroup getVariations() {
		return variations;
	}

	@Override
	public void setPlacedBy(Level world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(world, pos, state, placer, stack);
		if(!world.isClientSide) placedByHand(world, pos, ModBlocks.brick_concrete.get(), variations.NORMAL.length);
	}
}
