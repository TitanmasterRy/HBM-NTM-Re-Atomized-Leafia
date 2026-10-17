package com.leafia.gametest;

import java.util.List;

import com.hbm.blockentity.machine.BlockEntityCoreEmitter;
import com.hbm.blockentity.machine.BlockEntityCoreInjector;
import com.hbm.blockentity.machine.BlockEntityCoreReceiver;
import com.hbm.blockentity.machine.BlockEntityCoreStabilizer;
import com.hbm.blocks.ModBlocks;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemLens;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.LeafiaItems;
import com.leafia.contents.machines.powercores.dfc.LCEItemLens;
import com.leafia.contents.machines.powercores.dfc.LeafiaDFC;
import com.leafia.contents.machines.powercores.dfc.components.DFCParts;
import com.leafia.contents.machines.powercores.dfc.components.absorber.CoreReceiverMenu;
import com.leafia.contents.machines.powercores.dfc.components.absorber.LeafiaCoreReceiverTE;
import com.leafia.contents.machines.powercores.dfc.components.cemitter.CoreCEmitterMenu;
import com.leafia.contents.machines.powercores.dfc.components.cemitter.CoreCEmitterTE;
import com.leafia.contents.machines.powercores.dfc.components.emitter.LeafiaCoreEmitterTE;
import com.leafia.contents.machines.powercores.dfc.components.exchanger.CoreExchangerMenu;
import com.leafia.contents.machines.powercores.dfc.components.exchanger.CoreExchangerTE;
import com.leafia.contents.machines.powercores.dfc.components.injector.CoreInjectorMenu;
import com.leafia.contents.machines.powercores.dfc.components.injector.LeafiaCoreInjectorTE;
import com.leafia.contents.machines.powercores.dfc.components.stabilizer.LeafiaCoreStabilizerTE;
import com.leafia.contents.machines.powercores.dfc.core.LeafiaCoreTE;
import com.leafia.contents.machines.powercores.dfc.debris.AbsorberShrapnelEntity;
import com.leafia.contents.machines.powercores.dfc.debris.AbsorberShrapnelEntity.DebrisType;
import com.leafia.contents.gear.utility.fuzzy.FuzzyIdentifierItem;
import com.leafia.contents.network.spk_cable.uninos.SPKNet;
import com.leafia.contents.network.spk_cable.uninos.SPKNode;
import com.leafia.gametest.LeafiaGameTestFixtures.SpkReceiver;
import com.leafia.init.LeafiaBlockEntities;
import com.leafia.init.LeafiaEntities;
import com.leafia.lib.LeafiaRef;
import com.hbm.uninos.UniNodespace;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The addon DFC's components (DFC-3, D-3014): how each goes into its base block only while the overhaul
 * is on, the emitter's beam into a core and into an absorber, the absorber's pay, strain and two outputs,
 * the stabilizer's field and lenses, the injector's fuel rule, the creative booster, the heat exchanger,
 * the designator and identifier clicks, the drops, the shrapnel, and the menus.
 *
 * <p>Every switch flip is undone in {@code finally}; every machine is run inside one
 * body by calling {@code updateEntity()}, which is a tick (D-953), and what it built is removed. The one
 * test that needs a cable's net waits through {@code TickWindow} (D-1262). Numbers are upstream's
 * expressions evaluated with upstream's types, not copied out of a run.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaDFC3Tests {

	private static final String EMPTY = "empty";

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

	/** Places {@code block} with the overhaul on for that one placement, and restores the switch. */
	private static BlockEntity overhauled(GameTestHelper helper, BlockPos rel, Block block) {
		boolean was = LeafiaFeatures.dfcOverhaul;
		LeafiaFeatures.dfcOverhaul = true;
		try {
			helper.setBlock(rel, block);
		} finally {
			LeafiaFeatures.dfcOverhaul = was;
		}
		return helper.getLevel().getBlockEntity(helper.absolutePos(rel));
	}

	private static LeafiaCoreTE core(GameTestHelper helper, BlockPos rel) {
		BlockEntity be = overhauled(helper, rel, ModBlocks.dfc_core.get());
		helper.assertTrue(be instanceof LeafiaCoreTE, "no addon core: " + be);
		return (LeafiaCoreTE) be;
	}

	private static void clearAll(GameTestHelper helper) {
		for(int x = 0; x < 5; x++)
			for(int y = 1; y < 5; y++)
				for(int z = 0; z < 5; z++)
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
		// an overhauled part removed by setBlock drops what it held (D-3031): discard it rather than leave it in the world
		clearDrops(helper, new AABB(helper.absolutePos(BlockPos.ZERO)).expandTowards(5, 5, 5).inflate(2));
	}

	private static AABB area(GameTestHelper helper) {
		return new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(12);
	}

	// ---- the switch ------------------------------------------------------------------------

	@GameTest(template = EMPTY)
	public static void withTheOverhaulOffTheBaseComponentsStayTheBaseMods(GameTestHelper helper) {
		helper.assertTrue(!LeafiaFeatures.dfcOverhaul, "the overhaul must default off");
		Block[] blocks = {ModBlocks.dfc_emitter.get(), ModBlocks.dfc_receiver.get(), ModBlocks.dfc_injector.get(), ModBlocks.dfc_stabilizer.get()};
		Class<?>[] base = {BlockEntityCoreEmitter.class, BlockEntityCoreReceiver.class, BlockEntityCoreInjector.class, BlockEntityCoreStabilizer.class};
		for(int i = 0; i < blocks.length; i++) {
			BlockPos at = new BlockPos(i, 2, 1);
			helper.setBlock(at, blocks[i]);
			BlockEntity be = helper.getLevel().getBlockEntity(helper.absolutePos(at));
			helper.assertTrue(be != null && be.getClass() == base[i], blocks[i] + " made " + be);
		}
		clearAll(helper);
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void withTheOverhaulOnEachBaseComponentGetsTheAddonsSubclass(GameTestHelper helper) {
		Object[][] rows = {
				{ModBlocks.dfc_emitter.get(), LeafiaCoreEmitterTE.class, LeafiaBlockEntities.DFC_EMITTER.get()},
				{ModBlocks.dfc_receiver.get(), LeafiaCoreReceiverTE.class, LeafiaBlockEntities.DFC_RECEIVER.get()},
				{ModBlocks.dfc_injector.get(), LeafiaCoreInjectorTE.class, LeafiaBlockEntities.DFC_INJECTOR.get()},
				{ModBlocks.dfc_stabilizer.get(), LeafiaCoreStabilizerTE.class, LeafiaBlockEntities.DFC_STABILIZER.get()},
		};
		for(int i = 0; i < rows.length; i++) {
			BlockEntity be = overhauled(helper, new BlockPos(i, 2, 1), (Block) rows[i][0]);
			helper.assertTrue(!LeafiaFeatures.dfcOverhaul, "the switch was not restored");
			helper.assertTrue(be != null && be.getClass() == rows[i][1] && be.getType() == rows[i][2], rows[i][0] + " made " + be);
		}
		// The addon's own three: the reinforced absorber is the overhauled absorber's class on the same type.
		helper.setBlock(new BlockPos(0, 2, 3), LeafiaBlocks.dfc_reinforced.get());
		helper.setBlock(new BlockPos(1, 2, 3), LeafiaBlocks.dfc_exchanger.get());
		helper.setBlock(new BlockPos(2, 2, 3), LeafiaBlocks.dfc_cemitter.get());
		helper.assertTrue(helper.getBlockEntity(new BlockPos(0, 2, 3)) instanceof LeafiaCoreReceiverTE r && r.getType() == LeafiaBlockEntities.DFC_RECEIVER.get(), "reinforced");
		helper.assertTrue(helper.getBlockEntity(new BlockPos(1, 2, 3)) instanceof CoreExchangerTE, "exchanger");
		helper.assertTrue(helper.getBlockEntity(new BlockPos(2, 2, 3)) instanceof CoreCEmitterTE, "creative booster");
		clearAll(helper);
		helper.succeed();
	}

	// ---- the emitter -----------------------------------------------------------------------

	private static LeafiaCoreEmitterTE emitterAt(GameTestHelper helper, BlockPos rel, BlockPos targetRel) {
		LeafiaCoreEmitterTE e = (LeafiaCoreEmitterTE) overhauled(helper, rel, ModBlocks.dfc_emitter.get());
		e.leafia$targetPosition(helper.absolutePos(targetRel));
		return e;
	}

	@GameTest(template = EMPTY)
	public static void anEmitterFiresItsSparksIntoTheCoreAsMspkAndCoolsAfter(GameTestHelper helper) {
		// the emitter is left armed and fuelled by the body; a failed assertion must not leave it firing a 50-block beam into its neighbours (D-3042)
		try {
			LeafiaCoreTE core = core(helper, new BlockPos(1, 2, 1));
			LeafiaCoreEmitterTE e = emitterAt(helper, new BlockPos(3, 2, 1), new BlockPos(1, 2, 1));
			e.power = BlockEntityCoreEmitter.maxPower;
			e.watts = 100;
			e.isOn = true;
			e.tank.setTankType(Fluids.CRYOGEL);
			e.tank.setFill(64000);

			e.updateEntity();
			helper.assertTrue(core.getDFCIncomingSpk() == 10000 / 1_000_000d, "one shot of 10,000 SPK is 0.01 MSPK, got " + core.getDFCIncomingSpk());
			helper.assertTrue(e.power == BlockEntityCoreEmitter.maxPower - BlockEntityCoreEmitter.maxPower * 100 / 2000, "it paid 50 million HE, has " + e.power);
			helper.assertTrue(e.prev == 10000 && e.joules == 0 && e.leafia$isActive(), "prev " + e.prev + ", joules " + e.joules);
			helper.assertTrue(e.tank.getFill() == 64000, "the first shot is not yet cooled");
			helper.assertTrue(core.getDFCComponentPositions().contains(e.getBlockPos()), "the beam registers the emitter with the core");

			e.updateEntity();
			helper.assertTrue(e.tank.getFill() == 64000 - 20 && e.output.getFill() == 20, "a tick after a shot costs 20 mB into the output, " + e.tank.getFill() + "/" + e.output.getFill());
			helper.assertTrue(core.getDFCIncomingSpk() == 0.02, "and the second shot adds, got " + core.getDFCIncomingSpk());

			// Perfluoromethyl is a hundred times the cryogel; with neither the emitter melts.
			e.tank.setTankType(Fluids.PERFLUOROMETHYL_COLD);
			e.tank.setFill(2000);
			e.updateEntity();
			helper.assertTrue(e.tank.getFill() == 0 && e.output.getFill() == 2020, "2,000 mB of cold perfluoromethyl, " + e.tank.getFill() + "/" + e.output.getFill());
			e.updateEntity();
			helper.assertTrue(helper.getLevel().getBlockState(e.getBlockPos()).getBlock() == DFCParts.meltdownState().getBlock(),
					"a dry emitter becomes molten component, is " + helper.getLevel().getBlockState(e.getBlockPos()));
		} finally {
			clearAll(helper);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theBeamBurnsWhatStandsInItAndTheCreativeBoostersBurnsToo(GameTestHelper helper) {
		// the emitter is left armed and fuelled by the body; a failed assertion must not leave it firing a 50-block beam into its neighbours (D-3042)
		try {
			LeafiaCoreTE core = core(helper, new BlockPos(1, 2, 1));
			LeafiaCoreEmitterTE e = emitterAt(helper, new BlockPos(3, 2, 1), new BlockPos(1, 2, 1));
			e.power = BlockEntityCoreEmitter.maxPower;
			e.watts = 100;
			e.isOn = true;
			e.tank.setFill(64000);
			Cow cow = EntityType.COW.create(helper.getLevel());
			BlockPos mid = helper.absolutePos(new BlockPos(2, 2, 1));
			cow.moveTo(mid.getX() + 0.5, mid.getY(), mid.getZ() + 0.5);
			helper.getLevel().addFreshEntity(cow);
			try {
				e.updateEntity();
				helper.assertTrue(cow.isOnFire(), "the beam sets what is in it on fire");
				helper.assertTrue(cow.getHealth() < cow.getMaxHealth(), "and burns it for joules * 1e-6, health " + cow.getHealth());
			} finally {
				cow.discard();
			}
			clearAll(helper);

			core = core(helper, new BlockPos(1, 2, 1));
			helper.setBlock(new BlockPos(3, 2, 1), LeafiaBlocks.dfc_cemitter.get());
			CoreCEmitterTE c = (CoreCEmitterTE) helper.getBlockEntity(new BlockPos(3, 2, 1));
			c.leafia$targetPosition(helper.absolutePos(new BlockPos(1, 2, 1)));
			c.isOn = true;
			c.selecting = 1;
			Cow other = EntityType.COW.create(helper.getLevel());
			other.moveTo(mid.getX() + 0.5, mid.getY(), mid.getZ() + 0.5);
			helper.getLevel().addFreshEntity(other);
			try {
				c.updateEntity();
				helper.assertTrue(core.getDFCIncomingSpk() == 20_000_000L / 1_000_000d, "the creative booster's second beam is 20 MSPK, got " + core.getDFCIncomingSpk());
				helper.assertTrue(c.watts == 20_000 && c.power == 0 && c.tank.getFill() == 0, "no power, no coolant; setting " + c.watts);
				helper.assertTrue(other.isOnFire() && !other.isAlive(),
						"its beam ignites and burns for the beam's 20 MSPK x 1e-6 = 20 damage (D-3076; upstream burned for an empty buffer), health " + other.getHealth());
				c.updateEntity();
				helper.assertTrue(helper.getLevel().getBlockState(c.getBlockPos()).is(LeafiaBlocks.dfc_cemitter.get()), "and never melts");
				c.isOn = false;
				c.updateEntity();
				helper.assertTrue(c.watts == 0, "off, its setting is 0");
			} finally {
				other.discard();
			}
		} finally {
			clearAll(helper);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aBeamIntoAnAbsorbersFrontPaysItAndFromBehindBlowsItApart(GameTestHelper helper) {
		// the emitter is left armed and fuelled by the body; a failed assertion must not leave it firing a 50-block beam into its neighbours (D-3042)
		try {
			LeafiaCoreReceiverTE r = (LeafiaCoreReceiverTE) overhauled(helper, new BlockPos(1, 2, 1), ModBlocks.dfc_receiver.get());
			r.leafia$targetPosition(helper.absolutePos(new BlockPos(4, 2, 1))); // aimed back along the beam
			LeafiaCoreEmitterTE e = emitterAt(helper, new BlockPos(3, 2, 1), new BlockPos(1, 2, 1));
			e.power = BlockEntityCoreEmitter.maxPower;
			e.watts = 100;
			e.isOn = true;
			e.tank.setFill(64000);
			e.updateEntity();
			helper.assertTrue(r.joules == 10000L * 100 * 100 / 10000, "joules * 100 * watts / 10,000 SPK, got " + r.joules);

			r.leafia$targetPosition(helper.absolutePos(new BlockPos(0, 2, 1))); // now aimed away
			e.updateEntity();
			helper.assertTrue(helper.getLevel().getBlockState(r.getBlockPos()).isAir() || !(helper.getBlockEntity(new BlockPos(1, 2, 1)) instanceof LeafiaCoreReceiverTE),
					"an absorber struck from behind is blown apart");
			List<AbsorberShrapnelEntity> pieces = helper.getLevel().getEntitiesOfClass(AbsorberShrapnelEntity.class, area(helper));
			try {
				helper.assertTrue(pieces.size() == 8, "eight pieces, got " + pieces.size());
				int[] kinds = new int[DebrisType.values().length];
				for(AbsorberShrapnelEntity p : pieces) kinds[p.getDebrisType().ordinal()]++;
				helper.assertTrue(kinds[DebrisType.BEAM.ordinal()] == 3 && kinds[DebrisType.CORNER.ordinal()] == 2 && kinds[DebrisType.CABLE.ordinal()] == 1
						&& kinds[DebrisType.CORE.ordinal()] == 1 && kinds[DebrisType.FRONT.ordinal()] == 1, "three beams, two corners and one of the rest");
			} finally {
				for(AbsorberShrapnelEntity p : pieces) p.discard();
			}
		} finally {
			clearAll(helper);
		}
		helper.succeed();
	}

	// ---- the absorber ----------------------------------------------------------------------

	@GameTest(template = EMPTY)
	public static void theAbsorberTurnsItsPayIntoPowerAndSendsSpkOutOfItsBack(GameTestHelper helper) {
		LeafiaCoreReceiverTE r = (LeafiaCoreReceiverTE) overhauled(helper, new BlockPos(1, 2, 1), ModBlocks.dfc_receiver.get());
		r.tank.setTankType(Fluids.CRYOGEL);
		r.tank.setFill(64000);
		r.joules = 10;
		r.updateEntity();
		helper.assertTrue(r.power == 10 * 5000L, "5,000 HE a spark, got " + r.power);
		helper.assertTrue(r.syncJoules == 10 && r.joules == 0, "the pay is recorded and spent");
		helper.assertTrue(r.tank.getFill() == 64000 - 20 && r.output.getFill() == 20, "a paid tick costs 20 mB");
		helper.assertTrue(r.leafia$getFront() == Direction.NORTH, "it faces its target, north");

		helper.setBlock(new BlockPos(1, 2, 2), LeafiaGameTestFixtures.spkReceiver);
		SpkReceiver sink = (SpkReceiver) helper.getBlockEntity(new BlockPos(1, 2, 2));
		r.updateEntity();
		helper.assertTrue(sink.spk == 10, "power / 5,000 SPK goes out of the back, got " + sink.spk);
		helper.assertTrue(r.power == 0, "each SPK taken costs 5,000 HE, left " + r.power);
		helper.assertTrue(r.tank.getFill() == 64000 - 20, "an unpaid tick costs no coolant");
		clearAll(helper);
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void thePlainAbsorberStrainsAtAHundredBillionAndTheReinforcedOneDoesNot(GameTestHelper helper) {
		LeafiaCoreReceiverTE r = (LeafiaCoreReceiverTE) overhauled(helper, new BlockPos(1, 2, 1), ModBlocks.dfc_receiver.get());
		r.tank.setFill(64000);
		r.joules = LeafiaCoreReceiverTE.STRAIN_JOULES;
		r.updateEntity();
		helper.assertTrue(r.destructionLevel >= 1 && r.destructionLevel <= 2, "100 billion strains it by 1 or 2, got " + r.destructionLevel);
		r.joules = LeafiaCoreReceiverTE.STRAIN_JOULES - 1;
		int before = r.destructionLevel;
		r.updateEntity();
		helper.assertTrue(r.destructionLevel == before - 1, "below it the strain falls by one, " + before + " -> " + r.destructionLevel);

		helper.setBlock(new BlockPos(3, 2, 1), LeafiaBlocks.dfc_reinforced.get());
		LeafiaCoreReceiverTE reinforced = (LeafiaCoreReceiverTE) helper.getBlockEntity(new BlockPos(3, 2, 1));
		reinforced.tank.setFill(64000);
		reinforced.joules = LeafiaCoreReceiverTE.STRAIN_JOULES * 10;
		reinforced.updateEntity();
		helper.assertTrue(reinforced.destructionLevel == 0, "the reinforced absorber does not strain, got " + reinforced.destructionLevel);
		clearAll(helper);
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theAbsorberNeverSubscribesToACableAndTheEmitterDoes(GameTestHelper helper) {
		if(!LeafiaFeatures.spkCable) {
			helper.succeed();
			return;
		}
		LeafiaCoreEmitterTE e = (LeafiaCoreEmitterTE) overhauled(helper, new BlockPos(1, 2, 1), ModBlocks.dfc_emitter.get());
		helper.setBlock(new BlockPos(2, 2, 1), LeafiaBlocks.spk_cable.get()); // east: not the back (south)
		LeafiaCoreReceiverTE r = (LeafiaCoreReceiverTE) overhauled(helper, new BlockPos(1, 2, 3), ModBlocks.dfc_receiver.get());
		r.leafia$targetPosition(helper.absolutePos(new BlockPos(1, 2, 2))); // aimed north, as its first tick would
		helper.setBlock(new BlockPos(1, 2, 4), LeafiaBlocks.spk_cable.get()); // south: the side it would take SPK on
		helper.assertTrue(r.isInputPreferrable(Direction.SOUTH), "the absorber does take SPK on its south side");
		com.hbm.gametest.TickWindow.runAfterDelay(helper, 8, () -> {
			SPKNode eNode = (SPKNode) UniNodespace.getNode(helper.getLevel(), helper.absolutePos(new BlockPos(2, 2, 1)), SPKNet.THE_NETWORK_PROVIDER);
			SPKNode rNode = (SPKNode) UniNodespace.getNode(helper.getLevel(), helper.absolutePos(new BlockPos(1, 2, 4)), SPKNet.THE_NETWORK_PROVIDER);
			helper.assertTrue(eNode != null && eNode.net != null && rNode != null && rNode.net != null, "the cables have nets");
			helper.assertTrue(eNode.net.isSubscribed(e), "the emitter subscribes to the cable beside it");
			helper.assertTrue(!rNode.net.isSubscribed(r), "the absorber subscribes at its own position and never to the cable (upstream's)");
			clearAll(helper);
			helper.succeed();
		});
	}

	// ---- the stabilizer --------------------------------------------------------------------

	@GameTest(template = EMPTY)
	public static void theStabilizerAddsItsLensesFieldAndEnergyAndPaysTheFifthPower(GameTestHelper helper) {
		LeafiaCoreTE core = core(helper, new BlockPos(1, 2, 1));
		LeafiaCoreStabilizerTE s = (LeafiaCoreStabilizerTE) overhauled(helper, new BlockPos(3, 2, 1), ModBlocks.dfc_stabilizer.get());
		s.leafia$targetPosition(helper.absolutePos(new BlockPos(1, 2, 1)));
		helper.assertTrue(s.getMaxPower() == 10_000_000_000_000L, "a ten-trillion buffer");
		boolean booster = LeafiaItems.ams_focus_booster != null;
		ItemStack lens = booster ? new ItemStack(LeafiaItems.ams_focus_booster.get()) : new ItemStack(ModItems.ams_lens.get());
		s.setItem(0, lens);
		s.watts = 10;
		s.power = 1_000_000_000_000L;
		s.updateEntity();
		float field = LCEItemLens.fieldMod(lens);
		float drain = LCEItemLens.drainMod(lens);
		float energy = LCEItemLens.energyMod(lens);
		long demand = (long) Math.pow(10, 5);
		helper.assertTrue(s.isOn, "it stabilised");
		helper.assertTrue(core.getDFCStabilization() == 0 + field * (10 / 100d), "field " + core.getDFCStabilization());
		helper.assertTrue(core.getDFCStabilizers() == 1, "it counted itself");
		helper.assertTrue(core.getDFCEnergyMod() == (energy >= 1 ? 1.0 + (energy - 1) : 1.0 * energy), "energy " + core.getDFCEnergyMod());
		helper.assertTrue(s.power == 1_000_000_000_000L - (long) (demand * drain), "paid watts^5 * drain, left " + s.power);
		helper.assertTrue(ItemLens.getLensDamage(s.getItem(0)) == 10, "the lens wore by the setting");
		helper.assertTrue(s.hasLens() && s.getLens() == (booster ? IMixinLens.BOOSTER : IMixinLens.STANDARD), "and it knows its lens: " + s.getLens());

		// A second stabilizer adds its field; a limiter multiplies the energy instead.
		if(LeafiaItems.ams_focus_limiter != null) {
			LeafiaCoreStabilizerTE t = (LeafiaCoreStabilizerTE) overhauled(helper, new BlockPos(1, 2, 3), ModBlocks.dfc_stabilizer.get());
			t.leafia$targetPosition(helper.absolutePos(new BlockPos(1, 2, 1)));
			ItemStack limiter = new ItemStack(LeafiaItems.ams_focus_limiter.get());
			t.setItem(0, limiter);
			t.watts = 10;
			t.power = 1_000_000_000_000L;
			double eBefore = core.getDFCEnergyMod();
			double sBefore = core.getDFCStabilization();
			t.updateEntity();
			helper.assertTrue(core.getDFCEnergyMod() == eBefore * LCEItemLens.energyMod(limiter), "a limiter multiplies, " + core.getDFCEnergyMod());
			helper.assertTrue(core.getDFCStabilization() == sBefore + LCEItemLens.fieldMod(limiter) * (10 / 100d), "and the fields add");
			helper.assertTrue(core.getDFCStabilizers() == 2, "two stabilizers");
		}
		clearAll(helper);
		helper.succeed();
	}

	/** The lens names, as the interface spells them. */
	private static final class IMixinLens {
		static final com.leafia.overwrite_contents.interfaces.IMixinTileEntityCoreStabilizer.LensType STANDARD =
				com.leafia.overwrite_contents.interfaces.IMixinTileEntityCoreStabilizer.LensType.STANDARD;
		static final com.leafia.overwrite_contents.interfaces.IMixinTileEntityCoreStabilizer.LensType BOOSTER =
				com.leafia.overwrite_contents.interfaces.IMixinTileEntityCoreStabilizer.LensType.BOOSTER;
	}

	@GameTest(template = EMPTY)
	public static void theStabilizerNeedsALensTheFullCostAndWearsItsLensOut(GameTestHelper helper) {
		LeafiaCoreTE core = core(helper, new BlockPos(1, 2, 1));
		LeafiaCoreStabilizerTE s = (LeafiaCoreStabilizerTE) overhauled(helper, new BlockPos(3, 2, 1), ModBlocks.dfc_stabilizer.get());
		s.leafia$targetPosition(helper.absolutePos(new BlockPos(1, 2, 1)));
		s.watts = 10;
		s.setItem(0, new ItemStack(Items.DIRT));
		s.power = 1_000_000L;
		s.updateEntity();
		helper.assertTrue(!s.isOn && !s.hasLens() && core.getDFCStabilization() == 0, "dirt is not a lens");

		ItemStack lens = new ItemStack(ModItems.ams_lens.get());
		s.setItem(0, lens);
		s.power = 99_999L;
		s.updateEntity();
		helper.assertTrue(!s.isOn && s.power == 99_999L, "10^5 HE is the cost and 99,999 is not enough");

		s.power = 100_000L;
		ItemLens.setLensDamage(s.getItem(0), ((ItemLens) ModItems.ams_lens.get()).maxDamage - 10);
		s.updateEntity();
		helper.assertTrue(s.isOn && s.power == 0, "the base lens is a lens at 1, 1 and 1");
		helper.assertTrue(core.getDFCStabilization() == 1.0F * (10 / 100d) && core.getDFCEnergyMod() == 1, "field 0.1, energy unchanged");
		helper.assertTrue(s.getItem(0).isEmpty(), "worn to its maximum, the lens is gone");
		clearAll(helper);
		helper.succeed();
	}

	// ---- the injector ----------------------------------------------------------------------

	@GameTest(template = EMPTY)
	public static void theInjectorFillsTheCoreAndRefusesTheOtherTanksFuel(GameTestHelper helper) {
		LeafiaCoreTE core = core(helper, new BlockPos(1, 2, 1));
		LeafiaCoreInjectorTE inj = (LeafiaCoreInjectorTE) overhauled(helper, new BlockPos(3, 2, 1), ModBlocks.dfc_injector.get());
		inj.leafia$targetPosition(helper.absolutePos(new BlockPos(1, 2, 1)));
		inj.tanks[0].setTankType(Fluids.DEUTERIUM);
		inj.tanks[0].setFill(1000);
		inj.tanks[1].setTankType(Fluids.TRITIUM);
		inj.tanks[1].setFill(2000);
		inj.updateEntity();
		helper.assertTrue(core.tanks[0].getTankType() == Fluids.DEUTERIUM && core.tanks[0].getFill() == 1000, "deuterium into the core's first tank");
		helper.assertTrue(core.tanks[1].getTankType() == Fluids.TRITIUM && core.tanks[1].getFill() == 2000, "tritium into its second");
		helper.assertTrue(inj.tanks[0].getFill() == 0 && inj.tanks[1].getFill() == 0, "and the injector is empty");

		if(LeafiaItems.fuzzy_identifier != null) {
			ItemStack sameAsB = new ItemStack(LeafiaItems.fuzzy_identifier.get());
			FuzzyIdentifierItem.setType(sameAsB, Fluids.TRITIUM);
			inj.setItem(0, sameAsB);
			inj.updateEntity();
			helper.assertTrue(inj.tanks[0].getTankType() == Fluids.DEUTERIUM && !inj.getItem(0).isEmpty(), "tank A refuses tank B's fuel and keeps the identifier");
			ItemStack other = new ItemStack(LeafiaItems.fuzzy_identifier.get());
			FuzzyIdentifierItem.setType(other, Fluids.HELIUM3);
			inj.setItem(0, other);
			inj.updateEntity();
			helper.assertTrue(inj.tanks[0].getTankType() == Fluids.HELIUM3 && inj.getItem(0).isEmpty() && !inj.getItem(1).isEmpty(),
					"another fuel is read and the identifier moves down");
		}
		clearAll(helper);
		helper.succeed();
	}

	// ---- the creative booster's screen ------------------------------------------------------

	@GameTest(template = EMPTY)
	public static void theCreativeBoosterTakesItsScreensBeamsAndRefusesABadOne(GameTestHelper helper) {
		helper.setBlock(new BlockPos(1, 2, 1), LeafiaBlocks.dfc_cemitter.get());
		CoreCEmitterTE c = (CoreCEmitterTE) helper.getBlockEntity(new BlockPos(1, 2, 1));
		helper.assertTrue(java.util.Arrays.equals(c.joulesT, new long[] {100_000L, 20_000_000L, 100_000_000L, 1_000_000_000L}), "upstream's four beams");
		ServerPlayer player = mockPlayer(helper);
		c.onReceivePacketServer((byte) 0, 2, player);
		helper.assertTrue(c.selecting == 0, "nothing is taken from a player without the screen open");
		player.containerMenu = new CoreCEmitterMenu(1, player.getInventory(), c);
		c.onReceivePacketServer((byte) 0, 2, player);
		helper.assertTrue(c.selecting == 2, "key 0 chooses a beam, got " + c.selecting);
		c.onReceivePacketServer((byte) 0, 4, player);
		helper.assertTrue(c.selecting == 2, "an index past the four is refused (it would crash the next tick)");
		c.onReceivePacketServer((byte) 2, 5L, player);
		helper.assertTrue(c.joulesT[1] == 5L, "key n sets beam n - 1");
		player.containerMenu = player.inventoryMenu;
		clearAll(helper);
		helper.succeed();
	}

	// ---- the heat exchanger ----------------------------------------------------------------

	@GameTest(template = EMPTY)
	public static void theExchangerBoilsCoolantWithTheCoresHeatAtATwentieth(GameTestHelper helper) {
		LeafiaCoreTE core = core(helper, new BlockPos(1, 2, 1));
		helper.setBlock(new BlockPos(3, 2, 1), LeafiaBlocks.dfc_exchanger.get());
		CoreExchangerTE x = (CoreExchangerTE) helper.getBlockEntity(new BlockPos(3, 2, 1));
		x.leafia$targetPosition(helper.absolutePos(new BlockPos(1, 2, 1)));
		CoreExchangerTE.Chain chain = CoreExchangerTE.getBoiledFluid(Fluids.COOLANT, 1);
		helper.assertTrue(chain.steps() == 1 && chain.produced() == Fluids.COOLANT_HOT && chain.in() == 1 && chain.out() == 1, "coolant boils 1:1 into hot coolant");
		CoreExchangerTE.Chain water = CoreExchangerTE.getBoiledFluid(Fluids.WATER, 3);
		helper.assertTrue(water.steps() == 1 && water.produced() == Fluids.STEAM && water.in() == 1 && water.out() == 100,
				"water's chain stops at steam, which has no heating step of its own: " + water);
		x.setInput(Fluids.COOLANT, 1);
		x.input.setFill(10000);
		x.amountToHeat = 100;
		core.setDFCTemperature(1000);
		x.updateEntity();
		int heatAmt = Fluids.COOLANT_HOT.temperature - Fluids.COOLANT.temperature;
		int drain = Math.min((int) ((1000.0 - Fluids.COOLANT_HOT.temperature) / heatAmt * 1000.0), 100);
		helper.assertTrue(x.input.getFill() == 10000 - drain && x.output.getFill() == drain, "boiled " + drain + ": " + x.input.getFill() + "/" + x.output.getFill());
		helper.assertTrue(core.getDFCTemperature() == Math.max(1000 - drain * heatAmt / 1000.0 / 20, 0.0), "the core cools by a twentieth of the heat, " + core.getDFCTemperature());

		core.setDFCTemperature(Fluids.COOLANT_HOT.temperature);
		int in = x.input.getFill();
		x.updateEntity();
		helper.assertTrue(x.input.getFill() == in, "a core no hotter than the output boils nothing");

		// Upstream's setInput emptied the output whenever its fluid was not the INPUT's - always - and the
		// NBT load calls it, so a reload lost the spent coolant (D-3076): now only a changed product empties it.
		int outBefore = x.output.getFill();
		helper.assertTrue(outBefore > 0, "something was boiled");
		x.loadWithComponents(x.saveWithoutMetadata(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
		helper.assertTrue(x.output.getFill() == outBefore && x.input.getFill() == in, "a reload keeps the output (D-3076), output " + x.output.getFill());

		if(LeafiaItems.fuzzy_identifier != null) {
			ItemStack id = new ItemStack(LeafiaItems.fuzzy_identifier.get());
			FuzzyIdentifierItem.setType(id, Fluids.WATER);
			x.setItem(0, id);
			helper.assertTrue(x.input.getTankType() == Fluids.WATER && x.output.getTankType() == Fluids.STEAM && x.input.getFill() == 0,
					"an identifier sets the input and empties a tank of another fluid");
		}
		clearAll(helper);
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theExchangersScreenNeedsItOpenAndFloorsTheNumbers(GameTestHelper helper) {
		helper.setBlock(new BlockPos(1, 2, 1), LeafiaBlocks.dfc_exchanger.get());
		CoreExchangerTE x = (CoreExchangerTE) helper.getBlockEntity(new BlockPos(1, 2, 1));
		ServerPlayer player = mockPlayer(helper);
		x.onReceivePacketServer((byte) 0, 50, player);
		helper.assertTrue(x.amountToHeat == 1, "refused without the screen");
		CoreExchangerMenu menu = new CoreExchangerMenu(1, player.getInventory(), x);
		player.containerMenu = menu;
		helper.assertTrue(x.getListeners().contains(player), "opening the screen listens");
		x.onReceivePacketServer((byte) 0, 50, player);
		x.onReceivePacketServer((byte) 1, -3, player);
		helper.assertTrue(x.amountToHeat == 50 && x.tickDelay == 1, "the amount is taken and the delay floored at 1");
		menu.removed(player);
		helper.assertTrue(!x.getListeners().contains(player), "and closing it stops");
		player.containerMenu = player.inventoryMenu;
		clearAll(helper);
		helper.succeed();
	}

	// ---- the clicks and the drops ----------------------------------------------------------

	@GameTest(template = EMPTY)
	public static void aDesignatorRetargetsAPartAndAnIdentifierSwapsItsCoolant(GameTestHelper helper) {
		LeafiaCoreEmitterTE e = (LeafiaCoreEmitterTE) overhauled(helper, new BlockPos(1, 2, 1), ModBlocks.dfc_emitter.get());
		ServerPlayer player = mockPlayer(helper);
		ItemStack designator = new ItemStack(ModItems.designator.get());
		BlockPos target = helper.absolutePos(new BlockPos(4, 3, 4));
		CompoundTag tag = new CompoundTag();
		tag.putInt("xCoord", target.getX());
		tag.putInt("yCoord", target.getY());
		tag.putInt("zCoord", target.getZ());
		designator.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		helper.assertTrue(LeafiaDFC.useOnPart(helper.getLevel(), e.getBlockPos(), player, designator) != null, "the designator's click is taken");
		helper.assertTrue(e.leafia$getTargetPosition().equals(target), "and retargets the emitter to " + target + ", is " + e.leafia$getTargetPosition());
		CompoundTag self = new CompoundTag();
		self.putInt("xCoord", e.getBlockPos().getX());
		self.putInt("yCoord", e.getBlockPos().getY());
		self.putInt("zCoord", e.getBlockPos().getZ());
		designator.set(DataComponents.CUSTOM_DATA, CustomData.of(self));
		LeafiaDFC.useOnPart(helper.getLevel(), e.getBlockPos(), player, designator);
		helper.assertTrue(e.leafia$getTargetPosition().equals(target), "a part cannot be aimed at itself");
		helper.assertTrue(LeafiaDFC.useOnPart(helper.getLevel(), e.getBlockPos(), player, new ItemStack(ModItems.designator.get())) == null,
				"an empty designator is not this rule's");

		if(LeafiaItems.fuzzy_identifier != null) {
			ItemStack id = new ItemStack(LeafiaItems.fuzzy_identifier.get());
			FuzzyIdentifierItem.setType(id, Fluids.PERFLUOROMETHYL_COLD);
			helper.assertTrue(LeafiaDFC.useOnPart(helper.getLevel(), e.getBlockPos(), player, id) != null, "the identifier's click is taken");
			helper.assertTrue(e.tank.getTankType() == Fluids.PERFLUOROMETHYL_COLD && e.output.getTankType() == Fluids.PERFLUOROMETHYL_HOT, "and swaps both tanks");
			FuzzyIdentifierItem.setType(id, Fluids.WATER);
			helper.assertTrue(LeafiaDFC.useOnPart(helper.getLevel(), e.getBlockPos(), player, id) == null, "any other fluid is not this rule's");
		}
		clearAll(helper);
		helper.succeed();
	}

	/**
	 * {@code MixinItemDesignator}/{@code MixinItemDesignatorRange} (D-3031): a designator used on a block, through the real
	 * {@code ItemStack.useOn} (whose {@code ITEM_AFTER_BLOCK} event the addon answers), stores the block's x, y and z; a
	 * part retargeted with it then aims at that height and not at y = 0; a launch pad gets no height (upstream skips it).
	 * The range designator, through the real {@code ServerPlayerGameMode.useItem} (its {@code RightClickItem} event),
	 * stores the height of the block its 300-block ray hits, and the base's x/z name the same block.
	 */
	@GameTest(template = EMPTY)
	public static void aDesignatorRemembersTheHeightItWasAimedAt(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		BlockPos stoneRel = new BlockPos(4, 3, 4), padRel = new BlockPos(0, 2, 4), floorRel = new BlockPos(2, 1, 0);
		helper.setBlock(stoneRel, Blocks.STONE);
		helper.setBlock(padRel, ModBlocks.launch_pad.get());
		helper.setBlock(floorRel, Blocks.STONE);
		BlockPos stone = helper.absolutePos(stoneRel), pad = helper.absolutePos(padRel), floor = helper.absolutePos(floorRel);
		try {
			ItemStack d = new ItemStack(ModItems.designator.get());
			player.setItemInHand(InteractionHand.MAIN_HAND, d);
			d.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(stone), Direction.UP, stone, false)));
			CompoundTag tag = d.get(DataComponents.CUSTOM_DATA).copyTag();
			helper.assertTrue(tag.getInt("xCoord") == stone.getX() && tag.getInt("yCoord") == stone.getY() && tag.getInt("zCoord") == stone.getZ(),
					"the designator holds " + stone + ": " + tag);

			LeafiaCoreEmitterTE e = (LeafiaCoreEmitterTE) overhauled(helper, new BlockPos(1, 2, 1), ModBlocks.dfc_emitter.get());
			LeafiaDFC.useOnPart(helper.getLevel(), e.getBlockPos(), player, d);
			helper.assertTrue(e.leafia$getTargetPosition().equals(stone), "the emitter now aims at " + stone + ", is " + e.leafia$getTargetPosition());

			ItemStack p = new ItemStack(ModItems.designator.get());
			player.setItemInHand(InteractionHand.MAIN_HAND, p);
			p.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pad), Direction.UP, pad, false)));
			helper.assertTrue(!p.has(DataComponents.CUSTOM_DATA) || !p.get(DataComponents.CUSTOM_DATA).copyTag().contains("yCoord"),
					"a launch pad gets no height");

			ItemStack r = new ItemStack(ModItems.designator_range.get());
			player.setItemInHand(InteractionHand.MAIN_HAND, r);
			player.moveTo(floor.getX() + 0.5, floor.getY() + 3, floor.getZ() + 0.5, 0F, 90F);
			player.gameMode.useItem(player, helper.getLevel(), r, InteractionHand.MAIN_HAND);
			CompoundTag rt = r.get(DataComponents.CUSTOM_DATA).copyTag();
			helper.assertTrue(rt.getInt("xCoord") == floor.getX() && rt.getInt("yCoord") == floor.getY() && rt.getInt("zCoord") == floor.getZ(),
					"the range designator, looking straight down, holds " + floor + ": " + rt);
		} finally {
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			helper.setBlock(stoneRel, Blocks.AIR);
			helper.setBlock(padRel, Blocks.AIR);
			helper.setBlock(floorRel, Blocks.AIR);
			player.discard();
		}
		clearAll(helper);
		helper.succeed();
	}

	/**
	 * Removing an overhauled part drops its inventory whatever removes it - here {@code setBlock}, the case no event
	 * reaches (D-3014 hand-off 2, closed by M10's removal seam, D-3031) - and a base part still drops nothing, as the
	 * base's upstream does.
	 */
	@GameTest(template = EMPTY)
	public static void aRemovedPartDropsItsInventoryByAnyCause(GameTestHelper helper) {
		BlockPos rel = new BlockPos(2, 2, 2);
		LeafiaCoreInjectorTE inj = (LeafiaCoreInjectorTE) overhauled(helper, rel, ModBlocks.dfc_injector.get());
		inj.setItem(1, new ItemStack(Items.DIAMOND, 3));
		AABB around = new AABB(helper.absolutePos(rel)).inflate(2);
		try {
			helper.setBlock(rel, Blocks.AIR);
			helper.assertTrue(diamonds(helper, around) == 3, "the three diamonds are on the ground, found " + diamonds(helper, around));
			clearDrops(helper, around);
			helper.setBlock(rel, ModBlocks.dfc_injector.get());
			BlockEntity base = helper.getBlockEntity(rel);
			helper.assertTrue(!(base instanceof LeafiaCoreInjectorTE) && base instanceof net.minecraft.world.Container, "a base injector: " + base);
			((net.minecraft.world.Container) base).setItem(1, new ItemStack(Items.DIAMOND, 3));
			helper.setBlock(rel, Blocks.AIR);
			helper.assertTrue(diamonds(helper, around) == 0, "a base part drops nothing, found " + diamonds(helper, around));
		} finally {
			clearDrops(helper, around);
		}
		clearAll(helper);
		helper.succeed();
	}

	/**
	 * {@code MixinCoreCore}: the core has no collision with the overhaul on and the base's full cube with it off, both
	 * ways round in one body; the outline stays the cube (D-3031).
	 */
	@GameTest(template = EMPTY)
	public static void theCoreIsWalkThroughOnlyWithTheOverhaulOn(GameTestHelper helper) {
		BlockPos rel = new BlockPos(2, 2, 2);
		helper.setBlock(rel, ModBlocks.dfc_core.get());
		BlockPos abs = helper.absolutePos(rel);
		var state = helper.getLevel().getBlockState(abs);
		boolean was = LeafiaFeatures.dfcOverhaul;
		try {
			LeafiaFeatures.dfcOverhaul = true;
			helper.assertTrue(state.getCollisionShape(helper.getLevel(), abs).isEmpty(), "on: no collision");
			helper.assertTrue(!state.getShape(helper.getLevel(), abs).isEmpty(), "on: the outline is still there");
			LeafiaFeatures.dfcOverhaul = false;
			helper.assertTrue(state.getCollisionShape(helper.getLevel(), abs).bounds().equals(new AABB(0, 0, 0, 1, 1, 1)), "off: the full cube");
			LeafiaFeatures.dfcOverhaul = true;
			helper.assertTrue(state.getCollisionShape(helper.getLevel(), abs).isEmpty(), "on again: none");
		} finally {
			LeafiaFeatures.dfcOverhaul = was;
		}
		helper.setBlock(rel, Blocks.AIR);
		helper.succeed();
	}

	private static int diamonds(GameTestHelper helper, AABB box) {
		int count = 0;
		for(ItemEntity d : helper.getLevel().getEntitiesOfClass(ItemEntity.class, box)) if(d.getItem().is(Items.DIAMOND)) count += d.getItem().getCount();
		return count;
	}

	private static void clearDrops(GameTestHelper helper, AABB box) {
		for(ItemEntity d : helper.getLevel().getEntitiesOfClass(ItemEntity.class, box)) d.discard();
	}

	// ---- the shrapnel and the menus ----------------------------------------------------------

	@GameTest(template = EMPTY)
	public static void theShrapnelIsUpstreamsAndPicksUpAsItsPiece(GameTestHelper helper) {
		AbsorberShrapnelEntity e = new AbsorberShrapnelEntity(helper.getLevel(), 0, 0, 0, DebrisType.FRONT);
		helper.assertTrue(e.getType() == LeafiaEntities.DFC_SHRAPNEL.get(), "its own type");
		helper.assertTrue(e.getDimensions(Pose.STANDING).width() == 0.45F && e.getDimensions(Pose.STANDING).height() == 0.3F, "0.45 by 0.3");
		helper.assertTrue(e.getLifetime() == 24 * 3600 * 20 && !e.destroysBlocks(), "a day's life and it smashes nothing");
		helper.assertTrue(e.getDebrisType() == DebrisType.FRONT, "its kind");
		String[] names = {"dfcsh_cable", "dfcsh_core", "dfcsh_corner", "dfcsh_front", "dfcsh_beam"};
		for(DebrisType type : DebrisType.values()) {
			ItemStack pickup = AbsorberShrapnelEntity.pickupOf(type);
			if(!LeafiaFeatures.resourceItems) continue;
			helper.assertTrue(!pickup.isEmpty() && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(pickup.getItem()).getPath().equals(names[type.ordinal()]),
					type + " picks up as " + pickup);
		}
		e.discard();
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theComponentMenusAreUpstreams(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		LeafiaCoreInjectorTE inj = (LeafiaCoreInjectorTE) overhauled(helper, new BlockPos(1, 2, 1), ModBlocks.dfc_injector.get());
		CoreInjectorMenu im = (CoreInjectorMenu) inj.createMenu(1, player.getInventory(), player);
		int[][] islots = {{15, 16}, {15, 52}, {146, 17}, {146, 53}};
		for(int i = 0; i < 4; i++)
			helper.assertTrue(im.slots.get(i).x == islots[i][0] && im.slots.get(i).y == islots[i][1], "injector slot " + i);
		helper.assertTrue(im.slots.get(4).y == 84 && im.slots.get(4 + 27).y == 142, "the injector's inventory");

		LeafiaCoreReceiverTE r = (LeafiaCoreReceiverTE) overhauled(helper, new BlockPos(3, 2, 1), ModBlocks.dfc_receiver.get());
		CoreReceiverMenu rm = (CoreReceiverMenu) r.createMenu(1, player.getInventory(), player);
		helper.assertTrue(rm.slots.size() == 36 && rm.slots.get(0).x == 22 && rm.slots.get(0).y == 86 && rm.slots.get(27).y == 144, "the absorber's menu is the inventory, 14 right and 2 down");

		helper.setBlock(new BlockPos(1, 2, 3), LeafiaBlocks.dfc_cemitter.get());
		CoreCEmitterMenu cm = (CoreCEmitterMenu) ((CoreCEmitterTE) helper.getBlockEntity(new BlockPos(1, 2, 3))).createMenu(1, player.getInventory(), player);
		helper.assertTrue(cm.slots.size() == 36 && cm.slots.get(0).y == 88 && cm.slots.get(27).y == 146, "the booster's inventory at 88 and 146");

		helper.setBlock(new BlockPos(3, 2, 3), LeafiaBlocks.dfc_exchanger.get());
		CoreExchangerMenu xm = (CoreExchangerMenu) ((CoreExchangerTE) helper.getBlockEntity(new BlockPos(3, 2, 3))).createMenu(1, player.getInventory(), player);
		helper.assertTrue(xm.slots.get(0).x == 62 && xm.slots.get(0).y == 67 && xm.slots.get(1).y == 102 && xm.slots.get(28).y == 160, "the exchanger's slot and inventory");
		xm.removed(player);

		// The base mod's emitter and stabilizer screens stay theirs, as upstream keeps NTM-CE's.
		LeafiaCoreEmitterTE e = (LeafiaCoreEmitterTE) overhauled(helper, new BlockPos(0, 2, 0), ModBlocks.dfc_emitter.get());
		helper.assertTrue(e.createMenu(1, player.getInventory(), player) instanceof com.hbm.inventory.container.ContainerCoreEmitter, "the emitter keeps the base screen");

		// The screens' two read-outs.
		helper.assertTrue(LeafiaCoreReceiverTE.joulesColumn(LeafiaCoreReceiverTE.STRAIN_JOULES / 2) == 26 && LeafiaCoreReceiverTE.joulesColumn(Long.MAX_VALUE) == 52,
				"the absorber's power column is the pay over 100 billion, of 52");
		LeafiaCoreTE core = core(helper, new BlockPos(4, 2, 4));
		helper.assertTrue(LeafiaCoreInjectorTE.screenPicture(core) == -1, "an empty cold core: no picture");
		core.tanks[0].setFill(1);
		core.tanks[1].setFill(1);
		helper.assertTrue(LeafiaCoreInjectorTE.screenPicture(core) == 0, "fuelled");
		core.setDFCTemperature(100);
		helper.assertTrue(LeafiaCoreInjectorTE.screenPicture(core) == 1, "100 degrees");
		core.setDFCTemperature(1500);
		helper.assertTrue(LeafiaCoreInjectorTE.screenPicture(core) == 2, "1,500 degrees");
		clearAll(helper);
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theAbsorbersScreenLevelIsClampedAndNeedsTheScreen(GameTestHelper helper) {
		LeafiaCoreReceiverTE r = (LeafiaCoreReceiverTE) overhauled(helper, new BlockPos(1, 2, 1), ModBlocks.dfc_receiver.get());
		ServerPlayer player = mockPlayer(helper);
		helper.assertTrue(r.leafia$getLevel() == 1, "a new absorber is at full level");
		r.onReceivePacketServer((byte) 0, 0.25, player);
		helper.assertTrue(r.leafia$getLevel() == 1, "refused without its screen");
		player.containerMenu = new CoreReceiverMenu(1, player.getInventory(), r);
		r.onReceivePacketServer((byte) 0, 0.25, player);
		helper.assertTrue(r.leafia$getLevel() == 0.25, "taken with it");
		r.onReceivePacketServer((byte) 0, 7.0, player);
		helper.assertTrue(r.leafia$getLevel() == 1, "and clamped to 1");
		player.containerMenu = player.inventoryMenu;
		clearAll(helper);
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theOverhauledAbsorberJoinsTheCoresAbsorbersAndTakesItsShare(GameTestHelper helper) {
		LeafiaCoreTE core = core(helper, new BlockPos(1, 2, 1));
		LeafiaCoreReceiverTE r = (LeafiaCoreReceiverTE) overhauled(helper, new BlockPos(3, 2, 1), ModBlocks.dfc_receiver.get());
		r.leafia$targetPosition(helper.absolutePos(new BlockPos(1, 2, 1)));
		r.updateEntity();
		helper.assertTrue(core.getDFCAbsorbers().contains(r) && r.leafia$getCore() == core, "it registered with the core it aims at");
		helper.assertTrue(r.isInputPreferrable(Direction.EAST) && !r.isInputPreferrable(Direction.WEST),
				"and takes a beam only on the side away from the core");
		clearAll(helper);
		helper.succeed();
	}
}
