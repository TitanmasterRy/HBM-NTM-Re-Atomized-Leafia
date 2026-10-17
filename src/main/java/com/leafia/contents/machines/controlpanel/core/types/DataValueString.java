package com.leafia.contents.machines.controlpanel.core.types;

import java.util.Objects;

import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/** A string, with its float reading cached. Adapted from NTM-CE's {@code DataValueString} (D-3061). */
public class DataValueString extends DataValue {

	public String str;
	private float floatVal;

	public DataValueString(String s) {
		set(s);
	}

	public void set(String s) {
		this.str = s;
		float num = 0;
		try {
			num = Float.parseFloat(str);
		} catch(Exception x) {
		}
		floatVal = num;
	}

	@Override
	public float getNumber() {
		return floatVal;
	}

	/** True only for the exact lower-case text {@code true}. */
	@Override
	public boolean getBoolean() {
		return str.equals("true");
	}

	@Override
	public String toString() {
		return str;
	}

	@Override
	public <E extends Enum<E>> E getEnum(Class<E> clazz) {
		E[] enms = clazz.getEnumConstants();
		for(E enm : enms) {
			if(enm.name().equalsIgnoreCase(str))
				return enm;
		}
		return enms[0];
	}

	@Override
	public DataType getType() {
		return DataType.STRING;
	}

	@Override
	public DataValue copy() {
		return new DataValueString(str);
	}

	@Override
	public Tag writeToNBT() {
		return StringTag.valueOf(str);
	}

	@Override
	public void readFromNBT(Tag nbt) {
		set(((StringTag) nbt).getAsString());
	}

	@Override
	public boolean equals(Object obj) {
		if(this == obj) return true;
		if(!(obj instanceof DataValueString other)) return false;
		return Objects.equals(str, other.str);
	}

	@Override
	public int hashCode() {
		return Objects.hash(str);
	}
}
