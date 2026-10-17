package com.leafia.contents.machines.elevators.floors;

import com.leafia.contents.machines.elevators.EvPulleyTE;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.dev.container_utility.LeafiaPacketReceiver;
import com.leafia.dev.custompacket.PacketGuard;
import com.leafia.init.LeafiaBlockEntities;
import com.llib.technical.FiaLatch;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * A landing: its floor number, its doors, and whatever block it has been painted to look like.
 *
 * <p>Upstream: {@code .../floors/EvFloorTE.java}.
 *
 * <h2>{@code open} is a latch, not a float</h2>
 *
 * {@link FiaLatch} remembers what it last broadcast, so the landing doors are only synched on the
 * ticks they actually move. The cabin writes {@code open.cur} directly every tick it is adjacent
 * — see {@code ElevatorEntity.tickServer} — and this block entity notices the change and tells the
 * clients. That indirection is why the landing doors track the cabin doors without the cabin
 * knowing anything about packets.
 *
 * <h2>The paint is a block, stored by registry name</h2>
 *
 * Upstream stores {@code paintBlock} plus a metadata int, because 1.12.2 blocks are
 * block-plus-meta. Here a {@link BlockState} is the unit, but the <em>saved</em> form stays
 * upstream's registry-name string so an existing world keeps loading; {@code paintMeta} is kept
 * and read for that reason and is otherwise unused (1.21.1 has no block metadata).
 */
public class EvFloorTE extends BlockEntity implements LeafiaPacketReceiver {

	static final byte idGui = 31;
	static final byte idFloor = 0;
	static final byte idOpen = 1;
	static final byte idPaintBlock = 25;
	static final byte idPaintMeta = 26;

	public Block paintBlock = null;

	/** 1.12.2 block metadata. Saved and loaded so old worlds round-trip; nothing reads it. */
	public int paintMeta = 0;

	public int floor = 1;

	/** The lowest and highest floor the screen can send: its field takes two characters, so -9 to 99. */
	public static final int MIN_FLOOR = -9;
	public static final int MAX_FLOOR = 99;

	/**
	 * The players {@link #openGui} has sent the screen to (server only). The screen has no menu, so
	 * this is the only record that a player has it open (D-1094): a floor number is accepted from
	 * someone on this list and within reach, and from nobody else. Never saved.
	 */
	public final java.util.Set<java.util.UUID> editors = new java.util.HashSet<>();

	/** How far the landing doors are open, 0..1. Written by the cabin, broadcast from here. */
	public FiaLatch<Float> open = new FiaLatch<>(0f);

	/**
	 * The pulley driving this landing's shaft, found client-side by walking up. Public because
	 * {@code EvFloorRender} reads it to decide whether the call button is lit, which is
	 * upstream's arrangement.
	 */
	public EvPulleyTE pulley;
	private AABB renderAABB;

	public EvFloorTE(BlockPos pos, BlockState state) {
		super(LeafiaBlockEntities.EV_FLOOR.get(), pos, state);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, EvFloorTE te) {
		te.updateEntity();
	}

	public LeafiaPacket.Builder writePaint(LeafiaPacket.Builder packet) {
		if(paintBlock != null) {
			packet.__write(idPaintBlock, BuiltInRegistries.BLOCK.getKey(paintBlock).toString())
					.__write(idPaintMeta, paintMeta);
		}
		return packet;
	}

	/**
	 * Tells the client to re-mesh the surround after the paint has changed.
	 *
	 * <p>Called from <b>both</b> halves of the paint packet rather than only the second. The two
	 * always arrive together today, because {@link #writePaint} writes the metadata only when
	 * there is a block to write it for — but a re-mesh that depends on which of two fields
	 * happens to land last is a fragile thing to leave in place.
	 *
	 * <p>What actually re-meshes is {@code sendBlockUpdated} on the <b>core</b>, which dirties
	 * the section it sits in; the blocks being re-meshed are the dummies around it, which have
	 * no block entity of their own to update (D-1042). A landing is three wide and three tall,
	 * so its dummies share the core's section in every ordinary placement — and where a landing
	 * straddles a section boundary the far dummies keep the old paint until something else
	 * dirties their section. Upstream has exactly the same limitation, for the same reason.
	 */
	private void repaint() {
		setChanged();
		requestModelDataUpdate();
		if(level != null)
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
	}

	public void syncPaint() {
		writePaint(LeafiaPacket._start(this)).__sendToAffectedClients();
	}

	/**
	 * Tells one client to open the floor's screen. Upstream's {@code openGui}, verbatim.
	 *
	 * <p>The screen has no menu - it holds nothing - so there is no server-side container to
	 * open; the packet is the whole mechanism, and {@link #onReceivePacketLocal} acts on it.
	 * The only caller is {@link EvFloor#onScrew}, which a screwdriver reaches. See D-1077 for
	 * what that is waiting on.
	 */
	public void openGui(Player player) {
		editors.add(player.getUUID());
		LeafiaPacket._start(this).__write(idGui, 0).__sendToClient(player);
	}

	/** Every client type behind a holder a dedicated server never loads; D-1039 explains why. */
	@OnlyIn(Dist.CLIENT)
	private static final class ClientOnly {
		static void openGui(EvFloorTE te) {
			EvFloorGUI.open(te);
		}
	}

	@Override
	public String getPacketIdentifier() {
		return "EV_FLOOR";
	}

	@Override
	public void onReceivePacketLocal(byte key, Object value) {
		switch(key) {
			case idFloor -> floor = (int) value;
			case idGui -> {
				if(FMLEnvironment.dist.isClient()) ClientOnly.openGui(this);
			}
			case idOpen -> open.set((float) value).update();
			case idPaintBlock -> {
				paintBlock = BuiltInRegistries.BLOCK
						.getOptional(ResourceLocation.parse((String) value)).orElse(null);
				repaint();
			}
			case idPaintMeta -> {
				paintMeta = (int) value;
				repaint();
			}
			default -> { }
		}
	}

	@Override
	public void onReceivePacketServer(byte key, Object value, Player plr) {
		if(key == idFloor) {
			// D-1094: upstream stored whatever int it was sent, from anyone who could name the block.
			if(plr == null || !editors.contains(plr.getUUID())) {
				PacketGuard.reject("EvFloorTE floor", plr, "the floor screen was not opened for them");
				return;
			}
			if(!PacketGuard.near(plr, worldPosition)) {
				PacketGuard.reject("EvFloorTE floor", plr, "out of reach");
				editors.remove(plr.getUUID());
				return;
			}
			if(!(value instanceof Integer number) || number < MIN_FLOOR || number > MAX_FLOOR) {
				PacketGuard.reject("EvFloorTE floor", plr, "floor " + value);
				return;
			}
			floor = number;
			LeafiaPacket._start(this).__write(idFloor, floor).__sendToAffectedClients();
		}
	}

	/**
	 * Upstream pushes the door position and the paint to a player who has just come into range.
	 *
	 * <p>Asked for by the client's validate sweep ({@code LeafiaValidateSweep}, D-3040) when a player
	 * comes within range. Until that landed this was dead, and a landing painted to look like stone
	 * showed as unpainted to a player who arrived later (D-1029); the floor number never needed it,
	 * because {@link #updateEntity} re-broadcasts it every tick.
	 */
	@Override
	public void onPlayerValidate(Player plr) {
		writePaint(LeafiaPacket._start(this).__write(idOpen, open.cur)).__sendToClient(plr);
	}

	/** 1024 blocks: a shaft is tall and a landing high above still has to animate. */
	@Override
	public double affectionRange() {
		return 1024;
	}

	/**
	 * Upstream's render box for the landing doors, which swing outside their own block.
	 *
	 * <p>Not an override — see {@code EvBufferTE#renderBounds()}; NeoForge 21.1 asks the
	 * renderer, not the block entity. D-1039.
	 */
	public AABB renderBounds() {
		if(renderAABB == null) {
			renderAABB = new AABB(
					worldPosition.getX() - 1, worldPosition.getY(), worldPosition.getZ() - 1,
					worldPosition.getX() + 2, worldPosition.getY() + 3, worldPosition.getZ() + 2);
		}
		return renderAABB;
	}

	public void updateEntity() {

		if(open.needsUpdate())
			LeafiaPacket._start(this).__write(idOpen, open.update()).__sendToAffectedClients();

		if(level.isClientSide) {
			if(pulley != null && pulley.isRemoved()) pulley = null;
			if(pulley == null) {
				BlockPos centerPos = worldPosition.relative(EvFloor.facingOf(getBlockState()).getOpposite());
				pulley = EvFloor.getPulley(level, centerPos);
			}
		} else {
			LeafiaPacket._start(this).__write(idFloor, floor).__sendToClients(32);
		}
	}

	@Override
	protected void saveAdditional(CompoundTag compound, HolderLookup.Provider registries) {
		super.saveAdditional(compound, registries);
		compound.putByte("floor", (byte) floor);
		if(paintBlock != null) {
			compound.putString("paintBlock", BuiltInRegistries.BLOCK.getKey(paintBlock).toString());
			compound.putByte("paintMeta", (byte) paintMeta);
		}
	}

	@Override
	protected void loadAdditional(CompoundTag compound, HolderLookup.Provider registries) {
		super.loadAdditional(compound, registries);
		this.floor = compound.getByte("floor");
		if(compound.contains("paintBlock")) {
			this.paintBlock = BuiltInRegistries.BLOCK
					.getOptional(ResourceLocation.parse(compound.getString("paintBlock"))).orElse(null);
			this.paintMeta = compound.getByte("paintMeta");
		}
	}
}
