package com.leafia.contents.machines.controlpanel.core;

import net.minecraft.core.Direction;

/**
 * What the redstone nodes need from the block entity that owns a panel: the signal arriving at each face, and a way to set the
 * signal leaving it. NTM-CE's nodes named {@code TileEntityControlPanel} directly; an interface keeps them testable without a
 * block (D-3063).
 *
 * <p>Outputs set while an event is being handled are collected and applied together when the outermost handler finishes
 * ({@link #beginRedstoneOutputCollection}/{@link #finishRedstoneOutputCollection} nest), so a graph that writes several faces
 * changes the neighbours once.
 */
public interface IRedstonePanel {

	/** The signal arriving at this face, from whatever kind of source (CE's {@code getRedstonePower}). */
	int getRedstoneInputPower(Direction facing);

	/** The weak signal the neighbour at this face gives. */
	int getRedstoneInputWeak(Direction facing);

	/** The strong signal the neighbour at this face gives. */
	int getRedstoneInputStrong(Direction facing);

	void setWeakRedstoneOutput(Direction facing, int strength);

	void setStrongRedstoneOutput(Direction facing, int strength);

	void beginRedstoneOutputCollection();

	void finishRedstoneOutputCollection();
}
