package com.leafia.contents.machines.elevators.car.styles.panels;

import java.util.ArrayList;
import java.util.List;

import com.leafia.contents.machines.elevators.car.HitSrf;
import com.leafia.dev.math.FiaMatrix;

/**
 * The panel with the doorway in it — six surfaces instead of the blank wall's one.
 *
 * <p>Where {@link com.leafia.contents.machines.elevators.car.styles.EvWallBase} is a single sheet
 * 30/16 wide, this is that sheet with a 14/16 hole cut in it: a thin door leaf across the opening,
 * a wall to each side with its own returning edge, and a lintel over the top. The doorway is
 * 32/16 tall and the surrounding wall 36/16, the same pair as the blank wall.
 *
 * <p>Every surface is built off one shared base matrix, and {@link FiaMatrix} is immutable, so
 * each {@code mat.translate(...)} below branches from that base rather than accumulating — which
 * is why the four offsets read as absolute positions and not as a walk.
 *
 * <p>Upstream carries a commented-out earlier version of this layout above the live one. It is
 * dead and its numbers disagree with the live block (its door sits at {@code -23/16} where this
 * one is at {@code -11/16}), so it is not reproduced here; {@code EvGenericDoorBase} lines 13-27
 * upstream if anyone wants it.
 */
public abstract class EvGenericDoorBase extends ElevatorPanelBase {

	public EvGenericDoorBase(int rotation) {
		super(rotation);
	}

	@Override
	public List<HitSrf> getHitSurfaces() {

		List<HitSrf> surfaces = new ArrayList<>();

		FiaMatrix mat = new FiaMatrix().translate(0,0,-15/16d).rotateY(180);

		surfaces.add(new HitSrf(mat.translate(0,0,4/16d),-7/16d,0,7/16d,32/16d,1/16d));
		//door

		surfaces.add(new HitSrf(mat.translate(-7/16d,0,0),-8/16d,0,0,36/16d,4/16d));
		surfaces.add(new HitSrf(mat.translate(-7/16d,0,0).rotateY(-90),0,0,4/16d,32/16d,8/16d));
		// left wall

		surfaces.add(new HitSrf(mat.translate(7/16d,0,0),0,0,8/16d,36/16d,4/16d));
		surfaces.add(new HitSrf(mat.translate(7/16d,0,0).rotateY(90),-4/16d,0,0,32/16d,8/16d));
		// right wall

		surfaces.add(new HitSrf(mat.translate(0,32/16d,0),-7/16d,0,7/16d,4/16d,4/16d));
		// ceiling

		return surfaces;
	}
}
