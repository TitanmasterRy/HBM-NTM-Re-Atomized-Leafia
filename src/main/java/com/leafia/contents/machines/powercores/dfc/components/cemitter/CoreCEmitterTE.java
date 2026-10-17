package com.leafia.contents.machines.powercores.dfc.components.cemitter;

import javax.annotation.Nullable;

import com.leafia.contents.machines.powercores.dfc.components.emitter.LeafiaCoreEmitterTE;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.dev.custompacket.PacketGuard;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The creative DFC booster: an overhauled emitter that needs no power, no SPK and no coolant, and fires
 * one of four beam sizes you type into its screen.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/powercores/dfc/components/cemitter/CoreCEmitterTE.java},
 * which extends the mixed-in emitter and reaches its {@code @Unique} fields through method handles. Here it
 * extends {@link LeafiaCoreEmitterTE} and reads them directly; the block is {@code dfc_cemitter}.
 *
 * <h2>What it does</h2>
 *
 * Switched on, it fires {@code joulesT[selecting]} through the emitter's own ray every tick - 100 thousand,
 * 20 million, 100 million or a billion SPK by default - and sets its setting to {@code beam / 1,000}, which
 * is what the beam's SPK to a receiver is scaled by on the <em>next</em> tick. Switched off, it does
 * nothing. It never cools, never melts and never draws power.
 *
 * <h2>Upstream oddities, kept (rule 4)</h2>
 *
 * <ul>
 * <li><b>Its beam does no damage.</b> The ray burns for {@code joules * 1e-6}, and the creative booster never
 *     puts its beam in {@code joules}, so every entity in it takes zero. It still sets them on fire.</li>
 * <li><b>The setting is last tick's.</b> {@code watts} is assigned after the ray, so the first tick after a
 *     change pays a receiver at the old beam's rate.</li>
 * </ul>
 *
 * <h2>One guard upstream lacks</h2>
 *
 * The screen's selection index arrives from the client and upstream stores it as sent, so a modified
 * client choosing 4 crashes the server on the next tick ({@code joulesT[4]}). An index outside 0..3 is
 * refused (D-1094, the owner's rule for every serverbound packet); so is a beam that is not a number.
 */
public class CoreCEmitterTE extends LeafiaCoreEmitterTE {

	/** Upstream's four defaults. */
	public static final long[] DEFAULT_BEAMS = {100_000L, 20_000_000L, 100_000_000L, 1_000_000_000L};

	public long[] joulesT = DEFAULT_BEAMS.clone();
	public int selecting = 0;
	/** Client: a beam changed, so the screen re-reads its fields. */
	public boolean changed = false;

	public CoreCEmitterTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public String getPacketIdentifier() {
		return "DFC_CBOOSTER";
	}

	@Override
	public void updateEntity() {
		if(level.isClientSide) {
			leafia$clientTick();
			return;
		}
		aimIfUnaimed();
		if(isOn) {
			// 50,000,000 HE = 10,000 SPK
			// 1 SPK = 5,000 HE
			long out = joulesT[selecting];
			if(out > 0) {
				leafia$raycast(out);
			}
			watts = (int) (out / 1000L);
		} else {
			watts = 0;
		}

		this.markChanged();

		LeafiaPacket._start(this).__write(0, isOn).__write(1, watts).__write(2, prev).__sendToAffectedClients();
		sendChanges(null);

		LeafiaPacket._start(this).__write(KEY_TARGET, targetPosition).__sendToAffectedClients();
		this.networkPackNT(250);
	}

	@Override
	public void leafia$clientTick() {
		if(isOn) lastRaycast = leafia$raycast(0L);
	}

	public void sendChanges(@Nullable Player plr) {
		LeafiaPacket.Builder packet = LeafiaPacket._start(this).__write(3, selecting).__write(4, joulesT[0]).__write(5, joulesT[1]).__write(6, joulesT[2])
				.__write(7, joulesT[3]);
		if(plr == null) packet.__sendToAffectedClients();
		else packet.__sendToClient(plr);
	}

	@Override
	public LeafiaPacket.Builder syncClients(LeafiaPacket.Builder packet) {
		packet.__write(3, selecting).__write(4, joulesT[0]).__write(5, joulesT[1]).__write(6, joulesT[2]).__write(7, joulesT[3]);
		return super.syncClients(packet);
	}

	@Override
	protected void saveAdditional(CompoundTag compound, HolderLookup.Provider registries) {
		compound.putBoolean("isOn", isOn);
		compound.putLong("0", joulesT[0]);
		compound.putLong("1", joulesT[1]);
		compound.putLong("2", joulesT[2]);
		compound.putLong("3", joulesT[3]);
		compound.putInt("selecting", selecting);
		super.saveAdditional(compound, registries);
	}

	@Override
	protected void loadAdditional(CompoundTag compound, HolderLookup.Provider registries) {
		isOn = compound.getBoolean("isOn");
		joulesT[0] = compound.getLong("0");
		joulesT[1] = compound.getLong("1");
		joulesT[2] = compound.getLong("2");
		joulesT[3] = compound.getLong("3");
		selecting = compound.getInt("selecting");
		super.loadAdditional(compound, registries);
	}

	@Override
	public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
		return new CoreCEmitterMenu(id, playerInventory, this);
	}

	/**
	 * Upstream's, which replaces the emitter's handler rather than adding to it: key 0 (the server's
	 * {@code isOn}) lands in the beam's {@code active} flag the renderer reads, keys 3 to 7 are the screen's,
	 * and only the target (31) goes on to {@code IDFCBase}. Keys 1 and 2 are dropped, as upstream drops them;
	 * the screen's numbers arrive through {@code networkPackNT} instead (D-3014).
	 */
	@Override
	public void onReceivePacketLocal(byte key, Object value) {
		if(key == 0) {
			leafia$isActive((boolean) value);
		}
		if(key == 3) {
			selecting = (int) value;
		} else if(key >= 4 && key <= 7) {
			long v = (long) value;
			if(joulesT[key - 4] != v) {
				joulesT[key - 4] = v;
				changed = true;
			}
		}
		if(key == KEY_TARGET) leafia$targetPosition((BlockPos) value);
	}

	/**
	 * Upstream's: key 0 picks a beam, keys 1 to 4 set one, and every packet answers with the four. Two
	 * guards upstream lacks (class note): the sender has this booster's screen open, and the index is 0..3.
	 */
	@Override
	public void onReceivePacketServer(byte key, Object value, Player plr) {
		if(key == KEY_TARGET) {
			super.onReceivePacketServer(key, value, plr);
			return;
		}
		if(!(plr != null && plr.containerMenu instanceof CoreCEmitterMenu menu && menu.emitter == this)) {
			PacketGuard.reject("DFC_CBOOSTER", plr, "this booster's screen is not open");
			return;
		}
		if(key == 0) {
			if(!(value instanceof Integer i) || i < 0 || i >= joulesT.length) {
				PacketGuard.reject("DFC_CBOOSTER", plr, "selection " + value);
				return;
			}
			selecting = i;
		} else if(key < 5) {
			if(!(value instanceof Long l)) {
				PacketGuard.reject("DFC_CBOOSTER", plr, "beam " + value);
				return;
			}
			joulesT[key - 1] = l;
		}
		sendChanges(null);
		super.onReceivePacketServer(key, value, plr);
	}

	@Override
	public void onPlayerValidate(Player plr) {
		super.onPlayerValidate(plr);
		sendChanges(plr);
	}
}
