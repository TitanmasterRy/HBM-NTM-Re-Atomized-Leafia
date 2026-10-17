package com.leafia.contents.machines.elevators.car.chips;

import java.util.List;

import javax.annotation.Nullable;

import com.hbm.util.i18n.I18nUtil;
import com.leafia.contents.machines.elevators.car.ElevatorEntity;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * A control chip, as an item: slot 0 of a cabin, and the thing that decides how the lift behaves.
 *
 * <p>Upstream: {@code .../car/chips/EvChipItem.java}. Its superclass {@code AddonItemBaked} is the
 * fifth addon base class to dissolve — 52 lines of 1.12.2 model baking over {@code IDynamicModels},
 * all of which is a model JSON here (D-1038).
 *
 * <p>The chip id is the registry name with {@code ev_} chopped off, the same rule
 * {@link com.leafia.contents.machines.elevators.car.styles.EvStyleItem} uses, so the two stay
 * consistent and a saved cabin keeps matching.
 */
public class EvChipItem extends Item {

	private final String res;
	private final String descKey;

	public EvChipItem(Properties props, String name) {
		super(props);
		this.res = name.substring(3);
		this.descKey = "item." + name + ".desc";
	}

	/**
	 * Upstream writes this twice, as an anonymous subclass per chip, each splitting
	 * {@code item.ev_chip_<x>.desc} on NTM's {@code $} line-break character. One field and one
	 * method here: the two bodies differed only in the key, and the key is the registry name.
	 */
	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context,
			List<Component> tooltip, TooltipFlag flag) {
		for(String line : I18nUtil.resolveKeyArray(descKey))
			tooltip.add(Component.literal(line));
		super.appendHoverText(stack, context, tooltip, flag);
	}

	/**
	 * A fresh controller for this cabin, or null if the chip's id names none.
	 *
	 * <p>Upstream matches on a suffix rather than on equality, so any id ending {@code s6} gets the
	 * S6 controller. Kept — it is how upstream would have added a variant chip — but it does mean
	 * a future {@code ev_lifts6} would silently take the S6 behaviour.
	 */
	@Nullable
	public EvChipBase getController(ElevatorEntity entity) {
		if(res.endsWith("s6")) return new EvChipS6(entity);
		if(res.endsWith("skylift")) return new EvChipSkylift(entity);
		return null;
	}

	public String getChipId() {
		return res;
	}
}
