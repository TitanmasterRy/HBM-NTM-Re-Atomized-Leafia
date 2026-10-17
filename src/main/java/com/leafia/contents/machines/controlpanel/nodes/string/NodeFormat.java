package com.leafia.contents.machines.controlpanel.nodes.string;

import java.util.ArrayList;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.leafia.contents.machines.controlpanel.AddonNodesRegister;
import com.leafia.contents.machines.controlpanel.core.NodeConnection;
import com.leafia.contents.machines.controlpanel.core.NodeSystem;
import com.leafia.contents.machines.controlpanel.core.NodeTextBox;
import com.leafia.contents.machines.controlpanel.core.nodes.Node;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValue.DataType;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;
import com.leafia.contents.machines.controlpanel.core.types.DataValueString;
import com.leafia.contents.machines.controlpanel.elements.NodeRetarded;
import com.leafia.contents.machines.controlpanel.nodes.AddonNodeSupport;

import net.minecraft.nbt.CompoundTag;

/**
 * {@code String.format} over a typed format string (CCP-LEAFIA-NODES, D-3070). Adapted from Leafia's {@code NodeFormat}.
 *
 * <p>The node has a text box for the format and one input per conversion in it, rebuilt whenever the format is committed: a
 * {@code %s} gives a text input, and {@code %d %o %x %e %f %g %a} (and the upper-case forms) a number input. Flags, width,
 * precision and argument indexes are skipped when counting, and {@code %%} takes no input. A format Java rejects, or one that
 * wants more inputs than the node has, reads {@code "ERROR"}.
 *
 * <p>Hardening over Leafia's, because a client can send any format string: a width or precision of four digits or more reads
 * {@code "ERROR"} (Java would pad to it; {@code %2000000000s} is a two-gigabyte string), a format longer than
 * {@link #MAX_FORMAT} characters does too, the node keeps at most {@link #MAX_ARGS} inputs, and the output is cut to
 * {@link AddonNodeSupport#MAX_TEXT}.
 */
public class NodeFormat extends Node {

	public static final int MAX_FORMAT = 512;
	public static final int MAX_ARGS = 64;

	/** A flags/width/precision/index run after a percent sign: the characters {@code Formatter} accepts before the conversion letter. */
	private static final Pattern SPEC = Pattern.compile("%[-#+ 0,(<$.\\d]*");
	private static final Pattern BIG_NUMBER = Pattern.compile("\\d{4,}");

	public String format = "%s";
	private final NodeTextBox box;

	public NodeFormat(float x, float y) {
		super(x, y);
		this.outputs.add(new NodeRetarded("Output", this, outputs.size(), false, DataType.STRING, new DataValueString("")));
		box = new NodeTextBox("Format", this, otherElements.size(), format, s -> {
			format = s;
			updateFormat();
		});
		this.otherElements.add(box);
		evalCache = new DataValue[1];
		updateFormat();
		recalcSize();
	}

	/** Rebuilds the inputs from the format: one per conversion that takes an argument. */
	public void updateFormat() {
		for(NodeConnection c : inputs) {
			c.removeConnection();
		}
		this.inputs.clear();
		int argNum = 0;
		for(int i = 0; i < format.length() && argNum < MAX_ARGS; i += 2) {
			i = format.indexOf("%", i);
			if(i == -1)
				break;
			if(i + 1 >= format.length())
				break;
			boolean readNext = true;
			while(readNext && argNum < MAX_ARGS) {
				readNext = false;
				if(i + 1 >= format.length())
					break;
				String type = format.substring(i + 1, i + 2);
				switch(type) {
					case "%":
						break;
					case "$": case " ": case "-": case "#": case "+": case ".": case ",": case "(":
					case "0": case "1": case "2": case "3": case "4": case "5": case "6": case "7": case "8": case "9":
						i++;
						readNext = true;
						break;
					case "s": case "S":
						argNum++;
						inputs.add(new NodeConnection("Input " + argNum, this, inputs.size(), true, DataType.STRING, new DataValueString("")));
						break;
					case "d": case "o": case "x": case "X": case "e": case "E":
					case "f": case "g": case "G": case "a": case "A":
						argNum++;
						inputs.add(new NodeConnection("Input " + argNum, this, inputs.size(), true, DataType.NUMBER, new DataValueFloat(0)));
						break;
				}
			}
		}
		recalcSize();
	}

	/** True if {@code Formatter} would build something huge from this format: an over-long format, or a width/precision/index of 1000 or more. */
	public static boolean refused(String format) {
		if(format.length() > MAX_FORMAT)
			return true;
		Matcher m = SPEC.matcher(format);
		while(m.find()) {
			if(BIG_NUMBER.matcher(m.group()).find())
				return true;
		}
		return false;
	}

	@Override
	public DataValue evaluate(int idx) {
		if(cacheValid)
			return evalCache[0];
		cacheValid = true;

		List<Object> argsList = new ArrayList<>();
		for(int i = 0; i < inputs.size(); i++) {
			DataValue value = inputs.get(i).evaluate();
			if(value instanceof DataValueFloat)
				argsList.add(value.getNumber());
			else if(value instanceof DataValueString)
				argsList.add(value.toString());
		}

		Object[] args = argsList.toArray();

		String output = "ERROR";
		if(!refused(format)) {
			try {
				output = String.format(format, args);
			} catch(IllegalFormatException ignored) {}
		}

		return evalCache[0] = new DataValueString(AddonNodeSupport.clip(output));
	}

	@Override
	public float[] getColor() {
		return AddonNodesRegister.colorString;
	}

	@Override
	public String getDisplayName() {
		return "Format";
	}

	@Override
	public CompoundTag writeToNBT(CompoundTag tag, NodeSystem sys) {
		tag.putString("nodeType", "format");
		tag.putString("format", format);
		return super.writeToNBT(tag, sys);
	}

	@Override
	public void readFromNBT(CompoundTag tag, NodeSystem sys) {
		format = tag.getString("format");
		box.value = format; // Leafia's box kept showing the default after a load
		super.readFromNBT(tag, sys);
	}
}
