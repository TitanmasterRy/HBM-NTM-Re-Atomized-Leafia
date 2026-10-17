package com.leafia.contents.machines.controlpanel.core.types;

import java.util.Collections;
import java.util.Set;

import com.leafia.LeafiaMod;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/**
 * A value on the Custom Control Panel's wire: a variable, a node socket's default, an event field, a config entry.
 *
 * <p>Adapted from NTM-CE's {@code com.hbm.inventory.control_panel.types.DataValue} (D-3060, D-3061; credit in
 * {@code addon-leafia/NOTICE}). Four kinds exist, and the NBT shape is how a reader tells them apart: a
 * {@link FloatTag} is a {@link DataValueFloat}, a {@link StringTag} a {@link DataValueString}, and a compound is an
 * {@link DataValueEnum} when it carries an int {@code ordinal} and a {@link DataValueComposite} otherwise.
 *
 * <p><b>This is read from client-authored NBT</b> (a panel saved from the editor arrives whole), so
 * {@link #newFromNBT(Tag)} never throws: a malformed value is logged and becomes {@code null}, and the caller decides.
 */
public abstract class DataValue {

	public abstract float getNumber();

	public abstract boolean getBoolean();

	@Override
	public abstract String toString();

	public String getValueOf(String key) {
		return "ERROR";
	}

	public void setValueOf(String key, String value) {}

	public Set<String> values() {
		return Collections.emptySet();
	}

	public abstract DataType getType();

	public abstract <E extends Enum<E>> E getEnum(Class<E> clazz);

	public abstract DataValue copy();

	public abstract Tag writeToNBT();

	public abstract void readFromNBT(Tag nbt);

	public static DataValue newFromNBT(Tag base) {
		DataValue val = null;
		try {
			if(base instanceof CompoundTag tag) {
				if(tag.contains(DataValueEnum.KEY_ORDINAL, Tag.TAG_INT)) {
					val = new DataValueEnum<>(null);
				} else {
					val = new DataValueComposite();
				}
				val.readFromNBT(base);
			} else if(base instanceof FloatTag) {
				val = new DataValueFloat(0);
				val.readFromNBT(base);
			} else if(base instanceof StringTag) {
				val = new DataValueString("");
				val.readFromNBT(base);
			}
		} catch(Exception x) {
			// No stack trace: this is client-authored data, and a trace per bad value is a way to flood the log.
			LeafiaMod.LOGGER.warn("Failed to deserialize control-panel data value from NBT type {}: {}",
					base == null ? "null" : base.getClass().getName(), x.toString());
			return null;
		}
		return val;
	}

	public enum DataType {
		GENERIC(new float[]{0.5F, 0.5F, 0.5F}),
		NUMBER(new float[]{0.4F, 0.6F, 0}),
		STRING(new float[]{0, 1, 1}),
		ENUM(new float[]{0.5F, 0.29F, 0}),
		COMPOSITE(new float[]{1F, 0.399F, 0.842F});

		public static final DataType[] VALUES = values();
		private final float[] color;

		DataType(float[] color) {
			this.color = color;
		}

		public float[] getColor() {
			return color;
		}
	}
}
