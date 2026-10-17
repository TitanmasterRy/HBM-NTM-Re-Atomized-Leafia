package com.leafia.contents.control.battery;

import java.util.List;

import api.hbm.energymk2.IBatteryItem;
import com.hbm.util.BobMathUtil;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

/**
 * One of the addon's battery packs: the base mod's {@code ItemBatteryPack}, for a variant the
 * base enum cannot hold.
 *
 * <p>Upstream's {@code AddonBatteryPackItem} is dead — its whole body is commented out under
 * {@code @Deprecated // suck my butt} — because upstream made its packs live by appending them
 * to the base enum with a mixin, after which the base item served them. That route is closed
 * here (D-1001), so this class is that base item's behaviour over {@link AddonEnumBatteryPack},
 * and every method is {@code com.hbm.items.machine.ItemBatteryPack}'s, asymmetries included:
 *
 * <ul>
 * <li>a pack with no charge yet reads as <b>empty</b>, and reading it writes that down;
 * <li>discharging one with no charge yet writes 0 rather than subtracting from an implicit 0;
 * <li>its tooltip falls back to <b>full</b> on such a stack — the base mod's flagged
 *     inconsistency (D-021), carried so the two kinds of pack do not disagree with each other.
 * </ul>
 *
 * <p>So a pack charges and discharges in the energy storage block's slots, and anywhere else
 * that takes an {@link IBatteryItem}, exactly as the base mod's do.
 */
public class AddonBatteryPackItem extends Item implements IBatteryItem {

	private final AddonEnumBatteryPack variant;

	public AddonBatteryPackItem(Properties properties, AddonEnumBatteryPack variant) {
		super(properties);
		this.variant = variant;
	}

	public AddonEnumBatteryPack getVariant() {
		return variant;
	}

	private static boolean hasCharge(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.contains(IBatteryItem.getChargeTagName(stack));
	}

	@Override
	public void chargeBattery(ItemStack stack, long i) {
		IBatteryItem.writeCharge(stack, IBatteryItem.readCharge(stack) + i);
	}

	@Override
	public void setCharge(ItemStack stack, long i) {
		IBatteryItem.writeCharge(stack, i);
	}

	@Override
	public void dischargeBattery(ItemStack stack, long i) {
		if(hasCharge(stack))
			IBatteryItem.writeCharge(stack, IBatteryItem.readCharge(stack) - i);
		else
			IBatteryItem.writeCharge(stack, 0);
	}

	@Override
	public long getCharge(ItemStack stack) {
		if(hasCharge(stack))
			return IBatteryItem.readCharge(stack);
		IBatteryItem.writeCharge(stack, 0);
		return 0;
	}

	@Override
	public long getMaxCharge(ItemStack stack) {
		return variant.capacity;
	}

	@Override
	public long getChargeRate(ItemStack stack) {
		return variant.chargeRate;
	}

	@Override
	public long getDischargeRate(ItemStack stack) {
		return variant.dischargeRate;
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return getCharge(stack) != getMaxCharge(stack);
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		long max = getMaxCharge(stack);
		if(max <= 0) return 0;
		return Math.round(13.0F * (float) getCharge(stack) / (float) max);
	}

	@Override
	public int getBarColor(ItemStack stack) {
		long max = getMaxCharge(stack);
		float f = max <= 0 ? 0.0F : Math.max(0.0F, (float) getCharge(stack) / (float) max);
		return Mth.hsvToRgb(f / 3.0F, 1.0F, 1.0F);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		long maxCharge = getMaxCharge(stack);
		long chargeRate = getChargeRate(stack);
		long dischargeRate = getDischargeRate(stack);
		long charge = hasCharge(stack) ? getCharge(stack) : maxCharge;

		tooltip.add(Component.literal(ChatFormatting.GREEN + "Energy stored: " + BobMathUtil.getShortNumber(charge)
				+ "/" + BobMathUtil.getShortNumber(maxCharge) + "HE (" + (charge * 1000 / maxCharge / 10D) + "%)"));
		tooltip.add(Component.literal(ChatFormatting.YELLOW + "Charge rate: " + BobMathUtil.getShortNumber(chargeRate) + "HE/t"));
		tooltip.add(Component.literal(ChatFormatting.YELLOW + "Discharge rate: " + BobMathUtil.getShortNumber(dischargeRate) + "HE/t"));
		tooltip.add(Component.literal(ChatFormatting.GOLD + "Time for full charge: " + (maxCharge / chargeRate / 20 / 60D) + "min"));
		tooltip.add(Component.literal(ChatFormatting.GOLD + "Charge lasts for: " + (maxCharge / dischargeRate / 20 / 60D) + "min"));
	}
}
