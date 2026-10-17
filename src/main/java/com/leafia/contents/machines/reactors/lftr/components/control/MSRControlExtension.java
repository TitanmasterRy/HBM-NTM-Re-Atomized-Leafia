package com.leafia.contents.machines.reactors.lftr.components.control;

import java.util.List;

import com.hbm.blocks.ITooltipProvider;
import com.leafia.dev.machine.MachineTooltip;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * {@code msr_control_extension}, the LFTR Control Rod Extension: a tube laid along the axis it was
 * placed against, which lengthens a control rod's travel by one block for each stacked behind it.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/reactors/lftr/components/control/MSRControlExtension.java},
 * on NTM-CE's {@code BlockPipe} - an axis property set from the clicked face, cutout, not a full cube
 * (read from the 1.5.1.8 jar: it has no tooltip of its own). That is {@link RotatedPillarBlock}'s
 * placement, and the base mod's {@code BlockPipe} is its 1.7.10 cousin with a "Purely decorative"
 * line this one does not have, so it is not extended. The shape is upstream's: a tube inset by two
 * sixteenths on the two cross axes. The model is upstream's OBJ, rotated per axis by the blockstate.
 */
public class MSRControlExtension extends RotatedPillarBlock implements ITooltipProvider {

	private static final double INSET = 2D / 16;
	private static final VoxelShape X = Shapes.box(0, INSET, INSET, 1, 1 - INSET, 1 - INSET);
	private static final VoxelShape Y = Shapes.box(INSET, 0, INSET, 1 - INSET, 1, 1 - INSET);
	private static final VoxelShape Z = Shapes.box(INSET, INSET, 0, 1 - INSET, 1 - INSET, 1);

	public MSRControlExtension(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(AXIS, Direction.Axis.Y));
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		MachineTooltip.addMultiblock(tooltip);
		MachineTooltip.addModular(tooltip);
		addStandardInfo("tile.msr_control_extension.desc", tooltip);
		super.appendHoverText(stack, context, tooltip, flag);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return switch(state.getValue(AXIS)) {
			case X -> X;
			case Y -> Y;
			case Z -> Z;
		};
	}
}
