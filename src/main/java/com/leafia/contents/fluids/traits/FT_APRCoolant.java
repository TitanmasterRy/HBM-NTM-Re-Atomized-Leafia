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
 * A fluid the addon's APR reactor runs on as coolant and fuel: so many mB of this in, so many of
 * {@link #conversion} out. Oxygen gas back to liquid oxygen, 50:1, is the only one upstream
 * declares — the reverse of the boiler step that makes it.
 *
 * <p>Upstream: {@code com/leafia/contents/fluids/traits/FT_APRCoolant.java}, verbatim. Nothing
 * reads it until the APR is ported (D-1068).
 */
public class FT_APRCoolant extends FluidTrait {

	public FluidType conversion;
	public int in;
	public int out;

	public FT_APRCoolant() { }

	public FT_APRCoolant(FluidType conversion, int in, int out) {
		this.conversion = conversion;
		this.in = in;
		this.out = out;
	}

	@Override
	public void addInfoHidden(List<String> info) {
		info.add(ChatFormatting.DARK_AQUA + "[" + I18nUtil.resolveKey("trait._hazardfluid.aprCoolant") + "]");
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
