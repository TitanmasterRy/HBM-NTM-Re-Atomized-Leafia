package com.leafia.contents.machines.controlpanel.core.nodes;

import java.util.Map;

import com.leafia.contents.machines.controlpanel.core.NodeSystem;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;

/**
 * A node that takes the incoming event's variables as its outputs: CE's {@code NodeInput}.
 *
 * <p>An addition of the port. CE's {@link NodeSystem#receiveEvent} tested {@code instanceof NodeInput}; that class is batch 2's,
 * and the evaluation half had to compile first, so the test is on this interface instead and {@code NodeInput} (batch 2)
 * implements it. The method keeps CE's name and its return, which a {@code NodeInput} narrows to itself.
 */
public interface IInputNode {

	Node setOutputFromVars(Map<String, DataValue> vars);
}
