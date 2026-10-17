package com.leafia.contents.machines.powercores.dfc;

import java.util.List;

import com.leafia.dev.blocks.BlastResistanceTooltip;
import com.hbm.util.ContaminationUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A block of welded osmiridium, and its xanaxium-etched form: the addon's toughest blocks.
 *
 * <p>Upstream: {@code com.leafia.contents.machines.powercores.dfc.OsmiridiumBlock}, an
 * {@code AddonBlockBaked} with {@code setHardness(15)} and {@code setResistance(6500000)} at the
 * registration — 3,900,000 here by D-012's ×0.6.
 *
 * <p><b>The tooltip</b> is {@code AddonBlockBase}'s, the one survivor of that dissolved base
 * class (D-1015): above 50 blast resistance, a gold line saying how much. These are the first
 * addon blocks over 50, which is why it was here and not earlier; the corium ores are the second,
 * so it is {@link BlastResistanceTooltip} now (D-1076). Upstream shows
 * {@code getExplosionResistance(null)}, which in 1.12.2 is already the ÷5 figure — the same
 * number as {@link #getExplosionResistance()} here.
 *
 * <p><b>It radiates digamma.</b> Upstream ticks randomly and calls NTM-CE's
 * {@code ContaminationUtil.radiate(world, x + 0.5, y + 0.5, z + 0.5, 32, 0, 40, 0, 0, 0)}: no
 * radiation, 40 digamma, within 32 blocks, falling off with the cube of the distance. That
 * method is NTM-CE's, not {@code hbm-src}'s; D-1073 read it out of the vendored CE jar into an
 * addon stand-in and D-3023 retired that for the base mod's own {@code ContaminationUtil.radiate}.
 */
public class OsmiridiumBlock extends Block {

	public OsmiridiumBlock(Properties properties) {
		super(properties);
	}

	/** Upstream's {@code updateTick}, which its {@code needsRandomTick} makes a random tick. */
	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		ContaminationUtil.radiate(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, RANGE, 0F, DIGAMMA);
	}

	/** Upstream's arguments to {@code radiate}. */
	public static final double RANGE = 32;
	public static final float DIGAMMA = 40F;

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		BlastResistanceTooltip.add(this, tooltip);
	}
}
