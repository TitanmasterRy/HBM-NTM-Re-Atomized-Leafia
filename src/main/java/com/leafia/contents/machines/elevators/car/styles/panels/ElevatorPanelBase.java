package com.leafia.contents.machines.elevators.car.styles.panels;

import com.leafia.contents.machines.elevators.car.styles.EvWallBase;

/**
 * A wall that is <em>not</em> blank — it carries the cabin's buttons.
 *
 * <p>{@link #getStaticX()} and {@link #getStaticZ()} are where the fixed buttons (open, close,
 * bell, alarm) go; the floor buttons are laid out relative to them. They are in the panel's own
 * space, and {@code getStaticX} is in sixteenths while {@code getStaticZ} is in blocks — upstream's
 * mismatch, kept, because the car's button layout maths is written against both as they are.
 */
public abstract class ElevatorPanelBase extends EvWallBase {

	abstract public String getId();

	abstract public int getStaticX();

	public double getStaticZ() { return 0.9365; }

	public ElevatorPanelBase(int rotation) {
		super(rotation);
	}
}
