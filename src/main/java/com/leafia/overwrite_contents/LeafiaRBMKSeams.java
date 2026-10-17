package com.leafia.overwrite_contents;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import com.hbm.blockentity.machine.rbmk.BlockEntityRBMKBase;
import com.hbm.blockentity.machine.rbmk.BlockEntityRBMKConsole;
import com.hbm.blockentity.machine.rbmk.BlockEntityRBMKConsole.ColumnType;
import com.hbm.blockentity.machine.rbmk.BlockEntityRBMKConsole.RBMKColumn;
import com.hbm.blockentity.machine.rbmk.BlockEntityRBMKControlAuto;
import com.hbm.blockentity.machine.rbmk.BlockEntityRBMKRod;
import com.hbm.blockentity.machine.rbmk.RBMKDials;
import com.hbm.blockentity.machine.rbmk.RBMKHooks;
import com.hbm.blocks.fluid.CoriumFinite;
import com.hbm.blocks.machine.rbmk.RBMKBase;
import com.hbm.blocks.machine.rbmk.RBMKDebris;
import com.hbm.entity.projectile.EntityDebrisBase;
import com.hbm.items.ModItems;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.machines.reactors.pwr.PWRTicks;
import com.leafia.contents.machines.reactors.rbmk.RBMKConstants;
import com.leafia.contents.machines.reactors.rbmk.debris.RBMKDebrisSmoke;
import com.leafia.contents.machines.reactors.rbmk.debris.RBMKDebrisSmokeTE;
import com.leafia.dev.LeafiaUtil;
import com.leafia.dev.optimization.LeafiaParticlePacket.JumpingRBMKParticle;
import com.leafia.dev.optimization.LeafiaParticlePacket.RBMKJetParticle;
import com.leafia.init.LeafiaSounds;
import com.leafia.overwrite_contents.interfaces.IMixinRBMKColumn$ControlColumn;
import com.leafia.overwrite_contents.interfaces.IMixinTileEntityRBMKBase;
import com.leafia.overwrite_contents.interfaces.IMixinTileEntityRBMKControlAuto;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Where the addon plugs into the base mod's RBMK seams (D-2197): every server-side RBMK mixin upstream had,
 * as one {@link RBMKHooks.Hook} and one {@link EntityDebrisBase.SmashHandler}, registered once from the mod
 * constructor. Not an upstream file; D-1001 makes each mixin into {@code com.hbm} a seam consumer (D-3018).
 *
 * <table>
 * <tr><th>upstream</th><th>here</th></tr>
 * <tr><td>{@code MixinTileEntityRBMKBase} (update HEAD, NBT, sync, the meltdown's smoke)</td><td>{@link #tickColumn},
 * {@link #jump}, {@link #placeSmoke}; the state in the column's extra tag ({@link IMixinTileEntityRBMKBase})</td></tr>
 * <tr><td>{@code MixinTileEntityRBMKRod} (the {@code maxHeat}/{@code meltdown} redirects)</td><td>{@link #rodMeltdownHeat},
 * {@code preventOverheatMeltdown}</td></tr>
 * <tr><td>{@code MixinTileEntityRBMKControlAuto} (scram flag, acknowledge, console data)</td><td>{@code autoTargetLevel},
 * {@link #acknowledge}, {@code addConsoleData}</td></tr>
 * <tr><td>{@code MixinTileEntityRBMKConsole} (the AZ-5's extra key)</td><td>{@link #scramAll}</td></tr>
 * <tr><td>{@code MixinRBMKColumn$ControlColumn} (the tooltip line)</td><td>{@code addFancyStats}</td></tr>
 * <tr><td>{@code MixinEntityDebrisBase}, {@code MixinEntityRBMKDebris}</td><td>{@link #SMASH}</td></tr>
 * <tr><td>{@code MixinRenderRBMKLid} (the four fuels' colours)</td><td>{@link #cherenkovIndex}, written here and drawn by
 * {@code LeafiaRBMKClient}</td></tr>
 * </table>
 *
 * <p>Every hook reads {@link LeafiaFeatures#rbmkOverhaul} when it runs, so with the switch off (the default)
 * every reactor is the base mod's - except at a position in {@link #ARMED}, which is how a GameTest switches the
 * overhaul on for its own lattice without turning it on for every other test's reactor in the batch.
 */
public final class LeafiaRBMKSeams {

	private LeafiaRBMKSeams() {}

	/** GameTest seam: positions where the overhaul runs whatever the switch says. Nothing else adds to it. */
	public static final Set<BlockPos> ARMED = ConcurrentHashMap.newKeySet();

	/** Whether the overhaul acts at this position: the switch, or a test's armed lattice. */
	public static boolean active(BlockPos pos) {
		return LeafiaFeatures.rbmkOverhaul || ARMED.contains(pos);
	}

	private static boolean installed = false;

	public static synchronized void install() {
		if(installed) return;
		installed = true;
		RBMKHooks.register(HOOK);
		EntityDebrisBase.addSmashHandler(SMASH);
	}

	// ------------------------------------------------------------------ MixinTileEntityRBMKBase

	/** The heat above which damage stops being forgiven and the ramp's extra damage begins: upstream's 2000 and 3000. */
	public static final double HEAT_ALWAYS_DAMAGES = 2000;
	public static final double HEAT_EXTRA_DAMAGE = 3000;
	public static final double HEAT_EXTRA_STEP = 2000;

	/** Upstream's {@code @Unique} fields that were neither saved nor synced: one per column, gone with it. */
	static final class JumpState {
		boolean falling = false;
		float downwardSpeed = 0;
		double lastHeat = 20;
	}

	private static final Map<BlockEntityRBMKBase, JumpState> STATE = new WeakHashMap<>();

	static synchronized JumpState state(BlockEntityRBMKBase column) {
		return STATE.computeIfAbsent(column, c -> new JumpState());
	}

	/**
	 * One tick of upstream's damage counter. Above the column's rated heat (meltdowns allowed), a column
	 * takes {@code 2 + floor(max(0, heat - 3000) / 2000)} while it is still heating or at 2000 and over, and
	 * nothing while it cools from below 2000; at or below its rated heat it heals one a tick.
	 */
	public static int nextDamage(int damage, double heat, double lastHeat, double maxHeat, boolean meltdownsDisabled) {
		if(heat > maxHeat && !meltdownsDisabled) {
			if(heat >= lastHeat || heat >= HEAT_ALWAYS_DAMAGES)
				damage += IMixinTileEntityRBMKBase.dmgIncrement + (int) (Math.max(0, heat - HEAT_EXTRA_DAMAGE) / HEAT_EXTRA_STEP);
		} else {
			damage = Math.max(damage - 1, 0);
		}
		return damage;
	}

	/** Upstream's update HEAD: the counter, the meltdown past {@code maxDamage}, and the lid. Server only. */
	public static void tickColumn(BlockEntityRBMKBase column) {

		Level level = column.getLevel();
		if(!(level instanceof ServerLevel server)) return;

		JumpState st = state(column);
		int before = IMixinTileEntityRBMKBase.leafia$getDamage(column);
		double jumpBefore = IMixinTileEntityRBMKBase.leafia$jumpHeight(column);

		int damage = nextDamage(before, column.heat, st.lastHeat, column.maxHeat(), RBMKDials.getMeltdownsDisabled(level));
		IMixinTileEntityRBMKBase.leafia$setDamage(column, damage);

		if(damage > IMixinTileEntityRBMKBase.maxDamage) {
			// Upstream goes on to the lid of a column it has just melted; the base update stops here (D-3018).
			column.meltdown();
			return;
		}

		st.lastHeat = column.heat;
		jump(server, column, st);

		if(damage != before || IMixinTileEntityRBMKBase.leafia$jumpHeight(column) != jumpBefore) column.setChanged();
	}

	/** Every horizontal neighbour is a solid, visible, full cube or another RBMK block: nothing would show the lid's sides. */
	public static boolean checkSurrounding(Level level, BlockPos p) {
		for(Direction facing : Direction.Plane.HORIZONTAL) {
			BlockState state = level.getBlockState(p.relative(facing));
			if(!LeafiaUtil.isSolidVisibleCube(state) && !(state.getBlock() instanceof RBMKBase))
				return false;
		}
		return true;
	}

	/** The top block of the column whose core is at {@code pos}: up while the block above is the same block. */
	public static BlockPos columnTop(Level level, BlockPos pos) {
		Block block = level.getBlockState(pos).getBlock();
		BlockPos top = pos;
		while(level.getBlockState(top.above()).getBlock() == block) top = top.above();
		return top;
	}

	/** How much a lid rises a tick at this damage: {@code (int)(sqrt(damage / maxDamage) x 100) x 0.0005}. */
	public static double riseStep(int damage) {
		return riseDamage(damage) * 0.0005D;
	}

	/** How high a lid rises before it falls: {@code (int)(sqrt(damage / maxDamage) x 100) x 0.005}, ten steps. */
	public static double riseLimit(int damage) {
		return riseDamage(damage) * 0.005D;
	}

	private static int riseDamage(int damage) {
		return (int) (Math.pow(damage / (double) IMixinTileEntityRBMKBase.maxDamage, 0.5) * 100);
	}

	/** {@code nextInt}'s bound for a lid at rest to start rising: one chance in {@code max(1, (maxDamage - damage) / 3 + 5)}. */
	public static int jumpChanceBound(int damage) {
		return Math.max(1, (IMixinTileEntityRBMKBase.maxDamage - damage) / 3 + 5);
	}

	/**
	 * "@author Alcater &amp; Leafia": a damaged column's lid rises linearly to a height its damage sets and
	 * falls back under gravity, then lands with a clank. A column whose sides would show (any horizontal
	 * neighbour at its base, one above, or its top that is not a solid cube or another column) never jumps.
	 */
	static void jump(ServerLevel level, BlockEntityRBMKBase column, JumpState st) {

		int damage = IMixinTileEntityRBMKBase.leafia$getDamage(column);
		double jump = IMixinTileEntityRBMKBase.leafia$jumpHeight(column);

		if(damage <= 0 && !st.falling && jump <= 0)
			return;

		// do not jump if it has exposed sides that would reveal Z-fighting of jumping rods
		BlockPos pos = column.getBlockPos();
		BlockPos topPos = columnTop(level, pos);
		if(!checkSurrounding(level, pos) || !checkSurrounding(level, pos.above()) || !checkSurrounding(level, topPos))
			return;

		if(!st.falling) { // linear rise
			if(damage > 0) {
				int rand = level.random.nextInt(jumpChanceBound(damage));
				if(jump > 0 || rand == 0) {
					if(jump == 0) {
						new RBMKJetParticle(20 + level.random.nextInt(10)).emit(level,
								new Vec3(pos.getX() + 0.5, pos.getY() + RBMKDials.getColumnHeight(level) - 1, pos.getZ() + 0.5),
								new Vec3(0, 1, 0), 1000);
						new JumpingRBMKParticle(pos).emitServer(level);
					}
					jump = jump + riseStep(damage);

					if(jump > riseLimit(damage)) {
						jump = riseLimit(damage);
						st.falling = true;
					}
				}
			} else {
				st.falling = true;
			}
		} else { // gravity fall
			if(jump > 0) {
				st.downwardSpeed = st.downwardSpeed + RBMKConstants.gravity * 0.05F;
				jump = Math.max(jump - st.downwardSpeed, 0);
			} else {
				jump = 0;
				st.downwardSpeed = 0;
				st.falling = false;
				level.playSound(null, pos.getX(), pos.getY() + 4, pos.getZ(), LeafiaSounds.rbmkLid.get(), SoundSource.BLOCKS, 2.0F, 1.0F);
			}
		}

		IMixinTileEntityRBMKBase.leafia$setJumpHeight(column, jump);
	}

	/** For tests: whether the column's lid is on its way down, and how fast. */
	public static boolean isFalling(BlockEntityRBMKBase column) {
		return state(column).falling;
	}

	public static float downwardSpeed(BlockEntityRBMKBase column) {
		return state(column).downwardSpeed;
	}

	/**
	 * Upstream's injection before the meltdown's sound: the first RBMK debris or corium found going down the
	 * reactor's centre column, from one above the columns' tops, becomes smoking debris of half the reactor's
	 * mean width. "Down to 0" is the level's floor here (D-408).
	 */
	public static void placeSmoke(Level level, BlockPos origin, int minX, int maxX, int minZ, int maxZ, int avgX, int avgZ) {
		if(LeafiaBlocks.pribris_smoke == null) return;

		float scale = ((maxX - minX) + (maxZ - minZ)) / 2f / 2f;

		for(int y = origin.getY() + RBMKDials.getColumnHeight(level) + 1; y >= level.getMinBuildHeight(); y--) {
			BlockPos bp = new BlockPos(avgX, y, avgZ);
			Block block = level.getBlockState(bp).getBlock();
			if(block instanceof RBMKDebris || block instanceof CoriumFinite) {
				level.setBlockAndUpdate(bp, LeafiaBlocks.pribris_smoke.get().defaultBlockState());
				if(level.getBlockEntity(bp) instanceof RBMKDebrisSmokeTE te)
					te.setScale(scale);
				break;
			}
		}
	}

	// ------------------------------------------------------------------ MixinTileEntityRBMKRod

	/**
	 * The fuel channel's meltdown heat. The overhaul melts a reactor by damage, not by heat: a channel whose
	 * damage is past the limit reads {@code -100} (so it stops fissioning that tick, the redirected meltdown
	 * doing nothing), and otherwise it never melts by heat - unless meltdowns are disabled, when the dial's vent
	 * keeps upstream's threshold.
	 */
	public static double rodMeltdownHeat(BlockEntityRBMKRod rod, double heat) {
		if(IMixinTileEntityRBMKBase.leafia$getDamage(rod) > IMixinTileEntityRBMKBase.maxDamage)
			return -100; // fuck you
		if(RBMKDials.getMeltdownsDisabled(rod.getLevel()))
			return heat;
		return Double.POSITIVE_INFINITY;
	}

	/**
	 * The four fuels whose Cherenkov glow upstream recolours, as an index into {@link #CHERENKOV} (0 for any other
	 * fuel). Upstream read the rod's slot in the renderer; the base mod's client never sees the slot, so the
	 * server writes the index into the column's extra tag and the client hook reads it.
	 */
	public static int cherenkovIndex(Item item) {
		if(item == ModItems.rbmk_fuel_leaus.get() || item == ModItems.rbmk_fuel_heaus.get()) return 1;
		if(item == ModItems.rbmk_fuel_balefire_gold.get() || item == ModItems.rbmk_fuel_flashlead.get()) return 2;
		if(item == ModItems.rbmk_fuel_balefire.get()) return 3;
		if(item == ModItems.rbmk_fuel_drx.get()) return 4;
		return 0;
	}

	/** Upstream's colours: australium gold, antihydrogen violet, balefire green, digamma orange. */
	public static final float[][] CHERENKOV = {
			null,
			{ 1F, 0.9F, 0F },
			{ 0.6F, 0F, 1F },
			{ 0.25F, 1F, 0F },
			{ 1F, 0.25F, 0F },
	};

	public static final String KEY_CHERENKOV = "leafia_cherenkov";

	/** The colour a fuel channel's extra tag asks for, or null for the base mod's. Read by the client hook. */
	public static float[] cherenkovOf(BlockEntityRBMKBase column) {
		int index = column.getExtraData().getByte(KEY_CHERENKOV);
		return index > 0 && index < CHERENKOV.length ? CHERENKOV[index] : null;
	}

	static void tagCherenkov(BlockEntityRBMKRod rod) {
		int index = rod.slots[0].isEmpty() ? 0 : cherenkovIndex(rod.slots[0].getItem());
		CompoundTag tag = rod.getExtraData();
		if(index == 0) tag.remove(KEY_CHERENKOV);
		else tag.putByte(KEY_CHERENKOV, (byte) index);
	}

	// ------------------------------------------------------------------ MixinTileEntityRBMKControlAuto

	/** The key the rod's screen sends: false acknowledges this rod, true every automatic rod in its reactor. */
	public static final String KEY_ACKNOWLEDGE = "acknowledge";

	/** Upstream's {@code receiveControl} HEAD: clears the scram on this rod, or on every one joined to it. */
	public static void acknowledge(BlockEntityRBMKControlAuto rod, boolean all) {
		IMixinTileEntityRBMKControlAuto.leafia$setScrammed(rod, false);
		rod.setChanged();
		if(all) {
			for(BlockEntityRBMKBase column : BlockEntityRBMKBase.connectedColumns(rod.getLevel(), rod.getBlockPos())) {
				if(column instanceof BlockEntityRBMKControlAuto auto) {
					IMixinTileEntityRBMKControlAuto.leafia$setScrammed(auto, false);
					auto.setChanged();
				}
			}
		}
	}

	// ------------------------------------------------------------------ MixinTileEntityRBMKConsole

	/** Upstream's key for the AZ-5's extra order, which {@code LeafiaRBMKClient}'s screen hook adds to the packet. */
	public static final String KEY_AZ5_ALL = "fuckingstopalldamnautocontrolrods";

	/** The console's reach: upstream's {@code -7..7} on both axes around its target, at the target's height. */
	public static final int CONSOLE_REACH = 7;

	/** Every automatic rod in the console's 15x15 grid is shut off. Upstream does not stop the AZ-5's own packet. */
	public static void scramAll(BlockEntityRBMKConsole console) {
		Level level = console.getLevel();
		BlockPos target = console.getTarget();
		for(int i = -CONSOLE_REACH; i <= CONSOLE_REACH; i++) {
			for(int j = -CONSOLE_REACH; j <= CONSOLE_REACH; j++) {
				BlockEntity te = level.getBlockEntity(new BlockPos(target.getX() + i, target.getY(), target.getZ() + j));
				if(te instanceof BlockEntityRBMKControlAuto auto) {
					IMixinTileEntityRBMKControlAuto.leafia$setScrammed(auto, true);
					te.setChanged();
				}
			}
		}
	}

	// ------------------------------------------------------------------ MixinRBMKColumn$ControlColumn

	/** Upstream's tooltip line, an English literal there too. */
	public static final String SHUT_OFF = ChatFormatting.GOLD + "Rod is shut off";

	// ------------------------------------------------------------------ the hook

	public static final RBMKHooks.Hook HOOK = new RBMKHooks.Hook() {

		@Override
		public void onServerTick(BlockEntityRBMKBase column) {
			if(!active(column.getBlockPos())) return;
			if(column instanceof BlockEntityRBMKRod rod) tagCherenkov(rod);
			tickColumn(column);
		}

		@Override
		public double meltdownHeat(BlockEntityRBMKRod rod, double heat) {
			return active(rod.getBlockPos()) ? rodMeltdownHeat(rod, heat) : heat;
		}

		@Override
		public boolean preventOverheatMeltdown(BlockEntityRBMKRod rod) {
			// The redirected meltdown() does nothing: a fuel channel melts the reactor only through damage.
			return active(rod.getBlockPos());
		}

		@Override
		public double autoTargetLevel(BlockEntityRBMKControlAuto rod, double level) {
			if(active(rod.getBlockPos()) && IMixinTileEntityRBMKControlAuto.leafia$getScrammed(rod)) return 0;
			return level;
		}

		@Override
		public boolean receiveControl(BlockEntity receiver, CompoundTag data) {
			if(!active(receiver.getBlockPos())) return false;
			if(receiver instanceof BlockEntityRBMKControlAuto rod && data.contains(KEY_ACKNOWLEDGE)) {
				// acknowledge set to false means only this rod
				acknowledge(rod, data.getBoolean(KEY_ACKNOWLEDGE));
				return true;
			}
			if(receiver instanceof BlockEntityRBMKConsole console && data.contains(KEY_AZ5_ALL)) {
				scramAll(console);
			}
			return false;
		}

		@Override
		public void onMeltdown(RBMKHooks.Meltdown m) {
			if(!active(m.origin().getBlockPos())) return;
			placeSmoke(m.origin().getLevel(), m.origin().getBlockPos(), m.minX(), m.maxX(), m.minZ(), m.maxZ(), m.avgX(), m.avgZ());
		}

		@Override
		public void addConsoleData(BlockEntityRBMKBase column, CompoundTag data) {
			if(!active(column.getBlockPos())) return;
			if(column instanceof BlockEntityRBMKControlAuto auto && IMixinTileEntityRBMKControlAuto.leafia$getScrammed(auto))
				data.putBoolean(IMixinRBMKColumn$ControlColumn.KEY_EXCLAMATION, true);
		}

		@Override
		public void addFancyStats(RBMKColumn column, List<String> stats) {
			if(column.type == ColumnType.CONTROL_AUTO && IMixinRBMKColumn$ControlColumn.leafia$getExclamation(column))
				stats.add(SHUT_OFF);
		}
	};

	// ------------------------------------------------------------------ MixinEntityDebrisBase, MixinEntityRBMKDebris

	/**
	 * Debris tearing through blocks: smoking debris is never torn out ("prevent debris breaking smoke block"),
	 * and anything else that is torn out lets the blocks above it fall - one tick in five at once, otherwise on
	 * the next server tick. Upstream's other redirect, the block ray only for a piece that {@code destroysBlocks},
	 * is the base mod's own rule already. One handler for both mixins: upstream's RBMK debris was not a
	 * {@code EntityDebrisBase} in NTM-CE, and the second mixin is not even in its mixin list, but the base mod's
	 * is one, so this covers both.
	 */
	public static final EntityDebrisBase.SmashHandler SMASH = (debris, level, pos, state) -> {
		if(!active(pos)) return false;
		if(state.getBlock() instanceof RBMKDebrisSmoke) return true;
		BlockPos up = pos.above();
		Vec3 vel = debris.getDeltaMovement();
		if(level.random.nextInt(5) == 0)
			fallBlock(level, up, vel, 0);
		else
			PWRTicks.queueFunction(() -> fallBlock(level, up, vel, 0));
		return false;
	};

	/** How many blocks above a torn-out one may fall as blocks; past it they are deleted. Upstream's {@code count < 5}. */
	public static final int FALL_LIMIT = 5;

	/**
	 * Upstream's {@code leafia$fallBlock}: over a non-solid block, a solid visible cube becomes a falling block
	 * with the debris' velocity (first only) and anything else that is not air is deleted, and either way the
	 * block above is tried next tick. The falling block is spawned at once with the block's removal, where
	 * upstream removed the block and spawned it a tick later; 1.21.1's {@link FallingBlockEntity#fall} does both.
	 */
	public static void fallBlock(Level level, BlockPos pos, Vec3 velocity, int count) {
		if(level.isClientSide) return;
		if(!level.getBlockState(pos.below()).isSolid()) {
			boolean fallNextBlock = false;
			BlockState state = level.getBlockState(pos);
			if(LeafiaUtil.isSolidVisibleCube(state) && count < FALL_LIMIT) {
				FallingBlockEntity falling = FallingBlockEntity.fall(level, pos, state);
				falling.time = 1;
				if(velocity != null) falling.setDeltaMovement(velocity);
				fallNextBlock = true;
			} else if(!state.isAir()) {
				fallNextBlock = true;
				level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
			}
			BlockPos up = pos.above();
			if(fallNextBlock)
				PWRTicks.queueFunction(() -> fallBlock(level, up, null, count + 1));
		}
	}
}
