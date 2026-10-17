package com.leafia.contents.building.doors;

import com.hbm.blockentity.generic.BlockEntityDoorGeneric;
import com.leafia.init.LeafiaBlockEntities;
import com.leafia.passive.effects.LeafiaShakecam;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The block entity of the two crimson doors: the base mod's door, plus a camera shake when it
 * finishes moving.
 *
 * <p>Upstream: {@code com/leafia/contents/building/doors/AddonDoorTE.java}, whose whole body is an
 * {@code update()} that runs the base's and then, if the state changed, sends a shake to everyone
 * within ten blocks. The three shake settings come off the {@link AddonDoorDecl}; the shake is
 * sent when the door becomes <em>open</em> if that decl has an open shake and when it becomes
 * <em>closed</em> if it has a close shake. Neither crimson door has an open shake, so each
 * shakes once, when it slams shut: intensity 0.2 over 20 blocks for the small door and 0.4 over 30
 * for the large.
 *
 * <h2>Why this subclasses the door, and has its own type anyway</h2>
 *
 * A block entity's type names the blocks it may sit in, and the base door's type names only the base
 * mod's. The base class has no constructor that takes a type, so a subclass cannot hand
 * {@code super} its own. What NeoForge offers instead is {@link #getType()}: {@code BlockEntity}
 * itself says "always use getType()" and validates, saves and ticks through it, "so correct type is
 * checked for modded subclasses". Overriding it is therefore the supported way to give a subclass its
 * own type, and the one this uses. A protected {@code (type, pos, state)} constructor on
 * {@code BlockEntityDoorGeneric} would let this drop the override; it is the hand-off in D-3005.
 *
 * <h2>The base ticks privately</h2>
 *
 * {@code BlockEntityDoorGeneric.updateEntity} is private, so this cannot extend it the way
 * upstream's {@code update()} calls {@code super.update()}. {@link #tick} wraps the base's static
 * ticker instead - run it, compare the state before and after - which is what upstream's override
 * does. Upstream sends from whichever side runs {@code update}, but the state only changes on the
 * server (the client is told by packet), so the shake is sent from the server alone here.
 */
public class AddonDoorTE extends BlockEntityDoorGeneric {

	public AddonDoorTE(BlockPos pos, BlockState state) {
		super(pos, state);
	}

	@Override
	public BlockEntityType<?> getType() {
		return LeafiaBlockEntities.ADDON_DOOR.get();
	}

	/** Upstream's {@code shakeIntensityOpen}: null unless the decl says otherwise. */
	public String shakeIntensityOpen() {
		if(getDoorType() instanceof AddonDoorDecl decl)
			return decl.shakeIntensityOpen();
		return null;
	}

	public String shakeIntensityClose() {
		if(getDoorType() instanceof AddonDoorDecl decl)
			return decl.shakeIntensityClose();
		return null;
	}

	public String shakeRange() {
		if(getDoorType() instanceof AddonDoorDecl decl)
			return decl.shakeRange();
		return "12";
	}

	/** How far upstream's {@code TargetPoint} reaches: ten blocks, whatever the shake's own range. */
	public static final double SHAKE_PLAYER_RANGE = 10;

	/**
	 * The state the radiation system was last told about, or -1 before the first tick. CE's door tells
	 * it when a toggle starts and again when closing finishes; this sees the same transitions a tick
	 * later, by comparing, because the toggle happens in a player's click and not in the ticker.
	 */
	private byte rebuiltFor = -1;

	/** The server ticker the block hands out. */
	public static void tick(Level level, BlockPos pos, BlockState state, AddonDoorTE door) {

		byte previous = door.state;
		BlockEntityDoorGeneric.tick(level, pos, state, door);

		if(level.isClientSide) return;

		/*
		 * CE's door opens the core's own cell as well as the cells around it: its range walk sets
		 * shouldUseBB when it reaches the core, and clears it when the walk back does. This tree's
		 * base clears it on both walks (upstream 1.7.10 never read the flag), which would leave the
		 * core cell of an open doorway a solid block. The core is in both crimson doors' ranges
		 * (startPos 0,0,0), reached the moment the door starts to open and left only when it has
		 * shut, so the flag is "not shut". D-3005.
		 */
		door.shouldUseBB = door.state != STATE_CLOSED;

		door.tellRadiation(level, pos);

		if(door.state != previous && level instanceof ServerLevel server)
			door.shake(server);
	}

	private void tellRadiation(Level level, BlockPos pos) {
		rebuiltFor = AddonDoorGeneric.tellRadiation(level, pos, this, rebuiltFor);
	}

	/**
	 * Upstream's {@code if(getState() != prevState)} block, as the parameter strings it sends: the open
	 * shake if the door has just opened and its decl has one, the close shake if it has just shut and
	 * its decl has one, and null otherwise.
	 */
	public static String[] shakeFor(AddonDoorDecl decl, byte newState) {

		if(newState == STATE_OPEN && decl.shakeIntensityOpen() != null)
			return new String[] {
					"type=smooth",
					"intensity=0.15",
					"duration=" + decl.shakeIntensityOpen(),
					"speed=2",
					"blurDulling=50",
					"bloomDulling=50",
					"range=" + decl.shakeRange(),
					"curve=0.5"};

		if(newState == STATE_CLOSED && decl.shakeIntensityClose() != null)
			return new String[] {
					"type=smooth",
					"intensity=" + decl.shakeIntensityClose(),
					"duration=0.75",
					"speed=8",
					"blurDulling=50",
					"bloomDulling=50",
					"range=" + decl.shakeRange(),
					"curve=0.5"};

		return null;
	}

	private void shake(ServerLevel server) {
		if(!(getDoorType() instanceof AddonDoorDecl decl)) return;
		String[] params = shakeFor(decl, state);
		if(params != null)
			LeafiaShakecam.sendNear(server, worldPosition, SHAKE_PLAYER_RANGE, params);
	}
}
