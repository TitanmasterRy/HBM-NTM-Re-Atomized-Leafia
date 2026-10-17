package com.leafia.gametest;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hbm.blocks.generic.BlockSellafieldSlaked;
import com.hbm.inventory.gui.GuiInfoText;
import com.hbm.items.tool.ItemDiscord;
import com.hbm.util.BobMathUtil;
import com.leafia.config.LeafiaConfig;
import com.leafia.config.LeafiaFeatures;
import com.leafia.lib.LeafiaRef;
import com.leafia.overwrite_contents.LeafiaCosmeticSeams;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The addon's cosmetic overhauls (COSMETIC, D-3023): the seams that need no client class, the arithmetic the
 * client handlers call, and the shape of the resources they ship. The drawing itself is checked in a running
 * client (the {@code hazcos} scene). Every test that flips a switch puts it back in a {@code finally}.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaCosmeticTests {

	private static final String EMPTY = "empty";

	private static void near(GameTestHelper helper, double actual, double expected, String what) {
		helper.assertTrue(Math.abs(actual - expected) < 1e-6, what + ": expected " + expected + ", got " + actual);
	}

	private static byte[] bytes(String path) {
		try(InputStream in = LeafiaCosmeticTests.class.getResourceAsStream(path)) {
			if(in == null) throw new IllegalStateException("missing resource " + path);
			return in.readAllBytes();
		} catch(IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private static BufferedImage image(String path) {
		try {
			return ImageIO.read(new java.io.ByteArrayInputStream(bytes(path)));
		} catch(IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private static JsonObject json(String path) {
		return JsonParser.parseString(new String(bytes(path), StandardCharsets.UTF_8)).getAsJsonObject();
	}

	@GameTest(template = EMPTY)
	public static void theCosmeticSwitchIsOffByDefault(GameTestHelper helper) {
		helper.assertTrue(Boolean.FALSE.equals(LeafiaHazardSeamTests.defaultOf(LeafiaFeatures.SPEC.getSpec(), "cosmeticOverhaul")), "cosmeticOverhaul changes how base things look, so D-1002 has it off");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theEnergyBarSpeaksCorn(GameTestHelper helper) {
		boolean old = LeafiaConfig.bullshitUnits;
		try {
			LeafiaConfig.bullshitUnits = false;
			helper.assertTrue(LeafiaCosmeticSeams.ELECTRICITY.format(1000, 2000) == null, "off: declines");
			helper.assertTrue(GuiInfoText.electricityText(1000, 2000).equals(GuiInfoText.electricityTextDefault(1000, 2000)), "and the base mod's line stands");

			LeafiaConfig.bullshitUnits = true;
			String expected = BobMathUtil.getShortNumber(15000L) + "/" + BobMathUtil.getShortNumber(30000L) + "corn";
			helper.assertTrue(LeafiaCosmeticSeams.ELECTRICITY.format(1000, 2000).equals(expected), "1 HE = 15 corn: " + LeafiaCosmeticSeams.ELECTRICITY.format(1000, 2000));
			helper.assertTrue(GuiInfoText.electricityText(1000, 2000).equals(expected), "and the screen's text goes through the seam");
			helper.assertTrue(expected.endsWith("corn") && !expected.contains("HE"), "corn replaces HE");
		} finally {
			LeafiaConfig.bullshitUnits = old;
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void slakedSellafieldIsDarkerAndStopsAtNine(GameTestHelper helper) {
		boolean old = LeafiaFeatures.cosmeticOverhaul;
		try {
			LeafiaFeatures.cosmeticOverhaul = true;
			helper.assertTrue(BlockSellafieldSlaked.tint(0) == 0xFFFFFFFF, "level 0 is white");
			for(int level = 0; level <= 15; level++) {
				float brightness = 1F - (float) Math.pow(Math.min(level, 9) / 11F, 0.75);
				helper.assertTrue(BlockSellafieldSlaked.tint(level) == java.awt.Color.HSBtoRGB(0F, 0F, brightness), "level " + level + " tint");
			}
			helper.assertTrue(BlockSellafieldSlaked.tint(9) == BlockSellafieldSlaked.tint(15) && BlockSellafieldSlaked.tint(8) != BlockSellafieldSlaked.tint(9), "levels 9 and up are alike");
			helper.assertTrue((BlockSellafieldSlaked.tint(9) & 0xFF) > 0x10, "and never black: the base mod's level 15 is");

			LeafiaFeatures.cosmeticOverhaul = false;
			helper.assertTrue(BlockSellafieldSlaked.tint(15) == BlockSellafieldSlaked.tintDefault(15) && BlockSellafieldSlaked.tint(5) == BlockSellafieldSlaked.tintDefault(5), "off: the base mod's");
		} finally {
			LeafiaFeatures.cosmeticOverhaul = old;
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theRodOfDiscordReachesFiveHundredInCreative(GameTestHelper helper) {
		boolean old = LeafiaFeatures.cosmeticOverhaul;
		try {
			LeafiaFeatures.cosmeticOverhaul = true;
			Player creative = helper.makeMockPlayer(GameType.CREATIVE);
			Vec3 at = helper.absoluteVec(new Vec3(2.5, 3, 2.5));
			creative.setPos(at.x, at.y, at.z);
			creative.setXRot(-90F); // straight up
			Vec3 end = LeafiaCosmeticSeams.creativeRayEnd(creative);
			near(helper, end.distanceTo(creative.position()), 500, "creative: the ray is 500 long");
			near(helper, end.y - creative.getY(), 500, "and goes where the player looks");
			BlockHitResult far = ItemDiscord.ray(creative);
			helper.assertTrue(far.getLocation().distanceTo(creative.position()) <= 500.001, "the rod uses it");
			helper.setBlock(new BlockPos(2, 6, 2), Blocks.STONE);
			BlockHitResult near = ItemDiscord.ray(creative);
			helper.assertTrue(near.getType() == HitResult.Type.BLOCK && near.getLocation().y < at.y + 5, "a block in the way is hit: " + near.getLocation());
			helper.setBlock(new BlockPos(2, 6, 2), Blocks.AIR);

			Player survival = helper.makeMockPlayer(GameType.SURVIVAL);
			survival.setPos(at.x, at.y, at.z);
			survival.setXRot(-90F);
			helper.assertTrue(LeafiaCosmeticSeams.CREATIVE_RAY.ray(survival, 100) == null, "survival: the provider declines");
			helper.assertTrue(ItemDiscord.ray(survival).getLocation().distanceTo(survival.getEyePosition()) < 101, "and the rod keeps its 100");

			LeafiaFeatures.cosmeticOverhaul = false;
			helper.assertTrue(LeafiaCosmeticSeams.CREATIVE_RAY.ray(creative, 100) == null, "off: declines for creative too");
		} finally {
			LeafiaFeatures.cosmeticOverhaul = old;
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theWindPushesAlongTheCompass(GameTestHelper helper) {
		double[] none = LeafiaCosmeticSeams.windPush(100, 0, 0);
		helper.assertTrue(none[0] == 0 && none[1] == 0 && none[2] == 0, "no multiplier, no wind");
		double[] south = LeafiaCosmeticSeams.windPush(100, 0, 1);
		near(helper, south[2], 0.0025, "full wind at 0 degrees pushes +Z by 0.0025");
		near(helper, south[0], 0, "and not in X");
		double[] west = LeafiaCosmeticSeams.windPush(100, 90, 1);
		near(helper, west[0], -0.0025, "at 90 degrees it is -X");
		near(helper, west[2], 0, "and not Z");
		near(helper, LeafiaCosmeticSeams.windPush(50, 0, 2)[2], 0.0025 * Math.pow(0.5, 0.65) * 2, "power^0.65 and the multiplier");
		near(helper, LeafiaCosmeticSeams.windPush(100, 0, 1)[1], 0, "never vertical");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theDiamondCountsDigits(GameTestHelper helper) {
		helper.assertTrue(LeafiaCosmeticSeams.digits(0) == 1 && LeafiaCosmeticSeams.digits(9) == 1 && LeafiaCosmeticSeams.digits(10) == 2
				&& LeafiaCosmeticSeams.digits(99) == 2 && LeafiaCosmeticSeams.digits(100) == 3 && LeafiaCosmeticSeams.digits(12345) == 5, "one digit up to 9, then one more per ten-power");
		BufferedImage numbers = image("/assets/leafia/textures/numbers.png");
		helper.assertTrue(numbers.getWidth() == 64 && numbers.getHeight() == 64, "the sheet is 64x64: the quads' 1/64 texel");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theHotSellafieldPackIsHot(GameTestHelper helper) {
		String root = "/resourcepacks/hot_sellafield";
		JsonObject meta = json(root + "/pack.mcmeta").getAsJsonObject("pack");
		helper.assertTrue(meta.get("pack_format").getAsInt() == 34, "a 1.21.1 resource pack");
		double[] lastRed = {-1};
		for(int level = 0; level < 6; level++) {
			double red = 0, green = 0;
			for(int variant = 0; variant < 4; variant++) {
				BufferedImage img = image(root + "/assets/hbm/textures/block/sellafield_" + level + "_" + variant + ".png");
				helper.assertTrue(img.getWidth() == 16 && img.getHeight() == 16, "16x16");
				for(int y = 0; y < 16; y++)
					for(int x = 0; x < 16; x++) {
						int argb = img.getRGB(x, y);
						red += (argb >> 16) & 0xFF;
						green += (argb >> 8) & 0xFF;
					}
			}
			helper.assertTrue(red > green, "level " + level + " is orange, not the base mod's green: red " + red + ", green " + green);
			if(level == 0) lastRed[0] = red;
			if(level == 5) helper.assertTrue(red > lastRed[0], "and the hottest is redder than the coolest");
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theBatteryPacksAreDrawnByTheCellRenderer(GameTestHelper helper) {
		for(String name : new String[] {"battery_desh", "battery_euphemium", "battery_slop", "battery_spk", "battery_electro", "battery_saturnite"}) {
			JsonObject model = json("/assets/leafia/models/item/battery_pack_" + name + ".json");
			helper.assertTrue(model.get("parent").getAsString().equals("minecraft:builtin/entity"), name + " routes to the item renderer");
			JsonObject display = model.getAsJsonObject("display");
			helper.assertTrue(display.has("gui") && display.has("ground") && display.has("firstperson_righthand") && display.has("thirdperson_righthand"), name + " carries the display block");
			helper.assertTrue(display.getAsJsonObject("gui").getAsJsonArray("scale").get(0).getAsDouble() == 0.625, name + " gui scale, the base packs'");
			BufferedImage skin = image("/assets/leafia/textures/models/machines/" + name + ".png");
			helper.assertTrue(skin.getWidth() == 82 && skin.getHeight() == 68, name + " skin is the battery socket's 82x68");
		}
		helper.succeed();
	}
}
