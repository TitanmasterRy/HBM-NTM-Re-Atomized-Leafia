package com.leafia.dev;

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.annotation.Nullable;

import com.hbm.blockentity.BlockEntityProxyBase;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.items.machine.IItemFluidIdentifier;
import com.leafia.contents.network.FFNBT;
import com.leafia.contents.network.ff_duct.FFDuctTE;
import com.leafia.contents.network.ff_duct.uninos.IFFHandler;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Upstream's grab-bag of static helpers — <b>partially ported</b>, and deliberately so.
 *
 * <p>Upstream: {@code com/leafia/dev/LeafiaUtil.java}, 513 lines of helpers for entities,
 * inventories, NBT, vectors and shapes, written against 1.12.2 and consumed from all over the
 * addon. Porting it whole would mean porting each helper's 1.21.1 answer before anything needs
 * one, and several of them have no consumer here yet.
 *
 * <p>So this carries <b>one method</b>, the one the catwalk railing needs, <b>the full-cube test
 * structural integrity collapses by</b> (D-1086), <b>the scroll-bar
 * arithmetic</b> the advisor's list screen uses ({@link ScrollUtil}, D-1080), <b>and the two regex
 * helpers</b> the pneumatic filter is built on ({@link #matchesRegex}, {@link #isRegexValid},
 * D-1084), <b>and the blast knockback</b> the torex's shockwave pushes the player by
 * ({@link #getKnockback}, D-1089), <b>and the Forge-fluid duct's two</b>, {@link #canConnectFF}
 * and {@link #fillFF} (D-3012), and the ledger
 * records the file as partial. The pattern is the addon's own — {@code AddonFluids} and
 * {@code MachineTooltip} are both partial for the same reason — and it is preferred to a
 * hoist here because the method keeps upstream's name and home, so the rest arrives by
 * addition rather than by moving what is already in.
 */
public final class LeafiaUtil {

	private LeafiaUtil() {}

	/**
	 * Whether {@code regex} is a pattern Java can compile. Upstream's, line for line.
	 *
	 * <p>This is the whole of the check the filter's screen makes before it lets a player save:
	 * it says nothing about how long the pattern will take to run (D-1084).
	 */
	public static boolean isRegexValid(String regex) {
		try {
			Pattern.compile(regex);
			return true;
		} catch(PatternSyntaxException ignored) {
			return false;
		}
	}

	/**
	 * Whether the <b>whole</b> of {@code value} matches {@code regex} - {@link String#matches},
	 * not a search - and {@code false} for a pattern that does not compile. Upstream's.
	 *
	 * <p>So {@code stone} does not match {@code minecraft:stone}, and {@code .*stone} does. A
	 * pattern that cannot compile never throws: it simply matches nothing, which is why a
	 * <em>blacklist</em> holding one blocks nothing at all.
	 */
	public static boolean matchesRegex(String value, String regex) {
		try {
			return value.matches(regex);
		} catch(PatternSyntaxException ignored) {
			return false;
		}
	}

	/**
	 * A block that is a whole, solid, drawn cube a player could mistake for ground - the test
	 * upstream's structural integrity uses to decide between "falls" and "breaks".
	 *
	 * <p>Upstream's is {@code isFullCube() && material.isSolid() && !material.isReplaceable() &&
	 * renderType == MODEL}. There is no {@code Material} here: the full-cube test is "the
	 * collision shape is the whole block" and solidity is {@link BlockState#isSolid}, which is
	 * what vanilla's own {@code Material.isSolid} became. Both want a level and a position
	 * because a shape may depend on them; where the caller has neither, a block is asked in an
	 * empty world, which is right for every block whose shape is fixed.
	 */
	public static boolean isSolidVisibleCube(BlockState state, BlockGetter level, BlockPos pos) {
		return state.isCollisionShapeFullBlock(level, pos) && state.isSolid() && !state.canBeReplaced()
				&& state.getRenderShape() == RenderShape.MODEL;
	}

	/** {@link #isSolidVisibleCube(BlockState, BlockGetter, BlockPos)} for a block asked on its own. */
	public static boolean isSolidVisibleCube(BlockState state) {
		return isSolidVisibleCube(state, EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
	}

	/**
	 * A slab of the block one face thick, between two heights — upstream's
	 * {@code createAABBLayer}, as a {@link VoxelShape}.
	 *
	 * <p>Upstream returns an {@code AxisAlignedBB} and the caller uses it for three things:
	 * collision, ray tracing and drawing the selection outline. In 1.21.1 all three read a
	 * {@code VoxelShape} off the block, so the return type changes and the three call sites
	 * collapse into {@code getShape}/{@code getCollisionShape}. The <em>numbers</em> are
	 * upstream's exactly, including that {@code UP} and {@code DOWN} ignore
	 * {@code minY}/{@code maxY} and span the whole block.
	 *
	 * @param thickness how deep the slab is, in block units
	 * @param minY      the bottom of the slab, ignored for {@code UP} and {@code DOWN}
	 * @param maxY      the top of the slab, ignored for {@code UP} and {@code DOWN}
	 */
	public static VoxelShape createAABBLayer(Direction direction, double thickness, double minY, double maxY) {
		return switch(direction) {
			case UP -> Shapes.box(0, 1 - thickness, 0, 1, 1, 1);
			case DOWN -> Shapes.box(0, 0, 0, 1, thickness, 1);
			case NORTH -> Shapes.box(0, minY, 0, 1, maxY, thickness);
			case SOUTH -> Shapes.box(0, minY, 1 - thickness, 1, maxY, 1);
			case WEST -> Shapes.box(0, minY, 0, thickness, maxY, 1);
			case EAST -> Shapes.box(1 - thickness, minY, 0, 1, maxY, 1);
		};
	}

	/**
	 * The velocity a blast of strength {@code amplitude} gives something standing at
	 * {@code entityPos}, pointing away from {@code blastPos}. Upstream's, line for line.
	 *
	 * <p>The magnitude is {@code (max(amplitude - dist/2, 0)^2 / amplitude)^0.9 / 4}, computed in
	 * {@code float} as upstream does, so it falls to nothing at twice the amplitude and is
	 * strongest at the centre. The direction is the unit vector from the blast to the entity.
	 * <b>At exactly zero distance the direction is 0/0 and the result is NaN</b>, as upstream's is;
	 * nothing reaches it, because the player is never at the blast's centre when the shockwave
	 * arrives.
	 *
	 * <p>Pure on purpose: the vanilla {@code Vec3} is common code, so a server can run it
	 * (D-1089).
	 */
	public static Vec3 getKnockback(Vec3 entityPos, Vec3 blastPos, double amplitude) {
		Vec3 deltaPos = entityPos.subtract(blastPos);
		double dist = deltaPos.length();
		float force = (float) Math.pow(Math.pow(Math.max(amplitude - dist * 0.5, 0) / amplitude, 2) * amplitude, 0.9) / 4f;
		return new Vec3(deltaPos.x / dist * force, deltaPos.y / dist * force, deltaPos.z / dist * force);
	}

	/**
	 * Retypes {@code tank} from the fluid identifier in slot {@code in}, if the identifier names one
	 * of {@code allowedTypes}. Upstream's {@code setTypeOnly}, with the item handler replaced by the
	 * block entity's own slot array (D-3002).
	 *
	 * <p>Two shapes, as upstream has them. With {@code in == out} the identifier stays where it is.
	 * With a separate {@code out} slot, the identifier is moved there <b>only if that slot is
	 * empty</b> - and it is moved as a whole stack, then one is taken off the input, which is
	 * upstream's own arithmetic: an identifier stacked two deep ends as one in each slot. They are
	 * unstackable in practice, so nothing reaches it.
	 *
	 * <p>Changing the type empties the tank, and an identifier that names the type the tank already
	 * has is neither consumed nor moved.
	 *
	 * @return whether the tank was retyped
	 */
	public static boolean setTypeOnly(FluidTank tank, int in, int out, ItemStack[] slots, FluidType... allowedTypes) {
		return retype(tank, in, out, slots, type -> {
			for(FluidType usable : allowedTypes) if(usable.equals(type)) return true;
			return false;
		});
	}

	/**
	 * Retypes {@code tank} from the identifier in slot {@code in} unless {@code processor} refuses
	 * the type or it is one of {@code excludeTypes}. Upstream's {@code setTypeBy}.
	 *
	 * <p>The excluded types are how the AMS keeps its two coolant tanks apart: each is told not to
	 * become what the other already is.
	 *
	 * @return whether the tank was retyped
	 */
	public static boolean setTypeBy(FluidTank tank, int in, int out, ItemStack[] slots,
			Function<FluidType, Boolean> processor, FluidType... excludeTypes) {
		return retype(tank, in, out, slots, type -> {
			if(!processor.apply(type)) return false;
			for(FluidType excluded : excludeTypes) if(excluded.equals(type)) return false;
			return true;
		});
	}

	private static boolean retype(FluidTank tank, int in, int out, ItemStack[] slots, Predicate<FluidType> allowed) {

		if(slots[in].isEmpty() || !(slots[in].getItem() instanceof IItemFluidIdentifier id)) return false;

		if(in != out && !slots[out].isEmpty()) return false;

		FluidType newType = id.getType(null, null, slots[in]);

		if(!allowed.test(newType) || tank.getTankType() == newType) return false;

		tank.setTankType(newType);
		if(in != out) {
			slots[out] = slots[in].copy();
			slots[in].shrink(1);
		}
		tank.setFill(0);
		return true;
	}

	/**
	 * Whether a Forge-fluid duct carrying {@code type} connects to the block at {@code pos}, asked
	 * from the duct's side: a duct of the same fluid, or a machine that is an {@code IFFHandler} and
	 * exposes a fluid handler on {@code facing} (the side facing the duct). Upstream's
	 * {@code canConnectFF}, line for line; a proxy block entity answers for its core, as upstream's
	 * {@code TileEntityProxyBase.getTE()} does.
	 *
	 * <p>The capability half needs a {@link Level}; asked from anything else (a worldgen region), the
	 * machine half answers false.
	 */
	public static boolean canConnectFF(BlockGetter world, BlockPos pos, FluidType type, @Nullable Direction facing) {
		BlockEntity tileentity = world.getBlockEntity(pos);
		if(tileentity instanceof FFDuctTE duct && duct.getFluidType() == type)
			return true;
		BlockEntity mainTE = tileentity;
		if(tileentity instanceof BlockEntityProxyBase proxy)
			mainTE = proxy.getCore();
		if(tileentity != null && !(tileentity instanceof FFDuctTE) && world instanceof Level level
				&& level.getCapability(Capabilities.FluidHandler.BLOCK, pos, tileentity.getBlockState(), tileentity, facing) != null
				&& mainTE instanceof IFFHandler) {
			return true;
		}
		return false;
	}

	/**
	 * Moves {@code amount} of the sending tank's fluid into the receiving tank, tags first
	 * ({@link FFNBT#transferTags}). Upstream's {@code fillFF}, line for line, and it is <b>not</b> a
	 * NeoForge {@code fill}: it writes the receiving stack directly, so it ignores a tank's validator
	 * and lets a stack in whose tag differs - the network has already decided compatibility.
	 *
	 * <p><b>Two upstream quirks, kept (D-021).</b> An empty receiver is set to a copy of the sending
	 * stack at {@code amount} however little the sender holds; and the sender is always drained by
	 * {@code amount}, not by what went in. So asking for more than the sender has creates fluid, and
	 * asking for more than the receiver has room for destroys it. {@code FFNet}'s rounding reaches the
	 * first (D-3012); nothing in this port reaches the second, because every caller caps the amount at
	 * the room.
	 *
	 * @return what the receiver gained
	 */
	public static int fillFF(net.neoforged.neoforge.fluids.capability.templates.FluidTank sending,
			net.neoforged.neoforge.fluids.capability.templates.FluidTank receiving, int amount) {
		if(sending.getFluid().isEmpty()) return 0;
		if(amount <= 0) return 0;
		FFNBT.transferTags(sending, receiving);
		if(receiving.getFluid().isEmpty()) {
			net.neoforged.neoforge.fluids.FluidStack stack = sending.getFluid().copyWithAmount(amount);
			receiving.setFluid(stack);
			sending.drain(amount, IFluidHandler.FluidAction.EXECUTE);
			return amount;
		} else {
			int toFill = Math.min(receiving.getCapacity() - receiving.getFluidAmount(), amount);
			receiving.getFluid().grow(toFill);
			sending.drain(amount, IFluidHandler.FluidAction.EXECUTE);
			return toFill;
		}
	}

	/**
	 * Scroll-bar arithmetic: how far down a list a mouse position means, and where the bar sits
	 * for a given offset. Upstream's nested {@code ScrollUtil}, line for line.
	 *
	 * <p>A name with an underscore is about the internal scroll value (a row offset); one
	 * without is about where the bar is drawn. The bar's size and the area it runs in are both
	 * in pixels, and the position is relative to the top of that area.
	 */
	/**
	 * Upstream's {@code spreadFire}: 1.12.2 lava's fire spread ({@code BlockStaticLiquid.updateTick}) with the
	 * one-block reach widened to {@code radius}, plus the firestorm's ignition of each block it reaches. Two times in
	 * three it climbs one to two blocks up and out, lighting the first air beside something flammable and stopping at
	 * the first solid block; one time in three it tries three cells on its own layer and lights the air above any that
	 * burns. Nothing with {@code doFireTick} off, and it stops at an unloaded block rather than loading it.
	 * "Flammable" is lava's own test, {@code ignitedByLava} (1.12.2's {@code Material.getCanBurn}). Added for the arc
	 * welder (D-3027).
	 */
	public static void spreadFire(Level worldIn, BlockPos pos, int radius) {
		net.minecraft.util.RandomSource rand = worldIn.random;
		if(worldIn.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOFIRETICK)) {
			int i = rand.nextInt(3);
			if(i > 0) {
				BlockPos blockpos = pos;
				for(int j = 0; j < i; ++j) {
					blockpos = blockpos.offset(rand.nextInt(radius * 2 + 1) - radius, 1, rand.nextInt(radius * 2 + 1) - radius);
					if(!worldIn.isLoaded(blockpos))
						return;
					BlockState block = worldIn.getBlockState(blockpos);
					if(block.isAir()) {
						if(isSurroundingBlockFlammable(worldIn, blockpos)) {
							worldIn.setBlockAndUpdate(blockpos, net.minecraft.world.level.block.Blocks.FIRE.defaultBlockState());
							return;
						}
					} else if(block.blocksMotion()) {
						return;
					}
					com.leafia.dev.firestorm.IFirestormBlock.ignite(worldIn, blockpos);
				}
			} else {
				for(int k = 0; k < 3; ++k) {
					BlockPos blockpos1 = pos.offset(rand.nextInt(radius * 2 + 1) - radius, 0, rand.nextInt(radius * 2 + 1) - radius);
					if(!worldIn.isLoaded(blockpos1))
						return;
					if(worldIn.isEmptyBlock(blockpos1.above()) && worldIn.getBlockState(blockpos1).ignitedByLava())
						worldIn.setBlockAndUpdate(blockpos1.above(), net.minecraft.world.level.block.Blocks.FIRE.defaultBlockState());
					com.leafia.dev.firestorm.IFirestormBlock.ignite(worldIn, blockpos1);
				}
			}
		}
	}

	/** 1.12.2 lava's {@code isSurroundingBlockFlammable}: any of the six neighbours burns. */
	public static boolean isSurroundingBlockFlammable(Level worldIn, BlockPos pos) {
		for(Direction dir : Direction.values()) {
			if(worldIn.getBlockState(pos.relative(dir)).ignitedByLava())
				return true;
		}
		return false;
	}

	public static final class ScrollUtil {

		private ScrollUtil() {}

		/** The row offset a mouse position drags the bar to, rounded to the nearest row. */
		public static int _getScrollOffset(int scrollBarSize, int scrollAreaSize, double relativePosition, int maxScroll) {
			return Mth.clamp((int) (_getScrollRatio(scrollBarSize, scrollAreaSize, relativePosition) * maxScroll + 0.5), 0, maxScroll);
		}

		/** 0 to 1: how far along the track the bar's <em>centre</em> is at this position. */
		public static double _getScrollRatio(int scrollBarSize, int scrollAreaSize, double relativePosition) {
			return Mth.clamp((relativePosition - scrollBarSize / 2d) / (scrollAreaSize - scrollBarSize), 0, 1);
		}

		/** The ratio a row offset corresponds to. Not clamped: a zero {@code maxScroll} is NaN. */
		public static double _getScrollRatio(int offset, int maxScroll) {
			return (double) offset / maxScroll;
		}

		/** Where the bar's top edge is drawn, truncated to a pixel. */
		public static int getScrollBarPos(int scrollBarSize, int scrollAreaSize, double ratio) {
			return (int) getScrollBarPosDouble(scrollBarSize, scrollAreaSize, ratio);
		}

		public static double getScrollBarPosDouble(int scrollBarSize, int scrollAreaSize, double ratio) {
			return ratio * (scrollAreaSize - scrollBarSize);
		}
	}
}
