package com.leafia.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.building.generic.ConcreteBricks;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.DeferredBlock;

/**
 * The dark concrete bricks (D-1090): upstream's one block with four metas, as four blocks.
 *
 * <p>Strength is in {@link LeafiaStrengthTests}' table and the pickaxe in its tool test. What is here
 * is what is particular to the family: the finish order that the rad-resistance flag compares
 * against, the tooltip that flag prints, and that each finish has its names, textures, models and
 * drop.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaConcreteBrickTests {

	private static final String EMPTY = "empty";

	private static final String[] IDS = { "brick_concrete_dark", "brick_concrete_dark_mossy",
			"brick_concrete_dark_cracked", "brick_concrete_dark_broken" };

	/** Upstream's {@code en_us.lang}: {@code tile.brick_concrete_dark.<finish>.name}. */
	private static final String[] NAMES = { "Dark Concrete Bricks", "Mossy Dark Concrete Bricks",
			"Cracked Dark Concrete Bricks", "Broken Dark Concrete Bricks" };

	private static DeferredBlock<ConcreteBricks> holder(int i) {
		return switch(i) {
			case 0 -> LeafiaBlocks.brick_concrete_dark;
			case 1 -> LeafiaBlocks.brick_concrete_dark_mossy;
			case 2 -> LeafiaBlocks.brick_concrete_dark_cracked;
			default -> LeafiaBlocks.brick_concrete_dark_broken;
		};
	}

	private static InputStream open(String path) {
		return LeafiaConcreteBrickTests.class.getResourceAsStream(path);
	}

	private static JsonObject json(String path) throws IOException {
		try(InputStream in = open(path)) {
			if(in == null) throw new IOException(path + " is missing");
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}

	/**
	 * The four finishes are four registered blocks, named as upstream names them, each carrying its
	 * own finish, in upstream's order (the flag compares against that order), and each needing a
	 * pickaxe: 15 hardness, 96 resistance, which is upstream's 160 under D-012.
	 */
	@GameTest(template = EMPTY)
	public static void fourFinishesAreFourBlocksInUpstreamsOrder(GameTestHelper helper) {
		if(!LeafiaFeatures.concreteBricks) { helper.succeed(); return; }

		ConcreteBricks.Type[] types = ConcreteBricks.Type.values();
		helper.assertTrue(types.length == 4, "upstream has four finishes, the enum has " + types.length);
		String[] order = { "normal", "mossy", "cracked", "broken" };
		for(int i = 0; i < 4; i++) {
			helper.assertTrue(order[i].equals(types[i].getName()), "finish " + i + " is " + types[i].getName());
			helper.assertTrue(IDS[i].equals(types[i].registryName()), "finish " + order[i] + " registers as " + types[i].registryName());

			DeferredBlock<ConcreteBricks> h = holder(i);
			helper.assertTrue(h != null, IDS[i] + " is not registered");
			ConcreteBricks b = h.get();
			helper.assertTrue(BuiltInRegistries.BLOCK.getKey(b).equals(ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID, IDS[i])),
					IDS[i] + " is registered as " + BuiltInRegistries.BLOCK.getKey(b));
			helper.assertTrue(b.type() == types[i], IDS[i] + " carries the finish " + b.type());
			helper.assertTrue(b.defaultDestroyTime() == ConcreteBricks.HARDNESS, IDS[i] + " hardness " + b.defaultDestroyTime());
			helper.assertTrue(b.getExplosionResistance() == 96.0F, IDS[i] + " resists " + b.getExplosionResistance());
			helper.assertTrue(b.defaultBlockState().requiresCorrectToolForDrops(), IDS[i] + " drops to a bare hand");
			helper.assertTrue(b.getStateDefinition().getProperties().isEmpty(), IDS[i] + " carries a blockstate property");
		}
		helper.succeed();
	}

	/**
	 * Upstream's {@code isRadResistant(meta)} is {@code meta <= MOSSY}: the plain and the mossy bricks
	 * are, the cracked and broken ones are not. Nothing reads the flag here (a base-mod hand-off, D-1090),
	 * but its tooltip does: {@code [Radiation Shielding]} in dark green first, for those two, and the gold
	 * blast line after it for all four, 96 being over 50. The wording is not asserted: on a server
	 * {@code I18nUtil} answers with a constant by design (D-003).
	 */
	@GameTest(template = EMPTY)
	public static void onlyThePlainAndMossyBricksAreRadResistant(GameTestHelper helper) {
		if(!LeafiaFeatures.concreteBricks) { helper.succeed(); return; }

		boolean[] expected = { true, true, false, false };
		for(int i = 0; i < 4; i++) {
			ConcreteBricks b = holder(i).get();
			helper.assertTrue(b.isRadResistant() == expected[i], IDS[i] + " rad resistant: " + b.isRadResistant());
			helper.assertTrue(b.type().radResistant() == expected[i], IDS[i] + "'s finish says " + b.type().radResistant());

			List<Component> lines = new ArrayList<>();
			b.asItem().appendHoverText(new ItemStack(b), Item.TooltipContext.EMPTY, lines, TooltipFlag.NORMAL);
			helper.assertTrue(lines.size() == (expected[i] ? 2 : 1), IDS[i] + " prints " + lines.size() + " lines: " + lines);
			String first = lines.get(0).getString();
			if(expected[i]) {
				helper.assertTrue(first.startsWith("§2["), IDS[i] + "'s shielding line is not dark green and bracketed: " + first);
				helper.assertTrue(lines.get(1).getString().startsWith("§6"), IDS[i] + "'s blast line is not gold: " + lines.get(1).getString());
			} else {
				helper.assertTrue(first.startsWith("§6"), IDS[i] + "'s only line is not the gold blast line: " + first);
			}
		}
		helper.succeed();
	}

	/** Upstream's names, and a texture, models, a blockstate and a drop-self table for each finish. */
	@GameTest(template = EMPTY)
	public static void eachFinishHasItsNamesTextureModelsAndDrop(GameTestHelper helper) throws IOException {
		if(!LeafiaFeatures.concreteBricks) { helper.succeed(); return; }

		JsonObject lang = json("/assets/leafia/lang/en_us.json");
		for(int i = 0; i < 4; i++) {
			String id = IDS[i];
			String name = lang.has("block.leafia." + id) ? lang.get("block.leafia." + id).getAsString() : null;
			helper.assertTrue(NAMES[i].equals(name), id + " is named " + name + ", upstream's is " + NAMES[i]);

			try(InputStream in = open("/assets/leafia/textures/block/" + id + ".png")) {
				helper.assertTrue(in != null, id + ".png is missing");
				ByteBuffer head = ByteBuffer.wrap(in.readNBytes(24));
				helper.assertTrue(head.getInt(16) == 16 && head.getInt(20) == 16, id + ".png is " + head.getInt(16) + "x" + head.getInt(20));
			}

			String model = json("/assets/leafia/models/block/" + id + ".json").getAsJsonObject("textures").get("all").getAsString();
			helper.assertTrue(("leafia:block/" + id).equals(model), id + "'s model wears " + model);
			String item = json("/assets/leafia/models/item/" + id + ".json").get("parent").getAsString();
			helper.assertTrue(("leafia:block/" + id).equals(item), id + "'s item model is " + item);
			json("/assets/leafia/blockstates/" + id + ".json");

			JsonObject loot = json("/data/leafia/loot_table/blocks/" + id + ".json");
			String dropped = loot.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject().get("name").getAsString();
			helper.assertTrue(("leafia:" + id).equals(dropped), id + " drops " + dropped);
		}
		helper.assertTrue(Items.AIR != holder(0).get().asItem(), "the plain bricks have an item");
		helper.succeed();
	}
}
