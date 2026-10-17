package com.leafia.contents.building.generic.lined_asphalt;

import com.hbm.blocks.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * One of the fifty-five asphalt blocks with road markings painted on it.
 *
 * <p>Upstream: {@code .../building/generic/lined_asphalt/LinedAsphaltBlock.java}. The class is
 * 159 lines there and most of it does not port:
 *
 * <ul>
 * <li>{@code AsphaltLine}, the nested set-of-segments type, is hoisted onto
 *     {@link AsphaltBlock} so the interaction handler can use it without depending on this
 *     class.</li>
 * <li>{@code bakeModel}, {@code registerModel} and {@code registerSprite} — three quarters of
 *     what is left — <b>dissolve into model JSON</b>. Upstream retextures
 *     {@code minecraft:block/cube_bottom_top} in Java and installs the result in
 *     {@code ModelBakeEvent}; a model with a {@code parent} and three textures is that, said
 *     declaratively. A generator script writes all fifty-five.</li>
 * </ul>
 *
 * <p>What is left is the click, the drop and the pick — and the first of those is the only
 * reason the class exists at all.
 *
 * <h2>Two things a player will notice, both upstream's</h2>
 *
 * A painted asphalt is <b>not</b> the base mod's {@code BlockSpeedy}, so <b>walking on a painted
 * road is slower than walking on a bare one</b> — the 1.5x speed boost is the plain block's and
 * this class does not inherit it. And a painted block <b>drops plain asphalt</b>, so painting is
 * one-way: there is no unpaint, only breaking and replacing.
 *
 * <p>Both are upstream's, and the first in particular reads like an oversight. Flagged, not
 * fixed (D-021): it is a balance property of a block players build roads out of.
 *
 * <p>The drop itself is not code here. Upstream's {@code getItemDropped} is a loot table in
 * 1.21.1, and the generator writes one per block; they are the first loot tables this addon has
 * ever shipped (D-1047).
 */
public class LinedAsphaltBlock extends AsphaltBlock {

	/**
	 * Which segments this block draws, in upstream's {@code x z n s w e} order — upstream's
	 * {@code texture} field, renamed for what it holds rather than what it was used to look up.
	 */
	public final String lines;

	public LinedAsphaltBlock(Properties properties, String lines) {
		super(properties);
		this.lines = lines;
	}

	/**
	 * Right-clicking with a white dye paints one more segment.
	 *
	 * <p>Upstream's {@code onBlockActivated}, which is one line delegating to
	 * {@link AsphaltBlock#paintBlock}. The 1.21.1 hook is
	 * {@code useItemOn}, which runs before the held item's own use.
	 */
	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
			BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {

		double hitX = hit.getLocation().x - pos.getX();
		double hitZ = hit.getLocation().z - pos.getZ();

		if(AsphaltBlock.paintBlock(level, pos, player, hand, hitX, hitZ))
			return ItemInteractionResult.sidedSuccess(level.isClientSide);

		// Upstream returns false, which lets the click fall through to the held item. A dye has
		// nothing to do with a block, so in practice nothing happens - but PASS is what "false"
		// means here and SKIP_DEFAULT_BLOCK_INTERACTION would not be.
		return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
	}

	/**
	 * Picking a painted block gives plain asphalt — upstream's {@code getPickBlock}.
	 *
	 * <p>So middle-clicking a road you painted does not give you the painted block back, and in
	 * creative there is no way to copy one off the ground. The creative menu has all fifty-five.
	 */
	@Override
	public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
		return new ItemStack(ModBlocks.asphalt.get());
	}
}
