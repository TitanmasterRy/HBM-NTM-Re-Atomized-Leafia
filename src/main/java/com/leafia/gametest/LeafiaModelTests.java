package com.leafia.gametest;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.imageio.ImageIO;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.leafia.lib.LeafiaRef;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.RenderShape;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Checks on the addon's block models that no log line, missing-model count or GameTest about
 * behaviour would catch, because the failure is only visible on screen (D-1065).
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaModelTests {

	private static final String EMPTY = "empty";

	private static Path assets() {
		return ModList.get().getModFileById(LeafiaRef.MODID).getFile().findResource("assets", LeafiaRef.MODID);
	}

	/** Every {@code "model"} a blockstate names, variants and multipart alike. */
	private static void collectModels(JsonElement json, Set<String> out) {
		if(json.isJsonObject()) {
			for(var entry : json.getAsJsonObject().entrySet()) {
				if(entry.getKey().equals("model") && entry.getValue().isJsonPrimitive())
					out.add(entry.getValue().getAsString());
				else
					collectModels(entry.getValue(), out);
			}
		} else if(json.isJsonArray()) {
			for(JsonElement e : json.getAsJsonArray()) collectModels(e, out);
		}
	}

	private static JsonObject readModel(Path assets, String id) throws Exception {
		String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
		Path file = assets.resolve("models").resolve(path + ".json");
		return Files.exists(file) ? JsonParser.parseString(Files.readString(file)).getAsJsonObject() : null;
	}

	private static boolean hasSeeThroughTexels(Path assets, String texture) throws Exception {
		Path file = assets.resolve("textures").resolve(texture.substring(texture.indexOf(':') + 1) + ".png");
		if(!Files.exists(file)) return false;
		BufferedImage image;
		try(InputStream in = Files.newInputStream(file)) {
			image = ImageIO.read(in);
		}
		if(image == null || !image.getColorModel().hasAlpha()) return false;
		for(int y = 0; y < image.getHeight(); y++)
			for(int x = 0; x < image.getWidth(); x++)
				if((image.getRGB(x, y) >>> 24) < 255) return true;
		return false;
	}

	/**
	 * <b>A block model with see-through texels is not in the solid pass.</b> The solid pass
	 * ignores alpha, so every transparent texel draws as whatever colour it stores — black, for
	 * most painted textures — and a pipe with a mostly-clear overlay became a black silhouette.
	 * Nothing logged; the antimatter pipe had been like that since it landed (D-1022), and only a
	 * screenshot showed it.
	 *
	 * <p>Walks every model the addon's blockstates name, follows {@code leafia:} parents for
	 * textures and {@code render_type}, and opens each texture the model draws ({@code particle}
	 * is not drawn). A model that draws a texture with any alpha below 255 must name a
	 * {@code render_type} other than solid.
	 */
	@GameTest(template = EMPTY)
	public static void aModelWithSeeThroughTexelsIsNotSolid(GameTestHelper helper) throws Exception {

		Path assets = assets();
		Set<String> models = new TreeSet<>();
		int skipped = 0;
		try(var states = Files.list(assets.resolve("blockstates"))) {
			for(Path state : states.toList()) {
				// A block the chunk mesher never draws - a renderer-only multiblock, which is
				// ENTITYBLOCK_ANIMATED - keeps its model for the renderer, which picks its own
				// render type. Only MODEL blocks are in a pass this JSON decides. The first run
				// flagged the broof and the HP boiler for exactly this.
				String name = state.getFileName().toString().replace(".json", "");
				var block = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID, name));
				if(block.isPresent() && block.get().defaultBlockState().getRenderShape() != RenderShape.MODEL) {
					skipped++;
					continue;
				}
				collectModels(JsonParser.parseString(Files.readString(state)), models);
			}
		}
		helper.assertTrue(skipped > 0, "no renderer-only block was skipped - the filter is not working");
		helper.assertTrue(models.size() > 50, "only " + models.size() + " block models found");

		List<String> problems = new ArrayList<>();
		Map<String, Boolean> alphaCache = new HashMap<>();

		for(String id : models) {
			if(!id.startsWith(LeafiaRef.MODID + ":")) continue;
			Map<String, String> textures = new HashMap<>();
			String renderType = null;
			String current = id;
			while(current != null && current.startsWith(LeafiaRef.MODID + ":")) {
				JsonObject model = readModel(assets, current);
				if(model == null) break;
				if(renderType == null && model.has("render_type")) renderType = model.get("render_type").getAsString();
				if(model.has("textures"))
					for(var t : model.getAsJsonObject("textures").entrySet())
						textures.putIfAbsent(t.getKey(), t.getValue().getAsString());
				current = model.has("parent") ? model.get("parent").getAsString() : null;
			}
			if(renderType != null && !renderType.equals("minecraft:solid")) continue;

			for(var t : textures.entrySet()) {
				if(t.getKey().equals("particle") || t.getValue().startsWith("#") || !t.getValue().startsWith(LeafiaRef.MODID + ":")) continue;
				boolean alpha = alphaCache.computeIfAbsent(t.getValue(), tex -> {
					try { return hasSeeThroughTexels(assets, tex); } catch(Exception e) { throw new RuntimeException(e); }
				});
				if(alpha) {
					problems.add(id + " draws " + t.getValue() + " in the solid pass");
					break;
				}
			}
		}

		helper.assertTrue(problems.isEmpty(), problems.size() + " models: " + String.join("; ", problems));
		helper.succeed();
	}
}
