package com.leafia.dev.blocks;

import java.util.List;

import com.hbm.util.i18n.I18nUtil;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;

/**
 * The one behaviour that survives from upstream's {@code AddonBlockBase}: a gold line saying how
 * much blast a block takes, shown only above 50.
 *
 * <p>D-1015 found the rest of that class to be 1.12.2 registration plumbing and left this to "the
 * block that first needs it"; D-1071 put it on {@code OsmiridiumBlock}, and the status line said
 * "a second block over 50, at which point the tooltip becomes a shared helper". The two corium
 * ores (5,400) are that second block, so it is this (D-1076). Upstream's {@code AddonOreBase} has
 * its own copy of the same four lines, and {@code ComputerCableBlock} a third.
 *
 * <p>Upstream shows {@code getExplosionResistance(null)}, which in 1.12.2 is already the stored
 * value divided by five - the figure {@link Block#getExplosionResistance()} is here.
 */
public final class BlastResistanceTooltip {

	private BlastResistanceTooltip() {}

	/** Above this, the line is shown. Upstream's literal. */
	public static final float THRESHOLD = 50F;

	/** Adds the line if {@code block} takes more than {@link #THRESHOLD}. */
	public static void add(Block block, List<Component> tooltip) {
		float resistance = block.getExplosionResistance();
		if(resistance > THRESHOLD)
			tooltip.add(Component.literal(ChatFormatting.GOLD + I18nUtil.resolveKey("trait.blastres", resistance)));
	}
}
