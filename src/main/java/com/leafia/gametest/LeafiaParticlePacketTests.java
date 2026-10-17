package com.leafia.gametest;

import com.leafia.contents.potion.LeafiaPotion;
import com.leafia.dev.container_utility.LeafiaItemTransferable;
import com.leafia.dev.optimization.LeafiaParticlePacket;
import com.leafia.dev.optimization.LeafiaParticlePacket.AlkaliFire;
import com.leafia.dev.optimization.LeafiaParticlePacket.DFCBlastParticle;
import com.leafia.dev.optimization.LeafiaParticlePacket.DFCNukeParticle;
import com.leafia.dev.optimization.LeafiaParticlePacket.DFCShockParticleEditionTM;
import com.leafia.dev.optimization.LeafiaParticlePacket.FiaSpark;
import com.leafia.dev.optimization.LeafiaParticlePacket.FlashParticle;
import com.leafia.dev.optimization.LeafiaParticlePacket.LeafiaParticle;
import com.leafia.dev.optimization.LeafiaParticlePacket.MTSteamParticle;
import com.leafia.dev.optimization.LeafiaParticlePacket.RBMKJetParticle;
import com.leafia.dev.optimization.LeafiaParticlePacket.SmokeShockwaveParticle;
import com.leafia.dev.optimization.LeafiaParticlePacket.Sweat;
import com.leafia.dev.optimization.LeafiaParticlePacket.VanillaExt;
import com.leafia.dev.optimization.bitbyte.LeafiaBuf;
import com.leafia.lib.LeafiaRef;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The addon's particle spawner (D-3000) and its shift-click helper, as far as a server can see them.
 *
 * <p>What is checked is the <b>wire</b>: every kind survives a trip through the real
 * {@code STREAM_CODEC}, the header is upstream's layout read straight off the bytes, and a
 * receiver whose kind count differs refuses rather than misreads. Drawing a particle is a client
 * act and is checked in a client run instead (D-3000). The tests deliberately never call
 * {@code emit(CompoundTag)} or anything else that links a client class.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaParticlePacketTests {

	private static final String EMPTY = "empty";

	private static final Vec3 POS = new Vec3(10.5D, 64.25D, -3.125D);
	private static final Vec3 MOTION = new Vec3(0.5D, -0.25D, 0.125D);

	// ---- helpers -----------------------------------------------------------------------------

	private static LeafiaParticlePacket roundTrip(GameTestHelper helper, LeafiaParticle particle) {
		return roundTrip(helper, particle, POS, MOTION);
	}

	private static LeafiaParticlePacket roundTrip(GameTestHelper helper, LeafiaParticle particle, Vec3 pos, Vec3 motion) {
		RegistryFriendlyByteBuf wire = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
		LeafiaParticlePacket.STREAM_CODEC.encode(wire, particle.packet(pos, motion));
		LeafiaParticlePacket decoded = LeafiaParticlePacket.STREAM_CODEC.decode(wire);
		helper.assertTrue(wire.readableBytes() == 0, "the codec must consume exactly what it wrote");
		return decoded;
	}

	private static void same(GameTestHelper helper, double a, double b, String what) {
		helper.assertTrue(a == b, what + ": " + a + " != " + b);
	}

	private static void near(GameTestHelper helper, double a, double b, String what) {
		helper.assertTrue(Math.abs(a - b) < 1.0E-9, what + ": " + a + " != " + b);
	}

	/** The wire's two parts - the bit length and the bytes - read off an encoded packet. */
	private record Raw(int bits, byte[] data) {
		LeafiaBuf reader() {
			LeafiaBuf buf = new LeafiaBuf(Unpooled.buffer());
			buf.wrapOwned(data);
			buf.writerIndex = bits;
			return buf;
		}
	}

	private static Raw rawOf(GameTestHelper helper, LeafiaParticle particle) {
		RegistryFriendlyByteBuf wire = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
		LeafiaParticlePacket.STREAM_CODEC.encode(wire, particle.packet(POS, MOTION));
		int bits = ByteBufCodecs.VAR_INT.decode(wire);
		byte[] data = ByteBufCodecs.byteArray(LeafiaParticlePacket.MAX_PAYLOAD).decode(wire);
		return new Raw(bits, data);
	}

	// ---- the registry ------------------------------------------------------------------------

	/**
	 * The ids are pinned: an id is an index, so reordering the registry decodes every particle as the
	 * kind next to it, and nothing fails - it just draws the wrong thing.
	 */
	@GameTest(template = EMPTY)
	public static void theRegistryIdsAreAppendOnly(GameTestHelper helper) {

		LeafiaParticle[] kinds = { VanillaExt.Flame(), new Sweat(helper.spawn(EntityType.COW, new BlockPos(1, 1, 1)), Blocks.STONE.defaultBlockState(), 1),
				new AlkaliFire(3), new DFCBlastParticle(1, 1, 1, 1), new FlashParticle(), new SmokeShockwaveParticle(),
				new RBMKJetParticle(1), new MTSteamParticle(1), new DFCNukeParticle(),
				new LeafiaParticlePacket.PinkRBMK(), new LeafiaParticlePacket.JumpingRBMKParticle(BlockPos.ZERO),
				new FiaSpark(), new DFCShockParticleEditionTM(), new LeafiaParticlePacket.TauSpark() };

		for(int i = 0; i < kinds.length; i++) {
			helper.assertTrue(LeafiaParticlePacket.idOf(kinds[i]) == i,
					kinds[i].getClass().getSimpleName() + " must keep id " + i + ", has " + LeafiaParticlePacket.idOf(kinds[i]));
		}

		helper.assertTrue(LeafiaParticlePacket.registrySize() == kinds.length,
				"fourteen kinds are registered, all of upstream's; a fifteenth must be appended and this test extended: " + LeafiaParticlePacket.registrySize());

		helper.succeed();
	}

	/** A kind that was never registered cannot be sent, and says so at the source. */
	@GameTest(template = EMPTY)
	public static void anUnregisteredKindRefusesToEncode(GameTestHelper helper) {

		LeafiaParticle stranger = new LeafiaParticle() {
			@Override
			protected LeafiaParticle fromBits(LeafiaBuf buf, CompoundTag nbt) { return this; }

			@Override
			protected void toBits(LeafiaBuf buf) { }
		};

		helper.assertTrue(LeafiaParticlePacket.idOf(stranger) == -1, "an anonymous kind has no id");

		boolean refused = false;
		try {
			RegistryFriendlyByteBuf wire = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
			LeafiaParticlePacket.STREAM_CODEC.encode(wire, stranger.packet(POS, MOTION));
		} catch(IllegalStateException e) {
			refused = true;
		}

		helper.assertTrue(refused, "encoding an unregistered kind must throw rather than send a wrong id");
		helper.succeed();
	}

	// ---- the header --------------------------------------------------------------------------

	/**
	 * Upstream's layout, read straight off the bytes so this fails even if the reader and writer
	 * change together: position as doubles, motion as <em>floats</em>, then the kind count and the id.
	 */
	@GameTest(template = EMPTY)
	public static void theHeaderIsUpstreamsLayout(GameTestHelper helper) {

		Raw raw = rawOf(helper, new FlashParticle(5, 10, 10));
		LeafiaBuf buf = raw.reader();

		same(helper, buf.readDouble(), POS.x, "posX");
		same(helper, buf.readDouble(), POS.y, "posY");
		same(helper, buf.readDouble(), POS.z, "posZ");
		same(helper, buf.readFloat(), (float) MOTION.x, "mX");
		same(helper, buf.readFloat(), (float) MOTION.y, "mY");
		same(helper, buf.readFloat(), (float) MOTION.z, "mZ");

		int supports = buf.readUnsignedByte();
		int id = buf.readUnsignedByte();

		helper.assertTrue(supports == LeafiaParticlePacket.registrySize(), "the count byte is the registry size, got " + supports);
		helper.assertTrue(id == 4, "FlashParticle is id 4, got " + id);

		// And the body that follows is the flash's: two shorts and a float.
		helper.assertTrue(buf.readShort() == 5 && buf.readShort() == 10 && buf.readFloat() == 10F, "the flash's body follows the header");
		helper.assertTrue(buf.readableBits() == 0, "and nothing else: " + buf.readableBits() + " bits left");

		helper.succeed();
	}

	/** A particle's motion goes through a float on the wire, as upstream's does: 0.1 comes back as 0.1F. */
	@GameTest(template = EMPTY)
	public static void motionIsNarrowedThroughAFloat(GameTestHelper helper) {

		LeafiaParticlePacket packet = roundTrip(helper, new FlashParticle(), POS, new Vec3(0.1D, 0.2D, -0.3D));
		CompoundTag nbt = packet.nbt();

		same(helper, nbt.getDouble("mX"), (double) 0.1F, "mX");
		same(helper, nbt.getDouble("mY"), (double) 0.2F, "mY");
		same(helper, nbt.getDouble("mZ"), (double) -0.3F, "mZ");
		helper.assertTrue(nbt.getDouble("mX") != 0.1D, "and so is not the double that was sent");

		// The position is not narrowed.
		same(helper, nbt.getDouble("posX"), POS.x, "posX");
		same(helper, nbt.getDouble("posZ"), POS.z, "posZ");

		helper.succeed();
	}

	// ---- one round trip per kind -------------------------------------------------------------

	@GameTest(template = EMPTY)
	public static void everyVanillaExtModeSurvivesAndNamesItsEffect(GameTestHelper helper) {

		VanillaExt[] modes = { VanillaExt.Flame(), VanillaExt.Smoke(), VanillaExt.Volcano(), VanillaExt.Cloud(), VanillaExt.RedDust(),
				VanillaExt.BlueDust(), VanillaExt.GreenDust(), VanillaExt.LargeExplode(5, 4F), VanillaExt.TownAura(),
				VanillaExt.BlockDust(Blocks.REDSTONE_BLOCK.defaultBlockState()), VanillaExt.Lava() };

		String[] names = { "flame", "smoke", "volcano", "cloud", "reddust", "bluedust", "greendust", "largeexplode", "townaura", "blockdust", "lava" };

		for(int i = 0; i < modes.length; i++) {
			LeafiaParticlePacket packet = roundTrip(helper, modes[i]);
			helper.assertTrue(!packet.mismatched(), "mode " + i + " must decode");
			helper.assertTrue(packet.particle() instanceof VanillaExt v && v.mode == i, "mode " + i + " must come back as mode " + i);

			CompoundTag nbt = packet.nbt();
			helper.assertTrue("vanillaExt".equals(nbt.getString("type")), "type for mode " + i);
			helper.assertTrue(names[i].equals(nbt.getString("mode")), "mode string for " + i + ": " + nbt.getString("mode"));
		}

		// The two modes with a body.
		CompoundTag large = roundTrip(helper, VanillaExt.LargeExplode(5, 4F)).nbt();
		helper.assertTrue(large.getByte("count") == 5, "largeexplode count " + large.getByte("count"));
		same(helper, large.getFloat("size"), 4F, "largeexplode size");

		int redstoneState = net.minecraft.world.level.block.Block.getId(Blocks.REDSTONE_BLOCK.defaultBlockState());
		CompoundTag dust = roundTrip(helper, VanillaExt.BlockDust(Blocks.REDSTONE_BLOCK.defaultBlockState())).nbt();
		helper.assertTrue(dust.getInt("block") == redstoneState, "blockdust names the state id " + redstoneState + ", got " + dust.getInt("block"));

		helper.succeed();
	}

	/** A mode costs four bits, which is the point of the bit-packed buffer: nine for the whole simple mode. */
	@GameTest(template = EMPTY)
	public static void aSimpleVanillaExtModeIsFourBitsOfBody(GameTestHelper helper) {

		Raw header = rawOf(helper, VanillaExt.Flame());

		// 3 doubles + 3 floats + 2 bytes = 192 + 96 + 16 = 304 bits, then the 4-bit mode.
		helper.assertTrue(header.bits() == 304 + 4, "a flame is 308 bits, got " + header.bits());

		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void sweatCarriesTheEntityTheStateAndTheCount(GameTestHelper helper) {

		Cow cow = helper.spawn(EntityType.COW, new BlockPos(1, 1, 1));
		int state = net.minecraft.world.level.block.Block.getId(Blocks.REDSTONE_BLOCK.defaultBlockState());

		LeafiaParticlePacket packet = roundTrip(helper, new Sweat(cow, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3));
		Sweat sweat = (Sweat) packet.particle();

		helper.assertTrue(sweat.entityId == cow.getId() && sweat.stateId == state && sweat.count == 3, "fields");

		CompoundTag nbt = packet.nbt();
		helper.assertTrue("sweat".equals(nbt.getString("type")), "the base mod's effect type");
		helper.assertTrue(nbt.getInt("entity") == cow.getId(), "entity key");
		helper.assertTrue(nbt.getInt("block") == state, "block key is the state id the base creator reads");
		helper.assertTrue(nbt.getInt("count") == 3, "count key");

		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void alkaliFireKeepsItsPeriodAsASignedByte(GameTestHelper helper) {

		for(int period : new int[] { 1, 2, 3, 4, 100 }) {
			AlkaliFire read = (AlkaliFire) roundTrip(helper, new AlkaliFire(period)).particle();
			helper.assertTrue(read.period == period, "period " + period + " came back as " + read.period);
		}

		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theDfcBlastKeepsItsColourAndAmount(GameTestHelper helper) {

		DFCBlastParticle read = (DFCBlastParticle) roundTrip(helper, new DFCBlastParticle(0.25F, 0.5F, 0.75F, 9)).particle();

		same(helper, read.red, 0.25F, "red");
		same(helper, read.green, 0.5F, "green");
		same(helper, read.blue, 0.75F, "blue");
		helper.assertTrue(read.amount == 9, "amount " + read.amount);
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theFlashKeepsItsTimesAndScale(GameTestHelper helper) {

		FlashParticle read = (FlashParticle) roundTrip(helper, new FlashParticle(7, 123, 42.5F)).particle();

		helper.assertTrue(read.ticksIn == 7 && read.ticksOut == 123, "ticks " + read.ticksIn + "/" + read.ticksOut);
		same(helper, read.scale, 42.5F, "scale");

		// Upstream's defaults, which a sender that sets nothing gets.
		FlashParticle defaults = new FlashParticle();
		helper.assertTrue(defaults.ticksIn == 5 && defaults.ticksOut == 10 && defaults.scale == 10F, "defaults are 5, 10, 10");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theSmokeShockwaveKeepsAllFourNumbers(GameTestHelper helper) {

		SmokeShockwaveParticle sent = new SmokeShockwaveParticle();
		helper.assertTrue(sent.increment == 3 && sent.particleScale == 5 && sent.maxRadius == 200 && sent.particleLifetime == 12,
				"upstream's defaults are 3, 5, 200, 12");

		sent.increment = 1.0D / 3.0D; // a double that a float would lose
		sent.particleScale = 7.5F;
		sent.maxRadius = 1234.5678D;
		sent.particleLifetime = 99;

		SmokeShockwaveParticle read = (SmokeShockwaveParticle) roundTrip(helper, sent).particle();

		same(helper, read.increment, 1.0D / 3.0D, "increment (a full double)");
		same(helper, read.particleScale, 7.5F, "particleScale");
		same(helper, read.maxRadius, 1234.5678D, "maxRadius (a full double)");
		helper.assertTrue(read.particleLifetime == 99, "lifetime " + read.particleLifetime);
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theRbmkJetKeepsItsAgeAsASignedByte(GameTestHelper helper) {

		for(int age : new int[] { 0, 20, 127, -1 }) {
			RBMKJetParticle read = (RBMKJetParticle) roundTrip(helper, new RBMKJetParticle(age)).particle();
			helper.assertTrue(read.maxAge == age, "age " + age + " came back as " + read.maxAge);
		}

		helper.succeed();
	}

	/**
	 * Upstream's decoder returned the prototype's distance and never read the int it was sent, so a
	 * client always drew a travel distance of zero. Corrected (D-3000); this pins the correction.
	 */
	@GameTest(template = EMPTY)
	public static void theSteamPuffReadsItsDistanceFromTheWire(GameTestHelper helper) {

		MTSteamParticle read = (MTSteamParticle) roundTrip(helper, new MTSteamParticle(37)).particle();

		helper.assertTrue(read.travelDistance == 37, "distance 37 came back as " + read.travelDistance);
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theDfcNukeKeepsItsThreeNumbers(GameTestHelper helper) {

		DFCNukeParticle sent = new DFCNukeParticle();
		helper.assertTrue(sent.radius == 250 && sent.maxAge == 60 && sent.flashTime == 50, "upstream's defaults are 250, 60, 50");

		sent.radius = 1000;
		sent.maxAge = 400;
		sent.flashTime = 123;

		DFCNukeParticle read = (DFCNukeParticle) roundTrip(helper, sent).particle();
		helper.assertTrue(read.radius == 1000 && read.maxAge == 400 && read.flashTime == 123,
				read.radius + "/" + read.maxAge + "/" + read.flashTime);
		helper.succeed();
	}

	// ---- the protocol check ------------------------------------------------------------------

	private static LeafiaParticlePacket decodeHand(GameTestHelper helper, int supports, int id) {

		LeafiaBuf buf = new LeafiaBuf(Unpooled.buffer());
		buf.writeDouble(1D).writeDouble(2D).writeDouble(3D);
		buf.writeFloat(0F).writeFloat(0F).writeFloat(0F);
		buf.writeByte(supports);
		buf.writeByte(id);

		int length = buf.payloadLength();
		byte[] data = new byte[length];
		System.arraycopy(buf.bytes, buf.payloadOffset(), data, 0, length);

		RegistryFriendlyByteBuf wire = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
		ByteBufCodecs.VAR_INT.encode(wire, buf.writerIndex);
		ByteBufCodecs.byteArray(LeafiaParticlePacket.MAX_PAYLOAD).encode(wire, data);
		return LeafiaParticlePacket.STREAM_CODEC.decode(wire);
	}

	/** A sender with a different number of kinds is refused whole - its ids cannot be trusted. */
	@GameTest(template = EMPTY)
	public static void aDifferentKindCountIsAProtocolMismatch(GameTestHelper helper) {

		int ours = LeafiaParticlePacket.registrySize();

		LeafiaParticlePacket more = decodeHand(helper, ours + 1, 0);
		helper.assertTrue(more.mismatched() && more.nbt() == null && more.particle() == null, "a longer registry is refused");
		helper.assertTrue(more.supports() == ours + 1, "and the count it claimed is kept for the message: " + more.supports());

		LeafiaParticlePacket fewer = decodeHand(helper, ours - 1, 0);
		helper.assertTrue(fewer.mismatched(), "a shorter registry is refused too");

		LeafiaParticlePacket same = decodeHand(helper, ours, 4);
		helper.assertTrue(!same.mismatched(), "the same count is accepted");

		helper.succeed();
	}

	/** An id past the end of the registry cannot be a kind, whatever the count claims. */
	@GameTest(template = EMPTY)
	public static void anIdPastTheRegistryIsRefusedNotThrown(GameTestHelper helper) {

		LeafiaParticlePacket packet = decodeHand(helper, LeafiaParticlePacket.registrySize(), LeafiaParticlePacket.registrySize());
		helper.assertTrue(packet.mismatched(), "an out-of-range id must be refused, not index past the registry");
		helper.succeed();
	}

	/**
	 * A mock server player that is in the level but not on the player list. A player left on the list
	 * is sent the wind every tick by {@code Wind.updateWorld}, which has no channel to send it on and
	 * takes the whole server down (the advisor tests detach theirs for the same reason).
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

	// ---- sending -----------------------------------------------------------------------------

	/**
	 * A player with no real connection to the channel is skipped. Sending to one throws out of the
	 * caller - it took down a whole GameTest server once already (D-631) - so a particle spawner that
	 * is fired from a block entity's tick must not.
	 */
	@GameTest(template = EMPTY)
	public static void aPlayerWithoutTheChannelIsSkippedNotThrownAt(GameTestHelper helper) {

		ServerPlayer player = mockPlayer(helper);
		helper.assertTrue(helper.getLevel().players().contains(player), "the mock is in the level, so the loop sees it");
		Vec3 at = player.position();

		new FlashParticle().emit(helper.getLevel(), at, Vec3.ZERO);
		new DFCNukeParticle().emit(helper.getLevel(), at, Vec3.ZERO, 1.0D);

		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theDefaultRangeIsUpstreams340(GameTestHelper helper) {

		same(helper, new FlashParticle().getDefaultRange(), 340D, "range");
		same(helper, LeafiaParticlePacket.DEFAULT_RANGE, 340D, "constant");
		helper.succeed();
	}

	// ---- damaged skin's sweat -----------------------------------------------------------------

	/** One in 50, 30, 10, and then 10 for every level from III up: upstream's `50 - level * 20` with its cap. */
	@GameTest(template = EMPTY)
	public static void sweatOddsAreUpstreams(GameTestHelper helper) {

		helper.assertTrue(LeafiaPotion.sweatBound(0) == 50, "level I: " + LeafiaPotion.sweatBound(0));
		helper.assertTrue(LeafiaPotion.sweatBound(1) == 30, "level II: " + LeafiaPotion.sweatBound(1));
		helper.assertTrue(LeafiaPotion.sweatBound(2) == 10, "level III: " + LeafiaPotion.sweatBound(2));
		helper.assertTrue(LeafiaPotion.sweatBound(3) == 10, "capped, never 50 - 60 = -10, which nextInt would throw on: " + LeafiaPotion.sweatBound(3));
		helper.assertTrue(LeafiaPotion.sweatBound(40) == 10, "capped far past it too");
		helper.succeed();
	}

	/** Only damaged skin ticks. The other two effects have nothing to do per tick. */
	@GameTest(template = EMPTY)
	public static void onlyDamagedSkinTicks(GameTestHelper helper) {

		helper.assertTrue(LeafiaPotion.skindamage.get().shouldApplyEffectTickThisTick(100, 0), "skin damage ticks");
		helper.assertTrue(!LeafiaPotion.frigid.get().shouldApplyEffectTickThisTick(100, 0), "frigid does not");
		helper.assertTrue(!LeafiaPotion.overdose.get().shouldApplyEffectTickThisTick(100, 0), "overdose does not");
		helper.succeed();
	}

	// ---- LeafiaItemTransferable --------------------------------------------------------------

	/**
	 * A mixing-vat-shaped menu: three input slots (0-2), then two slots for one mode (3-4) or three for
	 * the other (5-7), then a nine-slot inventory (8-16). Its chain is the vat's, with the mode a field.
	 */
	private static final class VatLike extends LeafiaItemTransferable {

		static final int MACHINE = 8;
		boolean nuclear = false;
		final SimpleContainer machine = new SimpleContainer(MACHINE);
		final SimpleContainer inventory = new SimpleContainer(9);

		VatLike() {
			super(null, 0);
			for(int i = 0; i < MACHINE; i++) this.addSlot(new Slot(machine, i, 0, 0));
			for(int i = 0; i < 9; i++) this.addSlot(new Slot(inventory, i, 0, 0));
		}

		@Override
		public ItemStack quickMoveStack(Player player, int index) {
			LeafiaItemTransfer transfer = new LeafiaItemTransfer(MACHINE)._selected(index);
			transfer.__forSlots(0, 9999)
					.__tryMoveToInventory(true)
					.__forInventory()
					.__tryMoveToSlot(0, 2, false);
			return (nuclear ? transfer.__tryMoveToSlot(5, 7, false) : transfer.__tryMoveToSlot(3, 4, false)).__getReturn();
		}

		@Override
		public boolean stillValid(Player player) {
			return true;
		}
	}

	@GameTest(template = EMPTY)
	public static void aMachineStackShiftClicksIntoTheInventoryFromTheFarEnd(GameTestHelper helper) {

		VatLike menu = new VatLike();
		menu.machine.setItem(1, new ItemStack(Items.COBBLESTONE, 10));

		ItemStack returned = menu.quickMoveStack(null, 1);

		helper.assertTrue(returned.is(Items.COBBLESTONE) && returned.getCount() == 10, "it returns the pre-move copy so vanilla calls again: " + returned);
		helper.assertTrue(menu.machine.getItem(1).isEmpty(), "the machine slot is emptied");
		helper.assertTrue(menu.inventory.getItem(8).is(Items.COBBLESTONE) && menu.inventory.getItem(8).getCount() == 10,
				"reverse = true fills the inventory from its last slot, got " + menu.inventory.getItem(8));
		helper.assertTrue(menu.inventory.getItem(0).isEmpty(), "and not its first");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void anInventoryStackGoesToTheFirstRangeThenTheModesRange(GameTestHelper helper) {

		VatLike menu = new VatLike();
		menu.inventory.setItem(0, new ItemStack(Items.COBBLESTONE, 4));

		menu.quickMoveStack(null, VatLike.MACHINE);
		helper.assertTrue(menu.machine.getItem(0).is(Items.COBBLESTONE) && menu.machine.getItem(0).getCount() == 4,
				"the first free slot of 0-2 takes it: " + menu.machine.getItem(0));

		// Fill 0-2 with something that will not merge, and the stack falls through to 3-4.
		menu.machine.setItem(0, new ItemStack(Items.DIRT, 1));
		menu.machine.setItem(1, new ItemStack(Items.DIRT, 1));
		menu.machine.setItem(2, new ItemStack(Items.DIRT, 1));
		menu.inventory.setItem(1, new ItemStack(Items.COBBLESTONE, 4));

		menu.quickMoveStack(null, VatLike.MACHINE + 1);
		helper.assertTrue(menu.machine.getItem(3).is(Items.COBBLESTONE), "with 0-2 full it goes to the normal mode's 3-4, got " + menu.machine.getItem(3));
		helper.assertTrue(menu.machine.getItem(5).isEmpty(), "not the nuclear range");

		menu.nuclear = true;
		menu.inventory.setItem(2, new ItemStack(Items.GRAVEL, 4));

		menu.quickMoveStack(null, VatLike.MACHINE + 2);
		helper.assertTrue(menu.machine.getItem(5).is(Items.GRAVEL), "in nuclear mode the same click goes to 5-7, got " + menu.machine.getItem(5));
		helper.assertTrue(menu.machine.getItem(4).isEmpty(), "and not 3-4");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void anEmptySlotDecidesTheAnswerAndMovesNothing(GameTestHelper helper) {

		VatLike menu = new VatLike();
		menu.inventory.setItem(4, new ItemStack(Items.COBBLESTONE, 4));

		helper.assertTrue(menu.quickMoveStack(null, 0).isEmpty(), "an empty machine slot returns empty");
		helper.assertTrue(menu.quickMoveStack(null, VatLike.MACHINE + 3).isEmpty(), "an empty inventory slot returns empty");
		helper.assertTrue(menu.inventory.getItem(4).getCount() == 4, "and nothing else was disturbed");
		helper.succeed();
	}

	/**
	 * Upstream's quirk: {@code __forInventory} starts at the machine's <em>last</em> slot, so a click
	 * there is taken by the inventory branch too. It matters because it decides which branch runs.
	 */
	@GameTest(template = EMPTY)
	public static void theInventoryBranchStartsAtTheMachinesLastSlot(GameTestHelper helper) {

		VatLike menu = new VatLike();
		// Machine slots 0-2 are full of dirt so the first branch (machine -> inventory) is the only
		// one that can run for slot 7; make it unable to, by filling the whole inventory.
		for(int i = 0; i < 9; i++) menu.inventory.setItem(i, new ItemStack(Items.DIAMOND, 64));
		menu.machine.setItem(7, new ItemStack(Items.COBBLESTONE, 4));

		menu.quickMoveStack(null, 7);

		// With no room in the inventory, the machine branch changes nothing, and then slot 7 -
		// the machine's last - is inside __forInventory's range, so the stack is offered to 0-2.
		helper.assertTrue(menu.machine.getItem(0).is(Items.COBBLESTONE),
				"a click on the last machine slot falls through to the inventory branch and lands in 0-2, got " + menu.machine.getItem(0));

		// But slot 6, one before it, is outside that range and stays put.
		VatLike other = new VatLike();
		for(int i = 0; i < 9; i++) other.inventory.setItem(i, new ItemStack(Items.DIAMOND, 64));
		other.machine.setItem(6, new ItemStack(Items.COBBLESTONE, 4));
		helper.assertTrue(other.quickMoveStack(null, 6).isEmpty(), "slot 6 is not in the inventory branch's range");
		helper.assertTrue(other.machine.getItem(6).getCount() == 4, "and the stack stays where it was");
		helper.succeed();
	}

	/** A range's end is inclusive: {@code __tryMoveToSlot(0, 2)} reaches slot 2. */
	@GameTest(template = EMPTY)
	public static void aTargetRangeIsInclusiveOnBothEnds(GameTestHelper helper) {

		VatLike menu = new VatLike();
		menu.machine.setItem(0, new ItemStack(Items.DIRT, 1));
		menu.machine.setItem(1, new ItemStack(Items.DIRT, 1));
		menu.inventory.setItem(0, new ItemStack(Items.COBBLESTONE, 4));

		menu.quickMoveStack(null, VatLike.MACHINE);

		helper.assertTrue(menu.machine.getItem(2).is(Items.COBBLESTONE), "slot 2, the range's last, is a legal target, got " + menu.machine.getItem(2));
		helper.succeed();
	}

	/** The spark spray: eight fields, two of them doubles and two floats, and upstream's defaults for the rest. */
	@GameTest(template = EMPTY)
	public static void theFiaSparkKeepsAllEightFieldsAndUpstreamsDefaults(GameTestHelper helper) {

		FiaSpark defaults = new FiaSpark();
		helper.assertTrue(defaults.color == 0x80EEFF && defaults.spread == 45 && defaults.speedMin == 0.15 && defaults.speedMax == 0.35
				&& defaults.count == 1 && defaults.length == 8 && defaults.thickness == 0.02f && defaults.segmentsPerTick == 3,
				"upstream's defaults are 0x80EEFF, 45, 0.15, 0.35, 1, 8, 0.02, 3");

		FiaSpark sent = new FiaSpark();
		sent.color = 0x123456;
		sent.spread = 12.5;
		sent.speedMin = 0.0625;
		sent.speedMax = 0.5;
		sent.count = 9;
		sent.length = 33;
		sent.thickness = 0.125f;
		sent.segmentsPerTick = 6.5f;

		LeafiaParticlePacket packet = roundTrip(helper, sent);
		FiaSpark read = (FiaSpark) packet.particle();
		helper.assertTrue(read != sent, "a decode builds a new instance, never the prototype");
		helper.assertTrue(read.color == 0x123456 && read.count == 9 && read.length == 33, "ints " + read.color + "/" + read.count + "/" + read.length);
		same(helper, read.spread, 12.5, "spread");
		same(helper, read.speedMin, 0.0625, "speedMin");
		same(helper, read.speedMax, 0.5, "speedMax");
		same(helper, read.thickness, 0.125f, "thickness");
		same(helper, read.segmentsPerTick, 6.5f, "segmentsPerTick");
		same(helper, packet.nbt().getDouble("mX"), 0.5, "the spray's heading is the packet's motion");
		helper.succeed();
	}

	/**
	 * The tau spark (D-3031): five fields round the wire, upstream's defaults, and the effect tag it hands the addon's
	 * {@code coneBurst} creator - upstream's keys and values, with the alpha inverted out of the colour's top byte, the
	 * lifetimes five and eight times {@code life} truncated, and the angle a literal 70 whatever the field holds.
	 */
	@GameTest(template = EMPTY)
	public static void theTauSparkKeepsItsFieldsAndWritesUpstreamsConeBurst(GameTestHelper helper) {

		LeafiaParticlePacket.TauSpark defaults = new LeafiaParticlePacket.TauSpark();
		helper.assertTrue(defaults.color == 0xE6E6FF && defaults.count == 7 && defaults.angle == 70F && defaults.life == 1 && defaults.width == 0.01F,
				"upstream's defaults are 0xE6E6FF, 7, 70, 1, 0.01");

		LeafiaParticlePacket.TauSpark sent = new LeafiaParticlePacket.TauSpark();
		sent.color = 0x40FFEE80;
		sent.count = 11;
		sent.angle = 15F;
		sent.life = 2.5F;
		sent.width = 0.03F;
		LeafiaParticlePacket packet = roundTrip(helper, sent);
		LeafiaParticlePacket.TauSpark read = (LeafiaParticlePacket.TauSpark) packet.particle();
		helper.assertTrue(read != sent && read.color == 0x40FFEE80 && read.count == 11, "ints " + read.color + "/" + read.count);
		same(helper, read.angle, 15F, "angle");
		same(helper, read.life, 2.5F, "life");
		same(helper, read.width, 0.03F, "width");

		CompoundTag tag = read.writeEffect(new CompoundTag());
		helper.assertTrue(tag.getString("type").equals(LeafiaParticlePacket.TauSpark.EFFECT) && tag.getString("mode").equals("coneBurst"),
				"the addon's spark effect, cone burst: " + tag);
		same(helper, tag.getFloat("r"), 1F, "red 0xFF");
		same(helper, tag.getFloat("g"), 0xEE / 255F, "green 0xEE");
		same(helper, tag.getFloat("b"), 0x80 / 255F, "blue 0x80");
		same(helper, tag.getFloat("a"), 1 - 0x40 / 255F, "alpha is one minus the top byte");
		helper.assertTrue(tag.getInt("lifetime") == 12 && tag.getInt("randLifetime") == 20, "(int) 5 x 2.5 and (int) 8 x 2.5: " + tag);
		same(helper, tag.getFloat("angle"), 70F, "the angle upstream writes is 70, not the field");
		same(helper, tag.getFloat("width"), 0.03F, "width");
		same(helper, tag.getFloat("length"), 0.5F, "length");
		same(helper, tag.getFloat("gravity"), 0.1F, "gravity");
		same(helper, tag.getFloat("randomVelocity"), 0.1F, "random velocity");
		helper.assertTrue(tag.getInt("count") == 11, "the count is the field's");
		helper.assertTrue(new LeafiaParticlePacket.TauSpark().writeEffect(new CompoundTag()).getFloat("a") == 1F, "the default colour is opaque");

		// the creator's aim: the axis itself at u = 0, the cone's rim at u = 1, unit length, and up for no motion
		Vec3 axis = new Vec3(3, 0, 0);
		for(double w : new double[] {0, 0.25, 0.6}) {
			Vec3 centre = LeafiaParticlePacket.TauSpark.coneDirection(axis, 35, 0, w);
			Vec3 rim = LeafiaParticlePacket.TauSpark.coneDirection(axis, 35, 1, w);
			near(helper, centre.x, 1, "u = 0 is the axis");
			near(helper, rim.length(), 1, "a unit direction");
			near(helper, Math.toDegrees(Math.acos(rim.x)), 35, "u = 1 is the rim, 35 degrees off the axis");
		}
		near(helper, LeafiaParticlePacket.TauSpark.coneDirection(Vec3.ZERO, 35, 0, 0).y, 1, "no motion aims up");
		helper.succeed();
	}

	/** The lightning path: a byte count and three doubles a point, to the bit, and 255 points at most. */
	@GameTest(template = EMPTY)
	public static void theDfcShockParticleKeepsItsPathToTheBit(GameTestHelper helper) {

		java.util.List<Vec3> path = new java.util.ArrayList<>();
		path.add(new Vec3(1.5, 64.0, -2.25));
		path.add(new Vec3(1.5000000001, 64.1234567890123, -2.25));
		path.add(new Vec3(-123456.789, 0.1, 99.5));

		DFCShockParticleEditionTM read = (DFCShockParticleEditionTM) roundTrip(helper, new DFCShockParticleEditionTM(path)).particle();
		helper.assertTrue(read.poses0.size() == 3, "three points, got " + read.poses0.size());
		for(int i = 0; i < 3; i++)
			helper.assertTrue(read.poses0.get(i).equals(path.get(i)), "point " + i + " is a double each way: " + read.poses0.get(i) + " != " + path.get(i));

		// Doubles on the wire, not floats: 1.5 and 1.5000000001 are one float and two doubles.
		helper.assertTrue(!read.poses0.get(0).equals(read.poses0.get(1)), "the path is not narrowed to floats");

		// An empty path is a single byte of zero after the framing.
		DFCShockParticleEditionTM empty = (DFCShockParticleEditionTM) roundTrip(helper, new DFCShockParticleEditionTM(new java.util.ArrayList<>())).particle();
		helper.assertTrue(empty.poses0.isEmpty(), "an empty path reads back empty");
		helper.succeed();
	}
}
