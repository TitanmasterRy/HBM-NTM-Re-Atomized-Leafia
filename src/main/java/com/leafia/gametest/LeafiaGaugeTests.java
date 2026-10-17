package com.leafia.gametest;

import com.hbm.inventory.fluid.Fluids;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.network.fluid.FluidDuctEquipmentBase;
import com.leafia.contents.network.fluid.gauges.FluidDuctGauge;
import com.leafia.contents.network.fluid.gauges.FluidDuctGaugeTE;
import com.leafia.init.LeafiaBlockEntities;
import com.leafia.lib.LeafiaRef;

import api.hbm.block.IToolable.ToolType;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The flow gauge (D-3005): a ring of twenty ticks of the network's throughput, a dial scaled to a
 * number a player sets, and the arithmetic that turns one into the other.
 *
 * <p>The reading is read off {@code FluidNetMK2.fluidTracker}, which the network resets every tick,
 * so these tests write it and tick the block entity <em>in the same breath</em>, which is the only
 * way a test can say what the network moved.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaGaugeTests {

	private static final String EMPTY = "empty";
	private static final BlockPos WHERE = new BlockPos(1, 1, 1);

	private static FluidDuctGaugeTE gauge(GameTestHelper helper) {
		helper.setBlock(WHERE, LeafiaBlocks.fluid_duct_gauge_mdl.get());
		return (FluidDuctGaugeTE) helper.getBlockEntity(WHERE);
	}

	/** A server player taken back off the player list (D-3000): see {@code LeafiaEvCabinGuiTests}. */
	private static ServerPlayer mockPlayer(GameTestHelper helper) {
		@SuppressWarnings("deprecation")
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

	/** D-1002 for the gauge: it is on {@code features.content.fluidDuctEquipment}, with the valves. */
	@GameTest(template = EMPTY)
	public static void theSwitchDecidesWhetherTheGaugeExists(GameTestHelper helper) {

		net.minecraft.resources.ResourceLocation block = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID, "fluid_duct_gauge_mdl");
		helper.assertTrue(net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(block) == LeafiaFeatures.fluidDuctEquipment, "the block exists iff the switch is on");
		helper.assertTrue(net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(block) == LeafiaFeatures.fluidDuctEquipment, "and its item");
		helper.assertTrue(net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(
				net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID, "fluid_duct_gauge")) == LeafiaFeatures.fluidDuctEquipment, "and its block entity type");
		helper.assertTrue((LeafiaBlocks.fluid_duct_gauge_mdl != null) == LeafiaFeatures.fluidDuctEquipment, "and the holder is null when it is off");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theGaugeIsUpstreamsBlock(GameTestHelper helper) {

		if(!LeafiaFeatures.fluidDuctEquipment) { helper.succeed(); return; }

		var block = LeafiaBlocks.fluid_duct_gauge_mdl.get();
		BlockState state = block.defaultBlockState();

		helper.assertTrue(block instanceof FluidDuctGauge && block instanceof FluidDuctEquipmentBase, "a flow gauge, on the equipment base");
		helper.assertTrue(state.getDestroySpeed(helper.getLevel(), BlockPos.ZERO) == 5F, "hardness 5");
		helper.assertTrue(block.getExplosionResistance() == 6F, "resistance 10 x 0.6 = 6, the valves' own");
		helper.assertTrue(state.getSoundType() == SoundType.METAL, "the valves' step sound");
		helper.assertTrue(state.getRenderShape() == RenderShape.ENTITYBLOCK_ANIMATED, "drawn by its renderer");

		FluidDuctGaugeTE te = gauge(helper);
		helper.assertTrue(te.getType() == LeafiaBlockEntities.FLUID_DUCT_GAUGE.get(), "its own type");
		helper.assertTrue(te.maximum == 10_000L, "the dial starts at 10,000 mB/s");
		helper.assertTrue(te.fillPerSec == 0L, "and reads nothing");
		helper.assertTrue("DUCT_GAUGE".equals(te.getPacketIdentifier()), "upstream's packet id");
		helper.succeed();
	}

	/**
	 * Twenty slots, one written per tick, the reading the sum: a rolling second. Feeding it a
	 * constant 100 a tick reads 2,000 a second once the ring is full and not before; stopping drains
	 * it one slot a tick.
	 */
	@GameTest(template = EMPTY, timeoutTicks = 60)
	public static void theReadingIsARollingSecond(GameTestHelper helper) {

		if(!LeafiaFeatures.fluidDuctEquipment) { helper.succeed(); return; }

		FluidDuctGaugeTE te = gauge(helper);
		te.setType(Fluids.WATER);

		helper.runAtTickTime(5, () -> {

			helper.assertTrue(te.getNode() != null && te.getNode().net != null, "a typed gauge joins a network");

			// The block's own ticks have been writing zeros; this is the same code, hand-driven.
			for(int i = 1; i <= 20; i++) {
				te.getNode().net.fluidTracker = 100L;
				te.updateEntity();
				helper.assertTrue(te.fillPerSec == 100L * i, "after " + i + " ticks of 100 the second reads " + 100L * i + ", was " + te.fillPerSec);
			}

			te.getNode().net.fluidTracker = 100L;
			te.updateEntity();
			helper.assertTrue(te.fillPerSec == 2000L, "the ring is twenty slots: the 21st tick replaces the first, still " + te.fillPerSec);

			for(int i = 1; i <= 20; i++) {
				te.getNode().net.fluidTracker = 0L;
				te.updateEntity();
				helper.assertTrue(te.fillPerSec == 2000L - 100L * i, "stopping drains one slot a tick: " + te.fillPerSec + " after " + i);
			}
			helper.assertTrue(te.fillPerSec == 0L, "and it empties");
			helper.succeed();
		});
	}

	/** An untyped gauge reads nothing whatever its network carries. */
	@GameTest(template = EMPTY, timeoutTicks = 40)
	public static void anUntypedGaugeReadsNothing(GameTestHelper helper) {

		if(!LeafiaFeatures.fluidDuctEquipment) { helper.succeed(); return; }

		FluidDuctGaugeTE te = gauge(helper);

		helper.runAtTickTime(5, () -> {
			helper.assertTrue(te.getFluidType() == Fluids.NONE, "untyped");
			helper.assertTrue(te.getNode() != null && te.getNode().net != null, "Fluids.NONE has a network of its own");
			te.getNode().net.fluidTracker = 777L;
			te.updateEntity();
			helper.assertTrue(te.fillPerSec == 0L, "but no reading, got " + te.fillPerSec);
			helper.succeed();
		});
	}

	/**
	 * Upstream's renderer arithmetic: {@code min(fill / max x 360, 350 + rand)}. Half scale is 180
	 * degrees; full scale and beyond stops at 350 plus a tremble of 0 to 4; a gauge set to nothing
	 * divides by zero, which upstream lets through as NaN.
	 */
	@GameTest(template = EMPTY)
	public static void theNeedleTurnsToUpstreamsAngle(GameTestHelper helper) {

		if(!LeafiaFeatures.fluidDuctEquipment) { helper.succeed(); return; }

		FluidDuctGaugeTE te = gauge(helper);
		te.maximum = 10_000;

		te.fillPerSec = 0;
		helper.assertTrue(te.needleAngle() == 0F, "empty, the needle is home");
		te.fillPerSec = 5_000;
		helper.assertTrue(te.needleAngle() == 180F, "half scale is half a turn");
		te.fillPerSec = 2_500;
		helper.assertTrue(te.needleAngle() == 90F, "a quarter is a quarter");

		te.fillPerSec = 10_000;
		for(int rand = 0; rand < 5; rand++) {
			te.localDialRand = rand;
			helper.assertTrue(te.needleAngle() == 350F + rand, "full scale is past the stop, which is 350 + " + rand + "; was " + te.needleAngle());
		}

		te.fillPerSec = 9_800;   // 352.8 degrees
		te.localDialRand = 0;
		helper.assertTrue(Math.abs(te.needleAngle() - 350F) < 1e-4, "352.8 is stopped at 350 with no tremble");
		te.localDialRand = 4;
		helper.assertTrue(Math.abs(te.needleAngle() - 352.8F) < 1e-3, "and gets there with a tremble of 3 or more, was " + te.needleAngle());

		te.fillPerSec = 1_000_000;
		te.localDialRand = 4;
		helper.assertTrue(te.needleAngle() == 354F, "a pegged needle stops at 354 at most");

		te.maximum = 20_000;
		te.fillPerSec = 5_000;
		helper.assertTrue(te.needleAngle() == 90F, "the scale moves with the maximum");

		te.maximum = 0;
		te.fillPerSec = 0;
		helper.assertTrue(Float.isNaN(te.needleAngle()), "0 / 0 is NaN, as upstream's is");
		helper.succeed();
	}

	/** The scale is saved, and a save without one keeps the default. */
	@GameTest(template = EMPTY)
	public static void theMaximumIsSavedAndSent(GameTestHelper helper) {

		if(!LeafiaFeatures.fluidDuctEquipment) { helper.succeed(); return; }

		FluidDuctGaugeTE te = gauge(helper);
		te.maximum = 123_456_789_012L;
		te.fillPerSec = 4_242L;

		var registries = helper.getLevel().registryAccess();
		CompoundTag tag = te.saveWithFullMetadata(registries);
		helper.assertTrue(tag.getLong("maximum") == 123_456_789_012L, "saved under upstream's key");

		BlockEntity loaded = BlockEntity.loadStatic(helper.absolutePos(WHERE), helper.getBlockState(WHERE), tag, registries);
		helper.assertTrue(loaded instanceof FluidDuctGaugeTE g && g.maximum == 123_456_789_012L, "and loaded");

		tag.remove("maximum");
		BlockEntity bare = BlockEntity.loadStatic(helper.absolutePos(WHERE), helper.getBlockState(WHERE), tag, registries);
		helper.assertTrue(bare instanceof FluidDuctGaugeTE g && g.maximum == 10_000L, "a save without it keeps 10,000");

		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
		te.serialize(buf);
		FluidDuctGaugeTE received = new FluidDuctGaugeTE(LeafiaBlockEntities.FLUID_DUCT_GAUGE.get(), helper.absolutePos(WHERE), helper.getBlockState(WHERE));
		received.deserialize(buf);
		helper.assertTrue(received.fillPerSec == 4_242L && received.maximum == 123_456_789_012L, "the reading and the scale cross the wire");
		helper.succeed();
	}

	/**
	 * The edit comes back as key 0, a long. It is taken from the player the server opened the screen
	 * for, while they are in reach, and from nobody else; a wrong key or type is ignored.
	 */
	@GameTest(template = EMPTY)
	public static void theMaximumIsOnlyTakenFromWhoeverIsEditing(GameTestHelper helper) {

		if(!LeafiaFeatures.fluidDuctEquipment) { helper.succeed(); return; }

		FluidDuctGaugeTE te = gauge(helper);
		ServerPlayer player = mockPlayer(helper);
		Vec3 near = helper.absoluteVec(new Vec3(1.5, 1, 1.5));
		player.setPos(near.x, near.y, near.z);

		te.onReceivePacketServer(FluidDuctGaugeTE.ID_FILL, 5_000L, player);
		helper.assertTrue(te.maximum == 10_000L, "a stranger cannot set the scale");

		helper.assertTrue(LeafiaBlocks.fluid_duct_gauge_mdl.get().onScrew(helper.getLevel(), player, helper.absolutePos(WHERE),
				Direction.UP, Vec3.ZERO, ToolType.SCREWDRIVER), "a screwdriver is taken, and opens the screen");
		helper.assertTrue(te.editors.contains(player.getUUID()), "and the server remembers who has it");

		te.onReceivePacketServer(FluidDuctGaugeTE.ID_FILL, 5_000L, player);
		helper.assertTrue(te.maximum == 5_000L, "the editor sets it, got " + te.maximum);

		te.onReceivePacketServer(FluidDuctGaugeTE.ID_MAXIMUM, 7_000L, player);
		helper.assertTrue(te.maximum == 5_000L, "key 1 is the server's to send, not the client's");
		te.onReceivePacketServer(FluidDuctGaugeTE.ID_FILL, 7_000, player);
		helper.assertTrue(te.maximum == 5_000L, "an int is not a long");
		te.onReceivePacketServer(FluidDuctGaugeTE.ID_FILL, -3L, player);
		helper.assertTrue(te.maximum == -3L, "upstream stores any long, negative included");
		te.onReceivePacketServer(FluidDuctGaugeTE.ID_FILL, 0L, player);
		helper.assertTrue(te.maximum == 0L, "and zero");

		player.setPos(near.x + 400, near.y, near.z);
		te.onReceivePacketServer(FluidDuctGaugeTE.ID_FILL, 99L, player);
		helper.assertTrue(te.maximum == 0L, "out of reach is refused");
		helper.assertTrue(!te.editors.contains(player.getUUID()), "and ends the session");

		te.onReceivePacketServer(FluidDuctGaugeTE.ID_FILL, 99L, null);
		helper.assertTrue(te.maximum == 0L, "no player, no edit");
		helper.succeed();
	}

	/** Only a screwdriver; anything else is left to the block. */
	@GameTest(template = EMPTY)
	public static void onlyAScrewdriverOpensTheScreen(GameTestHelper helper) {

		if(!LeafiaFeatures.fluidDuctEquipment) { helper.succeed(); return; }

		FluidDuctGaugeTE te = gauge(helper);
		ServerPlayer player = mockPlayer(helper);
		var block = LeafiaBlocks.fluid_duct_gauge_mdl.get();

		helper.assertTrue(!block.onScrew(helper.getLevel(), player, helper.absolutePos(WHERE), Direction.UP, Vec3.ZERO, ToolType.WRENCH), "a wrench does nothing");
		helper.assertTrue(!block.onScrew(helper.getLevel(), player, helper.absolutePos(WHERE), Direction.UP, Vec3.ZERO, ToolType.HAND_DRILL), "nor a hand drill");
		helper.assertTrue(te.editors.isEmpty(), "and nobody is editing");
		helper.succeed();
	}
}
