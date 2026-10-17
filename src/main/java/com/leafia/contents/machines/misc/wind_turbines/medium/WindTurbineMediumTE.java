package com.leafia.contents.machines.misc.wind_turbines.medium;

import com.leafia.contents.machines.misc.wind_turbines.WindTurbineTEBase;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.passive.Wind;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * The medium wind turbine: a 6-block mast with a three-bladed rotor, generating from
 * {@link Wind#power} and pushing it out of the bottom.
 *
 * <p>Upstream: {@code .../wind_turbines/medium/WindTurbineMediumTE.java}.
 *
 * <h2>The output, which is four numbers and a clamp</h2>
 *
 * <pre>
 *   generated = min(Wind.power * dimensionMultiplier * 0.5 / 40, 1) * 5000 / 20
 *   generated = generated * (lubeFraction^0.25 * 0.95 + 0.05) * 3
 * </pre>
 *
 * So the wind saturates at 80 of its 100 — {@code 80 * 0.5 / 40} is exactly 1 — and everything
 * above that is the same output. A full tank of lubricant is worth twenty times a dry one, and
 * the fourth root means the first drops of it buy most of that. The {@code * 3} on the end is
 * upstream's and is applied <em>after</em> the clamp, so peak output is 750 HE/t rather than the
 * 250 the first line suggests. Every one of those is pinned by a test, because a wind turbine
 * that generates the wrong amount looks exactly like one that generates the right amount.
 *
 * <p>Outside the overworld {@code dimensionWindMultiplier} defaults to zero, so a turbine in the
 * nether generates the {@code 0.05} floor of its lubricant term and nothing more.
 *
 * <h2>The blades spin off the power, not off the wind</h2>
 *
 * The client has no wind of its own worth trusting mid-gust, so the rotor speed is derived from
 * the synced output: {@code sqrt(generated * 20 / 5000) / 20 * 720} degrees per tick. A stalled
 * turbine's blades stop, which is the visible tell that it is obstructed or dry.
 */
public class WindTurbineMediumTE extends WindTurbineTEBase {

	public WindTurbineMediumTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	AABB aabb;

	/**
	 * Upstream's render box, <b>including its typo</b>: the last corner is
	 * {@code pos.getX() + 2} where every sibling coordinate says Z. It makes the box lopsided by
	 * however far the turbine's X and Z differ, which at most world positions is enormous — so
	 * the box is far too big rather than too small, and the rotor never vanishes. Preserved under
	 * D-021: it is reachable, it is cosmetic, and "fixing" it would shrink a working box.
	 *
	 * <p>Not an override any more: 1.21.1 moved the render box and the view distance off the block
	 * entity and onto its <em>renderer</em>, so this is a plain method that
	 * {@code WindTurbineMediumRender.getRenderBoundingBox} returns. Keeping it here keeps it where
	 * upstream put it and keeps the typo attributable.
	 */
	public AABB getRenderBoundingBox() {

		if(aabb == null) {
			aabb = new AABB(
					worldPosition.getX() - 1,
					worldPosition.getY() - 1,
					worldPosition.getZ() - 1,
					worldPosition.getX() + 2,
					worldPosition.getY() + 8,
					worldPosition.getX() + 2);
		}

		return aabb;
	}

	public static void tick(Level level, BlockPos pos, BlockState state, WindTurbineMediumTE turbine) {
		turbine.updateEntity();
	}

	public void updateEntity() {

		if(level.isClientSide) {
			addBladeAngle((float) Math.pow(generated * 20 / 5000f, 0.5) / 20 * 360 * 2);
			return;
		}

		updateObstructed(5, 500);
		generated = computeOutput();

		consumeLube();
		tryProvide(level, worldPosition.below(), Direction.DOWN);

		LeafiaPacket._start(this)
				.__write(29, lube.getFill())
				.__write(30, generated)
				.__write(31, obstructed)
				.__sendToAffectedClients();
	}

	/**
	 * Upstream's output arithmetic, split out of {@link #updateEntity} so a test can read it
	 * without the tick around it — {@code updateObstructed} re-rolls the obstruction flag on its
	 * own schedule, so a test that drove the whole tick would be testing the dice as well.
	 *
	 * <p>Same seam as {@code VoiderTE.syncPacket} (D-1026) and {@code FluidDuctValveTE.wheelAngle}
	 * (D-1021): the numbers under test are the ones the machine really uses.
	 */
	public long computeOutput() {

		long output = 0;

		if(!obstructed) {
			double multiplier = Wind.dimensionWindMultiplier.getOrDefault(level.dimension(), 0d);
			output = (long) (Math.min(Wind.power * multiplier * 0.5 / 40, 1) * 5000 / 20);
		}

		double lubeMultiplier = Math.pow(lube.getFill() / (double) lube.getMaxFill(), 0.25) * 0.95 + 0.05;
		return (long) (output * lubeMultiplier * 3);
	}

	@Override
	public String getPacketIdentifier() {
		return "WINDTURB_MEDIUM";
	}

	/** One millibucket of lubricant every three seconds while it is working. */
	@Override
	public int lubeConsumptionPeriod() {
		return 60;
	}
}
