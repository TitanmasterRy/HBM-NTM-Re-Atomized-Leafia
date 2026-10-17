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
 * A random decimal from Min up to (not including) Max (CCP-LEAFIA-NODES, D-3070). Adapted from Leafia's {@code NodeRNGDecimal}:
 * {@code nextFloat(Max - Min) + Min}. Java's bounded {@code nextFloat} refuses a width that is not positive and finite, so Min at
 * or above Max, or a non-finite bound, or a node with no level, reads NaN. 1.21.1's random source has no bounded float, so the
 * draw is {@code nextFloat() * width} on the same condition. Not cached, like {@link NodeRNG}.
 */
public class NodeRNGDecimal extends Node {

	public final Control ctrl;

	public NodeRNGDecimal(float x, float y, Control ctrl) {
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
			float width = max.getNumber() - min.getNumber();
			if(width > 0 && !Float.isInfinite(width))
				value = level.getRandom().nextFloat() * width + min.getNumber();
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
		return "Decimal RNG";
	}

	@Override
	public CompoundTag writeToNBT(CompoundTag tag, NodeSystem sys) {
		tag.putString("nodeType", "leafia_rng_d");
		return super.writeToNBT(tag, sys);
	}
}
