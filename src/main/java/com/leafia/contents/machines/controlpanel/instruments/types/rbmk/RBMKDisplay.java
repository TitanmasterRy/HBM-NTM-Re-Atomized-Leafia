package com.leafia.contents.machines.controlpanel.instruments.types.rbmk;

import java.util.List;
import java.util.Map.Entry;

import com.hbm.blockentity.machine.rbmk.BlockEntityRBMKConsole.ColumnType;
import com.leafia.contents.machines.controlpanel.core.Control;
import com.leafia.contents.machines.controlpanel.core.ControlEvent;
import com.leafia.contents.machines.controlpanel.core.ControlPanel;
import com.leafia.contents.machines.controlpanel.core.controls.ControlType;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;
import com.leafia.contents.machines.controlpanel.instruments.AddonControlType;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.AABB;

/**
 * The RBMK display: a map of a rectangle of RBMK columns, read live from the world, drawn flat on the panel (CCP-INSTRUMENTS-2, D-3072).
 * The common half of Leafia's {@code RBMKDisplay}; the drawing is {@code instruments.client.RBMKDisplayDraw}, the pane
 * {@code RBMKDisplayCSE}. It has no variables, no events and no collision box; its family is Leafia's "RBMK Display" and its box is one
 * unit, though the map is {@code width x height} units, centred on it.
 *
 * <p><b>It reads the world, not the panel.</b> The control is linked (by its one tagged link) to a column; the map is the
 * {@code width x height} columns around that one, as the panel's own facing sees them ({@link #columnPos}). It is drawn on the client from
 * the columns that client holds, so a column outside the client's loaded chunks is a blank square and nothing travels over the wire.
 * Zero links, or two or more, draw an error in place of the map ({@link #linkError}).
 *
 * <p>Configuration: {@code width} and {@code height} (1 to 27 columns, the console's own grid is 15) and {@code offsetU},
 * {@code offsetV} (a tick box each: shift the checkerboard of the background by one column).
 *
 * <p><b>Hardening (D-1093 #4):</b> {@code width} and {@code height} are limited to the editor's 1..27 when read (the drawing visits
 * {@code width x height} columns a frame). <b>Fix of a Leafia pane bug (D-3072):</b> his pane started its height slider at the
 * <i>width</i>, so opening the configuration of a 3 x 5 map and pressing Save made it 3 x 3; the pane here starts it at the height.
 *
 * <p><b>The fuel dot's brightness depends on the client.</b> A fuel column's dot shows {@code 0.25 + 0.75 x enrichment}, and a client
 * holds the fuel's name, yield and xenon as text but not the rod itself; its enrichment reads 0 there, so a fuel dot draws at its
 * dimmest (0.25) until the base mod syncs it. Control rods, heat and the colour groups are synced and draw as upstream's do.
 */
public class RBMKDisplay extends Control {

	public static final int SIZE_MIN = 1, SIZE_MAX = 27;

	/** Leafia's {@code sc}: how many units one column is across. */
	public static final float SC = 1;

	private int width = 1;
	private int height = 1;
	private int offsetU = 0;
	private int offsetV = 0;

	public RBMKDisplay(String name, String registryName, ControlPanel panel) {
		super(name, registryName, panel);
		configMap.put("width", new DataValueFloat(width));
		configMap.put("height", new DataValueFloat(height));
		configMap.put("offsetU", new DataValueFloat(offsetU));
		configMap.put("offsetV", new DataValueFloat(offsetV));
	}

	private static int sane(float v, int fallback) {
		if(Float.isNaN(v))
			return fallback;
		return (int) Math.max(SIZE_MIN, Math.min(SIZE_MAX, v));
	}

	@Override
	protected void onConfigMapChanged() {
		for(Entry<String, DataValue> entry : configMap.entrySet()) {
			DataValue value = entry.getValue();
			switch(entry.getKey()) {
				case "width" -> width = sane(value.getNumber(), width);
				case "height" -> height = sane(value.getNumber(), height);
				case "offsetU" -> offsetU = value.getBoolean() ? 1 : 0;
				case "offsetV" -> offsetV = value.getBoolean() ? 1 : 0;
				default -> {}
			}
		}
	}

	public int getMapWidth() { return width; }
	public int getMapHeight() { return height; }
	public int getOffsetU() { return offsetU; }
	public int getOffsetV() { return offsetV; }

	@Override
	public ControlType getControlType() {
		return AddonControlType.RBMK_DISPLAY;
	}

	@Override
	public float[] getSize() {
		return new float[]{SC, SC, 0.1f};
	}

	@Override
	public AABB getBoundingBox() {
		return null;
	}

	/**
	 * The two-line errors Leafia drew in place of the map, lines separated by {@code $}: null when the control has exactly one link.
	 */
	public String linkError() {
		if(taggedLinks.isEmpty())
			return "RBMK$not linked";
		if(taggedLinks.size() > 1)
			return "Must have only$1 position$linked";
		return null;
	}

	/** The one linked position the map is centred on; null unless there is exactly one link. */
	public BlockPos center() {
		return taggedLinks.size() == 1 ? taggedLinks.values().iterator().next() : null;
	}

	/**
	 * Where the column drawn at cell {@code (x, y)} is in the world. {@code facing} is the panel's facing opposite (what the panel looks
	 * toward); the map's rows run along it and its columns along its clockwise neighbour, from the cell that holds {@code center}:
	 * Leafia's {@code center.offset(facing, -startYInt - y).offset(facing.rotateY(), startXInt + x)} with
	 * {@code start = floor(-(size - 1) / 2)}.
	 */
	public static BlockPos columnPos(BlockPos center, Direction facing, int width, int height, int x, int y) {
		int startXInt = (int) Math.floor(-(width - 1) / 2f);
		int startYInt = (int) Math.floor(-(height - 1) / 2f);
		return center.relative(facing, -startYInt - y).relative(facing.getClockWise(), startXInt + x);
	}

	/** Where the cell's centre is on the panel, in the control's own units: {@code (startX + x, startY + y)}, a column a unit apart. */
	public static float cellX(int width, int x) {
		return -(width - 1) / 2f + x;
	}

	public static float cellY(int height, int y) {
		return -(height - 1) / 2f + y;
	}

	// ------------------------------------------------------------------ what a column looks like

	/**
	 * The colour of a column's square. A control rod with a colour group ({@code color} 0 to 4 in its data) is that group's
	 * colour (red, yellow, dark green, blue, purple; a number past the five is white, as Leafia's chain of {@code if}s left it); any
	 * other column is a grey that reddens with its heat, {@code 0.65 + 0.35 x heat / maxHeat} red over {@code 0.65} green and blue.
	 */
	public static float[] columnColor(ColumnType type, CompoundTag data, double heat, double maxHeat) {
		boolean control = type == ColumnType.CONTROL || type == ColumnType.CONTROL_AUTO;
		if(control && data.contains("color") && data.getShort("color") >= 0) {
			short colorType = data.getShort("color");
			float r = 1, g = 1, b = 1;
			if(colorType == 0) { g = 0; b = 0; }
			else if(colorType == 1) { b = 0; }
			else if(colorType == 2) { r = 0; g = 0.5F; b = 0; }
			else if(colorType == 3) { r = 0; g = 0; }
			else if(colorType == 4) { r = 0.5F; g = 0; }
			return new float[]{r, g, b};
		}
		double h = maxHeat > 0 ? Math.min(heat / maxHeat, 1) : 0;
		if(Double.isNaN(h))
			h = 0;
		double baseColor = 0.65D;
		return new float[]{(float) (baseColor + ((1 - baseColor) * h)), (float) baseColor, (float) baseColor};
	}

	/**
	 * The colour of the dot a column carries, or null for none. Fuel: green, {@code 0.25 + 0.75 x enrichment}. A manual control rod:
	 * {@code (level, level, 0)}. An automatic one: {@code (level, 0, level)}. Every other kind of column has no dot.
	 */
	public static float[] dotColor(ColumnType type, CompoundTag data) {
		return switch(type) {
			case FUEL, FUEL_SIM -> new float[]{0, 0.25F + (float) (data.getDouble("enrichment") * 0.75D), 0};
			case CONTROL -> {
				float level = (float) data.getDouble("level");
				yield new float[]{level, level, 0};
			}
			case CONTROL_AUTO -> {
				float level = (float) data.getDouble("level");
				yield new float[]{level, 0, level};
			}
			default -> null;
		};
	}

	@Override
	public Control newControl(ControlPanel controlPanel) {
		return new RBMKDisplay(name, registryName, controlPanel);
	}

	@Override
	public void populateDefaultNodes(List<ControlEvent> list) {}
}
