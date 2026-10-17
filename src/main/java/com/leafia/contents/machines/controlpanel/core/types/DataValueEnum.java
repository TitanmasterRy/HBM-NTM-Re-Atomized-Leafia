package com.leafia.contents.machines.controlpanel.core.types;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.DyeColor;

/**
 * One constant of a registered enum. Adapted from NTM-CE's {@code DataValueEnum} (D-3061), with one deliberate divergence.
 *
 * <p><b>CE wrote the enum's Java class name and read it back with {@code Class.forName}.</b> That read runs on NBT a client
 * authored (the editor sends the whole panel tree), so a modified client could name
 * any class on the server's classpath and have its static initialiser run. The port keeps a <b>closed registry</b> instead:
 * an enum is addressed on the wire by a short id the addon registered in code, and an id that is not in the registry is an
 * error, never a lookup. The NBT key therefore changes from {@code clazz} to {@link #KEY_ENUM}. Recorded under D-1093 #4.
 *
 * <p>The registry holds {@code direction} (CE's {@code EnumFacing}) and {@code dye_color} (CE's {@code EnumDyeColor}), the two
 * enums the core uses. Their constant order is the 1.12.2 order, so a stored ordinal means the same constant; a GameTest pins it.
 * An enum an addon control needs is added with {@link #register}, from addon code only.
 */
public class DataValueEnum<T extends Enum<T>> extends DataValue {

	/** The registry id of the enum class. (CE: {@code clazz}, a Java class name.) */
	public static final String KEY_ENUM = "enum";
	/** The constant's ordinal, as in CE. */
	public static final String KEY_ORDINAL = "ordinal";

	private static final Map<String, Class<? extends Enum<?>>> BY_ID = new LinkedHashMap<>();
	private static final Map<Class<?>, String> BY_CLASS = new LinkedHashMap<>();

	static {
		register("direction", Direction.class);
		register("dye_color", DyeColor.class);
	}

	/**
	 * Adds an enum to the closed registry. Call it from addon code at class-initialisation or setup, never with a name that
	 * came off the wire. Registering the same id for the same class again is a no-op; a different class under a taken id throws.
	 */
	public static synchronized <E extends Enum<E>> void register(String id, Class<E> clazz) {
		Class<? extends Enum<?>> have = BY_ID.get(id);
		if(have != null && have != clazz)
			throw new IllegalArgumentException("Control-panel enum id '" + id + "' is already " + have.getName());
		BY_ID.put(id, clazz);
		BY_CLASS.put(clazz, id);
	}

	/** The registered ids, in registration order. */
	public static synchronized Set<String> registeredIds() {
		return Collections.unmodifiableSet(new LinkedHashMap<>(BY_ID).keySet());
	}

	/** The id of a registered enum class, or null. */
	public static synchronized String idOf(Class<?> clazz) {
		return BY_CLASS.get(clazz);
	}

	/** The class registered under this id, or null. This is the only way a wire string becomes a class. */
	public static synchronized Class<? extends Enum<?>> classOf(String id) {
		return BY_ID.get(id);
	}

	public Enum<T> value;
	public Class<T> enumClass;

	@SuppressWarnings("unchecked")
	public DataValueEnum(Enum<T> e) {
		value = e;
		if(e != null)
			enumClass = (Class<T>) e.getDeclaringClass();
	}

	@Override
	public float getNumber() {
		return value.ordinal();
	}

	@Override
	public boolean getBoolean() {
		return value.toString().toLowerCase().equals("true");
	}

	@Override
	public String toString() {
		return value.toString();
	}

	@Override
	@SuppressWarnings("unchecked")
	public <E extends Enum<E>> E getEnum(Class<E> clazz) {
		if(clazz == enumClass) {
			return (E) value;
		} else {
			return clazz.getEnumConstants()[0];
		}
	}

	@Override
	public DataType getType() {
		return DataType.ENUM;
	}

	public T[] getPossibleValues() {
		return enumClass.getEnumConstants();
	}

	@Override
	public DataValue copy() {
		return new DataValueEnum<>(value);
	}

	@Override
	public Tag writeToNBT() {
		String id = idOf(enumClass);
		if(id == null)
			throw new IllegalStateException("Enum " + enumClass.getName() + " is not in the control-panel enum registry");
		CompoundTag tag = new CompoundTag();
		tag.putString(KEY_ENUM, id);
		tag.putInt(KEY_ORDINAL, value.ordinal());
		return tag;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void readFromNBT(Tag nbt) {
		CompoundTag tag = (CompoundTag) nbt;
		String id = tag.getString(KEY_ENUM);
		Class<? extends Enum<?>> clazz = classOf(id);
		if(clazz == null)
			throw new IllegalArgumentException("Unknown control-panel enum id '" + id + "'");
		int ordinal = tag.getInt(KEY_ORDINAL);
		Enum<?>[] constants = clazz.getEnumConstants();
		if(ordinal < 0 || ordinal >= constants.length)
			throw new IllegalArgumentException("Ordinal " + ordinal + " is out of range for control-panel enum '" + id + "'");
		this.enumClass = (Class<T>) clazz;
		this.value = (Enum<T>) constants[ordinal];
	}

	@Override
	public boolean equals(Object obj) {
		if(this == obj) return true;
		if(!(obj instanceof DataValueEnum<?> other)) return false;
		return Objects.equals(value, other.value) && Objects.equals(enumClass, other.enumClass);
	}

	@Override
	public int hashCode() {
		return Objects.hash(value, enumClass);
	}
}
