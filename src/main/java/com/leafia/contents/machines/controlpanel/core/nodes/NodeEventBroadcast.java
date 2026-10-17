package com.leafia.contents.machines.controlpanel.core.nodes;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.leafia.contents.machines.controlpanel.core.ControlEvent;
import com.leafia.contents.machines.controlpanel.core.ControlEventSystem;
import com.leafia.contents.machines.controlpanel.core.IControllable;
import com.leafia.contents.machines.controlpanel.core.NodeConnection;
import com.leafia.contents.machines.controlpanel.core.NodeDropdown;
import com.leafia.contents.machines.controlpanel.core.NodeSystem;
import com.leafia.contents.machines.controlpanel.core.modular.StockNodesRegister;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValue.DataType;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

/**
 * Sends an event to the controls this one is linked to. Adapted from NTM-CE (D-3062). One input per variable of the chosen
 * event, plus a "Cancel" input that, when true, sends nothing.
 *
 * <p>When the control has a send graph for this event, it runs once <b>per receiver</b> with {@code receiver_id} set to the
 * receiver's index (counting only receivers it did not cancel) before that receiver is sent the event; a graph whose
 * {@link IOutputNode#doOutput} returns false (a {@link NodeCancelEvent}) skips that receiver. Without one the event goes to
 * every link at once. Sending is by position and resolved when it happens, so a link to an unloaded chunk receives nothing
 * ({@link ControlEventSystem}).
 *
 * <p>The dropdown's list of events is saved as {@code itemList} so that a graph still opens when an addon that registered an
 * event is gone ({@code StockNodeLoader} then keeps the node as an {@code UnknownNode}). A hardening over CE: an input that
 * evaluates to nothing is left out of the event rather than put in as null, and a node whose saved event name is not
 * registered sends nothing instead of throwing.
 */
public class NodeEventBroadcast extends NodeOutput {

	public String eventName;

	public NodeEventBroadcast(float x, float y, List<ControlEvent> ableToBroadcast) {
		super(x, y);
		NodeDropdown opSelector = new NodeDropdown(this, otherElements.size(), s -> {
			setEvent(s);
			return null;
		}, () -> eventName);
		for(ControlEvent c : ableToBroadcast) {
			opSelector.list.addItems(c.name);
		}
		this.otherElements.add(opSelector);
		setEvent(ableToBroadcast.get(0).name);
	}

	@Override
	public CompoundTag writeToNBT(CompoundTag tag, NodeSystem sys) {
		tag.putString("nodeType", "eventBroadcast");
		tag.putString("eventName", eventName);
		CompoundTag list = new CompoundTag();
		int i = 0;
		for(String s : ((NodeDropdown) this.otherElements.get(0)).list.itemNames) {
			list.putString("item" + i, s);
			i++;
		}
		tag.put("itemList", list);
		return super.writeToNBT(tag, sys);
	}

	@Override
	public void readFromNBT(CompoundTag tag, NodeSystem sys) {
		eventName = tag.getString("eventName");
		super.readFromNBT(tag, sys);
	}

	@Override
	public boolean doOutput(IControllable from, Map<String, NodeSystem> sendNodeMap, Map<String, BlockPos> positions) {
		Level world = from.getControlWorld();
		if(world == null || !ControlEvent.isRegistered(eventName))
			return true;
		ControlEvent e = ControlEvent.newEvent(eventName);
		for(NodeConnection c : inputs) {
			DataValue v = c.evaluate();
			if(c.name.startsWith("Cancel")) {
				if(v != null && v.getBoolean())
					return true;
				continue;
			}
			if(v != null)
				e.vars.put(c.name, v);
		}
		if(sendNodeMap != null) {
			if(sendNodeMap.containsKey(e.name)) {
				NodeSystem sys = sendNodeMap.get(e.name);
				int i = 0;
				cont:
				for(BlockPos pos : positions.values()) {
					sys.resetCachedValues();
					sys.setVar("receiver_id", new DataValueFloat(i));
					for(IOutputNode o : sys.outputNodes) {
						if(!o.doOutput(from, sendNodeMap, positions))
							continue cont;
					}
					ControlEventSystem.get(world).broadcastEvent(from.getControlPos(), e, pos);
					i++;
				}
			} else {
				ControlEventSystem.get(world).broadcastEvent(from.getControlPos(), e, positions.values());
			}
		} else {
			ControlEventSystem.get(world).broadcastEvent(from.getControlPos(), e, positions.values());
		}
		return true;
	}

	public void setEvent(String name) {
		eventName = name;
		for(NodeConnection c : inputs) {
			c.removeConnection();
		}
		this.inputs.clear();
		ControlEvent evt = ControlEvent.getRegisteredEvent(name);
		for(Entry<String, DataValue> e : evt.vars.entrySet()) {
			inputs.add(new NodeConnection(e.getKey(), this, inputs.size(), true, e.getValue().getType(), e.getValue().copy()));
		}
		inputs.add(new NodeConnection("Cancel", this, inputs.size(), true, DataType.GENERIC, new DataValueFloat(0)));
		recalcSize();
	}

	@Override
	public float[] getColor() {
		return StockNodesRegister.colorOutput;
	}

	@Override
	public String getDisplayName() {
		return "Broadcast";
	}
}
