package com.leafia.contents.machines.powercores.dfc;

import java.util.List;

import com.hbm.items.machine.ItemLens;
import com.hbm.util.i18n.I18nUtil;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * A stabilizer lens with three modifiers: how much field it buys, how much power it drains, and what it
 * does to the core's energy.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/powercores/dfc/LCEItemLens.java} ("when it's called
 * LeafiaItemLens but it's actually made by movblock"). The wear is the base mod's {@link ItemLens}:
 * a lens is used up by its {@code maxDamage} and the AMC and DFC stabilizers both read it.
 *
 * <h2>The five lenses, upstream's numbers</h2>
 *
 * <table>
 * <tr><th>id</th><th>wear</th><th>field</th><th>drain</th><th>energy</th></tr>
 * <tr><td>{@code ams_focus_blank}</td><td>400,000</td><td>0.5</td><td>1.75</td><td>1</td></tr>
 * <tr><td>{@code ams_focus_limiter}</td><td>2,500,000,000</td><td>1.5</td><td>1.75</td><td>0.8</td></tr>
 * <tr><td>{@code ams_focus_booster}</td><td>100,000,000</td><td>0.8</td><td>0.5</td><td>1.35</td></tr>
 * <tr><td>{@code ams_focus_omega}</td><td>1,000,000,000</td><td>5.0</td><td>10.0</td><td>3.5</td></tr>
 * <tr><td>{@code ams_focus_safe}</td><td>5,000,000,000</td><td>8.0</td><td>4.5</td><td>0.1</td></tr>
 * </table>
 *
 * <p>Upstream also turns the base mod's {@code ams_lens} into one of these with 1/1/1 when its DFC
 * overhaul is on ({@code MixinModItems}, a {@code new} redirect in {@code ModItems.<clinit>}). The
 * base item cannot change class here, so {@link #fieldMod}, {@link #drainMod} and {@link #energyMod}
 * answer 1 for any lens that is not one of these - the same numbers - and the base lens's three tooltip
 * lines are added by a client tooltip listener while the overhaul is on (D-3011).
 */
public class LCEItemLens extends ItemLens {

	public final float fieldMod;
	public final float drainMod;
	public final float energyMod;

	public LCEItemLens(Item.Properties properties, long maxDamage, float fieldMod, float drainMod, float energyMod) {
		super(properties, maxDamage);
		this.fieldMod = fieldMod;
		this.drainMod = drainMod;
		this.energyMod = energyMod;
	}

	/** This stack's field modifier; 1 for the base mod's lens or anything else. */
	public static float fieldMod(ItemStack stack) {
		return stack.getItem() instanceof LCEItemLens lens ? lens.fieldMod : 1.0F;
	}

	public static float drainMod(ItemStack stack) {
		return stack.getItem() instanceof LCEItemLens lens ? lens.drainMod : 1.0F;
	}

	public static float energyMod(ItemStack stack) {
		return stack.getItem() instanceof LCEItemLens lens ? lens.energyMod : 1.0F;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		addModifierLines(tooltip, fieldMod, drainMod, energyMod);
	}

	/** Upstream's three lines, colour codes and all; shared with the base lens's tooltip listener. */
	public static void addModifierLines(List<Component> tooltip, float fieldMod, float drainMod, float energyMod) {
		tooltip.add(Component.literal(ChatFormatting.YELLOW + I18nUtil.resolveKey("desc.lens.fieldmodifier") + " " + fieldText(fieldMod)));
		tooltip.add(Component.literal(ChatFormatting.YELLOW + I18nUtil.resolveKey("desc.lens.powdrainmodifier") + " " + drainText(drainMod)));
		tooltip.add(Component.literal(ChatFormatting.YELLOW + I18nUtil.resolveKey("desc.lens.energymodifier") + " " + energyText(energyMod)));
	}

	/** {@code (fieldMod >= 1 ? "§a+" : "§c") + (Math.round(fieldMod * 1000) * .10 - 100) + "%"}, upstream's expression. */
	public static String fieldText(float fieldMod) {
		return (fieldMod >= 1 ? "§a+" : "§c") + (Math.round(fieldMod * 1000) * .10 - 100) + "%";
	}

	public static String drainText(float drainMod) {
		return (drainMod >= 1 ? "§c+" : "§a") + (Math.round(drainMod * 1000) * .10 - 100) + "%";
	}

	public static String energyText(float energyMod) {
		return (energyMod > 1 ? "§6+" : ("§8" + (energyMod == 1 ? "+" : ""))) + (Math.round(energyMod * 1000) * .10 - 100) + "%";
	}
}
