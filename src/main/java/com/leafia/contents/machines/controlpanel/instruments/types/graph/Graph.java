package com.leafia.contents.machines.controlpanel.instruments.types.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.leafia.contents.machines.controlpanel.CCPSyncPacketBase;
import com.leafia.contents.machines.controlpanel.core.Control;
import com.leafia.contents.machines.controlpanel.core.ControlEvent;
import com.leafia.contents.machines.controlpanel.core.ControlPanel;
import com.leafia.contents.machines.controlpanel.core.controls.ControlType;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;
import com.leafia.contents.machines.controlpanel.core.types.DataValueString;
import com.leafia.contents.machines.controlpanel.instruments.AddonControlType;
import com.leafia.dev.optimization.bitbyte.LeafiaBuf;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The graph: a strip chart of the last {@code segments} numbers a graph's "Graph Add" node pushed into it, scaled between its
 * {@code minValue} and {@code maxValue} variables (CCP-INSTRUMENTS-1, D-3071). The common half of Leafia's {@code Graph}; the drawing
 * ({@code render}, {@code renderControl}, the GL clip planes) is {@code instruments.client.InstrumentRenderers}, and the
 * configuration pane is {@code GraphCSE}.
 *
 * <h2>What it keeps, and where</h2>
 * The samples ({@link #values}) are the one thing the control owns that is not a variable, so they are not synced by the panel's
 * variable deltas. They are saved ({@code graphData}, a list of floats, only read back when the count is the configured
 * {@code segments}) and sent to every player within 256 blocks each tick as a {@link CCPGraphSyncPacket}, as Leafia sent them.
 *
 * <h2>Hardening of what a client can author (D-1093 #4; invisible to anything the editor can make)</h2>
 * Every derived field is clamped to the editor's own slider range when read from the configuration, and the configuration map keeps
 * the raw value so a panel round-trips byte for byte: {@code segments} 1..200 (Leafia took any int, and a crafted panel could ask
 * for a two-billion-element array on the server), {@code verticalLineSpan} 1..20 (0 was an endless loop in the renderer),
 * {@code horizontalLineDivision} 0..20, {@code decimalLength} 0..5 (Leafia's own), {@code width} and {@code height} 1..10,
 * {@code textOffset} 0..1, the colour 0..255. A NaN reads as the value the field already had.
 */
public class Graph extends Control {

	/** The editor sliders' ranges, which are also the clamps (see the class note). */
	public static final int MIN_SEGMENTS = 1, MAX_SEGMENTS = CCPSyncPacketBase.MAX_SAMPLES;

	/** The thickness of the dark frame around the plot, in panel units. */
	public static final float BORDER = 0.1f;

	private float width = 6;
	private float height = 4;
	private int verticalLineSpan = 1;
	private int horizontalLineDivision = 10;
	private int segments = 40;
	private int decimalLength = 2;
	private float textOffset = 0.5f;
	private float red = 0;
	private float green = 255;
	private float blue = 0;
	private GraphSkin skin = GraphSkin.CONSOLE;

	/** The samples, oldest first. */
	public double[] values = new double[segments];

	public Graph(String name, String registryName, ControlPanel panel) {
		super(name, registryName, panel);
		vars.put("minValue", new DataValueFloat(0));
		vars.put("maxValue", new DataValueFloat(1));
		configMap.put("red", new DataValueFloat(red));
		configMap.put("green", new DataValueFloat(green));
		configMap.put("blue", new DataValueFloat(blue));
		configMap.put("skin", new DataValueString("Console"));
		configMap.put("segments", new DataValueFloat(40));
		configMap.put("verticalLineSpan", new DataValueFloat(1));
		configMap.put("horizontalLineDivision", new DataValueFloat(10));
		configMap.put("decimalLength", new DataValueFloat(2));
		configMap.put("textOffset", new DataValueFloat(0.5f));
		configMap.put("width", new DataValueFloat(6));
		configMap.put("height", new DataValueFloat(4));
	}

	private static float sane(float v, float lo, float hi, float fallback) {
		if(Float.isNaN(v))
			return fallback;
		return Math.max(lo, Math.min(hi, v));
	}

	@Override
	protected void onConfigMapChanged() {
		for(Entry<String, DataValue> entry : configMap.entrySet()) {
			DataValue value = entry.getValue();
			switch(entry.getKey()) {
				case "red" -> red = sane(value.getNumber(), 0, 255, red);
				case "green" -> green = sane(value.getNumber(), 0, 255, green);
				case "blue" -> blue = sane(value.getNumber(), 0, 255, blue);
				case "skin" -> {
					GraphSkin s = GraphSkin.byName(value.toString());
					if(s != null)
						skin = s;
				}
				case "segments" -> {
					segments = (int) sane(value.getNumber(), MIN_SEGMENTS, MAX_SEGMENTS, segments);
					rebuildArray();
				}
				case "verticalLineSpan" -> verticalLineSpan = (int) sane(value.getNumber(), 1, 20, verticalLineSpan);
				case "horizontalLineDivision" -> horizontalLineDivision = (int) sane(value.getNumber(), 0, 20, horizontalLineDivision);
				case "decimalLength" -> decimalLength = (int) sane(value.getNumber(), 0, 5, decimalLength);
				case "textOffset" -> textOffset = sane(value.getNumber(), 0, 1, textOffset);
				case "width" -> width = sane(value.getNumber(), 1, 10, width);
				case "height" -> height = sane(value.getNumber(), 1, 10, height);
				default -> {}
			}
		}
	}

	// ------------------------------------------------------------------ what the renderer reads

	public float getWidth() { return width; }
	public float getHeight() { return height; }
	public int getVerticalLineSpan() { return verticalLineSpan; }
	public int getHorizontalLineDivision() { return horizontalLineDivision; }
	public int getSegments() { return segments; }
	public int getDecimalLength() { return decimalLength; }
	public float getTextOffset() { return textOffset; }
	public float getRed() { return red; }
	public float getGreen() { return green; }
	public float getBlue() { return blue; }
	public GraphSkin getSkin() { return skin; }

	public double getMinValue() {
		return vars.get("minValue").getNumber();
	}

	public double getMaxValue() {
		return vars.get("maxValue").getNumber();
	}

	/**
	 * A colour scheme of the plot. Leafia: "we ain't doing enums because fuck those" - a class an addon could register more of.
	 * Colours are 0xRRGGBB. {@code emissive} skins are drawn at full brightness; {@code hasGrid} ones draw the grid lines.
	 */
	public static class GraphSkin {
		public final String name;
		public final boolean emissive;
		public final boolean hasGrid;
		public final int bgColor;
		public final int gridColor;
		public final int textColor;

		private GraphSkin(String name, boolean emissive, boolean hasGrid, int bgColor, int gridColor, int textColor) {
			this.name = name;
			this.emissive = emissive;
			this.hasGrid = hasGrid;
			this.bgColor = bgColor;
			this.gridColor = gridColor;
			this.textColor = textColor;
		}

		private static final List<GraphSkin> SKINS = new ArrayList<>();

		public static synchronized GraphSkin registerOrGet(String name, boolean emissive, boolean hasGrid, int bgColor, int gridColor, int textColor) {
			for(GraphSkin skin : SKINS) {
				if(skin.name.equals(name))
					return skin;
			}
			GraphSkin skin = new GraphSkin(name, emissive, hasGrid, bgColor, gridColor, textColor);
			SKINS.add(skin);
			return skin;
		}

		@Nullable
		public static synchronized GraphSkin byName(String name) {
			for(GraphSkin skin : SKINS) {
				if(skin.name.equals(name))
					return skin;
			}
			return null;
		}

		/** Every skin, in registration order. */
		public static synchronized List<GraphSkin> skins() {
			return Collections.unmodifiableList(new ArrayList<>(SKINS));
		}

		public static final GraphSkin CONSOLE = registerOrGet("Console", true, false, 0, 0, 0);
		public static final GraphSkin LIGHT = registerOrGet("Light", false, true, 0xFFFFFF, 0xCCCCCC, 0xAAAAAA);
		public static final GraphSkin DARK = registerOrGet("Dark", true, true, 0x1A1A1A, 0x3A3A3A, 0xCCCCCC);
	}

	@Override
	public ControlType getControlType() {
		return AddonControlType.GRAPH;
	}

	@Override
	public float[] getSize() {
		return new float[]{width + BORDER * 2, height + BORDER * 2, 0.1f};
	}

	/** No collision box: a click passes through a chart, as it did upstream. */
	@Override
	public AABB getBoundingBox() {
		return null;
	}

	// ------------------------------------------------------------------ samples

	/** Drops the oldest sample and appends this one. */
	public void pushValue(double value) {
		if(values.length == 0)
			return;
		for(int i = 0; i < values.length - 1; i++)
			values[i] = values[i + 1];
		values[values.length - 1] = value;
	}

	/** {@code value} as a fraction of the plot's height; the maximum itself when the range is empty. */
	public double scaleValue(double value) {
		double minValue = getMinValue();
		double maxValue = getMaxValue();
		if(maxValue == minValue)
			return maxValue;
		return (value - minValue) / (maxValue - minValue);
	}

	public void rebuildArray() {
		if(values.length != segments)
			values = new double[segments];
	}

	// ------------------------------------------------------------------ sync

	@Override
	public void receiveEvent(ControlEvent evt) {
		if(evt.name.equals("tick"))
			CCPSyncPacketBase.sendFor(this, syncPacket());
		super.receiveEvent(evt);
	}

	/** The packet that carries this graph's samples to the players near its panel. Needs a panel to know its own index. */
	public CCPGraphSyncPacket syncPacket() {
		int index = panel == null ? -1 : panel.controls.indexOf(this);
		return new CCPGraphSyncPacket(panel == null || panel.parent == null ? null : panel.parent.getControlPos(), index, this);
	}

	/**
	 * A graph's samples, server to client. The count is bounded on read ({@link CCPSyncPacketBase#MAX_SAMPLES}); a count outside
	 * 1..200 keeps nothing. The client keeps what it is sent whatever its own configured {@code segments} is, as Leafia's did, and the
	 * panel's full set (which carries the same samples) brings the two back in step.
	 */
	public static class CCPGraphSyncPacket extends CCPSyncPacketBase {

		private Graph graph;

		public CCPGraphSyncPacket() {}

		public CCPGraphSyncPacket(net.minecraft.core.BlockPos pos, int idx, Graph graph) {
			super(pos, idx);
			this.graph = graph;
		}


		@Override
		public Object signature() {
			return java.util.Arrays.asList(pos, idx, java.util.Arrays.toString(graph.values));
		}

		@Override
		public void encode(LeafiaBuf buf) {
			super.encode(buf);
			int n = Math.min(graph.values.length, MAX_SAMPLES);
			buf.writeShort(n);
			for(int i = 0; i < n; i++)
				buf.writeFloat((float) graph.values[i]);
		}

		@Override
		public Consumer<IPayloadContext> decode(LeafiaBuf buf) {
			Target target = readTarget(buf);
			int n = buf.readShort();
			if(n < 1 || n > MAX_SAMPLES)
				return null;
			double[] samples = new double[n];
			for(int i = 0; i < n; i++)
				samples[i] = buf.readFloat();
			return apply(target, Graph.class, g -> g.values = samples);
		}
	}

	// ------------------------------------------------------------------ construction and saving

	@Override
	public Control newControl(ControlPanel controlPanel) {
		return new Graph(name, registryName, controlPanel);
	}

	@Override
	public void populateDefaultNodes(List<ControlEvent> list) {}

	@Override
	public void readFromNBT(CompoundTag tag) {
		super.readFromNBT(tag);
		if(tag.contains("graphData", Tag.TAG_LIST)) {
			ListTag list = tag.getList("graphData", Tag.TAG_FLOAT);
			if(list.size() != segments)
				return;
			rebuildArray();
			for(int i = 0; i < list.size(); i++)
				values[i] = list.getFloat(i);
		}
	}

	@Override
	public CompoundTag writeToNBT(CompoundTag tag) {
		ListTag list = new ListTag();
		for(double value : values)
			list.add(FloatTag.valueOf((float) value));
		tag.put("graphData", list);
		return super.writeToNBT(tag);
	}
}
