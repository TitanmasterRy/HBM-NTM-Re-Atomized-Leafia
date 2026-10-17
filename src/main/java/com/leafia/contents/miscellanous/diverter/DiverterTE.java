package com.leafia.contents.miscellanous.diverter;

import com.leafia.config.LeafiaConfig;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.dev.container_utility.LeafiaPacketReceiver;
import com.leafia.init.LeafiaBlockEntities;
import com.leafia.savedata.PlayerDeathsSavedData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Meteor Protection Beacon's state (D-3022): who placed it, and how long until it works.
 *
 * <p>Upstream: {@code com/leafia/contents/miscellanous/diverter/DiverterTE.java}. Every server tick it reads when its
 * placer last died in this dimension ({@link PlayerDeathsSavedData}, 0 if never) and works out
 * {@code timeRemaining = 20 x diverterMinAliveTime - (now - lastDeath)} in ticks. A beacon with no placer (one not
 * placed by a player) is in error and never reaches 0. At 0 or below it is <b>active</b>: its block is the lit one,
 * and {@code DiverterBlock.isProtected} counts it. It sends the three values to every client in range each tick, for
 * the look-at panel.
 *
 * <p>So the clock is the placer's <em>life</em>, not the beacon's: a beacon placed by a player who has been alive an
 * hour works at once, and every death of its placer switches it off for half an hour again (with the default).
 * Only the placer's name is saved; the rest is worked out again every tick.
 *
 * <p>Swapping the block between lit and unlit keeps this block entity (upstream {@code validate()}s it back into
 * place, and so does this), so nothing is lost by the swap.
 */
public class DiverterTE extends BlockEntity implements LeafiaPacketReceiver {

	/** Upstream's starting value: what a client shows before the first packet. */
	public static final long INITIAL_TIME = 124384;

	String placerName = "";
	long timeRemaining = INITIAL_TIME;
	boolean isError = false;

	public DiverterTE(BlockPos pos, BlockState state) {
		super(LeafiaBlockEntities.DIVERTER.get(), pos, state);
	}

	public String getPlacerName() {
		return placerName;
	}

	public void setPlacerName(String name) {
		this.placerName = name;
		setChanged();
	}

	public long getTimeRemaining() {
		return timeRemaining;
	}

	public boolean isError() {
		return isError;
	}

	/** Whether this beacon protects the chunks round it. */
	public boolean isActive() {
		return timeRemaining <= 0;
	}

	/** Upstream's "actually is this necessary?": the lit block when active, the unlit one when not. */
	void replaceBlock(boolean state) {
		boolean curState = getBlockState().is(LeafiaBlocks.diverter.get());
		if(curState != state) {
			BlockState next = (state ? LeafiaBlocks.diverter : LeafiaBlocks.diverter_unlit).get().defaultBlockState();
			level.setBlock(worldPosition, next, Block.UPDATE_CLIENTS);
			this.clearRemoved();
			this.setBlockState(next);
			level.setBlockEntity(this);
		}
	}

	/** {@code 20 x minAliveTime - (now - lastDeath)}, at least 1 when the beacon has no placer. */
	public static long timeRemaining(long now, long lastDeath, int minAliveTime, boolean isError) {
		long t = 20L * minAliveTime - (now - lastDeath);
		if(isError) t = Math.max(t, 1);
		return t;
	}

	public static void tick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, DiverterTE diverter) {
		diverter.update();
	}

	public void update() {
		if(!(level instanceof ServerLevel server)) return;
		isError = placerName.isEmpty();
		long timestamp = PlayerDeathsSavedData.forLevel(server).timestamps.getOrDefault(placerName, 0L);
		timeRemaining = timeRemaining(server.getGameTime(), timestamp, LeafiaConfig.meteorDiverterMinAliveTime, isError);
		boolean active = timeRemaining <= 0;
		replaceBlock(active);
		LeafiaPacket._start(this)
				.__write(0, timeRemaining)
				.__write(1, isError)
				.__write(2, placerName)
				.__sendToAffectedClients();
		setChanged();
	}

	@Override
	protected void saveAdditional(CompoundTag compound, HolderLookup.Provider registries) {
		super.saveAdditional(compound, registries);
		compound.putString("placer", placerName);
	}

	@Override
	protected void loadAdditional(CompoundTag compound, HolderLookup.Provider registries) {
		super.loadAdditional(compound, registries);
		if(compound.contains("placer")) placerName = compound.getString("placer");
	}

	@Override
	public String getPacketIdentifier() {
		return "DIVERTER";
	}

	@Override
	public void onReceivePacketLocal(byte key, Object value) {
		if(key == 0) timeRemaining = (long) value;
		else if(key == 1) isError = (boolean) value;
		else if(key == 2) placerName = (String) value;
	}

	@Override
	public void onReceivePacketServer(byte key, Object value, Player plr) { }

	@Override
	public void onPlayerValidate(Player plr) { }
}
