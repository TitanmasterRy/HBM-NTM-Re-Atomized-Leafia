package com.leafia.contents.machines.heat.rtheater;

import java.util.ArrayList;
import java.util.List;

import com.hbm.blockentity.BlockEntityProxyCombo;
import com.hbm.blocks.BlockDummyable;
import com.hbm.blocks.ILookOverlay;
import com.hbm.blocks.ITooltipProvider;
import com.hbm.util.i18n.I18nUtil;
import com.leafia.init.LeafiaBlockEntities;
import com.mojang.serialization.MapCodec;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The radioisotope thermal heater: a 3x2x3 machine on the oil burner's frame, heat out of the top.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/heat/rtheater/HeaterRTGBlock.java} - an
 * {@code AddonBlockDummyable} with dimensions {@code {1, 0, 1, 1, 1, 1}}, offset 1, and five extras: the
 * four sides and the block above the core. Upstream's {@code createNewTileEntity} picks the proxy by the
 * extra's metadata - {@code meta - extra > 1} is a horizontal facing, an inventory proxy; the one above
 * faces down, a heat proxy - and that is what {@code getAxis().isVertical()} reads here, as the base mod's
 * oil burner does on the same frame.
 *
 * <p>The tooltip is upstream's {@code addStandardInfo}, under {@code tile.heater_rt.desc}, and the look-at
 * panel is upstream's {@code printHook}: the store in TU and the rate with a red arrow, in yellow on dark
 * yellow. Both texts are NTM-CE's lang (upstream ships no names of its own for this block; 1.12.2's lang
 * is global, so CE's {@code tile.heater_rt.*} named it).
 */
public class HeaterRTGBlock extends BlockDummyable implements ITooltipProvider, ILookOverlay {

	public static final MapCodec<HeaterRTGBlock> CODEC = simpleCodec(HeaterRTGBlock::new);

	public HeaterRTGBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends HeaterRTGBlock> codec() {
		return CODEC;
	}

	@Override
	public int[] getDimensions() {
		return new int[] { 1, 0, 1, 1, 1, 1 };
	}

	@Override
	public int getOffset() {
		return 1;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return switch(state.getValue(PART)) {
			case CORE -> new HeaterRTGTE(LeafiaBlockEntities.HEATER_RT.get(), pos, state);
			case EXTRA -> {
				BlockEntityProxyCombo proxy = new BlockEntityProxyCombo(LeafiaBlockEntities.HEATER_RT_DUMMY.get(), pos, state);
				// Upstream: TileEntityProxyCombo(false, false, false, true) above, (true, false, false) at the sides.
				yield state.getValue(DIR).getAxis().isVertical() ? proxy.heatSource() : proxy.inventory();
			}
			case DUMMY -> null;
		};
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if(state.getValue(PART) != Part.CORE) return null;
		return createTickerHelper(type, LeafiaBlockEntities.HEATER_RT.get(), HeaterRTGTE::tick);
	}

	@Override
	protected void fillSpace(Level world, BlockPos pos, Direction dir, int o) {

		super.fillSpace(world, pos, dir, o);

		BlockPos core = pos.relative(dir, o);

		this.makeExtra(world, core.offset(1, 0, 0));
		this.makeExtra(world, core.offset(-1, 0, 0));
		this.makeExtra(world, core.offset(0, 0, 1));
		this.makeExtra(world, core.offset(0, 0, -1));
		this.makeExtra(world, core.above());
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
			Player player, BlockHitResult hit) {

		if(level.isClientSide) return InteractionResult.SUCCESS;

		BlockPos core = findCore(level, pos);
		if(core == null) return InteractionResult.FAIL;

		if(level.getBlockEntity(core) instanceof HeaterRTGTE heater && player instanceof ServerPlayer serverPlayer)
			serverPlayer.openMenu(heater, core);

		return InteractionResult.SUCCESS;
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.ENTITYBLOCK_ANIMATED;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		addStandardInfo("tile.heater_rt.desc", tooltip);
		super.appendHoverText(stack, context, tooltip, flag);
	}

	/** The two lines upstream prints: the store, and the rate behind a red arrow. */
	public static List<String> overlayLines(HeaterRTGTE heater) {
		List<String> text = new ArrayList<>();
		text.add(String.format("%,d", heater.heatEnergy) + " TU");
		text.add("§c<- §r" + String.format("%,d", heater.heatGen) + " TU/t");
		return text;
	}

	@Override
	@OnlyIn(Dist.CLIENT)
	public void printHook(GuiGraphics gui, Level world, BlockPos pos) {

		BlockPos core = this.findCore(world, pos);
		if(core == null) return;

		if(!(world.getBlockEntity(core) instanceof HeaterRTGTE heater)) return;

		ILookOverlay.printGeneric(gui, I18nUtil.resolveKey(getDescriptionId()), 0xffff00, 0x404000, overlayLines(heater));
	}
}
