package com.leafia.contents.network.ff_duct.utility.filter;

import java.util.List;

import javax.annotation.Nullable;

import com.hbm.util.i18n.I18nUtil;
import com.leafia.contents.machines.reactors.lftr.components.element.MSRElementTE.MSRFuel;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * The Mixture Filter's item: it will not be placed until a fuel has been chosen for it.
 *
 * <p>Upstream: {@code com/leafia/contents/network/ff_duct/utility/filter/FFFilterItem.java}, an {@code ItemBlock}
 * whose NBT {@code "filter"} holds the {@link MSRFuel}'s name (here the stack's custom data, under the same key).
 *
 * <ul>
 * <li><b>Right click</b> (the air, or a block it did not place on) steps the filter to the next fuel -
 *     {@code th232, pa233, u233, u235, pa234}, then round to the first; an item with no filter, or one whose
 *     filter is not a fuel, starts at the first - and tells the player "Filter set to X!" in light purple. It
 *     happens on the server only; the stack then carries the key and can be placed.</li>
 * <li><b>Placing</b> an item with no filter key does nothing. A placement that fails is not a "no" - see below.</li>
 * <li>The tooltip names the filter, or an unreadable one as {@code [ERROR]} in red.</li>
 * </ul>
 *
 * <p><b>One change of verdict, to keep upstream's behaviour.</b> 1.12.2's {@code onItemUse} answers {@code FAIL} and
 * the client carries on to {@code onItemRightClick}; 1.21.1 stops on a {@code FAIL} without calling {@code use}. So a
 * use that upstream refuses answers {@code PASS} here, and a block click with an unset filter still steps it. The same
 * holds for a placement that failed for another reason with a filter set (nothing to place against, a protected
 * area): it steps the filter too, as upstream's does.
 */
public class FFFilterItem extends BlockItem {

	public static final String KEY = "filter";

	public FFFilterItem(Block block, Properties properties) {
		super(block, properties);
	}

	/** The stack's filter key, whether or not it names a fuel; null if there is none. */
	@Nullable
	public static String rawFilter(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.contains(KEY) ? data.copyTag().getString(KEY) : null;
	}

	/** The fuel the stack names, or null if it names none (no key, or a name that is not a fuel). */
	@Nullable
	public static MSRFuel getFilter(ItemStack stack) {
		String raw = rawFilter(stack);
		if(raw == null) return null;
		try {
			return MSRFuel.valueOf(raw);
		} catch(IllegalArgumentException ignored) {
			return null;
		}
	}

	public static void setFilter(ItemStack stack, MSRFuel fuel) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(KEY, fuel.name()));
	}

	/** The next fuel in the cycle after what the stack names (the first, if it names none). */
	public static MSRFuel next(ItemStack stack) {
		int index = -1;
		MSRFuel current = getFilter(stack);
		if(current != null) index = current.ordinal();
		index++;
		if(index >= MSRFuel.values().length)
			index = 0;
		return MSRFuel.values()[index];
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if(rawFilter(context.getItemInHand()) != null) {
			InteractionResult result = super.useOn(context);
			return result == InteractionResult.FAIL ? InteractionResult.PASS : result;
		}
		return InteractionResult.PASS;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if(!level.isClientSide) {
			MSRFuel fuel = next(stack);
			setFilter(stack, fuel);
			player.sendSystemMessage(Component.translatable("tile.ff_filter.message", Component.translatable("tile.msr.fuel." + fuel.name()))
					.withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		return InteractionResultHolder.pass(stack);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		String raw = rawFilter(stack);
		if(raw != null) {
			MSRFuel fuel = getFilter(stack);
			if(fuel != null)
				tooltip.add(Component.literal(ChatFormatting.LIGHT_PURPLE + I18nUtil.resolveKey("tile.ff_filter.filter", I18nUtil.resolveKey("tile.msr.fuel." + fuel.name()))));
			else
				tooltip.add(Component.literal(ChatFormatting.LIGHT_PURPLE + I18nUtil.resolveKey("tile.ff_filter.filter", ChatFormatting.RED + "[ERROR]")));
		}
		super.appendHoverText(stack, context, tooltip, flag);
	}
}
