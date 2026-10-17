package com.leafia.contents.machines.controlpanel.nodes.utility;

import com.leafia.contents.machines.controlpanel.AddonNodesRegister;
import com.leafia.contents.machines.controlpanel.core.Control;
import com.leafia.contents.machines.controlpanel.core.NodeConnection;
import com.leafia.contents.machines.controlpanel.core.NodeSystem;
import com.leafia.contents.machines.controlpanel.core.nodes.Node;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValue.DataType;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;
import com.leafia.contents.machines.controlpanel.nodes.AddonNodeSupport;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

/**
 * A random whole number from Min to Max inclusive (CCP-LEAFIA-NODES, D-3070). Adapted from Leafia's {@code NodeRNG}.
 *
 * <p>Both bounds are truncated to integers; the number is drawn from the level's random source. A range that is empty (Min above
 * Max), or one so wide the width overflows an int, or a node with no level, reads NaN, which is what Leafia's
 * {@code catch (Exception)} produced. <b>Not cached</b>, as Leafia's: every consumer's read draws again, so two nodes fed by one
 * RNG see two numbers; put a Cache node after it to share one.
 */
public class NodeRNG extends Node {

	public final Control ctrl;

	public NodeRNG(float x, float y, Control ctrl) {
		super(x, y);
		this.ctrl = ctrl;
		outputs.add(new NodeConnection("Random", this, outputs.size(), false, DataType.NUMBER, new DataValueFloat(0)));
		inputs.add(new NodeConnection("Min", this, inputs.size(), true, DataType.NUMBER, new DataValueFloat(0)));
		inputs.add(new NodeConnection("Max", this, inputs.size(), true, DataType.NUMBER, new DataValueFloat(1)));
		recalcSize();
		evalCache = new DataValue[1];
	}

	@Override
	public DataValue evaluate(int i) {
		if(cacheValid)
			return evalCache[0];
		if(inputs.size() < 2)
			return null;
		DataValue min = inputs.get(0).evaluate();
		DataValue max = inputs.get(1).evaluate();
		if(min == null || max == null)
			return null;
		float value = Float.NaN;
		Level level = AddonNodeSupport.levelOf(ctrl);
		if(level != null) {
			try {
				value = level.getRandom().nextInt((int) max.getNumber() - (int) min.getNumber() + 1) + (int) min.getNumber();
			} catch(RuntimeException ignored) {}
		}
		evalCache[0] = new DataValueFloat(value);
		return evalCache[0];
	}

	@Override
	public float[] getColor() {
		return AddonNodesRegister.colorUtility;
	}

	@Override
	public String getDisplayName() {
		return "RNG";
	}

	@Override
	public CompoundTag writeToNBT(CompoundTag tag, NodeSystem sys) {
		tag.putString("nodeType", "leafia_rng");
		return super.writeToNBT(tag, sys);
	}
}
