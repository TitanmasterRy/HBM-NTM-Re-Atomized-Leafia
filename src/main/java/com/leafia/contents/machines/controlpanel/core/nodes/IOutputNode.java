package com.leafia.contents.machines.controlpanel.core.nodes;

import java.util.Map;

import com.leafia.contents.machines.controlpanel.core.IControllable;
import com.leafia.contents.machines.controlpanel.core.NodeSystem;

import net.minecraft.core.BlockPos;

/**
 * A node that does something when its graph runs: CE's {@code NodeOutput} (broadcast an event, set a variable, drive redstone,
 * cancel). See {@link IInputNode} for why this is an interface: {@link NodeSystem} keeps its output nodes as these, and batch 2's
 * {@code NodeOutput} implements it.
 */
public interface IOutputNode {

	/**
	 * Executes this output node.
	 *
	 * <p>Returning {@code true} means processing continues normally. Returning {@code false} means the current receiver is
	 * cancelled when this output is evaluated from a send-node map, so an event broadcast skips that receiver. Only explicit
	 * cancellation ({@code NodeCancelEvent}) returns {@code false}; side-effect and no-op outputs return {@code true}.
	 */
	boolean doOutput(IControllable from, Map<String, NodeSystem> sendNodeMap, Map<String, BlockPos> links);
}
