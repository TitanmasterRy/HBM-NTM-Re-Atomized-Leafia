package com.leafia.contents.machines.controlpanel;

import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.machines.controlpanel.core.NodeSystem;
import com.leafia.contents.machines.controlpanel.core.modular.INodeLoader;
import com.leafia.contents.machines.controlpanel.core.nodes.Node;
import com.leafia.contents.machines.controlpanel.nodes.NodeBulkQuery;
import com.leafia.contents.machines.controlpanel.nodes.NodeGraphAdd;
import com.leafia.contents.machines.controlpanel.nodes.NodeGraphBounds;
import com.leafia.contents.machines.controlpanel.nodes.NodeSounder;
import com.leafia.contents.machines.controlpanel.nodes.bool.NodePulse;
import com.leafia.contents.machines.controlpanel.nodes.bool.NodeSRLatch;
import com.leafia.contents.machines.controlpanel.nodes.pack.NodeIC10;
import com.leafia.contents.machines.controlpanel.nodes.ror.NodeRoRReceiver;
import com.leafia.contents.machines.controlpanel.nodes.ror.NodeRoRSender;
import com.leafia.contents.machines.controlpanel.nodes.string.NodeAddString;
import com.leafia.contents.machines.controlpanel.nodes.string.NodeFormat;
import com.leafia.contents.machines.controlpanel.nodes.string.NodeSIPfx;
import com.leafia.contents.machines.controlpanel.nodes.string.NodeSubString;
import com.leafia.contents.machines.controlpanel.nodes.utility.NodeCache;
import com.leafia.contents.machines.controlpanel.nodes.utility.NodeClock;
import com.leafia.contents.machines.controlpanel.nodes.utility.NodeParseFloat;
import com.leafia.contents.machines.controlpanel.nodes.utility.NodeRNG;
import com.leafia.contents.machines.controlpanel.nodes.utility.NodeRNGDecimal;
import com.leafia.contents.machines.controlpanel.nodes.utility.NodeSummarizer;

import net.minecraft.nbt.CompoundTag;

/**
 * Recreates Leafia's nodes from their saved {@code nodeType} (CCP-LEAFIA-NODES, D-3070). Adapted from Leafia's
 * {@code AddonNodeLoader}; the {@code nodeType} strings are upstream's, byte for byte, because they are what a saved panel
 * holds. Returns null for any other type, so the stock loader's and the placeholder's rules are unchanged.
 *
 * <p>The graph instrument's two nodes ({@code leafia_graph}, {@code leafia_graph_bounds}) came with CCP-INSTRUMENTS-1 (D-3071).
 * The IC10 node ({@code leafia_ic10}) came with CCP-IC10 (D-3073); with {@code controlPanelIC10} off it is not built, and a saved
 * graph holding one keeps it as an {@code UnknownNode} with its tag intact, which is how the port treats every node it cannot build.
 */
public class AddonNodeLoader implements INodeLoader {

	@Override
	public Node nodeFromNBT(CompoundTag tag, NodeSystem sys) {
		return switch(tag.getString("nodeType")) {
			case "sounder" -> new NodeSounder(0, 0);
			case "addString" -> new NodeAddString(0, 0);
			case "subString" -> new NodeSubString(0, 0);
			case "sipfx" -> new NodeSIPfx(0, 0);
			case "format" -> new NodeFormat(0, 0);
			case "bulkQuery" -> new NodeBulkQuery(0, 0, sys.parent);
			case "leafia_clock" -> new NodeClock(0, 0);
			case "leafia_pulse" -> new NodePulse(0, 0);
			case "leafia_sr_latch" -> new NodeSRLatch(0, 0);
			case "leafia_summarizer" -> new NodeSummarizer(0, 0);
			case "leafia_cache" -> new NodeCache(0, 0);
			case "leafia_graph" -> new NodeGraphAdd(0, 0, sys.parent);
			case "leafia_graph_bounds" -> new NodeGraphBounds(0, 0, sys.parent);
			case "leafia_parse_float" -> new NodeParseFloat(0, 0);
			case "leafia_ror_receiver" -> new NodeRoRReceiver(0, 0, sys.parent);
			case "leafia_ror_sender" -> new NodeRoRSender(0, 0, sys.parent);
			case "leafia_rng" -> new NodeRNG(0, 0, sys.parent);
			case "leafia_rng_d" -> new NodeRNGDecimal(0, 0, sys.parent);
			// With IC10 off the node is not built, so a saved graph keeps it as an inert placeholder with its tag intact.
			case "leafia_ic10" -> LeafiaFeatures.controlPanelIC10 ? new NodeIC10(0, 0, sys.parent) : null;
			default -> null;
		};
	}
}
