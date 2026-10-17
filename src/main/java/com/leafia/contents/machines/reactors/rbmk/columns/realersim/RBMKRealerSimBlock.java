package com.leafia.contents.machines.reactors.rbmk.columns.realersim;

import java.util.List;

import com.hbm.blockentity.BlockEntityProxyCombo;
import com.hbm.blockentity.machine.rbmk.BlockEntityRBMKBase;
import com.hbm.blocks.ITooltipProvider;
import com.hbm.blocks.machine.rbmk.RBMKRodReaSim;
import com.leafia.init.LeafiaBlockEntities;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The RealerSim fuel channel's block, plain and moderated.
 *
 * <p>Upstream: {@code com.leafia.contents.machines.reactors.rbmk.columns.realersim.RBMKRealerSimBlock},
 * an {@code RBMKRodReaSim} whose only change is {@code createNewTileEntity}: the core gets
 * {@link RBMKRealerSimTE}, the top gets an inventory proxy, the rest nothing. Everything a player
 * does to the column — loading a rod, the lid, breaking it hot — is the base mod's
 * {@code RBMKRod} and is inherited.
 *
 * <p>Here the block entity types are the addon's own, because a {@code BlockEntityType} may only
 * name blocks that construct it and the base mod's types name the base mod's blocks. So the top's
 * proxy is {@link LeafiaBlockEntities#RBMK_REALERSIM_PROXY}, built exactly as the base mod's
 * ReaSim builds its own, and the ticker is bound to {@link LeafiaBlockEntities#RBMK_REALERSIM}.
 *
 * <p>The tooltip is NTM-CE's {@code RBMKBase.addInformation}, which shows the block's
 * {@code .desc} behind shift. {@code hbm-src}'s RBMK blocks have none, so the base mod's do not
 * either, and the addon adds it on its own blocks only.
 */
public class RBMKRealerSimBlock extends RBMKRodReaSim implements ITooltipProvider {

	public static final MapCodec<RBMKRealerSimBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			propertiesCodec(),
			Codec.BOOL.fieldOf("moderated").forGetter(block -> block.moderated)
	).apply(instance, RBMKRealerSimBlock::new));

	public RBMKRealerSimBlock(Properties properties, boolean moderated) {
		super(properties, moderated);
	}

	@Override
	protected MapCodec<? extends RBMKRealerSimBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return switch(state.getValue(PART)) {
			case CORE -> new RBMKRealerSimTE(pos, state);
			case EXTRA -> new BlockEntityProxyCombo(LeafiaBlockEntities.RBMK_REALERSIM_PROXY.get(), pos, state).inventory();
			default -> null;
		};
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if(level.isClientSide) return null;
		if(state.getValue(PART) != Part.CORE) return null;
		return createTickerHelper(type, LeafiaBlockEntities.RBMK_REALERSIM.get(),
				(lvl, pos, st, be) -> BlockEntityRBMKBase.tick(lvl, pos, st, be));
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		addStandardInfo(getDescriptionId() + ".desc", tooltip);
		super.appendHoverText(stack, context, tooltip, flag);
	}
}
