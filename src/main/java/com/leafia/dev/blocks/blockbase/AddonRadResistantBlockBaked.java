package com.leafia.dev.blocks.blockbase;

import java.util.List;

import com.hbm.blocks.IRadResistantBlock;
import com.hbm.handler.radiation.ChunkRadiationManager;
import com.hbm.util.i18n.I18nUtil;
import com.leafia.dev.blocks.BlastResistanceTooltip;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A block that shields radiation: the addon's dark concrete bricks, the PWR's pressure vessel and
 * reflector, and the PWR's ports.
 *
 * <p>Upstream: {@code com/leafia/dev/blocks/blockbase/AddonRadResistantBlockBaked.java}, an
 * {@code AddonBlockBaked} that implements NTM-CE's {@code IRadResistantBlock}. Three things are
 * carried and one dissolves:
 *
 * <ul>
 * <li><b>The flag.</b> Upstream's {@code isRadResistant(int meta)}, default true, which the dark
 *     concrete narrows to its first two finishes. This port registers finishes as separate blocks
 *     (D-1090), so it is {@link #isRadResistant()} on the block, and it is what the base mod's seam
 *     reads: {@link #getResistance()} answers 1 for a shielding block and 0 otherwise, so
 *     {@link IRadResistantBlock#blocksRadiation} agrees with the flag. The PWR's neutron trace reads
 *     the <em>interface</em> rather than the flag - see {@code PWRElementTE.stopsNeutrons}.</li>
 * <li><b>The section rebuild.</b> Upstream calls {@code RadiationSystemNT.markSectionForRebuild} from
 *     {@code onBlockAdded} and {@code breakBlock} when the block shields. Here that is
 *     {@link ChunkRadiationManager#markSectionForRebuild}, the M1 seam, from {@link #onPlace} and
 *     {@link #onRemove}; it only sets a flag, and has a real effect only once a handler that caches
 *     section geometry exists (D-2195).</li>
 * <li><b>The tooltip.</b> "[Radiation Shielding]" in dark green for a shielding block, then
 *     {@code AddonBlockBase}'s blast-resistance line ({@link BlastResistanceTooltip}).</li>
 * <li>{@code AddonBlockBaked}'s model baking is a model JSON here (D-1015).</li>
 * </ul>
 *
 * <p>NTM-CE's own {@code BlockRadResistant}, which the PWR's hull, reflector, port and OCCS blocks
 * extend upstream, does the same three things for a block that always shields, so those blocks
 * extend this class instead: the base mod has no {@code BlockRadResistant} (hbm-src has none).
 */
public class AddonRadResistantBlockBaked extends Block implements IRadResistantBlock {

	public AddonRadResistantBlockBaked(Properties properties) {
		super(properties);
	}

	/** Upstream's {@code isRadResistant(meta)}: true unless a subclass narrows it. */
	public boolean isRadResistant() {
		return true;
	}

	/** The base seam's flag: 1 shields, anything else does not (hbm-src's convention). */
	@Override
	public int getResistance() {
		return isRadResistant() ? 1 : 0;
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		if(!oldState.is(state.getBlock()) && isRadResistant())
			ChunkRadiationManager.markSectionForRebuild(level, pos);
		super.onPlace(state, level, pos, oldState, movedByPiston);
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if(!newState.is(state.getBlock()) && isRadResistant())
			ChunkRadiationManager.markSectionForRebuild(level, pos);
		super.onRemove(state, level, pos, newState, movedByPiston);
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		if(isRadResistant())
			tooltip.add(Component.literal(ChatFormatting.DARK_GREEN + "[" + I18nUtil.resolveKey("trait.radshield") + "]"));
		BlastResistanceTooltip.add(this, tooltip);
	}
}
