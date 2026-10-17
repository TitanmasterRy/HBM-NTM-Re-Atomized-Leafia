package com.leafia.contents.network.ff_duct.utility.filter;

import javax.annotation.Nullable;

import com.leafia.contents.machines.reactors.lftr.components.element.MSRElementTE.MSRFuel;
import com.leafia.contents.network.ff_duct.utility.FFDuctUtilityBase;
import com.leafia.dev.blocks.ICustomItemBlockProvider;
import com.leafia.init.LeafiaBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * {@code ff_filter}, the Special Pipe Mixture Filter. Upstream: {@code .../utility/filter/FFFilterBlock.java}.
 *
 * <p>A filter is placed with a fuel chosen on its item ({@link FFFilterItem}); a placement whose stack names no
 * fuel - a dispenser's, a command's, a creative pick - breaks the block again at once, dropping it, as upstream's
 * {@code onBlockPlacedBy} does. The block makes {@link FFFilterTE2}; the older {@link FFFilterTE} is not made by
 * anything.
 */
public class FFFilterBlock extends FFDuctUtilityBase implements ICustomItemBlockProvider {

	public FFFilterBlock(Properties properties) {
		super(properties);
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new FFFilterTE2(LeafiaBlockEntities.FF_FILTER.get(), pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return serverTicker(level, type, LeafiaBlockEntities.FF_FILTER.get());
	}

	/** Takes the fuel off the stack; with none (or a name that is no fuel), the block is destroyed with its drops. */
	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		if(level.isClientSide) return; // the server decides; a client that guessed wrongly is corrected
		if(level.getBlockEntity(pos) instanceof FFFilterTE2 filter) {
			MSRFuel fuel = FFFilterItem.getFilter(stack);
			if(fuel != null) {
				filter.setFilter(fuel);
				return;
			}
		}
		level.destroyBlock(pos, true);
	}

	@Override
	public BlockItem provideItem() {
		return new FFFilterItem(this, new net.minecraft.world.item.Item.Properties());
	}
}
