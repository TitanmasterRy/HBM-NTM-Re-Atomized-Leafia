package com.leafia.contents.machines.controlpanel.core.modular;

import java.util.ArrayList;
import java.util.List;

import com.leafia.contents.machines.controlpanel.core.ControlEvent;
import com.leafia.contents.machines.controlpanel.core.NodeSystem;
import com.leafia.contents.machines.controlpanel.core.nodes.Node;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeBoolean;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeBuffer;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeCancelEvent;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeCompositeRead;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeCompositeWrite;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeConditional;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeEventBroadcast;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeFunction;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeGetVar;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeInput;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeMath;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeQueryBlock;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeRedstoneInput;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeRedstoneOutput;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeSetVar;

import net.minecraft.nbt.CompoundTag;

/**
 * Recreates the stock nodes from their saved {@code nodeType}: the thirteen of batch 2 (D-3062) and the two redstone nodes
 * batch 3 added (D-3063), which completes the class.
 *
 * <p>Adapted from NTM-CE's {@code StockNodeLoader}. One difference, for a graph the player's client wrote: an
 * {@code eventBroadcast} whose saved list names an event nobody registered, or names none, is not recognised (the loader returns
 * null), so {@code NodeSystem} keeps it as an {@code UnknownNode} with its tag intact. CE's {@code getRegisteredEvent} threw on
 * the unknown name, and an empty list threw in the constructor.
 */
public class StockNodeLoader implements INodeLoader {

	@Override
	public Node nodeFromNBT(CompoundTag tag, NodeSystem sys) {
		Node node = null;
		switch(tag.getString("nodeType")) {
			case "cancelEvent":
				node = new NodeCancelEvent(0, 0);
				break;
			case "eventBroadcast":
				CompoundTag list = tag.getCompound("itemList");
				List<ControlEvent> l = new ArrayList<>();
				for(int i = 0; i < list.getAllKeys().size(); i++) {
					String name = list.getString("item" + i);
					if(!ControlEvent.isRegistered(name))
						return null;
					l.add(ControlEvent.getRegisteredEvent(name));
				}
				if(l.isEmpty())
					return null;
				node = new NodeEventBroadcast(0, 0, l);
				break;
			case "getVar":
				node = new NodeGetVar(0, 0, sys.parent);
				break;
			case "queryBlock":
				node = new NodeQueryBlock(0, 0, sys.parent);
				break;
			case "math":
				node = new NodeMath(0, 0);
				break;
			case "boolean":
				node = new NodeBoolean(0, 0);
				break;
			case "function":
				node = new NodeFunction(0, 0);
				break;
			case "buffer":
				node = new NodeBuffer(0, 0);
				break;
			case "conditional":
				node = new NodeConditional(0, 0);
				break;
			case "setVar":
				node = new NodeSetVar(0, 0, sys.parent);
				break;
			case "input":
				node = new NodeInput(0, 0, "");
				break;
			case "composite_read":
				node = new NodeCompositeRead(0, 0);
				break;
			case "composite_write":
				node = new NodeCompositeWrite(0, 0);
				break;
			case "redstoneInput":
				node = new NodeRedstoneInput(0, 0, sys.parent);
				break;
			case "redstoneOutput":
				node = new NodeRedstoneOutput(0, 0);
				break;
		}
		return node;
	}
}
