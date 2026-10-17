package com.leafia.contents.machines.controlpanel.core.types;

import java.util.Objects;

import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.Tag;

/** A number. Adapted from NTM-CE's {@code DataValueFloat} (D-3061). Booleans are 1 and 0. */
public class DataValueFloat extends DataValue {

	public float num;

	public DataValueFloat(float f) {
		num = f;
	}

	public DataValueFloat(boolean b) {
		num = b ? 1.0F : 0.0F;
	}

	@Override
	public float getNumber() {
		return num;
	}

	@Override
	public boolean getBoolean() {
		return num != 0;
	}

	@Override
	public String toString() {
		return Float.toString(num);
	}

	@Override
	public DataType getType() {
		return DataType.NUMBER;
	}

	/** The enum constant at this number truncated to an int, or the first when it is out of range (a NaN truncates to 0). */
	@Override
	public <E extends Enum<E>> E getEnum(Class<E> clazz) {
		int i = (int) num;
		E[] enums = clazz.getEnumConstants();
		if(i >= 0 && i < enums.length) {
			return enums[i];
		}
		return enums[0];
	}

	@Override
	public DataValue copy() {
		return new DataValueFloat(num);
	}

	@Override
	public Tag writeToNBT() {
		return FloatTag.valueOf(num);
	}

	@Override
	public void readFromNBT(Tag nbt) {
		num = ((FloatTag) nbt).getAsFloat();
	}

	@Override
	public boolean equals(Object obj) {
		if(this == obj) return true;
		if(!(obj instanceof DataValueFloat other)) return false;
		return Float.compare(other.num, num) == 0;
	}

	@Override
	public int hashCode() {
		return Objects.hash(num);
	}
}
