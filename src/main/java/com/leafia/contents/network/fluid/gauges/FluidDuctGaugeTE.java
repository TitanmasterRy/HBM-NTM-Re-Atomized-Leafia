package com.leafia.contents.network.fluid.gauges;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.leafia.contents.machines.controlpanel.core.ControlEvent;
import com.leafia.contents.machines.controlpanel.core.ControllableLifecycle;
import com.leafia.contents.machines.controlpanel.core.IControllable;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.hbm.inventory.fluid.Fluids;
import com.leafia.contents.network.fluid.FluidDuctEquipmentTE;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.dev.container_utility.LeafiaPacketReceiver;
import com.leafia.dev.custompacket.PacketGuard;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * The flow gauge's block entity: how much fluid went through this length of pipe in the last second,
 * and the number the dial is scaled to.
 *
 * <p>Upstream: {@code com/leafia/contents/network/fluid/gauges/FluidDuctGaugeTE.java}.
 *
 * <h2>The reading</h2>
 *
 * Every tick the gauge writes what its network moved that tick - {@code FluidNetMK2.fluidTracker}, and
 * zero if it has no node, no network or no fluid - into the next slot of a ring of twenty, and its
 * reading {@link #fillPerSec} is the sum of the ring: a rolling second, updated every tick, and so a
 * number of millibuckets per second. The ring is a plain {@code long[20]} that starts at zero and is
 * not saved, so a gauge that has just loaded counts up from nothing over its first second.
 *
 * <p>The tracker is the <em>whole network's</em> throughput, not this block's: two gauges on one
 * network read the same number, which is also what the base mod's own pipe gauge does.
 *
 * <h2>The dial</h2>
 *
 * {@link #maximum} is what a full turn of the needle stands for: 10,000 mB/s until a player changes it
 * with a screwdriver. {@link #needleAngle} is upstream's renderer arithmetic
 * ({@code min(fill / max x 360, 350 + dialRand)}), here so that a test can reach it: a full turn is
 * 360 degrees but the needle stops at 350 plus a random 0 to 4, redrawn every client tick, so a
 * pegged gauge trembles by four degrees at the stop. With {@code maximum = 0} the division is
 * {@code NaN} or infinity and so is the angle; upstream permits it and so does this.
 *
 * <h2>What changed</h2>
 *
 * <ul>
 * <li><b>The sync</b> is the base mod's {@code networkPackNT} rather than upstream's
 *     {@code LeafiaPacket} (keys 0 and 1 every tick) - the same substitution the valves made (D-1015),
 *     for the same reason: this is a base-mod pipe. The player's edit still comes back over
 *     {@code LeafiaPacket}, key 0, as upstream's does.</li>
 * <li><b>The control panel.</b> Upstream implements {@code IControllable} and answers one query,
 *     {@code perSecSmooth}. The Custom Control Panel is not adopted (D-1093 #8), so the interface and
 *     the event-system calls in {@code validate}/{@code invalidate} are dropped; the number is
 *     {@link #fillPerSec}, public.</li>
 * <li><b>The edit is authorised.</b> Upstream stores whatever {@code long} arrives from anyone who can
 *     name the block. The screen is opened by the server now ({@link #openGui}), so the server knows
 *     who has it open: a value is accepted from that player, while in reach (D-1094).</li>
 * </ul>
 */
public class FluidDuctGaugeTE extends FluidDuctEquipmentTE implements LeafiaPacketReceiver, IControllable {

	/** The ring's length: twenty ticks, a second. */
	public static final int WINDOW = 20;

	/** The dial's default full scale, in mB/s. */
	public static final long DEFAULT_MAXIMUM = 10_000;

	/** The angle the needle stops at, before the tremble. */
	public static final float STOP = 350F;

	public static final byte ID_FILL = 0;
	public static final byte ID_MAXIMUM = 1;
	/** Not upstream's: tells the one client to open the screen (its screwdriver opened it on the client). */
	public static final byte ID_GUI = 2;

	long[] fills = new long[WINDOW];
	int needle = 0;

	public long maximum = DEFAULT_MAXIMUM;

	/** Millibuckets in the last second. Upstream's public field, and what a control panel would have read. */
	public long fillPerSec = 0;

	/** Client only: the needle's tremble, 0 to 4, redrawn every tick. */
	public int localDialRand = 0;

	/**
	 * The players the server has opened the screen for. The screen has no menu, so this is the only
	 * record that someone has it open. Never saved.
	 */
	public final Set<UUID> editors = new HashSet<>();

	public FluidDuctGaugeTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, FluidDuctGaugeTE gauge) {
		gauge.updateEntity();
	}

	@Override
	public void updateEntity() {

		super.updateEntity();

		if(level == null) return;

		if(!level.isClientSide) {

			needle = Math.floorMod(needle + 1, WINDOW);
			fills[needle] = 0;

			if(node != null && node.net != null && getFluidType() != Fluids.NONE)
				fills[needle] = node.net.fluidTracker;

			long sum = 0;
			for(long fill : fills)
				sum += fill;

			fillPerSec = sum;

			networkPackNT(SYNC_RANGE);

		} else {
			localDialRand = level.random.nextInt(5);
		}
	}

	/** Upstream's renderer arithmetic: how far the needle has turned, in degrees. */
	public float needleAngle() {
		return Math.min(fillPerSec / (float) maximum * 360, STOP + localDialRand);
	}

	// ---- the screen -----------------------------------------------------------------------------

	/** Tells one client to open the screen, and remembers that it did. */
	public void openGui(Player player) {
		editors.add(player.getUUID());
		// A connection that never negotiated the channel is sent nothing: NeoForge throws out of the
		// tick loop for it, as it does for the particle packet (D-3000). A real client has it.
		if(player instanceof ServerPlayer server && !server.connection.hasChannel(LeafiaPacket.TYPE)) return;
		LeafiaPacket._start(this).__write(ID_GUI, 0).__sendToClient(player);
	}

	/** Every client type behind a holder a dedicated server never loads; D-1039 explains why. */
	@OnlyIn(Dist.CLIENT)
	private static final class ClientOnly {
		static void openGui(FluidDuctGaugeTE te) {
			FluidDuctGaugeGUI.open(te);
		}
	}

	@Override
	public String getPacketIdentifier() {
		return "DUCT_GAUGE";
	}

	@Override
	public void onReceivePacketLocal(byte key, Object value) {
		if(key == ID_GUI && FMLEnvironment.dist.isClient())
			ClientOnly.openGui(this);
	}

	@Override
	public void onReceivePacketServer(byte key, Object value, Player plr) {

		if(key != ID_FILL || !(value instanceof Long number)) return;

		if(plr == null || !editors.contains(plr.getUUID())) {
			PacketGuard.reject("FluidDuctGaugeTE maximum", plr, "the gauge screen was not opened for them");
			return;
		}

		if(!PacketGuard.near(plr, worldPosition)) {
			PacketGuard.reject("FluidDuctGaugeTE maximum", plr, "out of reach");
			editors.remove(plr.getUUID());
			return;
		}

		maximum = number;
		setChanged();
		networkPackNT(SYNC_RANGE);
	}

	/** Nothing to push on arrival: the reading rides {@code networkPackNT}, which reaches everyone in range. */
	@Override
	public void onPlayerValidate(Player plr) { }

	// ---- sync and save --------------------------------------------------------------------------

	@Override
	public void serialize(RegistryFriendlyByteBuf buf) {
		super.serialize(buf);
		buf.writeLong(fillPerSec);
		buf.writeLong(maximum);
	}

	@Override
	public void deserialize(RegistryFriendlyByteBuf buf) {
		super.deserialize(buf);
		fillPerSec = buf.readLong();
		maximum = buf.readLong();
	}

	@Override
	protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.loadAdditional(nbt, registries);
		if(nbt.contains("maximum")) maximum = nbt.getLong("maximum");
	}

	@Override
	protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.saveAdditional(nbt, registries);
		nbt.putLong("maximum", maximum);
	}

	// ---- the Custom Control Panel (CCP-RETROFIT, D-3069) ----

	/**
	 * Upstream's one query key, the fluid that moved through the pipe over the last 20 ticks. (The base mod's own pipe gauge, which the
	 * panel reaches through an adapter, answers {@code perTick} and {@code perSecond}; this addon gauge never did.)
	 */
	@Override
	public Map<String, DataValue> getQueryData() {
		return ControllableLifecycle.values("perSecSmooth", fillPerSec);
	}

	@Override public BlockPos getControlPos() { return worldPosition; }
	@Override public Level getControlWorld() { return level; }

	@Override
	public void onLoad() {
		super.onLoad();
		ControllableLifecycle.join(this);
	}

	@Override
	public void setRemoved() {
		ControllableLifecycle.leave(this);
		super.setRemoved();
	}

	@Override
	public void onChunkUnloaded() {
		ControllableLifecycle.leave(this);
		super.onChunkUnloaded();
	}
}
