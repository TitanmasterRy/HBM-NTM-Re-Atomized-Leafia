package com.leafia.config;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import com.leafia.lib.LeafiaRef;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * {@code {"type": "leafia:feature", "feature": "digammaCrater"}}: true while the named switch in
 * {@link LeafiaFeatures} is on. Not upstream - 1.12.2 had no datapack registries to gate.
 *
 * <p>It exists for the one kind of content a Java switch cannot withhold: an entry in a <em>datapack</em>
 * registry, such as the digamma crater's biome, which the game reads from JSON. NeoForge evaluates
 * {@code neoforge:conditions} on every datapack registry entry it loads ({@code RegistryDataLoader}, read
 * in the decompile), so a disabled feature's biome is simply not loaded, which is D-1002's rule
 * ("a disabled feature registers nothing") applied to a JSON registry. A biome tag that names a
 * conditional biome has to mark it {@code "required": false}.
 *
 * <p>The name is a field of {@link LeafiaFeatures}, read by reflection so a new switch needs no edit
 * here; an unknown name answers false and says so in the log once per load, because a typo should not
 * silently enable anything. The switches are a STARTUP config, so they are already read when any
 * datapack loads.
 */
public record LeafiaFeatureCondition(String feature) implements ICondition {

	public static final MapCodec<LeafiaFeatureCondition> CODEC = RecordCodecBuilder.mapCodec(builder -> builder
			.group(Codec.STRING.fieldOf("feature").forGetter(LeafiaFeatureCondition::feature))
			.apply(builder, LeafiaFeatureCondition::new));

	public static final DeferredRegister<MapCodec<? extends ICondition>> CONDITIONS =
			DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, LeafiaRef.MODID);

	static {
		CONDITIONS.register("feature", () -> CODEC);
	}

	public static void register(IEventBus modBus) {
		CONDITIONS.register(modBus);
	}

	@Override
	public boolean test(IContext context) {
		return isOn(feature);
	}

	/** Whether the switch named {@code feature} is on; false for a name {@link LeafiaFeatures} does not have. */
	public static boolean isOn(String feature) {
		try {
			Field field = LeafiaFeatures.class.getField(feature);
			if(field.getType() != boolean.class || !Modifier.isStatic(field.getModifiers())) return unknown(feature);
			return field.getBoolean(null);
		} catch(NoSuchFieldException | IllegalAccessException e) {
			return unknown(feature);
		}
	}

	private static boolean unknown(String feature) {
		com.leafia.LeafiaMod.LOGGER.warn("leafia:feature condition names no feature switch: {}", feature);
		return false;
	}

	@Override
	public MapCodec<? extends ICondition> codec() {
		return CODEC;
	}

	@Override
	public String toString() {
		return "leafia:feature(\"" + feature + "\")";
	}
}
