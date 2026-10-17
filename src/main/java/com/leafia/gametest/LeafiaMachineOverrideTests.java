package com.leafia.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.custom_hbm.sound.LCEAudioProxy;
import com.custom_hbm.sound.LCEAudioWrapper;
import com.hbm.blockentity.machine.BlockEntityFEL;
import com.hbm.blockentity.machine.BlockEntityMachineArcWelder;
import com.hbm.blockentity.machine.BlockEntityMachineSiren;
import com.hbm.blockentity.machine.BlockEntityMachineSolderingStation;
import com.hbm.blocks.BlockDummyable;
import com.hbm.blocks.ModBlocks;
import com.hbm.inventory.RecipesCommon.AStack;
import com.hbm.inventory.recipes.ArcWelderRecipes;
import com.hbm.inventory.recipes.ArcWelderRecipes.ArcWelderRecipe;
import com.hbm.inventory.recipes.SolderingRecipes;
import com.hbm.inventory.recipes.SolderingRecipes.SolderingRecipe;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemCassette;
import com.hbm.items.machine.ItemCassette.TrackType;
import com.hbm.items.machine.ItemFELCrystal.EnumWavelengths;
import com.hbm.sound.NtmSounds;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.machines.powercores.dfc.core.LeafiaCoreTE;
import com.leafia.dev.custompacket.LeafiaPacketTypes;
import com.leafia.init.LeafiaBlockEntities;
import com.leafia.lib.LeafiaRef;
import com.leafia.overwrite_contents.detonator.LeafiaLaserDetonator;
import com.leafia.overwrite_contents.fel.LeafiaFELTE;
import com.leafia.overwrite_contents.packets.LaserDetonatorPacket;
import com.leafia.overwrite_contents.siren.LeafiaSirenTE;
import com.leafia.overwrite_contents.welding.LeafiaArcWelderTE;
import com.leafia.overwrite_contents.welding.LeafiaSolderingStationTE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The siren, laser detonator, arc welder, soldering station and free-electron laser overhauls (D-3027): the
 * switches and the overrides they decide, the siren's falloff, sound and packets, the welder's and the soldering
 * station's base ticks without their particles and the station's sync, the wet welder's shock, the laser's prices and burns, and the
 * detonator's aim.
 *
 * <p>Every switch flipped is restored in the same body (rule 10); every machine placed is removed before the body
 * ends. A tick is a call (D-953).
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaMachineOverrideTests {

	private static final String EMPTY = "empty";
	private static final String BIG = "empty16";

	// ---- the rig ---------------------------------------------------------------------------

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

	private static BlockState core(Block block, Direction dir) {
		return block.defaultBlockState().setValue(BlockDummyable.PART, BlockDummyable.Part.CORE).setValue(BlockDummyable.DIR, dir);
	}

	/** Places {@code state} with a switch held at {@code value}, restoring it, and returns what the block made. */
	private static BlockEntity placeWith(GameTestHelper helper, BlockPos pos, BlockState state, Consumer<Boolean> set, BooleanSupplier get, boolean value) {
		boolean was = get.getAsBoolean();
		set.accept(value);
		try {
			helper.setBlock(pos, state);
		} finally {
			set.accept(was);
		}
		return helper.getBlockEntity(pos);
	}

	private static ItemStack of(AStack ingredient) {
		List<ItemStack> stacks = ingredient.extractForNEI();
		if(stacks.isEmpty()) throw new GameTestAssertException("ingredient " + ingredient + " names no item");
		return stacks.get(0).copyWithCount(ingredient.stacksize);
	}

	// ---- the switches ----------------------------------------------------------------------

	/**
	 * Each of the four base machines makes its own block entity while its switch is off and the addon's, on the
	 * addon's type in the base block, while it is on; the switches are restored.
	 */
	@GameTest(template = EMPTY)
	public static void theFourMachinesAreTheBaseModsUntilSwitchedOn(GameTestHelper helper) {
		BlockPos pos = new BlockPos(2, 1, 2);
		record Case(BlockState state, Consumer<Boolean> set, BooleanSupplier get, Class<?> base, Class<?> addon, Object type) {}
		List<Case> cases = List.of(
				new Case(ModBlocks.machine_siren.get().defaultBlockState(), v -> LeafiaFeatures.sirenOverhaul = v, () -> LeafiaFeatures.sirenOverhaul,
						BlockEntityMachineSiren.class, LeafiaSirenTE.class, LeafiaBlockEntities.SIREN.get()),
				new Case(core(ModBlocks.machine_arc_welder.get(), Direction.NORTH), v -> LeafiaFeatures.arcWelderOverhaul = v, () -> LeafiaFeatures.arcWelderOverhaul,
						BlockEntityMachineArcWelder.class, LeafiaArcWelderTE.class, LeafiaBlockEntities.ARC_WELDER.get()),
				new Case(core(ModBlocks.machine_soldering_station.get(), Direction.NORTH), v -> LeafiaFeatures.solderingOverhaul = v, () -> LeafiaFeatures.solderingOverhaul,
						BlockEntityMachineSolderingStation.class, LeafiaSolderingStationTE.class, LeafiaBlockEntities.SOLDERING_STATION.get()),
				new Case(core(ModBlocks.machine_fel.get(), Direction.NORTH), v -> LeafiaFeatures.felOverhaul = v, () -> LeafiaFeatures.felOverhaul,
						BlockEntityFEL.class, LeafiaFELTE.class, LeafiaBlockEntities.FEL.get()));
		for(Case c : cases) {
			helper.assertTrue(!c.get().getAsBoolean(), c.addon().getSimpleName() + "'s switch defaults off");
			BlockEntity off = placeWith(helper, pos, c.state(), c.set(), c.get(), false);
			helper.assertTrue(off != null && off.getClass() == c.base(), "off: the base mod's " + c.base().getSimpleName() + ", got " + off);
			helper.setBlock(pos, Blocks.AIR);
			BlockEntity on = placeWith(helper, pos, c.state(), c.set(), c.get(), true);
			helper.assertTrue(on != null && on.getClass() == c.addon() && on.getType() == c.type() && on.getBlockState().is(c.state().getBlock()),
					"on: the addon's " + c.addon().getSimpleName() + " in the base block, got " + on);
			helper.assertTrue(!c.get().getAsBoolean(), "the switch was restored");
			helper.setBlock(pos, Blocks.AIR);
		}
		helper.succeed();
	}

	// ---- the siren -------------------------------------------------------------------------

	/** A wrapper that records what the siren does with it. */
	private static final class Recorder extends LCEAudioWrapper {
		final List<String> log = new ArrayList<>();
		@Nullable java.util.function.BiFunction<Float, Double, Double> attenuation;
		@Override public LCEAudioWrapper setLooped(boolean looped) { log.add("looped " + looped); return this; }
		@Override public LCEAudioWrapper startSound() { log.add("start"); return this; }
		@Override public LCEAudioWrapper stopSound() { log.add("stop"); return this; }
		@Override public LCEAudioWrapper setCustomAttenuation(java.util.function.BiFunction<Float, Double, Double> f) { attenuation = f; return this; }
	}

	private record Request(SoundEvent sound, float volume, float pitch, float x, float y, float z) {}

	/**
	 * The client: a track makes one looping sound at the siren's centre, at the track's volume, which falls off in a
	 * straight line from {@code min(vol, 1)} at 6 blocks to nothing at {@code 6 + 1.5 vol}; the "playing" key starts and
	 * stops it, once each; a new track stops the old sound, the empty track drops it.
	 */
	@GameTest(template = EMPTY)
	public static void theSirenSoundsThroughTheAddonsFalloff(GameTestHelper helper) {
		helper.assertTrue(LeafiaSirenTE.attenuation(50, 6) == 1 && LeafiaSirenTE.attenuation(50, 3) == 1 && LeafiaSirenTE.attenuation(50, 43.5) == 0.5
				&& LeafiaSirenTE.attenuation(50, 81) == 0 && LeafiaSirenTE.attenuation(500, 756) == 0 && LeafiaSirenTE.attenuation(500, 381) == 0.5,
				"full to 6, half at 6 + 0.75 vol, gone at 6 + 1.5 vol");
		BlockPos pos = new BlockPos(2, 1, 2);
		LeafiaSirenTE siren = (LeafiaSirenTE) placeWith(helper, pos, ModBlocks.machine_siren.get().defaultBlockState(),
				v -> LeafiaFeatures.sirenOverhaul = v, () -> LeafiaFeatures.sirenOverhaul, true);
		List<Request> requests = new ArrayList<>();
		List<Recorder> made = new ArrayList<>();
		LCEAudioProxy.Factory previous = LCEAudioProxy.factory;
		LCEAudioProxy.factory = (world, sound, start, stop, cat, x, y, z, volume, pitch) -> {
			requests.add(new Request(sound, volume, pitch, x, y, z));
			Recorder r = new Recorder();
			made.add(r);
			return r;
		};
		try {
			int hatch = TrackType.HATCH.ordinal();
			siren.onReceivePacketLocal(LeafiaSirenTE.KEY_TRACK, hatch);
			siren.onReceivePacketLocal(LeafiaSirenTE.KEY_LOOPED, true);
			siren.onReceivePacketLocal(LeafiaSirenTE.KEY_PLAYING, true);
			siren.onReceivePacketLocal(LeafiaSirenTE.KEY_TRACK, hatch);
			siren.onReceivePacketLocal(LeafiaSirenTE.KEY_PLAYING, true);
			helper.assertTrue(requests.size() == 1, "one sound for one track: " + requests.size());
			Request r = requests.get(0);
			BlockPos abs = siren.getBlockPos();
			helper.assertTrue(r.sound() == NtmSounds.ALARM_HATCH.get() && r.volume() == 250 && r.pitch() == 1
					&& r.x() == abs.getX() + 0.5f && r.y() == abs.getY() + 0.5f && r.z() == abs.getZ() + 0.5f, "the hatch siren at 250, centred: " + r);
			Recorder sound = made.get(0);
			helper.assertTrue(sound.log.equals(List.of("looped true", "start")), "looped, started once: " + sound.log);
			helper.assertTrue(sound.attenuation != null && sound.attenuation.apply(250F, 193.5D) == 0.5 && sound.attenuation.apply(250F, 381D) == 0, "the falloff is installed, half at 193.5, gone at 381");
			siren.onReceivePacketLocal(LeafiaSirenTE.KEY_PLAYING, false);
			siren.onReceivePacketLocal(LeafiaSirenTE.KEY_PLAYING, false);
			helper.assertTrue(sound.log.equals(List.of("looped true", "start", "stop")), "stopped once: " + sound.log);
			siren.onReceivePacketLocal(LeafiaSirenTE.KEY_TRACK, TrackType.KLAXON.ordinal());
			helper.assertTrue(requests.size() == 2 && requests.get(1).volume() == 50, "a new track is a new sound");
			siren.onReceivePacketLocal(LeafiaSirenTE.KEY_TRACK, TrackType.NULL.ordinal());
			helper.assertTrue(siren.audio == null && made.get(1).log.contains("stop"), "the empty track drops it");
		} finally {
			LCEAudioProxy.factory = previous;
		}
		helper.setBlock(pos, Blocks.AIR);
		helper.succeed();
	}

	/** A siren that records its packets instead of sending them. */
	private static final class RecordingSiren extends LeafiaSirenTE {
		final List<String> sent = new ArrayList<>();
		RecordingSiren(BlockPos pos) {
			super(LeafiaBlockEntities.SIREN.get(), pos, ModBlocks.machine_siren.get().defaultBlockState());
		}
		@Override protected void sendSiren(Integer track, Boolean looped, boolean playing) { sent.add(track + "/" + looped + "/" + playing); }
		@Override protected void sendState(int id, boolean active) { sent.add("base " + id + (active ? "+" : "-")); }
	}

	private static ItemStack cassette(TrackType track) {
		ItemStack stack = new ItemStack(ModItems.siren_track.get());
		ItemCassette.setTrack(stack, track);
		return stack;
	}

	/**
	 * The server: no cassette is the base mod's "off" (and the addon's, once, if it had been sounding); a looping track is
	 * the addon's packet every tick; a one-shot track plays on the rising edge and, as the base mod's, runs to its end (D-3076).
	 */
	@GameTest(template = EMPTY)
	public static void theSirenSendsUpstreamsPackets(GameTestHelper helper) {
		BlockPos rel = new BlockPos(2, 1, 2);
		helper.setBlock(rel, ModBlocks.machine_siren.get().defaultBlockState());
		RecordingSiren siren = new RecordingSiren(helper.absolutePos(rel));
		siren.setLevel(helper.getLevel());
		siren.updateEntity();
		helper.assertTrue(siren.sent.equals(List.of("base 0-")), "no cassette is the base off: " + siren.sent);
		siren.sent.clear();
		siren.slots[0] = cassette(TrackType.APC_PASS);
		siren.updateEntity();
		helper.assertTrue(siren.sent.isEmpty(), "an unpowered one-shot is silent");
		helper.setBlock(rel.above(), Blocks.REDSTONE_BLOCK);
		siren.updateEntity();
		siren.updateEntity();
		int pass = TrackType.APC_PASS.ordinal();
		helper.assertTrue(siren.sent.equals(List.of(pass + "/false/true")), "the rising edge plays it once: " + siren.sent);
		helper.setBlock(rel.above(), Blocks.AIR);
		siren.updateEntity();
		helper.assertTrue(siren.sent.equals(List.of(pass + "/false/true")), "the falling edge sends nothing: the track runs to its end: " + siren.sent);
		siren.sent.clear();
		siren.slots[0] = cassette(TrackType.HATCH);
		siren.updateEntity();
		siren.updateEntity();
		int hatch = TrackType.HATCH.ordinal();
		helper.assertTrue(siren.sent.equals(List.of(hatch + "/true/false", hatch + "/true/false")), "a loop every tick: " + siren.sent);
		// D-3076: the cassette taken out of a looping siren silences the addon's sound, once
		siren.sent.clear();
		helper.setBlock(rel.above(), Blocks.REDSTONE_BLOCK);
		siren.updateEntity();
		siren.sent.clear();
		siren.slots[0] = ItemStack.EMPTY;
		siren.updateEntity();
		siren.updateEntity();
		helper.assertTrue(siren.sent.equals(List.of("base 0-", "null/null/false", "base 0-")), "the base off every tick, the addon's off once: " + siren.sent);
		helper.setBlock(rel.above(), Blocks.AIR);
		helper.setBlock(rel, Blocks.AIR);
		helper.succeed();
	}

	// ---- the welder and the soldering station ----------------------------------------------

	/**
	 * The addon welder runs the base welder's own tick (D-3031): no copy of it, only an empty override of the
	 * particle seam D-2206 added. Held to the base welder number for number over the 101 ticks of two motors, and
	 * the override is pinned as declared, so re-copying the tick, or losing the seam override, fails here.
	 */
	@GameTest(template = BIG)
	public static void theWelderRunsTheBaseTickWithoutItsParticles(GameTestHelper helper) {
		helper.assertTrue(declares(LeafiaArcWelderTE.class, "spawnWorkParticles"), "the welder overrides the particle seam");
		helper.assertTrue(!declares(LeafiaArcWelderTE.class, "tickWelding"), "and carries no copied tick");
		BlockPos a = new BlockPos(3, 1, 3), b = new BlockPos(11, 1, 3);
		BlockEntityMachineArcWelder base = (BlockEntityMachineArcWelder) placeWith(helper, a, core(ModBlocks.machine_arc_welder.get(), Direction.NORTH),
				v -> LeafiaFeatures.arcWelderOverhaul = v, () -> LeafiaFeatures.arcWelderOverhaul, false);
		LeafiaArcWelderTE ours = (LeafiaArcWelderTE) placeWith(helper, b, core(ModBlocks.machine_arc_welder.get(), Direction.NORTH),
				v -> LeafiaFeatures.arcWelderOverhaul = v, () -> LeafiaFeatures.arcWelderOverhaul, true);
		ArcWelderRecipe recipe = ArcWelderRecipes.recipes.get(0);
		for(BlockEntityMachineArcWelder w : List.of(base, ours)) {
			w.slots[0] = of(recipe.ingredients[0]);
			w.slots[1] = of(recipe.ingredients[1]);
			w.slots[4] = new ItemStack(ModItems.battery_creative.get());
		}
		for(int t = 0; t < 101; t++) {
			base.updateEntity();
			ours.updateEntity();
			helper.assertTrue(base.progress == ours.progress && base.power == ours.power && base.consumption == ours.consumption
					&& base.maxPower == ours.maxPower && base.processTime == ours.processTime,
					"tick " + t + ": base " + base.progress + "/" + base.power + " vs " + ours.progress + "/" + ours.power);
		}
		helper.assertTrue(ItemStack.matches(base.slots[3], ours.slots[3]) && !ours.slots[3].isEmpty(), "both made " + base.slots[3]);
		helper.setBlock(a, Blocks.AIR);
		helper.setBlock(b, Blocks.AIR);
		helper.succeed();
	}

	/** The soldering station the same way, over the analog circuit: the base tick, the seam override, no copy. */
	@GameTest(template = BIG)
	public static void theSolderingStationRunsTheBaseTickWithoutItsParticles(GameTestHelper helper) {
		helper.assertTrue(declares(LeafiaSolderingStationTE.class, "spawnWorkParticles"), "the station overrides the particle seam");
		helper.assertTrue(!declares(LeafiaSolderingStationTE.class, "updateEntity") && !declares(LeafiaSolderingStationTE.class, "tickSoldering"),
				"and does not override the tick at all");
		BlockPos a = new BlockPos(3, 1, 3), b = new BlockPos(11, 1, 3);
		BlockEntityMachineSolderingStation base = (BlockEntityMachineSolderingStation) placeWith(helper, a, core(ModBlocks.machine_soldering_station.get(), Direction.NORTH),
				v -> LeafiaFeatures.solderingOverhaul = v, () -> LeafiaFeatures.solderingOverhaul, false);
		LeafiaSolderingStationTE ours = (LeafiaSolderingStationTE) placeWith(helper, b, core(ModBlocks.machine_soldering_station.get(), Direction.NORTH),
				v -> LeafiaFeatures.solderingOverhaul = v, () -> LeafiaFeatures.solderingOverhaul, true);
		SolderingRecipe recipe = SolderingRecipes.recipes.get(0);
		for(BlockEntityMachineSolderingStation s : List.of(base, ours)) loadSoldering(s, recipe);
		for(int t = 0; t < 101; t++) {
			base.updateEntity();
			ours.updateEntity();
			helper.assertTrue(base.progress == ours.progress && base.power == ours.power && base.consumption == ours.consumption && base.maxPower == ours.maxPower,
					"tick " + t + ": base " + base.progress + "/" + base.power + " vs " + ours.progress + "/" + ours.power);
		}
		helper.assertTrue(ItemStack.matches(base.slots[6], ours.slots[6]) && !ours.slots[6].isEmpty(), "both made " + base.slots[6]);
		helper.setBlock(a, Blocks.AIR);
		helper.setBlock(b, Blocks.AIR);
		helper.succeed();
	}

	/**
	 * The overhauled station's sync carries the output it is making, so the bench shows it (D-3027 hand-off 2: the
	 * copied tick kept the recipe in a local, and the base sync wrote "no recipe" for as long as the overhaul was on).
	 * The bytes are the real {@code serialize}, read back through the real {@code deserialize}.
	 */
	@GameTest(template = BIG)
	public static void theOverhauledSolderingStationSyncsWhatItIsMaking(GameTestHelper helper) {
		BlockPos b = new BlockPos(5, 1, 5);
		LeafiaSolderingStationTE ours = (LeafiaSolderingStationTE) placeWith(helper, b, core(ModBlocks.machine_soldering_station.get(), Direction.NORTH),
				v -> LeafiaFeatures.solderingOverhaul = v, () -> LeafiaFeatures.solderingOverhaul, true);
		SolderingRecipe recipe = SolderingRecipes.recipes.get(0);
		loadSoldering(ours, recipe);
		ours.updateEntity();
		helper.assertTrue(ours.progress > 0, "it is working");
		var buf = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
		ours.serialize(buf);
		ours.display = ItemStack.EMPTY;
		ours.deserialize(buf);
		helper.assertTrue(ours.display.is(recipe.output.getItem()), "the bench shows " + recipe.output + ", synced " + ours.display);
		helper.setBlock(b, Blocks.AIR);
		helper.succeed();
	}

	private static void loadSoldering(BlockEntityMachineSolderingStation s, SolderingRecipe recipe) {
		for(int i = 0; i < recipe.toppings.length; i++) s.slots[i] = of(recipe.toppings[i]);
		for(int i = 0; i < recipe.pcb.length; i++) s.slots[3 + i] = of(recipe.pcb[i]);
		for(int i = 0; i < recipe.solder.length; i++) s.slots[5 + i] = of(recipe.solder[i]);
		s.slots[7] = new ItemStack(ModItems.battery_creative.get());
	}

	private static boolean declares(Class<?> type, String method) {
		for(var m : type.getDeclaredMethods()) if(m.getName().equals(method)) return true;
		return false;
	}

	/**
	 * A working welder next to water shocks every entity within two blocks of its centre for 4; dry, or idle, it does
	 * not. The wet check reads water blocks in the 5 x 3 x 5 from its layer up, and runs every 50 ticks.
	 */
	@GameTest(template = BIG)
	public static void aWetWelderShocksWhatIsNearIt(GameTestHelper helper) {
		BlockPos at = new BlockPos(5, 1, 5);
		LeafiaArcWelderTE w = (LeafiaArcWelderTE) placeWith(helper, at, core(ModBlocks.machine_arc_welder.get(), Direction.NORTH),
				v -> LeafiaFeatures.arcWelderOverhaul = v, () -> LeafiaFeatures.arcWelderOverhaul, true);
		helper.assertTrue(LeafiaArcWelderTE.WET_CHECK_INTERVAL == 50 && LeafiaArcWelderTE.SHOCK_DAMAGE == 4 && LeafiaArcWelderTE.SHOCK_RANGE == 2
				&& LeafiaArcWelderTE.FIRE_ODDS == 50, "every 50 ticks, 4 within 2, fire one in 50");
		Pig pig = helper.spawn(EntityType.PIG, at.east());
		helper.assertTrue(!w.checkWet(), "dry");
		ArcWelderRecipe recipe = ArcWelderRecipes.recipes.get(0);
		w.slots[0] = of(recipe.ingredients[0]);
		w.slots[1] = of(recipe.ingredients[1]);
		w.slots[4] = new ItemStack(ModItems.battery_creative.get());
		w.updateEntity();
		w.updateEntity();
		helper.assertTrue(w.progress > 0, "it is working");
		w.isWet = w.checkWet();
		w.tickHazard();
		helper.assertTrue(pig.getHealth() == pig.getMaxHealth(), "a dry welder hurts nothing");
		helper.setBlock(at.offset(-2, 2, 2), Blocks.WATER);
		helper.assertTrue(w.checkWet(), "water two out and two up is wet");
		w.isWet = w.checkWet();
		pig.invulnerableTime = 0;
		w.tickHazard();
		helper.assertTrue(pig.getHealth() == pig.getMaxHealth() - 4, "the pig took 4: " + pig.getHealth());
		w.progress = 0;
		pig.invulnerableTime = 0;
		float before = pig.getHealth();
		w.tickHazard();
		helper.assertTrue(pig.getHealth() == before, "an idle welder does not shock");
		pig.discard();
		helper.setBlock(at.offset(-2, 2, 2), Blocks.AIR);
		helper.setBlock(at, Blocks.AIR);
		helper.succeed();
	}

	// ---- the free-electron laser -----------------------------------------------------------

	/**
	 * {@code 1250 x 4^n} where the base laser charges {@code 3^n}; the heat each wavelength gives a core; and what it
	 * does to a pig, case by case with no fall-through: visible blinds without burning, UV burns, IR does nothing.
	 */
	@GameTest(template = EMPTY)
	public static void theLaserIsRepricedAndHitsUpstreamsWay(GameTestHelper helper) {
		BlockPos pos = new BlockPos(2, 1, 2);
		LeafiaFELTE fel = (LeafiaFELTE) placeWith(helper, pos, core(ModBlocks.machine_fel.get(), Direction.NORTH),
				v -> LeafiaFeatures.felOverhaul = v, () -> LeafiaFeatures.felOverhaul, true);
		int[] cost = { 0, 5000, 20000, 80000, 320000, 1280000 };
		EnumWavelengths[] modes = { EnumWavelengths.NULL, EnumWavelengths.IR, EnumWavelengths.VISIBLE, EnumWavelengths.UV, EnumWavelengths.GAMMA, EnumWavelengths.DRX };
		for(int i = 0; i < modes.length; i++)
			helper.assertTrue(fel.getPowerCost(modes[i]) == cost[i], modes[i] + " costs " + cost[i] + ", got " + fel.getPowerCost(modes[i]));
		helper.assertTrue(LeafiaFELTE.coreHeat(EnumWavelengths.IR) == 15 && LeafiaFELTE.coreHeat(EnumWavelengths.VISIBLE) == 7
				&& LeafiaFELTE.coreHeat(EnumWavelengths.UV) == 8.5 && LeafiaFELTE.coreHeat(EnumWavelengths.GAMMA) == 32.5
				&& LeafiaFELTE.coreHeat(EnumWavelengths.DRX) == 75.5 && LeafiaFELTE.DFC_SCALE == 0.4, "core heat 15 / 7 / 8.5 / 32.5 / 75.5 x 0.4");
		Pig a = helper.spawn(EntityType.PIG, new BlockPos(1, 2, 1));
		Pig b = helper.spawn(EntityType.PIG, new BlockPos(3, 2, 1));
		Pig c = helper.spawn(EntityType.PIG, new BlockPos(1, 2, 3));
		fel.hitEntity(a, EnumWavelengths.VISIBLE);
		fel.hitEntity(b, EnumWavelengths.UV);
		fel.hitEntity(c, EnumWavelengths.IR);
		helper.assertTrue(a.hasEffect(MobEffects.BLINDNESS) && a.getRemainingFireTicks() <= 0, "visible blinds and does not burn");
		helper.assertTrue(b.getRemainingFireTicks() > 0 && !b.hasEffect(MobEffects.BLINDNESS), "UV burns");
		helper.assertTrue(c.getRemainingFireTicks() <= 0 && !c.hasEffect(MobEffects.BLINDNESS), "IR does nothing");
		a.discard();
		b.discard();
		c.discard();
		helper.setBlock(pos, Blocks.AIR);
		helper.succeed();
	}

	/**
	 * The beam: short of the price it drops to nothing and empties the buffer; IR stops at the first block; DRX burns
	 * through, leaving digamma fire on digamma ash; and a beam into the addon's DFC core feeds it {@code cost / 5000} SPK
	 * and {@code 0.4 x} the wavelength's heat, stopping there.
	 */
	@GameTest(template = BIG)
	public static void theLaserBurnsByWavelengthAndFeedsACore(GameTestHelper helper) {
		BlockPos at = new BlockPos(2, 1, 8);
		LeafiaFELTE fel = (LeafiaFELTE) placeWith(helper, at, core(ModBlocks.machine_fel.get(), Direction.EAST),
				v -> LeafiaFeatures.felOverhaul = v, () -> LeafiaFeatures.felOverhaul, true);
		BlockPos stone = at.offset(3, 1, 0);
		helper.setBlock(stone, Blocks.STONE);
		fel.isOn = true;
		fel.slots[1] = new ItemStack(ModItems.laser_crystal_co2.get());
		fel.power = 4999;
		fel.updateEntity();
		helper.assertTrue(fel.mode == EnumWavelengths.NULL && fel.power == 0, "short of 5,000 it drops to nothing and empties: " + fel.mode + " " + fel.power);
		fel.power = 12000;
		fel.updateEntity();
		helper.assertTrue(fel.mode == EnumWavelengths.IR && fel.power == 7000 && fel.distance == 3, "IR costs 5,000 and stops three out: " + fel.power + " " + fel.distance);
		helper.assertTrue(helper.getBlockState(stone).is(Blocks.STONE), "and leaves the stone");
		// the burns, block by block (a DRX beam is not stopped by anything but a core, so it is not run down the template)
		fel.mode = EnumWavelengths.DRX;
		BlockPos dirt = at.offset(5, 1, 0);
		helper.setBlock(dirt, Blocks.DIRT);
		boolean blocked = fel.hitBlock(helper.absolutePos(dirt), Blocks.DIRT.defaultBlockState());
		helper.assertTrue(!blocked && helper.getBlockState(dirt).is(ModBlocks.fire_digamma.get()) && helper.getBlockState(dirt.below()).is(ModBlocks.ash_digamma.get()),
				"DRX burns through, digamma fire on ash");
		fel.mode = EnumWavelengths.IR;
		helper.assertTrue(fel.hitBlock(helper.absolutePos(stone), Blocks.STONE.defaultBlockState()), "IR is stopped");
		fel.mode = EnumWavelengths.GAMMA;
		helper.setBlock(stone, Blocks.BEDROCK);
		helper.assertTrue(fel.hitBlock(helper.absolutePos(stone), Blocks.BEDROCK.defaultBlockState()) && helper.getBlockState(stone).is(Blocks.BEDROCK),
				"gamma cannot touch bedrock");
		// the core
		BlockPos coreAt = at.offset(8, 1, 0);
		boolean was = LeafiaFeatures.dfcOverhaul;
		LeafiaFeatures.dfcOverhaul = true;
		try {
			helper.setBlock(coreAt, ModBlocks.dfc_core.get());
		} finally {
			LeafiaFeatures.dfcOverhaul = was;
		}
		BlockEntity be = helper.getBlockEntity(coreAt);
		helper.assertTrue(be instanceof LeafiaCoreTE, "the overhauled core: " + be);
		LeafiaCoreTE core = (LeafiaCoreTE) be;
		double spk = core.getDFCIncomingSpk(), heat = core.getDFCTemperature();
		fel.mode = EnumWavelengths.UV;
		helper.assertTrue(fel.hitBlock(helper.absolutePos(coreAt), helper.getBlockState(coreAt)), "a core stops the beam");
		helper.assertTrue(Math.abs(core.getDFCIncomingSpk() - spk - 16) < 1e-9 && Math.abs(core.getDFCTemperature() - heat - 3.4) < 1e-9,
				"UV feeds 80,000 / 5,000 = 16 SPK and 8.5 x 0.4 = 3.4 heat: " + (core.getDFCIncomingSpk() - spk) + " / " + (core.getDFCTemperature() - heat));
		helper.setBlock(coreAt, Blocks.AIR);
		helper.setBlock(dirt, Blocks.AIR);
		helper.setBlock(dirt.below(), Blocks.AIR);
		helper.setBlock(stone, Blocks.AIR);
		helper.setBlock(at, Blocks.AIR);
		helper.succeed();
	}

	// ---- the laser detonator ---------------------------------------------------------------

	/**
	 * The detonator's aim: 128 blocks, 512 sneaking with one hand empty; the ray leaves half a block ahead, 0.16 down
	 * and 0.1 toward the holding hand on the short ray only; the sights are 0.3, signed by hand; and while the overhaul
	 * is on, its click is the addon's ({@code PASS}, cancelled) and the base item's while it is off. The laser's motes
	 * step out from each end by {@code ((p / 30)^1.5 + 1) x 0.2}.
	 */
	@GameTest(template = EMPTY)
	public static void theLaserDetonatorAimsFromTheHand(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		player.setPos(helper.absoluteVec(new Vec3(2.5, 1, 2.5)));
		player.setYRot(0);
		player.setYHeadRot(0);
		player.setXRot(0);
		player.setMainArm(HumanoidArm.RIGHT);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.detonator_laser.get()));
		player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		player.setShiftKeyDown(false);
		helper.assertTrue(LeafiaLaserDetonator.range(player) == 128, "128 standing");
		player.setShiftKeyDown(true);
		helper.assertTrue(LeafiaLaserDetonator.range(player) == 512, "512 sneaking one-handed");
		player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(net.minecraft.world.item.Items.STONE));
		helper.assertTrue(LeafiaLaserDetonator.range(player) == 128, "128 with both hands full");
		player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		Vec3 eye = player.position().add(0, player.getEyeHeight(), 0);
		Vec3 right = LeafiaLaserDetonator.rayStart(player, InteractionHand.MAIN_HAND, 128).subtract(eye);
		Vec3 left = LeafiaLaserDetonator.rayStart(player, InteractionHand.OFF_HAND, 128).subtract(eye);
		Vec3 far = LeafiaLaserDetonator.rayStart(player, InteractionHand.MAIN_HAND, 512).subtract(eye);
		helper.assertTrue(right.distanceTo(new Vec3(-0.1, -0.16, 0.5)) < 1e-6, "facing south the right hand is west: " + right);
		helper.assertTrue(left.distanceTo(new Vec3(0.1, -0.16, 0.5)) < 1e-6, "the left hand east: " + left);
		helper.assertTrue(far.distanceTo(new Vec3(0, -0.16, 0.5)) < 1e-6, "the long ray from the middle: " + far);
		boolean was = LeafiaFeatures.laserDetonatorOverhaul;
		try {
			LeafiaFeatures.laserDetonatorOverhaul = false;
			helper.assertTrue(LeafiaLaserDetonator.getViewADS(player) == 0, "no sights with the overhaul off");
			PlayerInteractEvent.RightClickItem off = NeoForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickItem(player, InteractionHand.MAIN_HAND));
			helper.assertTrue(!off.isCanceled(), "off, the base item answers the click");
			LeafiaFeatures.laserDetonatorOverhaul = true;
			helper.assertTrue(Math.abs(LeafiaLaserDetonator.getViewADS(player) - 0.3F) < 1e-6, "sights of 0.3 in the main hand");
			player.setItemInHand(InteractionHand.OFF_HAND, player.getMainHandItem());
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			helper.assertTrue(Math.abs(LeafiaLaserDetonator.getViewADS(player) + 0.3F) < 1e-6, "and -0.3 in the off hand");
			player.setItemInHand(InteractionHand.MAIN_HAND, player.getOffhandItem());
			player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
			player.setXRot(-90); // at the sky: nothing to detonate
			PlayerInteractEvent.RightClickItem on = NeoForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickItem(player, InteractionHand.MAIN_HAND));
			helper.assertTrue(on.isCanceled() && on.getCancellationResult() == InteractionResult.PASS, "on, the addon's click, PASS");
			helper.assertTrue(LeafiaLaserDetonator.carries(player), "it carries one");
		} finally {
			LeafiaFeatures.laserDetonatorOverhaul = was;
		}
		List<Double> steps = LaserDetonatorPacket.moteSteps(2);
		helper.assertTrue(steps.size() == 5 && Math.abs(steps.get(0) - 0.2) < 1e-12 && Math.abs(steps.get(1) - (0.2 + (Math.pow(0.2 / 30, 1.5) + 1) * 0.2)) < 1e-12,
				"five motes from each end over two blocks: " + steps);
		helper.assertTrue(LeafiaPacketTypes.count() == 41, "forty-one packet types (D-3028 appended two, D-3040 one, D-3041 the rad goggles box, D-3071 three, D-3072 two)");
		helper.succeed();
	}
}
