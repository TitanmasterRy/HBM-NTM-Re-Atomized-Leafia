package com.leafia.contents.machines.powercores.dfc;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * The two things a player throws into a collapsing Dark Fusion Core to stop it.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/powercores/dfc/CrucifixItem.java}, registered twice:
 * {@code fix_tool} ("Cruci"fix"") always works and {@code fix_survival} ("Satanic Energy Suppressor")
 * fails four times in five less one in ten per stabilizer. What they do is the core's
 * ({@code LeafiaCoreTE.vaporization}); this class is the tooltip and one rule - <b>a dropped crucifix
 * cannot be destroyed</b>, so it survives the fire and the blast it is thrown into. Upstream calls
 * {@code setEntityInvulnerable(true)} on the item entity every tick; 1.21.1's is
 * {@code setInvulnerable}, from NeoForge's {@code IItemExtension.onEntityItemUpdate}.
 */
public class CrucifixItem extends Item {

	/** Which of the two this is: the always-working one or the survival one. */
	private final boolean always;

	public CrucifixItem(Item.Properties properties, boolean always) {
		super(properties);
		this.always = always;
	}

	public boolean isAlwaysEffective() {
		return always;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> list, TooltipFlag flag) {
		if(always)
			list.add(Component.literal(ChatFormatting.GRAY + "Throw this into a collapsing DFC to fix it"));
		else {
			list.add(Component.literal(ChatFormatting.GRAY + "It's a crucifix."));
			list.add(Component.literal(ChatFormatting.GRAY + "Throw this into a collapsing DFC to shut it down."));
			list.add(Component.literal(ChatFormatting.GRAY + "Chance of failure is 80% and is decreased by 10% for each stabilizers there are."));
		}
		list.add(Component.literal(ChatFormatting.RED + "Shut down all boosters before using this or it will not work!"));
		super.appendHoverText(stack, context, list, flag);
	}

	@Override
	public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
		if(entity.isAlive())
			entity.setInvulnerable(true);
		return super.onEntityItemUpdate(stack, entity);
	}
}
