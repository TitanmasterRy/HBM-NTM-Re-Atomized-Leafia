package com.leafia.gametest;

import java.util.Map;

import com.hbm.util.Tuple.Pair;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.building.sign.SignBlock;
import com.leafia.contents.building.sign.SignTE;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The thirty-six letter signs.
 *
 * <p>Two things here are worth a test rather than a comment. The <b>set</b> — thirty-six blocks,
 * one per character, each with its own registry id — is generated in three places (the registry,
 * the blockstates, the item models) and a mismatch between them is a missing model for one letter
 * that nobody notices until they build a word with it. And the <b>colour</b>, which is the only
 * state a sign has, reaches a client through two independent paths and neither is obvious.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaSignTests {

	private static final String EMPTY = "empty";
	private static final BlockPos WALL = new BlockPos(2, 2, 2);
	private static final BlockPos SIGN = new BlockPos(2, 2, 3);

	/** Upstream's alphabet, all of it, each block registered under its own lowercase id. */
	@GameTest(template = EMPTY)
	public static void allThirtySixLettersAreRegistered(GameTestHelper helper) {

		helper.assertTrue(LeafiaBlocks.SIGN_LETTERS.equals("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"),
				"upstream's character set is ten digits and twenty-six letters, this is "
						+ LeafiaBlocks.SIGN_LETTERS);
		helper.assertTrue(LeafiaBlocks.letter_signs.size() == 36,
				"there should be 36 sign blocks, there are " + LeafiaBlocks.letter_signs.size());

		for(int i = 0; i < LeafiaBlocks.SIGN_LETTERS.length(); i++) {

			String letter = LeafiaBlocks.SIGN_LETTERS.substring(i, i + 1);
			SignBlock block = LeafiaBlocks.letter_signs.get(letter).get();

			helper.assertTrue(block.letter.equals(letter),
					"the block registered for '" + letter + "' draws '" + block.letter + "'");
			helper.assertTrue(block.getName().getString().contains(letter),
					"its name should name the letter, it is " + block.getName().getString());
		}

		helper.succeed();
	}

	/**
	 * The block is invisible and everything visible is the renderer's.
	 *
	 * <p>Upstream gets this from 1.12.2's {@code BlockContainer}, whose render type is
	 * {@code INVISIBLE}; here it comes from {@code BaseEntityBlock}. It is worth pinning because a
	 * later "let's give the signs a model" would make the letter float inside a solid cube, and
	 * because the generated blockstates exist <em>only</em> to supply a particle texture.
	 */
	@GameTest(template = EMPTY)
	public static void theBlockItselfIsInvisible(GameTestHelper helper) {

		BlockState state = LeafiaBlocks.letter_signs.get("A").get().defaultBlockState();

		helper.assertTrue(state.getRenderShape() == RenderShape.INVISIBLE,
				"a sign is drawn entirely by its block entity renderer, so the block must be"
						+ " INVISIBLE - it is " + state.getRenderShape());

		helper.succeed();
	}

	/** Dyeing a sign is the only thing a player can do to it, and it sticks across a reload. */
	@GameTest(template = EMPTY)
	public static void dyeingASignSticks(GameTestHelper helper) {

		SignTE sign = place(helper);

		helper.assertTrue(sign.color == DyeColor.WHITE, "a fresh sign is white, this one is " + sign.color);

		sign.color = DyeColor.LIME;
		sign.setChanged();

		var saved = sign.saveWithoutMetadata(helper.getLevel().registryAccess());
		SignTE reloaded = new SignTE(helper.absolutePos(SIGN), sign.getBlockState());
		reloaded.loadWithComponents(saved, helper.getLevel().registryAccess());

		helper.assertTrue(reloaded.color == DyeColor.LIME,
				"the colour should survive a save, came back as " + reloaded.color);

		helper.succeed();
	}

	/**
	 * The colour reaches a client two ways, and the test drives both.
	 *
	 * <p>{@code onPlayerValidate} is upstream's — a client that comes into range asks, and gets an
	 * answer aimed at it alone. <b>Nothing calls it in this port</b> (the sweep lives in the
	 * unported {@code LeafiaClientListener}), so the colour also rides vanilla's block-entity
	 * update tag. Both are exercised here: if the validate half is ever wired up it must already
	 * work, and the update tag is what a player actually sees today.
	 */
	@GameTest(template = EMPTY)
	public static void bothColourPathsCarryTheColour(GameTestHelper helper) {

		SignTE sign = place(helper);
		sign.color = DyeColor.MAGENTA;

		// Path 1: vanilla's update tag, which is what makes a sign the right colour on chunk load.
		var tag = sign.getUpdateTag(helper.getLevel().registryAccess());
		helper.assertTrue(tag.getByte("color") == (byte) DyeColor.MAGENTA.getId(),
				"the update tag should carry the colour, it holds " + tag.getByte("color"));

		// Path 2: upstream's validate reply, built the way onPlayerValidate builds it.
		LeafiaPacket packet = LeafiaPacket._start(sign).__write(0, sign.color.getId()).build();
		helper.assertTrue(packet != null && packet.identifier() == "SIGN".hashCode(),
				"the validate reply should be addressed to upstream's SIGN identifier");

		SignTE other = new SignTE(helper.absolutePos(SIGN), sign.getBlockState());
		for(Map.Entry<Byte, Pair<Byte, Object>> entry
				: packet.decode(helper.getLevel().registryAccess()).entrySet()) {
			other.onReceivePacketLocal(LeafiaPacket.key(entry.getKey()), entry.getValue().getValue());
		}

		helper.assertTrue(other.color == DyeColor.MAGENTA,
				"slot 0 is the colour index, and it came back as " + other.color);

		helper.succeed();
	}

	/**
	 * A sign needs a wall, and loses when the wall goes.
	 *
	 * <p>Upstream drops it by hand from {@code neighborChanged}; here {@code canSurvive} says the
	 * rule once and vanilla does the dropping. The half that <em>is</em> a deviation — upstream
	 * lets you hang one in mid-air until something pokes it — is why the first assertion is worth
	 * making explicitly rather than assuming.
	 */
	@GameTest(template = EMPTY)
	public static void aSignNeedsAWall(GameTestHelper helper) {

		BlockState state = LeafiaBlocks.letter_signs.get("A").get().defaultBlockState()
				.setValue(SignBlock.FACING, Direction.SOUTH);

		helper.setBlock(WALL, Blocks.STONE);
		helper.assertTrue(state.canSurvive(helper.getLevel(), helper.absolutePos(SIGN)),
				"a sign facing south with stone to its north should survive");

		helper.setBlock(WALL, Blocks.AIR);
		helper.assertTrue(!state.canSurvive(helper.getLevel(), helper.absolutePos(SIGN)),
				"and must not survive once the wall is gone");

		helper.succeed();
	}

	/** A sign on a wall, facing out of it. */
	private static SignTE place(GameTestHelper helper) {

		helper.setBlock(WALL, Blocks.STONE);
		helper.setBlock(SIGN, LeafiaBlocks.letter_signs.get("A").get().defaultBlockState()
				.setValue(SignBlock.FACING, Direction.SOUTH));

		return (SignTE) helper.getBlockEntity(SIGN);
	}
}
