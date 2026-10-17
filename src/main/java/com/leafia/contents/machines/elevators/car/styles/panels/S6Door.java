package com.leafia.contents.machines.elevators.car.styles.panels;

/**
 * The S6 cabin's door panel — the only concrete panel upstream ships.
 *
 * <p>It adds nothing to {@link EvGenericDoorBase}'s geometry; it names itself {@code s6door} for
 * the style lookup and puts the fixed buttons 11/16 left of the panel's centre.
 */
public class S6Door extends EvGenericDoorBase {

	public S6Door(int rotation) {
		super(rotation);
	}

	@Override
	public String getId() {
		return "s6door";
	}

	@Override
	public int getStaticX() {
		return -11;
	}
}
