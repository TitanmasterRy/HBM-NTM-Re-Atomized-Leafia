package com.leafia.contents.machines.controlpanel.core.modular;

import com.leafia.contents.machines.controlpanel.core.NodeSystem;
import com.leafia.contents.machines.controlpanel.core.nodes.Node;

import net.minecraft.nbt.CompoundTag;

/** Turns a saved node's tag back into a node, or returns null if it does not know the tag's {@code nodeType}. Adapted from NTM-CE (D-3061). */
public interface INodeLoader {

	Node nodeFromNBT(CompoundTag tag, NodeSystem sys);
}
