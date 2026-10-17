package com.leafia.contents.machines.controlpanel.core.nodes;

/**
 * Marks a node that owns a sub-graph: CE's {@code NodeFunction}. Its {@code evaluate(0)} is the enable bit, and when that is
 * true the system runs the sub-graph it keeps for the node. An addition of the port, for the reason {@link IInputNode} gives;
 * batch 2's {@code NodeFunction} implements it.
 */
public interface IFunctionNode {
}
