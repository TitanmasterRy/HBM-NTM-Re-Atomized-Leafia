package com.leafia.gametest;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.imageio.ImageIO;

import com.hbm.blocks.ModBlocks;
import com.hbm.blocks.generic.BlockBobble;
import com.hbm.blocks.generic.BlockBobble.BobbleType;
import com.hbm.entity.effect.EntityCloudFleija;
import com.hbm.entity.logic.EntityNukeExplosionMK3;
import com.hbm.handler.radiation.ChunkRadiationManager;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.gui.element.DrawnTanks;
import com.hbm.inventory.recipes.CentrifugeRecipes;
import com.hbm.items.ModItems;
import com.hbm.items.special.ItemDrop;
import com.hbm.slices.GunSystem;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaItems;
import com.leafia.contents.LeafiaResourceItems;
import com.leafia.contents.gear.combat.guns.am_rifle.AMRifleBakes;
import com.leafia.contents.gear.utility.fuzzy.FuzzyScreenClicks;
import com.leafia.dev.custompacket.LeafiaCustomPacket;
import com.leafia.dev.custompacket.LeafiaPacketTypes;
import com.leafia.lib.LeafiaRef;
import com.leafia.overwrite_contents.LeafiaBobbleheads;
import com.leafia.overwrite_contents.LeafiaFleijaClouds;
import com.leafia.overwrite_contents.LeafiaRadObserver;
import com.leafia.overwrite_contents.LeafiaRadObserver.Sent;
import com.leafia.overwrite_contents.packets.RadHighlightPacket;
import com.leafia.passive.rendering.TopRender.Highlight;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The addon's consumers of the base mod's M13 seams (D-2207, D-3041): the bobblehead figure appended through the enum extension and the
 * centrifuge row that names it, the rad goggles' observer, the anti-schrabidium cloud's mark through the drop spawn hook, the AM rifle's
 * part bakes, the fuzzy identifier's click on a base screen's tank. The rest of the batch's consumers are pinned where their subject's
 * tests live: the duct resolver in {@code LeafiaCanneryTests}, the plant's repairable resolver in {@code LeafiaFluidFirestormTests}, the
 * landmine processor read-back in {@code LeafiaExplCTests}, the rack's crate data in {@code LeafiaRackTests}.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaM13Tests {

	private static final String EMPTY = "empty";

	// ------------------------------------------------------------------ the bobblehead

	/** The figure exists, is the 25th, is an extension, and has its item and its skin; the base's own 24 are untouched. */
	@GameTest(template = EMPTY)
	public static void theAddonBobbleheadIsAppendedToTheBaseEnum(GameTestHelper helper) {
		BobbleType leafia = LeafiaBobbleheads.leafia();
		helper.assertTrue(leafia != null && leafia.isExtension(), "the figure is an extension");
		helper.assertTrue(BobbleType.values().length == 25 && leafia.ordinal() == 24, "after upstream's 24: " + BobbleType.values().length);
		helper.assertTrue(leafia.getSerializedName().equals("leafia_leafia") && BobbleType.bySerializedName("leafia_leafia") == leafia, "found by its name");
		helper.assertTrue(leafia.name.equals("Leafia") && leafia.label.equals("Leafia") && !leafia.skinLayers, "its engraving");
		for(BobbleType type : BobbleType.values()) {
			if(type != leafia) helper.assertTrue(!type.isExtension(), type + " is still upstream's");
		}

		BlockBobble block = ModBlocks.bobblehead.get();
		Item item = block.getVariantStack(block.defaultBlockState().setValue(BlockBobble.VARIANT, leafia)).getItem();
		helper.assertTrue(BuiltInRegistries.ITEM.getKey(item).equals(ResourceLocation.fromNamespaceAndPath("hbm", "bobblehead_leafia_leafia")),
				"the base registered its item: " + BuiltInRegistries.ITEM.getKey(item));

		ResourceLocation skin = LeafiaBobbleheads.skin();
		helper.assertTrue(skin.equals(LeafiaRef.rl("textures/models/trinkets/leafia.png")), "its skin is named");
		try(InputStream in = LeafiaM13Tests.class.getResourceAsStream("/assets/leafia/textures/models/trinkets/leafia.png")) {
			helper.assertTrue(in != null, "and shipped");
			var image = ImageIO.read(in);
			helper.assertTrue(image.getWidth() == 64 && image.getHeight() == 32, "in the figures' 64x32 layout: " + image.getWidth() + "x" + image.getHeight());
		} catch(java.io.IOException e) {
			helper.fail("reading the skin: " + e);
		}
		// and its item model: the base's datagen writes one per figure it knows, so this one ships with the addon (D-3050)
		try(InputStream in = LeafiaM13Tests.class.getResourceAsStream("/assets/hbm/models/item/bobblehead_" + leafia.getSerializedName() + ".json")) {
			helper.assertTrue(in != null, "the item model is shipped, or the client draws the missing model");
			var model = com.google.gson.JsonParser.parseString(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
			helper.assertTrue("minecraft:builtin/entity".equals(model.get("parent").getAsString()), "drawn by the figures' item renderer, as the base's 24 are: " + model.get("parent"));
		} catch(java.io.IOException e) {
			helper.fail("reading the item model: " + e);
		}
		helper.succeed();
	}

	/** The centrifuge row upstream's {@code AddonCentrifugeRecipes} wrote for the figure: the Eevee strand, a waste leaf and a mustard willow. */
	@GameTest(template = EMPTY)
	public static void theBobbleheadCentrifugesIntoTheEeveeStrandAndFriends(GameTestHelper helper) {
		BlockBobble block = ModBlocks.bobblehead.get();
		ItemStack figure = block.getVariantStack(block.defaultBlockState().setValue(BlockBobble.VARIANT, LeafiaBobbleheads.leafia()));
		ItemStack[] out = CentrifugeRecipes.getOutput(figure);
		helper.assertTrue(out != null && out.length == 3, "three outputs: " + (out == null ? null : out.length));
		helper.assertTrue(out[0].is(LeafiaResourceItems.get("dna_eevee").get()) && out[0].getCount() == 1, "the Eevee strand");
		helper.assertTrue(out[1].is(ModBlocks.waste_leaves.get().asItem()) && out[1].getCount() == 1, "a waste leaf");
		helper.assertTrue(out[2].is(ModItems.plant_item_mustardwillow.get()) && out[2].getCount() == 1, "a mustard willow");
		ItemStack[] other = CentrifugeRecipes.getOutput(block.getVariantStack(block.defaultBlockState().setValue(BlockBobble.VARIANT, BobbleType.STRENGTH)));
		helper.assertTrue(other == null, "no other figure has one");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the rad goggles

	/**
	 * A mock server player that is in the level but not on the player list: a player left on the list is sent the wind every tick by
	 * {@code Wind.updateWorld}, which has no channel to send it on and takes the whole server down (as the particle tests' mock is).
	 */
	@SuppressWarnings("deprecation")
	private static ServerPlayer mockPlayer(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			var field = net.minecraft.server.players.PlayerList.class.getDeclaredField("players");
			field.setAccessible(true);
			((java.util.List<?>) field.get(helper.getLevel().getServer().getPlayerList())).remove(player);
		} catch(ReflectiveOperationException e) {
			throw new IllegalStateException("could not detach the mock player", e);
		}
		return player;
	}

	/** A watcher on the observer's sink, restored after. */
	private static final class Recorder implements Consumer<Sent> {
		final List<Sent> sent = new ArrayList<>();

		@Override
		public void accept(Sent s) {
			sent.add(s);
		}
	}

	/**
	 * An increment goes to a goggled player within 256 blocks of the eye (strictly), once, with the amount, at the block; a player without
	 * the goggles and one beyond range hear nothing.
	 */
	@GameTest(template = EMPTY)
	public static void aGoggledPlayerIsShownAnIncrementWithin256Blocks(GameTestHelper helper) {
		if(LeafiaItems.radglasses == null) { helper.succeed(); return; }
		ServerLevel level = helper.getLevel();
		ServerPlayer goggled = mockPlayer(helper);
		ServerPlayer bare = mockPlayer(helper);
		goggled.setItemSlot(EquipmentSlot.HEAD, new ItemStack(LeafiaItems.radglasses.get()));
		goggled.setPos(0.5, 70, 0.5);
		bare.setPos(0.5, 70, 0.5);
		double eye = goggled.getEyeHeight();
		int y = 70 + (int) Math.floor(eye); // the block the eye is in, so only x counts below

		Recorder recorder = new Recorder();
		Consumer<Sent> old = LeafiaRadObserver.setSink(recorder);
		try {
			ChunkRadiationManager.observeIncrement(level, 10, y, 0, 2.5F);
			helper.assertTrue(recorder.sent.size() == 1, "one box for the goggled player, none for the bare one: " + recorder.sent.size());
			Sent first = recorder.sent.get(0);
			helper.assertTrue(first.player() == goggled && first.pos().equals(new BlockPos(10, y, 0)) && first.amount() == 2.5F, "at the block, with the amount");

			recorder.sent.clear();
			double dx = 255.4; // eye (0.5, 70 + eye, 0.5) to the centre (x + 0.5, y + 0.5, 0.5): inside 256
			ChunkRadiationManager.observeIncrement(level, (int) Math.round(dx), y, 0, 1F);
			helper.assertTrue(recorder.sent.size() == 1, "255 blocks away is in range: " + recorder.sent.size());

			recorder.sent.clear();
			ChunkRadiationManager.observeIncrement(level, 257, y, 0, 1F);
			helper.assertTrue(recorder.sent.isEmpty(), "257 blocks away is not");
			ChunkRadiationManager.observeIncrement(level, 0, y, 300, 1F);
			helper.assertTrue(recorder.sent.isEmpty(), "300 on another axis is not");

			goggled.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
			ChunkRadiationManager.observeIncrement(level, 10, y, 0, 1F);
			helper.assertTrue(recorder.sent.isEmpty(), "without the goggles on the head, nobody is shown anything");
		} finally {
			LeafiaRadObserver.setSink(old);
			goggled.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
			goggled.discard();
			bare.discard();
		}
		helper.succeed();
	}

	/** The box and its label: upstream's ten seconds, colour {@code 0xAAFF11} and {@code +x.xRAD}; the packet carries the block and the amount. */
	@GameTest(template = EMPTY)
	public static void theGogglesBoxIsUpstreamsAndItsPacketCarriesTheDose(GameTestHelper helper) {
		helper.assertTrue(RadHighlightPacket.DURATION == 10F && RadHighlightPacket.COLOR == 0xAAFF11, "ten seconds in 0xAAFF11");
		helper.assertTrue(RadHighlightPacket.label(12.34F).equals("+12.3RAD") && RadHighlightPacket.label(0.04F).equals("+0.0RAD") && RadHighlightPacket.label(7F).equals("+7.0RAD"),
				"one decimal: " + RadHighlightPacket.label(12.34F));

		Highlight box = RadHighlightPacket.show(new BlockPos(3, 64, -5), 4.25F);
		try {
			helper.assertTrue(Highlight.instances.contains(box) && box.lifetime == 10F && box.color == 0xAAFF11, "shown for ten seconds");
			helper.assertTrue(box.label.length == 1 && box.label[0].equals("+4.3RAD"), "labelled with the dose: " + box.label[0]);
			helper.assertTrue(box.center.equals(new net.minecraft.world.phys.Vec3(3.5, 64.5, -4.5)), "centred on the block: " + box.center);
		} finally {
			box.hide();
		}

		LeafiaCustomPacket packet = LeafiaCustomPacket.__start(new RadHighlightPacket(new BlockPos(-30, 70, 123), 1.5F));
		var opened = LeafiaCustomPacket.open(packet);
		helper.assertTrue(opened.prototype() instanceof RadHighlightPacket && LeafiaPacketTypes.idOf(new RadHighlightPacket()) == LeafiaPacketTypes.RAD_HIGHLIGHT, "registered");
		helper.assertTrue(BlockPos.of(opened.buf().readLong()).equals(new BlockPos(-30, 70, 123)) && opened.buf().readFloat() == 1.5F, "the block, then the amount");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the anti-schrabidium cloud

	/**
	 * End to end through the base's drop: a landed anti-schrabidium cell's cloud reaches a hook after the addon's already marked
	 * ({@code leafia_antischrab}), its explosion is not marked, and a different item's cloud, a cloud with the switch off and a cloud
	 * that is not the cell's are left alone.
	 */
	@GameTest(template = EMPTY)
	public static void aDroppedAntischrabidiumCellsCloudIsMarked(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
		List<Entity> seen = new ArrayList<>();
		List<Boolean> marked = new ArrayList<>();
		// registered after the addon's own, so it is asked second; true keeps the explosion and the cloud out of the level
		ItemDrop.SpawnHook keeper = (drop, stack, spawned) -> {
			seen.add(spawned);
			marked.add(spawned instanceof EntityCloudFleija cloud && cloud.getExtraData().getBoolean(LeafiaFleijaClouds.ANTISCHRAB));
			return true;
		};
		boolean was = LeafiaFeatures.folkvangr;
		ItemDrop.addSpawnHook(keeper);
		try {
			LeafiaFeatures.folkvangr = true;
			ItemStack cell = new ItemStack(ModItems.cell_anti_schrabidium.get());
			ItemEntity drop = new ItemEntity(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, cell);
			drop.setOnGround(true);
			cell.getItem().onEntityItemUpdate(cell, drop);
			helper.assertTrue(seen.size() == 2 && seen.get(0) instanceof EntityNukeExplosionMK3 && seen.get(1) instanceof EntityCloudFleija, "the explosion, then the cloud: " + seen);
			helper.assertTrue(!marked.get(0) && marked.get(1), "only the cloud is marked antischrabidium: " + marked);
			helper.assertTrue(LeafiaFleijaClouds.getIsAntischrab(seen.get(1)), "and the cloud knows it");

			// the hook itself, for the cases the drop cannot reach
			EntityCloudFleija other = new EntityCloudFleija(level, 10);
			LeafiaFleijaClouds.ANTISCHRAB_CELL.onSpawn(drop, new ItemStack(ModItems.singularity.get()), other);
			helper.assertTrue(!other.getExtraData().getBoolean(LeafiaFleijaClouds.ANTISCHRAB), "a cloud from another item is not marked");
			LeafiaFeatures.folkvangr = false;
			LeafiaFleijaClouds.ANTISCHRAB_CELL.onSpawn(drop, cell, other);
			helper.assertTrue(!other.getExtraData().getBoolean(LeafiaFleijaClouds.ANTISCHRAB), "nor with the folkvangr switch off");
			LeafiaFeatures.folkvangr = true;
			helper.assertTrue(!LeafiaFleijaClouds.ANTISCHRAB_CELL.onSpawn(drop, cell, other), "the hook never takes the entity: the base adds it");
			helper.assertTrue(other.getExtraData().getBoolean(LeafiaFleijaClouds.ANTISCHRAB), "and marks the cell's cloud");
		} finally {
			ItemDrop.removeSpawnHook(keeper);
			LeafiaFeatures.folkvangr = was;
			for(Entity e : seen) e.discard();
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ the AM rifle's part bakes

	/**
	 * Each group's bake is where the base mod's seam puts it, in the addon's namespace, and the file is there with one material the
	 * part bake's override can resolve; every bake hides the other four.
	 */
	@GameTest(template = EMPTY)
	public static void theAmRiflePartsAreBakedThroughTheBaseModsSeam(GameTestHelper helper) {
		helper.assertTrue(AMRifleBakes.OBJ.equals(LeafiaRef.rl("weapons/am_rifle")), "the OBJ is leafia:models/weapons/am_rifle.obj");
		helper.assertTrue(AMRifleBakes.PARTS.length == 5, "five groups");
		try(InputStream obj = LeafiaM13Tests.class.getResourceAsStream("/assets/leafia/models/weapons/am_rifle.obj")) {
			helper.assertTrue(obj != null, "the OBJ is shipped");
			String text = new String(obj.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
			helper.assertTrue(text.contains("usemtl None") && !text.contains("usemtl Rifle") && !text.contains("mtllib"), "under the one material the override defines");
			for(String group : AMRifleBakes.PARTS) helper.assertTrue(text.contains("\no " + group + "\n"), "has the group " + group);
		} catch(java.io.IOException e) {
			helper.fail("reading the OBJ: " + e);
		}
		for(String group : AMRifleBakes.PARTS) {
			ResourceLocation bake = GunSystem.partLocation(AMRifleBakes.OBJ, group);
			helper.assertTrue(bake.equals(LeafiaRef.rl("gun/part/weapons/am_rifle/" + group.toLowerCase(java.util.Locale.ROOT))), group + ": " + bake);
			try(InputStream in = LeafiaM13Tests.class.getResourceAsStream("/assets/leafia/models/" + bake.getPath() + ".json")) {
				helper.assertTrue(in != null, group + "'s model JSON is at " + bake.getPath());
				var json = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
				helper.assertTrue(json.get("model").getAsString().equals("leafia:models/weapons/am_rifle.obj") && json.get("mtl_override").getAsString().equals("hbm:models/none.mtl"),
						group + " draws the OBJ under the base's material library");
				var visibility = json.getAsJsonObject("visibility");
				helper.assertTrue(visibility.size() == 4 && !visibility.has(group), group + " shows itself and hides the other four");
			} catch(java.io.IOException e) {
				helper.fail("reading " + group + ": " + e);
			}
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ the fuzzy identifier's click on a base screen

	/**
	 * What a press means: the carried identifier, a tank the screen drew under the mouse, a fluid in it. Other screens, an empty hand,
	 * another item, a point outside the box and an empty tank mean nothing; the last drawn of two overlapping boxes wins.
	 */
	@GameTest(template = EMPTY)
	public static void aClickOnABaseScreensTankSetsTheCarriedIdentifier(GameTestHelper helper) {
		if(LeafiaItems.fuzzy_identifier == null) { helper.succeed(); return; }
		ItemStack identifier = new ItemStack(LeafiaItems.fuzzy_identifier.get());
		Object screen = new Object();
		Object other = new Object();
		DrawnTanks.beginFrame();
		try {
			DrawnTanks.record(screen, new FluidTank(Fluids.WATER, 16_000), 10, 20, 16, 52);
			DrawnTanks.record(screen, new FluidTank(Fluids.OIL, 16_000), 40, 20, 16, 52);
			DrawnTanks.record(screen, new FluidTank(Fluids.NONE, 16_000), 70, 20, 16, 52);
			DrawnTanks.record(screen, new FluidTank(Fluids.LAVA, 16_000), 14, 24, 4, 4);

			helper.assertTrue(FuzzyScreenClicks.target(screen, 12, 30, identifier) == Fluids.WATER, "a click inside a tank's box names its fluid");
			helper.assertTrue(FuzzyScreenClicks.target(screen, 45, 30, identifier) == Fluids.OIL, "another tank, its own");
			helper.assertTrue(FuzzyScreenClicks.target(screen, 16, 28, identifier) == Fluids.LAVA, "the last drawn of two overlapping boxes wins");
			helper.assertTrue(FuzzyScreenClicks.target(screen, 30, 30, identifier) == null, "between the tanks is no click");
			helper.assertTrue(FuzzyScreenClicks.target(screen, 75, 30, identifier) == null, "an empty tank has no fluid to copy");
			helper.assertTrue(FuzzyScreenClicks.target(other, 12, 30, identifier) == null, "another screen's rectangles are not this one's");
			helper.assertTrue(FuzzyScreenClicks.target(screen, 12, 30, ItemStack.EMPTY) == null && FuzzyScreenClicks.target(screen, 12, 30, new ItemStack(Items.STICK)) == null,
					"only the identifier on the cursor clicks");
		} finally {
			DrawnTanks.beginFrame();
		}
		helper.succeed();
	}
}
