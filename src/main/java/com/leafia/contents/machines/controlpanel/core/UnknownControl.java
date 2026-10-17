package com.leafia.contents.machines.controlpanel.core;

import java.util.Collections;
import java.util.List;

import com.leafia.contents.machines.controlpanel.core.controls.ControlType;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.AABB;

/**
 * Stands in for a saved control that could not be brought to life, and writes its tag back unchanged, so that a panel saved
 * with an addon's control and loaded without the addon (or with a control that fails to read) does not lose it. Adapted from
 * NTM-CE's {@code UnknownControl} (D-3063; its author's note: "for round-tripping in case someone added and removed an addon and
 * wants to add it back later").
 *
 * <p>It has no events, no nodes and no behaviour, which is the point: since D-3063 it is also where a control goes when its graph
 * fails {@link ControlPanelGuard#graphProblem} on load, so a hostile or damaged graph is kept and never run. It remembers the box
 * and height the control had ({@code ntmPlaceholderMeta}) so the editor can still show and move it.
 *
 * <p>CE's {@code render}, {@code getModel} and {@code getGuiTexture} are the client's (§2.2); batch 5 draws a white box for this
 * type from {@code core.client}.
 */
public class UnknownControl extends Control {

	public static final ControlType UNKNOWN = ControlType.registerOrGet("Unknown");
	private static final float MIN_PLACEHOLDER_SPAN = 0.25F;

	private CompoundTag rawTag;
	private float boxMinX;
	private float boxMinY;
	private float boxMaxX;
	private float boxMaxY;
	private float boxHeight;
	private AABB boundingBox;

	/** Why this control is inert, when it is a registered control that would not load or was unsafe; null for a plain unknown. */
	public final String problem;

	public UnknownControl(ControlPanel panel, CompoundTag rawTag) {
		this(panel, rawTag, null);
	}

	public UnknownControl(ControlPanel panel, CompoundTag rawTag, String problem) {
		super(resolveDisplayName(rawTag), rawTag.getString("name"), panel);
		this.problem = problem;
		loadRawTag(rawTag);
	}

	private static String resolveDisplayName(CompoundTag rawTag) {
		if(rawTag.contains("myName")) {
			return rawTag.getString("myName");
		}
		String registryName = rawTag.getString("name");
		return registryName.isEmpty() ? "Unknown Control" : registryName;
	}

	private void loadRawTag(CompoundTag tag) {
		this.rawTag = tag.copy();
		this.name = resolveDisplayName(this.rawTag);
		this.posX = this.rawTag.getFloat("X");
		this.posY = this.rawTag.getFloat("Y");
		loadPlaceholderMeta(this.rawTag.getCompound(PLACEHOLDER_META_TAG));
	}

	/**
	 * Whether another placeholder keeps the same control: the same tag but for what an editor may change without knowing what
	 * the control is (name, position, the remembered box). An edit may only carry an unknown control through unchanged.
	 */
	public boolean sameContent(UnknownControl other) {
		return stripped(rawTag).equals(stripped(other.rawTag));
	}

	private static CompoundTag stripped(CompoundTag tag) {
		CompoundTag copy = tag.copy();
		copy.remove("myName");
		copy.remove("X");
		copy.remove("Y");
		copy.remove(PLACEHOLDER_META_TAG);
		return copy;
	}

	/** The tag this placeholder keeps, for tests and for the editor's "unresolved" readout. */
	public CompoundTag rawTag() {
		return rawTag.copy();
	}

	@Override
	public ControlType getControlType() {
		return UNKNOWN;
	}

	@Override
	public float[] getSize() {
		return new float[]{Math.max(boxMaxX - boxMinX, 0.1F), Math.max(boxMaxY - boxMinY, 0.1F), boxHeight};
	}

	@Override
	public void fillBox(float[] box) {
		box[0] = boxMinX;
		box[1] = boxMinY;
		box[2] = boxMaxX;
		box[3] = boxMaxY;
	}

	@Override
	public AABB getBoundingBox() {
		return boundingBox;
	}

	@Override
	public Control newControl(ControlPanel panel) {
		return new UnknownControl(panel, rawTag, problem);
	}

	@Override
	public void populateDefaultNodes(List<ControlEvent> receiveEvents) {}

	@Override
	public List<String> getInEvents() {
		return Collections.emptyList();
	}

	/** Nothing is ever delivered to a placeholder: its graphs are not built. */
	@Override
	public void receiveEvent(ControlEvent evt) {}

	@Override
	public CompoundTag writeToNBT(CompoundTag tag) {
		CompoundTag copy = rawTag.copy();
		copy.putString("myName", name);
		copy.putFloat("X", posX);
		copy.putFloat("Y", posY);
		copy.put(PLACEHOLDER_META_TAG, writePlaceholderMeta());
		return copy;
	}

	@Override
	public void readFromNBT(CompoundTag tag) {
		loadRawTag(tag);
	}

	private void loadPlaceholderMeta(CompoundTag meta) {
		this.boxMinX = meta.contains(PLACEHOLDER_BOX_MIN_X) ? meta.getFloat(PLACEHOLDER_BOX_MIN_X) : this.posX;
		this.boxMinY = meta.contains(PLACEHOLDER_BOX_MIN_Y) ? meta.getFloat(PLACEHOLDER_BOX_MIN_Y) : this.posY;
		this.boxMaxX = meta.contains(PLACEHOLDER_BOX_MAX_X) ? meta.getFloat(PLACEHOLDER_BOX_MAX_X) : this.posX + 1;
		this.boxMaxY = meta.contains(PLACEHOLDER_BOX_MAX_Y) ? meta.getFloat(PLACEHOLDER_BOX_MAX_Y) : this.posY + 1;
		if(this.boxMaxX <= this.boxMinX) {
			this.boxMaxX = this.boxMinX + MIN_PLACEHOLDER_SPAN;
		}
		if(this.boxMaxY <= this.boxMinY) {
			this.boxMaxY = this.boxMinY + MIN_PLACEHOLDER_SPAN;
		}
		this.boxHeight = meta.contains(PLACEHOLDER_SIZE_HEIGHT) ? Math.max(meta.getFloat(PLACEHOLDER_SIZE_HEIGHT), 0.1F) : 0.1F;

		if(meta.contains(PLACEHOLDER_HAS_BOUNDING_BOX) && !meta.getBoolean(PLACEHOLDER_HAS_BOUNDING_BOX)) {
			this.boundingBox = null;
			return;
		}
		if(meta.contains(PLACEHOLDER_BOUNDS_MIN_X) && meta.contains(PLACEHOLDER_BOUNDS_MIN_Y) && meta.contains(PLACEHOLDER_BOUNDS_MIN_Z)
				&& meta.contains(PLACEHOLDER_BOUNDS_MAX_X) && meta.contains(PLACEHOLDER_BOUNDS_MAX_Y) && meta.contains(PLACEHOLDER_BOUNDS_MAX_Z)) {
			this.boundingBox = new AABB(
					meta.getDouble(PLACEHOLDER_BOUNDS_MIN_X), meta.getDouble(PLACEHOLDER_BOUNDS_MIN_Y), meta.getDouble(PLACEHOLDER_BOUNDS_MIN_Z),
					meta.getDouble(PLACEHOLDER_BOUNDS_MAX_X), meta.getDouble(PLACEHOLDER_BOUNDS_MAX_Y), meta.getDouble(PLACEHOLDER_BOUNDS_MAX_Z));
			this.boxHeight = Math.max(this.boxHeight, (float) Math.max(0.1D, this.boundingBox.maxY - this.boundingBox.minY));
			return;
		}
		this.boundingBox = new AABB(boxMinX, 0, boxMinY, boxMaxX, boxHeight, boxMaxY);
	}

	private CompoundTag writePlaceholderMeta() {
		CompoundTag meta = new CompoundTag();
		meta.putFloat(PLACEHOLDER_BOX_MIN_X, boxMinX);
		meta.putFloat(PLACEHOLDER_BOX_MIN_Y, boxMinY);
		meta.putFloat(PLACEHOLDER_BOX_MAX_X, boxMaxX);
		meta.putFloat(PLACEHOLDER_BOX_MAX_Y, boxMaxY);
		meta.putFloat(PLACEHOLDER_SIZE_HEIGHT, boxHeight);
		meta.putBoolean(PLACEHOLDER_HAS_BOUNDING_BOX, boundingBox != null);
		if(boundingBox != null) {
			meta.putDouble(PLACEHOLDER_BOUNDS_MIN_X, boundingBox.minX);
			meta.putDouble(PLACEHOLDER_BOUNDS_MIN_Y, boundingBox.minY);
			meta.putDouble(PLACEHOLDER_BOUNDS_MIN_Z, boundingBox.minZ);
			meta.putDouble(PLACEHOLDER_BOUNDS_MAX_X, boundingBox.maxX);
			meta.putDouble(PLACEHOLDER_BOUNDS_MAX_Y, boundingBox.maxY);
			meta.putDouble(PLACEHOLDER_BOUNDS_MAX_Z, boundingBox.maxZ);
		}
		return meta;
	}
}
