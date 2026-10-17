package com.leafia.contents.machines.controlpanel.core;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

/**
 * A click on a control, from the client's ray to the packet's tag. The <b>common half</b> of NTM-CE's
 * {@code BlockControlPanel.onBlockActivated} client branch (D-3065): the ray cast and the tag are pure, so a GameTest can
 * exercise them; sending the packet is the client's ({@code ControlPanelClient}).
 *
 * <p>CE worked out which control the eye ray hits <i>on the client</i>, with the client's matrices, and sent the index to the
 * server. The server never trusts it: {@code ControlPanelTE.applyClick} re-checks reach, that the index exists, and that the
 * event is a {@code ctrl_press}, and the base mod's {@code NBTControlPacket} refuses a position that is not loaded or whose
 * block entity does not grant the player permission (D-3063 trust boundary, point 9). This class only builds the honest packet.
 */
public final class ControlPanelClick {

	private ControlPanelClick() {}

	/** The key {@code ControlPanelTE.applyClick} reads the control index from. */
	public static final String KEY_INDEX = "click_control";

	/**
	 * The index of the control the ray hits, nearest first, or -1. {@code eye} and {@code look} are in world space; the panel
	 * wants the ray in block space, so the block's corner is taken off the start (CE's {@code getSelectedControl}).
	 */
	public static int pickIndex(ControlPanel panel, BlockPos blockPos, Vec3 eye, Vec3 look) {
		Vec3 local = new Vec3(eye.x - blockPos.getX(), eye.y - blockPos.getY(), eye.z - blockPos.getZ());
		Control hit = panel.pick(local, look);
		return hit == null ? -1 : panel.controls.indexOf(hit);
	}

	/** The tag CE sent for a press: a {@code ctrl_press} event carrying {@code isSneaking}, plus the control's index. */
	public static CompoundTag press(int index, boolean sneaking) {
		CompoundTag tag = ControlEvent.newEvent("ctrl_press").setVar("isSneaking", sneaking).writeToNBT(new CompoundTag());
		tag.putInt(KEY_INDEX, index);
		return tag;
	}
}
