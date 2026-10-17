package com.leafia.contents.control.battery;

/**
 * The addon's six battery packs and their numbers.
 *
 * <p>Upstream: {@code com.leafia.contents.control.battery.AddonEnumBatteryPack}, six static fields
 * that {@code MixinEnumBatteryPack} fills by appending constants to the base mod's own
 * {@code ItemBatteryPack.EnumBatteryPack} at class-init time. The addon never mixes into
 * {@code com.hbm} (D-1001), and a Java enum is closed, so here the six are an enum of their own
 * with the base enum's constructors copied term for term — the values are the <b>mixin's</b>,
 * which differ from the dead {@code AddonBatteryPackItem}'s commented-out table (desh 175,000
 * there 125,000; euphemium 1,500,000 there 500,000; slop 12,222,222 there 833,333,333). The
 * mixin is what ran, so the mixin is the specification (D-1067).
 *
 * <p>None is a capacitor: the mixin overrides {@code isCapacitor} to test the name for
 * {@code CAPACITOR_}, and all six are named {@code BATTERY_}.
 */
public enum AddonEnumBatteryPack {

	BATTERY_DESH      ("battery_desh",             175_000L, false),
	BATTERY_EUPHEMIUM ("battery_euphemium",      1_500_000L, false),
	BATTERY_SLOP      ("battery_slop",          12_222_222L, 20L * 60L * 10L),
	BATTERY_SPK       ("battery_spk",       16_666_666_667L, 20L * 60L * 5L),
	BATTERY_ELECTRO   ("battery_electro",  166_666_666_667L, 20L * 60L * 5L),
	BATTERY_SATURNITE ("battery_saturnite",        800_000L, false);

	/**
	 * The texture name. Upstream's constructor turns it into the battery socket's skin under
	 * {@code hbm:textures/models/machines/}; here it is {@code leafia:textures/models/machines/<tex>.png},
	 * which {@link AddonBatteryPackItemRender} binds (D-3023; D-1067 had shipped none because the base
	 * mod's pack renderer was not ported then).
	 */
	public final String tex;
	public final long capacity;
	public final long chargeRate;
	public final long dischargeRate;

	/** The base enum's boolean constructor: 15 minutes of discharge, charging at ten times the rate. */
	AddonEnumBatteryPack(String tex, long dischargeRate, boolean capacitor) {
		this(tex,
				capacitor ? (dischargeRate * 20 * 30) : (dischargeRate * 20 * 60 * 15),
				capacitor ? dischargeRate : dischargeRate * 10,
				dischargeRate);
	}

	/** The base enum's duration constructor: the rate times the duration in ticks. */
	AddonEnumBatteryPack(String tex, long dischargeRate, long duration) {
		this(tex, dischargeRate * duration, dischargeRate * 10, dischargeRate);
	}

	AddonEnumBatteryPack(String tex, long capacity, long chargeRate, long dischargeRate) {
		this.tex = tex;
		this.capacity = capacity;
		this.chargeRate = chargeRate;
		this.dischargeRate = dischargeRate;
	}

	/** The item's registry path, the base mod's {@code battery_pack_<texture>} pattern. */
	public String itemName() {
		return "battery_pack_" + tex;
	}
}
