package com.leafia.contents.fluids.traits;

import java.io.IOException;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.trait.FluidTrait;
import com.hbm.util.i18n.I18nUtil;

import net.minecraft.ChatFormatting;

/**
 * A fluid the addon's APR reactor can cool with: so many mB of this in, so many of
 * {@link #conversion} out. Pyrogel to cryogel, 1:1, is the only one upstream declares.
 *
 * <p>Upstream: {@code com/leafia/contents/fluids/traits/FT_APRCoolable.java}, verbatim. Nothing
 * reads it until the APR is ported; until then it is a tooltip line under shift.
 *
 * <p><b>Upstream has no no-argument constructor here</b>, unlike {@link FT_APRCoolant} and every
 * other trait. A fluid-trait config loader instantiates traits reflectively from the name map,
 * so reading an {@code aprcoolable} entry back would fail. This port's base mod does not read
 * that config yet, so it is not reachable; kept as upstream's and flagged rather than "fixed"
 * (rule 4, D-1068).
 */
public class FT_APRCoolable extends FluidTrait {

	public FluidType conversion;
	public int in;
	public int out;

	public FT_APRCoolable(FluidType conversion, int in, int out) {
		this.conversion = conversion;
		this.in = in;
		this.out = out;
	}

	@Override
	public void addInfoHidden(List<String> info) {
		info.add(ChatFormatting.AQUA + "[" + I18nUtil.resolveKey("trait._hazardfluid.aprCoolable") + "]");
	}

	@Override
	public void serializeJSON(JsonWriter writer) throws IOException {
		writer.name("conversion").value(conversion.getName());
		writer.name("in").value(in);
		writer.name("out").value(out);
	}

	@Override
	public void deserializeJSON(JsonObject obj) {
		conversion = Fluids.fromName(obj.get("conversion").getAsString());
		in = obj.get("in").getAsInt();
		out = obj.get("out").getAsInt();
	}
}
