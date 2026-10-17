package com.llib;

import java.util.function.Function;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Ported from `com.llib.LeafiaLib`.
 *
 * <p>Upstream's own block raytrace ("since minecraft base raytrace is basically shit i made my
 * own one"), plus three unrelated helpers that live in the same file: a character-swapping
 * string utility, SI scale constants, and a braille-character graph plotter the DFC's receiver
 * GUI draws with.
 *
 * <p>The raytrace walks the dominant axis one unit at a time and visits every block the ray
 * could pass through on the other two, handing each to a callback that decides whether to
 * stop. That is the part upstream cares about: vanilla's raytrace returns the first hit, this
 * one lets a caller see every block along the way, which is how the DFC beam and the wind
 * turbine's obstruction check work.
 *
 * <h2>What changed</h2>
 *
 * <ul>
 *   <li><b>{@code Material} is gone from the game</b> — 1.20 removed it — so
 *       {@code LeafiaRayTraceProgress.material} has no successor and is dropped rather than
 *       approximated. Checked before dropping it, per standing rule 5: of the nine upstream
 *       files that run this raytrace, <b>none reads that field</b>. They read {@code posSnapped}
 *       (9), {@code posIntended} (7), {@code state} (6), {@code block} (4).</li>
 *   <li>The one place {@code Material} carried meaning was
 *       {@code state.getMaterial() == Material.PORTAL}, letting the trace see portal blocks
 *       despite their empty collision box. That becomes {@link #isPortalLike}, naming the three
 *       blocks that had {@code PORTAL} as their material.</li>
 *   <li>{@code Block#canCollideCheck} has no 1.21.1 equivalent. In 1.12.2 it answered "is this
 *       block solid enough to stop a ray", with liquids overriding it to depend on the
 *       {@code stopOnLiquid} flag. {@link #canCollide} reproduces that: a non-empty collision
 *       shape, or a fluid when the caller asked to stop on liquids.</li>
 *   <li>{@code IBlockState#collisionRayTrace} becomes {@code state.getShape(...).clip(...)},
 *       which returns the same nullable hit.</li>
 *   <li>{@code RayTraceResult} is {@link BlockHitResult}; {@code Vec3d} is {@link Vec3};
 *       {@code EnumFacing} is {@link Direction}; {@code new BlockPos(Vec3d)} is
 *       {@link BlockPos#containing}; {@code World#isValid} is {@link Level#isInWorldBounds}.</li>
 * </ul>
 *
 * <p><b>Not yet exercised.</b> Every caller of the raytrace is either a {@code com.hbm} mixin,
 * which D-1001 turns into a base-mod hook instead, or a feature later than L1 — the wind turbine
 * (L4) is the first legitimate one. The mapping above is faithful to what upstream does; it has
 * not been watched working in a world, and the turbine batch is where that happens.
 */
public class LeafiaLib {

	public static class LeafiaRayTraceConfig {
		public final Level world;
		public final Vec3 start;
		public final Vec3 end;
		public final Vec3 unitDir;
		public final Direction pivotAxisFace;
		public final Vec3 secondaryVector;

		LeafiaRayTraceConfig(Level world, Vec3 start, Vec3 end, Vec3 unitDir, Direction pivotAxisFace, Vec3 secondaryVector) {
			this.world = world;
			this.start = start;
			this.end = end;
			this.unitDir = unitDir;
			this.pivotAxisFace = pivotAxisFace;
			this.secondaryVector = secondaryVector;
		}
	}

	/** Upstream also carried a {@code material} here; see the class note for why it is gone. */
	public static class LeafiaRayTraceProgress {
		public final int distanceAlongPivot;
		public final Vec3 posIntended;
		public final BlockPos posSnapped;
		public final Block block;
		public final BlockState state;
		public boolean stop = false;

		LeafiaRayTraceProgress(int distanceAlongPivot, Vec3 posIntended, BlockPos posSnapped, Block block, BlockState state) {
			this.distanceAlongPivot = distanceAlongPivot;
			this.posIntended = posIntended;
			this.posSnapped = posSnapped;
			this.block = block;
			this.state = state;
		}
	}

	public static class LeafiaRayTraceControl<T> {
		final T value;
		final boolean stop;
		final boolean overwrite;

		private LeafiaRayTraceControl(T value, boolean stop, boolean overwrite) {
			this.value = value;
			this.stop = stop;
			this.overwrite = overwrite;
		}
	}

	// piece of sh*t that i couldnt make it so you could call these by like "this.RETURN()" on fucking interface that'd be cool smfh
	public static class LeafiaRayTraceController<T> {
		public LeafiaRayTraceControl<T> RETURN(T value) { return new LeafiaRayTraceControl<>(value, true, true); }
		public LeafiaRayTraceControl<T> BREAK() { return new LeafiaRayTraceControl<>(null, true, false); }
		public LeafiaRayTraceControl<T> CONTINUE(T value) { return new LeafiaRayTraceControl<>(value, false, true); }
		public LeafiaRayTraceControl<T> CONTINUE() { return new LeafiaRayTraceControl<>(null, false, false); }
		private LeafiaRayTraceController() {}
	}

	@FunctionalInterface
	public interface LeafiaRayTraceFunction<T> {
		LeafiaRayTraceControl<T> process(LeafiaRayTraceController<T> process, LeafiaRayTraceConfig config, LeafiaRayTraceProgress current);
	}

	public static <T> T leafiaRayTraceBlocksCustom(Level world, Vec3 start, Vec3 end, LeafiaRayTraceFunction<T> callback) {
		// since minecraft base raytrace is basically shit i made my own one
		Vec3 direction = end.subtract(start);
		Vec3 unit = direction.normalize();
		double[] inclinations = new double[2];
		double maxAxisValue;

		Vec3 dirAbs = new Vec3(Math.abs(direction.x), Math.abs(direction.y), Math.abs(direction.z));
		Vec3 mVector;
		Vec3 aVector;
		Vec3 bVector;
		Direction facing;
		if((dirAbs.x >= dirAbs.y) && (dirAbs.x >= dirAbs.z)) {
			facing = (direction.x > 0) ? Direction.EAST : Direction.WEST;
			mVector = new Vec3(1, 0, 0);
			aVector = new Vec3(0, 1, 0);
			bVector = new Vec3(0, 0, 1);
			inclinations[0] = direction.y / dirAbs.x;
			inclinations[1] = direction.z / dirAbs.x;
			maxAxisValue = direction.x;
		} else if((dirAbs.y >= dirAbs.x) && (dirAbs.y >= dirAbs.z)) {
			facing = (direction.y > 0) ? Direction.UP : Direction.DOWN;
			mVector = new Vec3(0, 1, 0);
			aVector = new Vec3(1, 0, 0);
			bVector = new Vec3(0, 0, 1);
			inclinations[0] = direction.x / dirAbs.y;
			inclinations[1] = direction.z / dirAbs.y;
			maxAxisValue = direction.y;
		} else if((dirAbs.z >= dirAbs.x) && (dirAbs.z >= dirAbs.y)) {
			facing = (direction.z > 0) ? Direction.SOUTH : Direction.NORTH;
			mVector = new Vec3(0, 0, 1);
			aVector = new Vec3(1, 0, 0);
			bVector = new Vec3(0, 1, 0);
			inclinations[0] = direction.x / dirAbs.z;
			inclinations[1] = direction.y / dirAbs.z;
			maxAxisValue = direction.z;
		} else
			throw new RuntimeException("Cannot find maximum axis :/");

		LeafiaRayTraceConfig config = new LeafiaRayTraceConfig(
				world, start, end, unit, facing,
				aVector.scale(inclinations[0]).add(bVector.scale(inclinations[1]))
		);
		LeafiaRayTraceController<T> session = new LeafiaRayTraceController<>();
		T returnValue = null;
		for(int m = 0; m <= Math.abs(maxAxisValue); m++) {
			Vec3 posVec = start
					.add(mVector.scale(m * Math.signum(maxAxisValue)))
					.add(aVector.scale(m * inclinations[0]))
					.add(bVector.scale(m * inclinations[1]));
			for(int a = (int) Math.floor(Math.abs(inclinations[0] * m)); a <= (int) Math.ceil(Math.abs(inclinations[0] * m)); a++) {
				for(int b = (int) Math.floor(Math.abs(inclinations[1] * m)); b <= (int) Math.ceil(Math.abs(inclinations[1] * m)); b++) {
					BlockPos pos = BlockPos.containing(
							start
									.add(mVector.scale(m * Math.signum(maxAxisValue)))
									.add(aVector.scale(a * Math.signum(inclinations[0])))
									.add(bVector.scale(b * Math.signum(inclinations[1])))
					);
					if(world.isInWorldBounds(pos)) {
						BlockState state = world.getBlockState(pos);
						Block block = state.getBlock();
						LeafiaRayTraceProgress progress = new LeafiaRayTraceProgress(
								m, posVec, pos, block, state
						);
						LeafiaRayTraceControl<T> signal = callback.process(session, config, progress);
						if(signal.overwrite) returnValue = signal.value;
						if(signal.stop) return returnValue;
					}
				}
			}
		}
		return returnValue;
	}

	/**
	 * The blocks that carried {@code Material.PORTAL} in 1.12.2, which upstream traces through
	 * despite their empty collision shape.
	 */
	private static boolean isPortalLike(BlockState state) {
		return state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.END_PORTAL) || state.is(Blocks.END_GATEWAY);
	}

	/**
	 * Stands in for 1.12.2's {@code Block#canCollideCheck}: solid enough to stop a ray, or a
	 * fluid when the caller asked to stop on liquids.
	 */
	private static boolean canCollide(BlockState state, Level world, BlockPos pos, boolean stopOnLiquid) {
		if(stopOnLiquid && !state.getFluidState().isEmpty()) return true;
		return !state.getCollisionShape(world, pos).isEmpty();
	}

	@Nullable
	public static BlockHitResult leafiaRayTraceBlocks(Level world, Vec3 start, Vec3 end, boolean stopOnLiquid, boolean ignoreBlockWithoutBoundingBox, boolean returnLastUncollidableBlock) {
		return leafiaRayTraceBlocksCustom(world, start, end, (process, config, current) -> {
			BlockState state = current.state;
			BlockPos pos = current.posSnapped;
			Vec3 posVec = current.posIntended;
			Vec3 unit = config.unitDir;
			if(!ignoreBlockWithoutBoundingBox || isPortalLike(state) || !state.getCollisionShape(world, pos).isEmpty()) {
				if(canCollide(state, world, pos, stopOnLiquid)) {
					BlockHitResult result = state.getShape(world, pos)
							.clip(posVec.subtract(unit.scale(2)), posVec.add(unit.scale(2)), pos);

					if(result != null) {
						return process.RETURN(result);
					}
				}
				else if(returnLastUncollidableBlock) {
					return process.CONTINUE(BlockHitResult.miss(posVec, config.pivotAxisFace, pos));
				}
			}
			return process.CONTINUE();
		});
	}

	/**
	 * <s>Replaces all occurrences of <tt>a</tt> and <tt>b</tt> within <tt>s</tt> with the other.</s>
	 * <p>Too painful to make.
	 * @param s The string to replace texts within
	 * @param a Pattern A
	 * @param b Pattern B
	 * @return Resulting string
	 */
	public static String stringSwap(String s, String a, String b) {
		throw new UnsupportedOperationException("Too painful to make :/");
	}

	/**
	 * Replaces all occurrences of <tt>a</tt> and <tt>b</tt> within <tt>s</tt> with the other.
	 * @param s The string to replace texts within
	 * @param a Char A
	 * @param b Char B
	 * @return Resulting string
	 */
	public static String stringSwap(String s, char a, char b) {
		StringBuilder builder = new StringBuilder(s.length());
		for(char c : s.toCharArray()) {
			if(c == a)
				builder.append(b);
			else if(c == b)
				builder.append(a);
			else
				builder.append(c);
		}
		return builder.toString();
	}

	public static class NumScale {
		public static final int KILO = 1_000;
		public static final int MEGA = 1_000_000;
		public static final int GIGA = 1_000_000_000;
		public static final long TERRA = 1_000_000_000_000L;
		public static final long PETA = 1_000_000_000_000_000L;
		public static final long EXA = 1_000_000_000_000_000_000L;
		//,ZETTA,YOTTA,RONNA,QUETTA;
	}

	static int brailleStart = 0x2800;
	static int[] brailleMapping = new int[] {1 << 6, 1 << 2, 1 << 1, 1, 1 << 7, 1 << 5, 1 << 4, 1 << 3};

	/** Plots a function as rows of braille characters, two dot-columns and four dot-rows per cell. */
	public static String[] drawGraph(int width, int height, int lfSpacing, double minX, double maxX, double minY, double maxY, Function<Double, Double> callback) {
		double increment = (maxX - minX) / width / 2;
		String[] output = new String[height];
		int dotHeight = height * 4 + lfSpacing * (height - 1);
		for(int i = 0; i < height; i++) output[i] = "";
		Integer prevY = null;
		for(int xb = 0; xb < width * 2; xb += 2) {
			int[] bits = new int[height];
			int column = 0;
			for(int xf = xb; xf <= xb + 1; xf++) {
				double xcal = xf * increment;
				double ycal = callback.apply(xcal);
				int yf = (int) ((ycal - minY) / (maxY - minY) * dotHeight + 0.5);
				int yTgt = yf;
				if(prevY != null) yTgt = prevY;
				if(Math.abs(yf - yTgt) >= 1) {
					int difference = yf - yTgt;
					yTgt += (int) Math.signum(difference);
				}
				int drawY0 = Math.min(yf, yTgt);
				int drawY1 = Math.max(yf, yTgt);
				for(int row = 0; row < height; row++) {
					int y0 = row * (4 + lfSpacing + 1);
					int y1 = y0 + 3;
					if(drawY0 < y0 && drawY1 < y0) continue;
					if(drawY0 > y1 && drawY1 > y1) continue;
					for(int yc = Math.max(drawY0, y0); yc <= Math.min(drawY1, y1); yc++) {
						int offset = yc - y0;
						bits[row] = bits[row] | brailleMapping[offset + column * 4];
					}
				}
				column++;
				prevY = yf;
			}
			for(int i = 0; i < height; i++) {
				output[height - 1 - i] = output[height - 1 - i] + (char) (brailleStart + bits[i]);
			}
		}
		return output;
	}
}
