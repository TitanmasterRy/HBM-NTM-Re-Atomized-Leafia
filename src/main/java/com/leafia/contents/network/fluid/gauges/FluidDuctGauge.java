package com.leafia.contents.network.fluid.gauges;

import java.util.ArrayList;
import java.util.List;

import com.hbm.blockentity.network.BlockEntityPipeBaseNT;
import com.hbm.blocks.ILookOverlay;
import com.hbm.blocks.ITooltipProvider;
import com.hbm.util.i18n.I18nUtil;
import com.leafia.contents.network.fluid.FluidDuctEquipmentBase;
import com.leafia.init.LeafiaBlockEntities;

import api.hbm.block.IToolable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * `fluid_duct_gauge_mdl`, the flow gauge: a length of the base mod's fluid duct with a dial on it that
 * shows how much is moving through the run.
 *
 * <p>Upstream: {@code com/leafia/contents/network/fluid/gauges/FluidDuctGauge.java}. It is the valve's
 * sibling and shares its base ({@link FluidDuctEquipmentBase}): the same thin collision box, the same
 * orientation from where the player stood, the same renderer. What it adds is the look overlay - the
 * fluid in its colour, and under it the reading in mB/s - and a screwdriver that opens the screen
 * where the dial's full scale is set.
 *
 * <p>Upstream does the screwdriver in {@code onBlockActivated}, on the client: an {@code ItemTooling}
 * of type screwdriver in the hand opens {@code FluidDuctGaugeGUI} straight away. The base mod
 * dispatches tools through {@link IToolable} instead, so this is {@link #onScrew}, and because the
 * server has to know who is editing (D-1094) the server tells the client to open the screen rather
 * than the client opening it itself. It answers true on both sides, so the swing happens.
 *
 * <p>The tooltip is the one line upstream's language file has under
 * {@code tile.fluid_duct_gauge_mdl.desc}, "Use screwdriver to change display maximum". Upstream's
 * block class does not write it; the file does, so the key is read for the block like every other
 * {@code .desc} in the mod, behind shift.
 */
public class FluidDuctGauge extends FluidDuctEquipmentBase implements IToolable, ITooltipProvider {

	public FluidDuctGauge(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new FluidDuctGaugeTE(LeafiaBlockEntities.FLUID_DUCT_GAUGE.get(), pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return createTickerHelper(type, LeafiaBlockEntities.FLUID_DUCT_GAUGE.get(), FluidDuctGaugeTE::tick);
	}

	@Override
	@OnlyIn(Dist.CLIENT)
	public void printHook(GuiGraphics gui, Level world, BlockPos pos) {

		if(!(world.getBlockEntity(pos) instanceof BlockEntityPipeBaseNT duct)) return;

		List<String> text = new ArrayList<>();
		text.add("&[" + duct.getFluidType().getColor() + "&]" + duct.getFluidType().getLocalizedName());

		FluidDuctGaugeTE gauge = (FluidDuctGaugeTE) duct;
		text.add(gauge.fillPerSec + "mB/s");

		ILookOverlay.printGeneric(gui, I18nUtil.resolveKey(((Block) this).getDescriptionId()), 0xffff00, 0x404000, text);
	}

	@Override
	public boolean onScrew(Level world, Player player, BlockPos pos, Direction side, Vec3 hit, ToolType tool) {

		if(tool != ToolType.SCREWDRIVER) return false;

		if(!world.isClientSide && world.getBlockEntity(pos) instanceof FluidDuctGaugeTE gauge)
			gauge.openGui(player);

		return true;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		addStandardInfo("tile.fluid_duct_gauge_mdl.desc", tooltip);
		super.appendHoverText(stack, context, tooltip, flag);
	}
}
