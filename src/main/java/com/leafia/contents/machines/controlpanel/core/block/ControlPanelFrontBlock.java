package com.leafia.contents.machines.controlpanel.core.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The upright control panel front, a nearly vertical slab (11.31 degrees) with the same controls as the desk block. Adapted from
 * NTM-CE's {@code BlockControlPanelFront} (D-3063).
 *
 * <p><b>Upstream never shipped this block.</b> The whole class is commented out in CE (it does not compile: it names {@code UP}
 * and {@code DOWN} properties it never declared) and {@code ModBlocks} never registers it, although the renderer has a branch for
 * it and the model exists. It is ported as the plan lists it, completed the obvious way: it is the desk block with the front
 * geometry ({@link ControlPanelTE} picks that from the block type), always placed upright, with CE's own strength of 3 hardness
 * and 30 resistance. It is <b>registered but not in the creative tab</b>, as upstream's registry has it (absent), so a player
 * only meets one by command.
 */
public class ControlPanelFrontBlock extends ControlPanelBlock {

	public static final MapCodec<ControlPanelFrontBlock> FRONT_CODEC = simpleCodec(ControlPanelFrontBlock::new);

	public ControlPanelFrontBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return FRONT_CODEC;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}
}
