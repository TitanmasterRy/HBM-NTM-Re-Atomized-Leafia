package com.leafia.contents.building.doors;

import com.hbm.blockentity.generic.BlockEntityDoorGeneric;
import com.hbm.blockentity.generic.DoorDecl;
import com.hbm.render.anim.BusAnimation;
import com.hbm.render.anim.BusAnimationKeyframe.IType;
import com.hbm.render.anim.BusAnimationSequence;
import com.hbm.render.blockentity.door.IRenderDoors;
import com.leafia.contents.building.doors.renderers.CrimsonDoorLargeRender;
import com.leafia.contents.building.doors.renderers.CrimsonDoorSmallRender;
import com.leafia.contents.building.doors.renderers.ReactorDoorRender;
import com.leafia.init.LeafiaSounds;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.AABB;

/**
 * What the addon's three doors are made of: sizes, timings, sounds, animation and shape.
 *
 * <p>Upstream: {@code com/leafia/contents/building/doors/AddonDoorDecl.java}. A {@link DoorDecl} is the
 * strategy object {@code BlockDoorGeneric} and {@code BlockEntityDoorGeneric} are generic over, so
 * the three doors need no block or block-entity logic of their own beyond what they add; this is
 * where their numbers live.
 *
 * <h2>What changed in the translation</h2>
 *
 * <ul>
 * <li><b>The animation classes.</b> {@code BusAnimationSedna} and {@code BusAnimationSequenceSedna}
 *     are NTM-CE's names for the animation the base mod has as {@link BusAnimation} and
 *     {@link BusAnimationSequence}; the keyframe types are the same ({@code SIN_FULL},
 *     {@code SIN_UP}) and so are the call shapes. {@code DoorState} is a byte here -
 *     {@link BlockEntityDoorGeneric#STATE_OPENING} and {@link BlockEntityDoorGeneric#STATE_CLOSING}
 *     are the two upstream compares against.</li>
 * <li><b>The shape.</b> Upstream's {@code getBlockBound(BlockPos relPos, boolean open)} is the base
 *     mod's {@code getBlockBound(int x, int y, int z, boolean open, boolean forCollision)}: the same
 *     position relative to the core, rotated into the door's own frame, split into three ints, and
 *     one more flag the addon's doors do not read. Every box is upstream's.</li>
 * <li><b>The skins.</b> Upstream's {@code getDefaultSkins()} returns seven textures and the base
 *     mod's {@code DoorDecl} asks only how many ({@code getSkinCount}); the renderer picks the
 *     texture, so the seven live in {@link AddonDefaultSkins} and the reactor door says seven.</li>
 * </ul>
 *
 * <h2>The reactor door's numbers</h2>
 *
 * 109 ticks to open, and the animation inside it is 4,350 ms against 5,450 ms of door: the leaf
 * waits 43 ticks (2,150 ms) while the handle turns, then swings 44 ticks (2,200 ms) on a sine. The
 * handle turns 0.25 of its travel in 150 ms, creeps to 0.75 over 700 ms and finishes in 150 ms
 * ({@code handleLockDuration = 3} ticks, {@code handleLockAmount = 0.25}). Closing is the mirror
 * with the leaf first - 44 ticks of swing, then the handle held for 66 ticks (3,300 ms) before it
 * turns back. {@code ReactorDoorTE} times its sounds and shakes to the same tick counts.
 */
public abstract class AddonDoorDecl extends DoorDecl {

	/**
	 * The reactor door's seven finishes, in upstream's order. These are texture files rather than
	 * atlas sprites: the renderer draws the door on its own render type, so they are looked up by
	 * path. See D-3005.
	 */
	public static class AddonDefaultSkins {
		public static final ResourceLocation reactordoor_normal = skin("reactordoor");
		public static final ResourceLocation reactordoor_plain = skin("reactordoor_trefoilless");
		public static final ResourceLocation reactordoor_yellow = skin("reactordoor_yellow");
		public static final ResourceLocation reactordoor_gray = skin("reactordoor_gray");
		public static final ResourceLocation reactordoor_gray_plain = skin("reactordoor_gray_trefoilless");
		public static final ResourceLocation reactordoor_dark_gray = skin("reactordoor_but_grayer");
		public static final ResourceLocation reactordoor_green_plain = skin("reactordoor_geen_trefoilless");

		/** All seven, which is what the skin index counts through. */
		public static final ResourceLocation[] ALL = {
				reactordoor_normal, reactordoor_plain, reactordoor_yellow, reactordoor_gray,
				reactordoor_gray_plain, reactordoor_dark_gray, reactordoor_green_plain
		};

		private static ResourceLocation skin(String name) {
			return ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID, "textures/block/" + name + ".png");
		}
	}

	/** Upstream's {@code shakeIntensityOpen}: the shake's duration in seconds as the command's string, or null for none. */
	public String shakeIntensityOpen() {
		return null;
	}

	/** Upstream's {@code shakeIntensityClose}, which is the shake's intensity. */
	public String shakeIntensityClose() {
		return null;
	}

	public String shakeRange() {
		return "12";
	}

	/** Upstream's {@code handleLockDuration}, in ticks. */
	public static final int HANDLE_LOCK_DURATION = 3;
	/** Upstream's {@code handleLockAmount}. */
	public static final double HANDLE_LOCK_AMOUNT = 0.25;

	/**
	 * The reactor door.
	 *
	 * <p>Upstream declares this one as a plain {@code DoorDecl}, not an {@code AddonDoorDecl}, so it
	 * has no shake settings of its own: {@code ReactorDoorTE} sends its shakes itself, at fixed
	 * ticks.
	 */
	public static final DoorDecl REACTOR_DOOR = new DoorDecl() {

		@Override
		public IRenderDoors getSEDNARenderer() {
			return ReactorDoorRender.INSTANCE;
		}

		@Override
		public int getSkinCount() {
			return AddonDefaultSkins.ALL.length;
		}

		@Override
		public BusAnimation getBusAnimation(byte state, byte skinIndex) {
			if(state == BlockEntityDoorGeneric.STATE_OPENING) {
				return new BusAnimation()
						.addBus("HANDLE", new BusAnimationSequence()
								.setPos(0, 0, 0)
								.addPos(0, HANDLE_LOCK_AMOUNT, 0, HANDLE_LOCK_DURATION * 50, IType.SIN_FULL)
								.addPos(0, 1 - HANDLE_LOCK_AMOUNT, 0, (20 - HANDLE_LOCK_DURATION * 2) * 50)
								.addPos(0, 1, 0, HANDLE_LOCK_DURATION * 50, IType.SIN_FULL))
						.addBus("DOOR", new BusAnimationSequence()
								.setPos(0, 0, 0)
								.addPos(0, 0, 0, 43 * 50)
								.addPos(0, 1, 0, 44 * 50, IType.SIN_FULL));
			}
			if(state == BlockEntityDoorGeneric.STATE_CLOSING) {
				return new BusAnimation()
						.addBus("DOOR", new BusAnimationSequence()
								.setPos(0, 1, 0)
								.addPos(0, 0, 0, 44 * 50, IType.SIN_UP))
						.addBus("HANDLE", new BusAnimationSequence()
								.setPos(0, 1, 0)
								.addPos(0, 1, 0, (23 + 43) * 50)
								.addPos(0, 1 - HANDLE_LOCK_AMOUNT, 0, HANDLE_LOCK_DURATION * 50, IType.SIN_FULL)
								.addPos(0, HANDLE_LOCK_AMOUNT, 0, (20 - HANDLE_LOCK_DURATION * 2) * 50)
								.addPos(0, 0, 0, HANDLE_LOCK_DURATION * 50, IType.SIN_FULL));
			}
			return null;
		}

		@Override
		public int timeToOpen() {
			return 86 + 23;
		}

		/*
		 * Upstream's own note: "someone decipher this please" - it is startX, startY, startZ, the
		 * number of columns the door opens through (negative runs the other way), the width or
		 * height of each, and the axis. It cannot do a door thicker than one block.
		 */
		@Override
		public int[][] getDoorOpenRanges() {
			return new int[][] {{0, 0, 1, -2, 2, 0}};
		}

		@Override
		public int[] getDimensions() {
			return new int[] {2, 0, 1, 0, 1, 1};
		}
	};

	/** Both crimson doors: the same four sounds, and the same animation shape at their own lengths. */
	private abstract static class CrimsonDoorDecl extends AddonDoorDecl {

		/** The animation's length in ticks; upstream writes the milliseconds as {@code 50 * ticks}. */
		private final int ticks;

		/** The column that holds the stacked leaf: z = +-1 for the small door and z = +-3 for the large one. */
		private final int edge;

		CrimsonDoorDecl(int ticks, int edge) {
			this.ticks = ticks;
			this.edge = edge;
		}

		// Upstream names these "Loop" and "End" and fills Loop with the clip called "start". Kept:
		// the base mod's handleNewState plays the loop on the first transition and the end on the last.
		@Override
		public Holder<SoundEvent> getOpenSoundLoop() {
			return LeafiaSounds.crimDoorOpenStart;
		}

		@Override
		public Holder<SoundEvent> getOpenSoundEnd() {
			return LeafiaSounds.crimDoorOpenEnd;
		}

		@Override
		public Holder<SoundEvent> getCloseSoundLoop() {
			return LeafiaSounds.crimDoorCloseStart;
		}

		@Override
		public Holder<SoundEvent> getCloseSoundEnd() {
			return LeafiaSounds.crimDoorCloseEnd;
		}

		@Override
		public BusAnimation getBusAnimation(byte state, byte skinIndex) {
			if(state == BlockEntityDoorGeneric.STATE_OPENING)
				return new BusAnimation().addBus("DOOR", new BusAnimationSequence()
						.setPos(0, 0, 0)
						.addPos(0, 1, 0, 50 * ticks, IType.SIN_FULL));
			if(state == BlockEntityDoorGeneric.STATE_CLOSING)
				return new BusAnimation().addBus("DOOR", new BusAnimationSequence()
						.setPos(0, 1, 0)
						.addPos(0, 0, 0, 50 * ticks, IType.SIN_UP));
			return null;
		}

		@Override
		public int timeToOpen() {
			return ticks;
		}

		/**
		 * Upstream writes the same four lines in both doors, differing only in {@link #edge}.
		 *
		 * <p>Open, the two outermost columns keep a 9/16 slab on their outer side (where the leaf
		 * has slid in) and the top row keeps a 5/16 lintel; everything else is empty. Shut, the
		 * default applies - a full block.
		 */
		@Override
		public AABB getBlockBound(int x, int y, int z, boolean open, boolean forCollision) {
			if(!open)
				return super.getBlockBound(x, y, z, open, forCollision);
			if(z == edge)
				return new AABB(1 - sixteenths(9), 0, 0, 1, 1, 1);
			else if(z == -edge)
				return new AABB(0, 0, 0, sixteenths(9), 1, 1);
			else if(y == 2)
				return new AABB(0, 1 - sixteenths(5), 0, 1, 1, 1);
			return super.getBlockBound(x, y, z, open, forCollision);
		}
	}

	/** The pixel widths upstream writes as {@code 0.0625f * n}, in float as it does. */
	private static double sixteenths(int n) {
		return 0.0625f * n;
	}

	public static final AddonDoorDecl CRIMSON_DOOR_SMALL = new CrimsonDoorDecl(40, 1) {

		@Override
		public String shakeIntensityClose() {
			return "0.2";
		}

		@Override
		public String shakeRange() {
			return "20";
		}

		@Override
		public IRenderDoors getSEDNARenderer() {
			return CrimsonDoorSmallRender.INSTANCE;
		}

		@Override
		public int[][] getDoorOpenRanges() {
			return new int[][] {{0, 0, 0, -2, 3, 2}, {0, 0, 0, 2, 3, 2}};
		}

		@Override
		public int[] getDimensions() {
			return new int[] {2, 0, 0, 0, 1, 1};
		}
	};

	public static final AddonDoorDecl CRIMSON_DOOR_LARGE = new CrimsonDoorDecl(54, 3) {

		@Override
		public String shakeIntensityClose() {
			return "0.4";
		}

		@Override
		public String shakeRange() {
			return "30";
		}

		@Override
		public IRenderDoors getSEDNARenderer() {
			return CrimsonDoorLargeRender.INSTANCE;
		}

		@Override
		public int[][] getDoorOpenRanges() {
			return new int[][] {{0, 0, 0, -4, 3, 2}, {0, 0, 0, 4, 3, 2}};
		}

		@Override
		public int[] getDimensions() {
			return new int[] {2, 0, 0, 0, 3, 3};
		}
	};
}
