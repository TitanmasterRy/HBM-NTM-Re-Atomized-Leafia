package com.leafia.contents.machines.misc.modular_turbine;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import com.leafia.lib.LeafiaRef;

import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ModelEvent;

/**
 * The turbine renderer's baked parts, by name.
 *
 * <p>Not an upstream file. Upstream draws its parts straight out of three loaded OBJs with
 * {@code renderPart("Name")}; here each (part, texture) pair is a model JSON written by
 * a generator script, which also writes the manifest this class
 * reads. Registering from the manifest rather than from a list typed here is what keeps the two in
 * step: a part that is drawn but not registered bakes to the missing-model cube, and nothing logs it
 * until somebody looks at a turbine.
 */
public final class ModularTurbineModels {

	private ModularTurbineModels() {}

	public static final List<String> NAMES = read();

	private static List<String> read() {
		try(InputStream in = ModularTurbineModels.class.getResourceAsStream("/assets/leafia/modular_turbine_models.txt")) {
			if(in == null) return List.of();
			return Arrays.stream(new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n"))
					.map(String::trim).filter(s -> !s.isEmpty()).toList();
		} catch(IOException e) {
			throw new IllegalStateException("could not read the modular turbine model manifest", e);
		}
	}

	/** The model for {@code modular_turbine_part_<name>}. Throws on a name the generator never wrote. */
	public static ModelResourceLocation get(String name) {
		String full = "modular_turbine_part_" + name;
		if(!NAMES.contains(full))
			throw new IllegalArgumentException("no turbine part model " + full);
		return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID, "block/" + full));
	}

	/** Whether the generator wrote {@code modular_turbine_part_<name>}. Upstream has no {@code Tube3}. */
	public static boolean has(String name) {
		return NAMES.contains("modular_turbine_part_" + name);
	}

	public static void register(ModelEvent.RegisterAdditional event) {
		for(String name : NAMES)
			event.register(ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID, "block/" + name)));
	}
}
