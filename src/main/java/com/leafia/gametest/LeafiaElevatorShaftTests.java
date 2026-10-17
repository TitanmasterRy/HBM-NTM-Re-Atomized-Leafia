package com.leafia.gametest;

import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.machines.elevators.EvShaftNeo;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The elevator shaft rail — the first piece of the elevator campaign.
 *
 * <p>The shape is the whole of this block, and it is the part a translation gets wrong silently:
 * upstream builds a rotated box with its own matrix maths every time it is asked, and 1.21.1 wants
 * four pre-built {@link VoxelShape}s because a shape cannot be rotated after the fact. If that
 * conversion is off, the rail's outline sits in the wrong place or the wrong way round, and
 * nothing else in the game complains.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaElevatorShaftTests {

	private static final String EMPTY = "empty";
	private static final BlockPos WHERE = new BlockPos(2, 2, 2);

	/**
	 * A rail is a thin strip: a sixteenth deep, a block tall, a block wide, and it is <em>outside
	 * the block's own middle</em> — pushed against the wall it is mounted on.
	 */
	@GameTest(template = EMPTY)
	public static void theRailIsAThinStripAgainstTheWall(GameTestHelper helper) {

		EvShaftNeo shaft = LeafiaBlocks.elevator_shaft.get();

		for(Direction facing : Direction.Plane.HORIZONTAL) {

			BlockState state = shaft.defaultBlockState().setValue(EvShaftNeo.FACING, facing);
			AABB box = state.getShape(helper.getLevel(), helper.absolutePos(WHERE)).bounds();

			double depth = Math.min(box.getXsize(), box.getZsize());
			double width = Math.max(box.getXsize(), box.getZsize());

			helper.assertTrue(Math.abs(depth - 1 / 16D) < 1e-6,
					facing + ": the rail should be 1/16 deep, is " + depth);
			helper.assertTrue(Math.abs(width - 1D) < 1e-6,
					facing + ": and a full block wide, is " + width);
			helper.assertTrue(Math.abs(box.getYsize() - 1D) < 1e-6,
					facing + ": and a full block tall, is " + box.getYsize());
		}

		helper.succeed();
	}

	/**
	 * The four facings give four <em>different</em> boxes, and opposite facings are on opposite
	 * sides.
	 *
	 * <p>This is what catches the conversion collapsing: a sign error in the rotation, or building
	 * every shape from the same angle, leaves four identical boxes and every other assertion still
	 * passes.
	 */
	@GameTest(template = EMPTY)
	public static void eachFacingPutsTheRailSomewhereElse(GameTestHelper helper) {

		EvShaftNeo shaft = LeafiaBlocks.elevator_shaft.get();
		BlockPos pos = helper.absolutePos(WHERE);

		AABB north = shaft.defaultBlockState().setValue(EvShaftNeo.FACING, Direction.NORTH)
				.getShape(helper.getLevel(), pos).bounds();
		AABB south = shaft.defaultBlockState().setValue(EvShaftNeo.FACING, Direction.SOUTH)
				.getShape(helper.getLevel(), pos).bounds();
		AABB east = shaft.defaultBlockState().setValue(EvShaftNeo.FACING, Direction.EAST)
				.getShape(helper.getLevel(), pos).bounds();
		AABB west = shaft.defaultBlockState().setValue(EvShaftNeo.FACING, Direction.WEST)
				.getShape(helper.getLevel(), pos).bounds();

		helper.assertTrue(!north.equals(south), "north and south must not give the same box");
		helper.assertTrue(!east.equals(west), "east and west must not give the same box");
		helper.assertTrue(!north.equals(east), "the rail must turn with the facing");

		// Opposite facings mirror through the block's centre on the axis they lie on.
		helper.assertTrue(Math.abs((north.minZ + south.maxZ) - 1D) < 1e-6
						&& Math.abs((north.maxZ + south.minZ) - 1D) < 1e-6,
				"north and south should mirror about z=0.5, they are " + north + " and " + south);

		helper.succeed();
	}

	/** You walk through a shaft rail — that is what lets you stand in the shaft at all. */
	@GameTest(template = EMPTY)
	public static void theRailHasNoCollision(GameTestHelper helper) {

		BlockState state = LeafiaBlocks.elevator_shaft.get().defaultBlockState();
		helper.setBlock(WHERE, state);

		VoxelShape collision = state.getCollisionShape(helper.getLevel(), helper.absolutePos(WHERE),
				CollisionContext.empty());

		helper.assertTrue(collision == Shapes.empty() || collision.isEmpty(),
				"a shaft rail must not collide with anything, its collision shape is " + collision);

		helper.succeed();
	}

	/**
	 * The car's light marker: air that glows at <b>15</b>, not at 1.
	 *
	 * <p>Upstream writes {@code setLightLevel(1)}, which is 1.12.2's 0..1 float. Translated
	 * literally that becomes level 1 — a shaft lit just enough to look broken rather than dark,
	 * which is the kind of wrong nothing else reports. Nothing places one of these yet (the car is
	 * the middle of the cycle D-1032 scopes), so this test is the only thing that will notice.
	 */
	@GameTest(template = EMPTY)
	public static void theCarsLightMarkerIsFullBrightAir(GameTestHelper helper) {

		BlockState light = LeafiaBlocks.elevator_light.get().defaultBlockState();

		helper.assertTrue(light.getLightEmission() == 15,
				"upstream's setLightLevel(1) is a 0..1 float, so this must be 15, it is "
						+ light.getLightEmission());
		helper.assertTrue(light.isAir(),
				"it has to answer isAir, or nothing that walks a column of them will pass through");
		helper.assertTrue(light.getCollisionShape(helper.getLevel(), helper.absolutePos(WHERE)).isEmpty(),
				"and it must not collide");
		helper.assertTrue(LeafiaBlocks.elevator_light.get().asItem() == net.minecraft.world.item.Items.AIR,
				"it has no item: upstream passes setCreativeTab(null) and the car places it");

		helper.succeed();
	}

	/** It is placed facing the player, and it rotates with the world. */
	@GameTest(template = EMPTY)
	public static void itTurnsWithTheWorld(GameTestHelper helper) {

		BlockState north = LeafiaBlocks.elevator_shaft.get().defaultBlockState()
				.setValue(EvShaftNeo.FACING, Direction.NORTH);

		helper.assertTrue(north.rotate(Rotation.CLOCKWISE_90).getValue(EvShaftNeo.FACING) == Direction.EAST,
				"a quarter turn clockwise takes north to east");
		helper.assertTrue(north.rotate(Rotation.CLOCKWISE_180).getValue(EvShaftNeo.FACING) == Direction.SOUTH,
				"and a half turn to south");

		helper.succeed();
	}
}
