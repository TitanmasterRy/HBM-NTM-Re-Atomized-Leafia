package com.leafia.contents.machines.controlpanel.core.nodes;

import java.util.ArrayList;
import java.util.List;

import com.leafia.contents.machines.controlpanel.core.NodeConnection;
import com.leafia.contents.machines.controlpanel.core.NodeElement;
import com.leafia.contents.machines.controlpanel.core.NodeSystem;
import com.leafia.contents.machines.controlpanel.core.modular.INodeLoader;
import com.leafia.contents.machines.controlpanel.core.modular.NTMControlPanelRegistry;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;

import net.minecraft.nbt.CompoundTag;

/**
 * One box of a control's node graph. The <b>common half</b> of NTM-CE's {@code Node} (D-3061): position and size, sockets,
 * evaluation caching, NBT, and the loader dispatch. {@code render} and {@code drawConnections} are the client's (§2.2) and
 * live in {@code core.client} from batch 7.
 *
 * <p>A node is evaluated lazily: {@link #evaluate} on a socket index pulls its inputs through {@link NodeConnection#evaluate()}
 * and caches the result until {@link NodeSystem#resetCachedValues()}. Every concrete node (batch 2 onward) writes its own
 * {@code nodeType} key in {@link #writeToNBT} and is recreated from it by an {@link INodeLoader}.
 */
public abstract class Node {

	public float posX;
	public float posY;
	public float size;

	public List<NodeElement> otherElements = new ArrayList<>();
	public List<NodeConnection> inputs = new ArrayList<>();
	public List<NodeConnection> outputs = new ArrayList<>();

	public boolean cacheValid = false;
	public DataValue[] evalCache = null;

	public abstract DataValue evaluate(int idx);

	public abstract float[] getColor();

	public abstract String getDisplayName();

	public Node(float x, float y) {
		setPosition(x, y);
	}

	public CompoundTag writeToNBT(CompoundTag tag, NodeSystem sys) {
		CompoundTag inputs = new CompoundTag();
		for(int i = 0; i < this.inputs.size(); i++) {
			inputs.put("con" + i, this.inputs.get(i).writeToNBT(new CompoundTag(), sys));
		}
		tag.put("in", inputs);

		CompoundTag outputs = new CompoundTag();
		for(int i = 0; i < this.outputs.size(); i++) {
			outputs.put("con" + i, this.outputs.get(i).writeToNBT(new CompoundTag(), sys));
		}
		tag.put("out", outputs);

		tag.putFloat("X", posX);
		tag.putFloat("Y", posY);
		tag.putFloat("S", size);
		return tag;
	}

	public void readFromNBT(CompoundTag tag, NodeSystem sys) {
		this.inputs.clear();
		this.outputs.clear();
		CompoundTag inputs = tag.getCompound("in");
		for(int i = 0; i < inputs.getAllKeys().size(); i++) {
			NodeConnection c = new NodeConnection(null, this, 0, false, null, new DataValueFloat(0));
			c.readFromNBT(inputs.getCompound("con" + i), sys);
			this.inputs.add(c);
		}

		CompoundTag outputs = tag.getCompound("out");
		for(int i = 0; i < outputs.getAllKeys().size(); i++) {
			NodeConnection c = new NodeConnection(null, this, 0, false, null, new DataValueFloat(0));
			c.readFromNBT(outputs.getCompound("con" + i), sys);
			this.outputs.add(c);
		}

		this.posX = tag.getFloat("X");
		this.posY = tag.getFloat("Y");
		this.size = tag.getFloat("S");
		cacheValid = false;
	}

	/** Asks each registered loader in turn; the first to recognise {@code nodeType} wins. Null if none does. */
	public static Node nodeFromNBT(CompoundTag tag, NodeSystem sys) {
		for(INodeLoader loader : NTMControlPanelRegistry.nbtNodeLoaders) {
			Node node = loader.nodeFromNBT(tag, sys);
			if(node != null)
				return node;
		}
		return null;
	}

	public void setPosition(float x, float y) {
		this.posX = x;
		this.posY = y;
		for(NodeElement c : otherElements)
			c.resetOffset();
		for(NodeConnection c : inputs)
			c.resetOffset();
		for(NodeConnection c : outputs)
			c.resetOffset();
	}

	public void recalcSize() {
		this.size = (otherElements.size() + inputs.size() + outputs.size()) * 8 + 2;
		this.setPosition(posX, posY);
	}

	public boolean onClick(float x, float y) {
		for(NodeElement n : otherElements) {
			if(n.onClick(x, y))
				return true;
		}
		return false;
	}

	public float[] getBoundingBox() {
		return new float[]{posX, posY, posX + 40, posY + 6 + size};
	}

	public float[] getExtendedBoundingBox() {
		return new float[]{posX - 2, posY, posX + 42, posY + 6 + size};
	}
}
