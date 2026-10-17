package com.leafia.init.hazards.modifiers;

import javax.annotation.Nullable;

import com.hbm.hazard.modifier.HazardModifier;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Lets a stack carry its own radiation: an absolute value that replaces the registered level, or
 * a multiplier on it.
 *
 * <p>Each classified radiation entry gets one of these keyed to its own type, so a stack can be
 * made, say, twice as alpha-active without touching its gamma. Upstream keeps the keys on the
 * stack's NBT tag as {@code <key>_abs} and {@code <key>_mod} doubles; here they are in the stack's
 * {@code custom_data} component, under the same names. <b>The absolute wins</b> when both are
 * present, and a stack with neither - or with no tag at all - passes the level through untouched.
 *
 * <p>Nothing in the port writes these keys yet: upstream's writers are the activation and
 * decay machinery, which is unported (D-1078).
 */
public class NBTModifier extends HazardModifier {

	private final NBTKey nbtKey;

	public NBTModifier(NBTKey nbtKey) {
		this.nbtKey = nbtKey;
	}

	@Override
	public float modify(ItemStack stack, @Nullable LivingEntity holder, float level) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if(data == null) return level;
		CompoundTag compound = data.copyTag();
		if(compound.contains(nbtKey.getAbsKey())) {
			return (float) compound.getDouble(nbtKey.getAbsKey());
		}
		if(compound.contains(nbtKey.getModKey())) {
			double value = compound.getDouble(nbtKey.getModKey());
			return (float) (value * level);
		}
		return level;
	}

	public void setAbsolute(ItemStack stack, double value) {
		setAbsolute(stack, nbtKey, value);
	}

	public void setMod(ItemStack stack, double value) {
		setMod(stack, nbtKey, value);
	}

	public static void setAbsolute(ItemStack stack, NBTKey nbtKey, double value) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putDouble(nbtKey.getAbsKey(), value));
	}

	public static void setMod(ItemStack stack, NBTKey nbtKey, double value) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putDouble(nbtKey.getModKey(), value));
	}

	public enum NBTKey {
		ALPHA("alpha"),
		BETA("beta"),
		GAMMA("gamma"),
		NEUTRONS("neutrons"),
		ACTIVATION("activation"), // original radiation
		RADON("radon"),
		XRAY("xray");

		private final String absKey;
		private final String modKey;

		NBTKey(String key) {
			this.absKey = key + "_abs";
			this.modKey = key + "_mod";
		}

		public String getModKey() {
			return modKey;
		}

		public String getAbsKey() {
			return absKey;
		}
	}
}
