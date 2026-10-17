package com.leafia.init.hazards.types;

import java.util.List;

import com.hbm.hazard.modifier.HazardModifier;
import com.hbm.hazard.type.HazardTypeBase;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Sharp edges: an item that makes a hit, a fall or a pick-up cut deeper.
 *
 * <p>Upstream: {@code com.leafia.init.hazards.types.HazardTypeSharpEdges}. The type does nothing of
 * its own - {@code onUpdate} and {@code updateEntity} are empty upstream and here - it is a tooltip
 * and a number. What a sharp item <em>does</em> is {@link com.leafia.eventbuses.LeafiaSharpEdges},
 * which reads the level off this type and adds a second, pointed hit that bypasses armour.
 *
 * <p>{@link #sharpStackNerf} is upstream's mutable static: a stack of N sharp items adds
 * {@code level x (N x (1 - nerf) + nerf)} percent, not {@code level x N}.
 *
 * <p>Upstream builds the lines from {@code I18nUtil}, which answers a sarcastic constant on a
 * server; here they are translatable components, so the same code is safe on either side. The
 * stack line's "Stack:" is the addon's own key ({@code trait._hazarditem.stack}) because the
 * 1.12.2 key it used ({@code desc.stack}) is not in the base mod's language file.
 */
public class HazardTypeSharpEdges extends HazardTypeBase {

	public static final HazardTypeSharpEdges INSTANCE = new HazardTypeSharpEdges();

	/** Upstream's {@code sharpStackNerf}, 0.75. Mutable as upstream's is; a test that writes it restores it. */
	public static float sharpStackNerf = 0.75F;

	private HazardTypeSharpEdges() {}

	@Override
	public void onUpdate(LivingEntity target, float level, ItemStack stack) { }

	@Override
	public void updateEntity(ItemEntity item, float level) { }

	@Override
	public void addHazardInformation(Player player, List<Component> list, float level, ItemStack stack, List<HazardModifier> modifiers) {
		list.add(Component.literal("[").append(Component.translatable("trait._hazarditem.sharp")).append("]")
				.withStyle(ChatFormatting.DARK_RED));
		list.add(Component.literal(" -::").withStyle(ChatFormatting.DARK_RED)
				.append(Component.translatable("trait._hazarditem.sharp.add", percent(level)).withStyle(ChatFormatting.RED)));
		if(stack.getCount() > 1) {
			list.add(Component.literal(" -::").withStyle(ChatFormatting.DARK_RED)
					.append(Component.translatable("trait._hazarditem.stack").withStyle(ChatFormatting.RED))
					.append(Component.literal(" " + stackPercent(level, stack.getCount()) + "%").withStyle(ChatFormatting.RED)));
		}
	}

	/** Upstream's {@code Math.round(v * 1) + "%"}. */
	public static String percent(float level) {
		return Math.round((double) level) + "%";
	}

	/** Upstream's {@code Math.round(v * count * (1 - nerf) + v * nerf)}. */
	public static long stackPercent(float level, int count) {
		double v = level;
		return Math.round(v * count * (1 - sharpStackNerf) + v * sharpStackNerf);
	}
}
