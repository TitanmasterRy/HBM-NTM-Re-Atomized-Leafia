package com.leafia.contents.machines.controlpanel;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.leafia.contents.machines.controlpanel.core.Control;
import com.leafia.contents.machines.controlpanel.core.block.ControlPanelTE;
import com.leafia.dev.custompacket.LeafiaCustomPacket;
import com.leafia.dev.custompacket.LeafiaCustomPacketEncoder;
import com.leafia.dev.optimization.bitbyte.LeafiaBuf;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The base of the instrument sync packets (CCP-INSTRUMENTS-1, D-3071): a panel position and the index of one control on it, then
 * whatever state that control keeps only on the server's side of the wire (a graph's samples, a lever's drawn angle). Adapted from
 * Leafia's {@code CCPSyncPacketBase}, which implemented {@code LeafiaCustomPacketEncoder}; it is the same hub here, so the three
 * packets of this batch are three more types in {@link com.leafia.dev.custompacket.LeafiaPacketTypes}.
 *
 * <h2>What the port changes, all of it on the trust boundary</h2>
 * <ul>
 * <li><b>Server to client only.</b> {@link #acceptsServerbound} stays {@code false}, so {@link LeafiaCustomPacket#handle} drops one
 * arriving at a server before it is decoded; and every callback built on {@link #resolve} also refuses a context whose flow is
 * not clientbound, so a mistake in that gate would still not let a client drive an instrument.</li>
 * <li><b>The decoder keeps nothing on the prototype.</b> Leafia's {@code load} wrote {@code pos}, {@code idx}, {@code world} and
 * {@code instrument} into the registered prototype from the network thread, which {@code LeafiaCustomPacketEncoder} forbids.
 * {@link #readTarget} returns the two numbers and {@link #resolve} turns them into a control inside the game-thread callback.</li>
 * <li><b>The target is looked up, never trusted.</b> The position must be a loaded one on the receiving side's own level, hold a
 * panel, the index must be one of that panel's controls, and the control must be of the class the packet is for. Anything else is
 * ignored: a stale packet for a panel the client has since lost, or one that names the wrong thing, does nothing.</li>
 * <li><b>Counts are bounded on read</b> by the packets themselves ({@link #MAX_SAMPLES}); a decoder never reads further than it
 * is willing to keep.</li>
 * </ul>
 *
 * <p>{@code @SideOnly(CLIENT) load} is gone with the Minecraft client reference it carried: the level is the receiving player's
 * ({@code context.player().level()}), which is the client's on a client and needs no client type to name.
 */
public abstract class CCPSyncPacketBase implements LeafiaCustomPacketEncoder {

	/** The widest array a sync packet will keep: the editor's own ceiling on a graph's segments. */
	public static final int MAX_SAMPLES = 200;

	/** How far from a panel a player is sent its instruments' state, in blocks. Leafia's own number. */
	public static final double RANGE = 256;

	public BlockPos pos;
	public int idx;

	public CCPSyncPacketBase() {}

	public CCPSyncPacketBase(BlockPos pos, int idx) {
		this.pos = pos;
		this.idx = idx;
	}

	@Override
	public void encode(LeafiaBuf buf) {
		buf.writeVec3i(pos);
		buf.writeShort(idx);
	}

	/** The two numbers every sync packet starts with, as read. Nothing about them is known to be good yet. */
	public record Target(BlockPos pos, int idx) {}

	public static Target readTarget(LeafiaBuf buf) {
		BlockPos pos = buf.readPos();
		int idx = buf.readShort();
		return new Target(pos, idx);
	}

	/**
	 * The control this packet is about, or null when there is none. Looks only at a position the receiving level already has loaded,
	 * and only at a panel there; the index is checked against that panel; the class against the one the caller wants.
	 */
	@Nullable
	public static <T extends Control> T resolve(IPayloadContext context, Target target, Class<T> type) {
		if(context.flow() != PacketFlow.CLIENTBOUND)
			return null;
		Player player = context.player();
		if(player == null)
			return null;
		return find(player.level(), target, type);
	}

	/** {@link #resolve} on a level directly, for the tests and for any caller that already has one. */
	@Nullable
	public static <T extends Control> T find(Level level, Target target, Class<T> type) {
		if(level == null || target == null || target.pos() == null || !level.isLoaded(target.pos()))
			return null;
		BlockEntity be = level.getBlockEntity(target.pos());
		if(!(be instanceof ControlPanelTE panel) || panel.panel == null)
			return null;
		int idx = target.idx();
		if(idx < 0 || idx >= panel.panel.controls.size())
			return null;
		Control c = panel.panel.controls.get(idx);
		return type.isInstance(c) ? type.cast(c) : null;
	}

	/**
	 * Sends a packet for this control to every player within {@link #RANGE} of its panel. Server side only: a control on a client's
	 * copy, or one whose panel is not in the world, sends nothing. Returns whether it was handed to the network.
	 */
	public static boolean sendFor(Control control, CCPSyncPacketBase packet) {
		if(control == null || control.panel == null || control.panel.parent == null || packet == null)
			return false;
		if(!(control.panel.parent.getControlWorld() instanceof ServerLevel level))
			return false;
		if(!due(control, packet.signature(), level.getGameTime()))
			return false;
		BlockPos at = control.panel.parent.getControlPos();
		LeafiaCustomPacket built = LeafiaCustomPacket.__start(packet);
		if(built == null)
			return false;
		built.__sendToAllAround(level, at, RANGE);
		return true;
	}

	/** A packet is re-sent unchanged at most this often, so a player who walks into range still gets the state (D-3074). */
	public static final int RESEND_TICKS = 20;

	private static final Map<Control, Sent> LAST_SENT = Collections.synchronizedMap(new WeakHashMap<>());

	private record Sent(Object signature, long tick) {}

	/**
	 * What this packet carries, compared with what the control last sent: equal state is not re-sent for {@link #RESEND_TICKS}
	 * (D-3074; Leafia sent every control every tick, a graph being 40 floats). Null means "always send".
	 */
	public Object signature() {
		return null;
	}

	/** Whether a packet with this signature should go out now; records it when it should. */
	public static boolean due(Control control, Object signature, long gameTime) {
		if(signature == null)
			return true;
		Sent last = LAST_SENT.get(control);
		if(last != null && last.signature().equals(signature) && gameTime >= last.tick() && gameTime - last.tick() < RESEND_TICKS)
			return false;
		LAST_SENT.put(control, new Sent(signature, gameTime));
		return true;
	}

	/** What an instrument's callback does with its resolved control; a convenience for the three packets. */
	protected static <T extends Control> Consumer<IPayloadContext> apply(Target target, Class<T> type, Consumer<T> action) {
		return context -> {
			T control = resolve(context, target, type);
			if(control != null)
				action.accept(control);
		};
	}
}
