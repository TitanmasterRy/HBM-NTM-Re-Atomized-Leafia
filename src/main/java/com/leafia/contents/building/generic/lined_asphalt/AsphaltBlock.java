package com.leafia.contents.building.generic.lined_asphalt;

import com.leafia.contents.LeafiaBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Painting road markings onto asphalt with a white dye.
 *
 * <p>Upstream: {@code .../building/generic/lined_asphalt/AsphaltBlock.java}. A base class there,
 * and a base class here, but what matters is the static {@link #paintBlock} — the whole
 * mechanic, called from two places.
 *
 * <h2>The mechanic</h2>
 *
 * Every asphalt block, plain or already painted, carries a set of up to six line segments:
 * {@code x} and {@code z} are full lines across the block, {@code n s w e} are half lines from
 * the centre out to that edge. One right-click with a white dye adds one segment and swaps the
 * block for the one whose name is the new set.
 *
 * <p>Which segment depends on the hand and on where you clicked: the <b>main hand</b> adds the
 * half line toward the nearer edge, and the <b>off hand</b> adds the full line along the nearer
 * axis. So a crossroads is four main-hand clicks or two off-hand ones.
 *
 * <p>Nothing removes a segment, and <b>the dye is never consumed</b> — upstream's
 * {@code paintBlock} does not shrink the stack, so one white dye paints every road you will ever
 * build. Carried as it is: it is a balance decision, not an oversight to correct (D-021).
 *
 * <h2>Plain asphalt is the base mod's, and that is why this is a static</h2>
 *
 * The empty set is not one of this addon's blocks — it is {@code hbm:asphalt}, and upstream
 * reaches it with {@code MixinBlockSpeedy}, a mixin into {@code com.hbm.blocks.generic
 * .BlockSpeedy}. <b>This port cannot do that</b> (D-1001), so the base mod's asphalt is reached
 * through a NeoForge interaction event instead — see {@link AsphaltPainting}. The mechanic lives
 * here as a static so that both callers, the event and this addon's own blocks, run the same
 * code rather than two copies of it.
 */
public class AsphaltBlock extends Block {

	public AsphaltBlock(Properties properties) {
		super(properties);
	}

	/**
	 * Adds one line segment to the asphalt at {@code pos}, if a white dye is being held.
	 *
	 * @param hitX where in the block the click landed along X, 0 to 1
	 * @param hitZ the same along Z
	 * @return true when the block was changed, which is upstream's "the click was handled"
	 */
	public static boolean paintBlock(Level level, BlockPos pos, Player player, InteractionHand hand,
			double hitX, double hitZ) {

		ItemStack stack = player.getItemInHand(hand);
		if(!(stack.getItem() instanceof DyeItem dye) || dye.getDyeColor() != DyeColor.WHITE)
			return false;

		hitX -= 0.5;
		hitZ -= 0.5;

		AsphaltLine line = AsphaltLine.of(level.getBlockState(pos).getBlock());
		boolean xAxis = Math.abs(hitX) > Math.abs(hitZ);

		if(hand == InteractionHand.OFF_HAND) {
			// The off hand draws the through line, not the half line.
			if(xAxis) line.x = true;
			else line.z = true;
		} else if(xAxis) {
			if(hitX > 0) line.e = true;
			else line.w = true;
		} else {
			// Upstream's `hitZ < 0` is north, which is -Z. Kept rather than tidied: the two
			// branches above test `> 0` and this one `< 0`, and the asymmetry is correct.
			if(hitZ < 0) line.n = true;
			else line.s = true;
		}

		Block painted = line.block();
		if(painted == null) return false;

		level.setBlockAndUpdate(pos, painted.defaultBlockState());
		return true;
	}

	/**
	 * One block's set of line segments — upstream's {@code LinedAsphaltBlock.AsphaltLine}.
	 *
	 * <p>Hoisted out of {@code LinedAsphaltBlock}, where upstream nests it, for the reason that
	 * keeps coming up in this campaign (D-1034, D-1040, D-1041): {@link AsphaltPainting} needs
	 * the set and nothing else from that class, and leaving it nested would make the event
	 * handler depend on the block.
	 *
	 * <p>Upstream's {@code rotate()} is not carried. Nothing in the clone calls it — it rotates a
	 * set a quarter turn, which would be how a wrench or a rotating piston moved one, and neither
	 * exists here.
	 */
	public static final class AsphaltLine {

		public boolean x, z, n, s, w, e;

		private AsphaltLine() {}

		/** The set a block already has; empty for plain asphalt or anything else. */
		public static AsphaltLine of(Block block) {
			AsphaltLine line = new AsphaltLine();
			if(block instanceof LinedAsphaltBlock lined) {
				String t = lined.lines;
				line.x = t.indexOf('x') >= 0;
				line.z = t.indexOf('z') >= 0;
				line.n = t.indexOf('n') >= 0;
				line.s = t.indexOf('s') >= 0;
				line.w = t.indexOf('w') >= 0;
				line.e = t.indexOf('e') >= 0;
			}
			return line;
		}

		/**
		 * The set's name, in upstream's bit order.
		 *
		 * <p>The order is load-bearing: it is how a set is looked up, and it has to match the
		 * order the registration loop builds names in, which is {@code x z n s w e}.
		 */
		@Override
		public String toString() {
			StringBuilder out = new StringBuilder();
			if(x) out.append('x');
			if(z) out.append('z');
			if(n) out.append('n');
			if(s) out.append('s');
			if(w) out.append('w');
			if(e) out.append('e');
			return out.toString();
		}

		/**
		 * The block that draws this set, or null if there is none.
		 *
		 * <p>Upstream's {@code getBlock}: eight sets draw the same picture as a shorter one — a
		 * full {@code x} line plus both {@code w} and {@code e} halves is just the {@code x}
		 * line — so no block is registered for them and the lookup is redirected first.
		 */
		public Block block() {
			return LeafiaBlocks.linedAsphalt(toString());
		}
	}
}
