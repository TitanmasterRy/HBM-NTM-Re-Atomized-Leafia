package com.leafia.contents.machines.controlpanel.nodes.pack;

import java.util.ArrayList;
import java.util.Arrays;

import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.machines.controlpanel.core.Control;
import com.leafia.contents.machines.controlpanel.core.NodeButton;
import com.leafia.contents.machines.controlpanel.core.NodeConnection;
import com.leafia.contents.machines.controlpanel.core.NodeSystem;
import com.leafia.contents.machines.controlpanel.core.nodes.Node;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValue.DataType;
import com.leafia.contents.machines.controlpanel.core.types.DataValueComposite;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;
import com.leafia.contents.machines.controlpanel.core.types.DataValueString;
import com.leafia.contents.machines.controlpanel.ic10.IC10Program;
import com.leafia.contents.machines.controlpanel.ic10.IC10State;
import com.leafia.contents.machines.controlpanel.nodes.AddonNodeSupport;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

/**
 * The IC10 node: a program, and the state it runs in, between an input pack and an output pack (CCP-IC10, D-3073). Adapted from
 * Leafia's {@code NodeIC10}; the socket names, the default program, the colour and the save format (the {@code nodeType}
 * {@code leafia_ic10}, {@code compressed}/{@code instructions}, {@code state}) are Leafia's.
 *
 * <p>Outputs: {@code Output} (the pack the program wrote with {@code s} and {@code pulse}), {@code Error} (the name of the error that
 * stopped it, or empty) and {@code Line} (the line it stopped at, zero-based). The first read of any of them in an evaluation
 * runs the program once ({@link IC10State#update}); the rest read the same run.
 *
 * <p>It runs only when {@link LeafiaFeatures#controlPanelIC10} is on, and never on a client: the editor's copy of a panel holds
 * these nodes and does not run them. Its two buttons are drawn by the node editor and pressed by the actions
 * {@code IC10Client} registers ("Edit Code" opens the editor on {@link #instructions}; "Reset State" starts the state afresh).
 *
 * <p>The program is bounded at every door ({@link IC10Program}): on load, by the editor, and by the guard on anything a client sends.
 */
public class NodeIC10 extends Node {

	@SafeVarargs
	static <T> ArrayList<T> asArrayList(T... a) {
		return new ArrayList<>(Arrays.asList(a));
	}

	/** Leafia's default program, "3 Second Clock", which is also the first of the editor's examples. */
	public static ArrayList<ArrayList<Object>> defaultProgram() {
		return asArrayList(
				asArrayList("alias", 1, "timer", 1, "r0"),
				asArrayList("alias", 1, "on", 1, "r1"),
				asArrayList(),
				asArrayList("#", " reset variables"),
				asArrayList("move", 1, "timer", 1, "0"),
				asArrayList("move", 1, "on", 1, "0"),
				asArrayList(),
				asArrayList("Loop:"),
				asArrayList(2, "#", " check if timer equals 3"),
				asArrayList(2, "seq", 1, "on", 1, "timer", 1, "3"),
				asArrayList(2, "s", 1, "\"signal\"", 1, "on"),
				asArrayList(2),
				asArrayList(2, "#", " adds timer 1 and modulo by 4"),
				asArrayList(2, "#", " (1mod4=1, 2mod4=2, 3mod4=3, 4mod4=0, 5mod4=1, 6mod4=2...)"),
				asArrayList(2, "add", 1, "timer", 1, "timer", 1, "1"),
				asArrayList(2, "mod", 1, "timer", 1, "timer", 1, "4"),
				asArrayList(2),
				asArrayList(2, "#", " 1 second = 2 stationeers ticks"),
				asArrayList(2, "yield"),
				asArrayList(2, "yield"),
				asArrayList(2, "j", 1, "Loop")
		);
	}

	public ArrayList<ArrayList<Object>> instructions = defaultProgram();
	public IC10State state = new IC10State(this);
	public Control ctrl;
	public final NodeButton editCode;
	public final NodeButton resetState;

	public NodeIC10(float x, float y, Control ctrl) {
		super(x, y);
		this.ctrl = ctrl;
		editCode = new NodeButton("Edit Code", this, otherElements.size());
		otherElements.add(editCode);
		resetState = new NodeButton("Reset State", this, otherElements.size());
		otherElements.add(resetState);
		outputs.add(new NodeConnection("Output", this, outputs.size(), false, DataType.COMPOSITE, new DataValueComposite()));
		outputs.add(new NodeConnection("Error", this, outputs.size(), false, DataType.STRING, new DataValueString("")));
		outputs.add(new NodeConnection("Line", this, outputs.size(), false, DataType.NUMBER, new DataValueFloat(0)));
		inputs.add(new NodeConnection("Input", this, inputs.size(), true, DataType.COMPOSITE, new DataValueComposite()));
		recalcSize();
	}

	/** The state starts afresh (the "Reset State" button). */
	public void resetState() {
		state = new IC10State(this);
	}

	/** True if this node may run a program now: the switch is on and its panel is not on a client. */
	private boolean mayRun() {
		if(!LeafiaFeatures.controlPanelIC10)
			return false;
		Level level = AddonNodeSupport.levelOf(ctrl);
		return level != null && !level.isClientSide();
	}

	@Override
	public DataValue evaluate(int i) {
		if(!cacheValid) {
			cacheValid = true;
			if(mayRun())
				state.update();
		}
		if(i == 0)
			return state.output;
		else if(i == 1)
			return new DataValueString(state.error != null ? state.error : "");
		else
			return new DataValueFloat(state.line - 1);
	}

	@Override
	public float[] getColor() {
		return DataType.COMPOSITE.getColor();
	}

	@Override
	public String getDisplayName() {
		return "IC10 Sequence";
	}

	@Override
	public void readFromNBT(CompoundTag tag, NodeSystem sys) {
		super.readFromNBT(tag, sys);
		if(IC10Program.has(tag)) {
			byte[] data = IC10Program.bytes(tag);
			instructions = data == null ? new ArrayList<>() : IC10Program.decode(data);
		}
		if(tag.contains("state")) {
			state = new IC10State(this);
			state.deserialize(tag.getCompound("state"));
		}
	}

	@Override
	public CompoundTag writeToNBT(CompoundTag tag, NodeSystem sys) {
		tag.putString("nodeType", "leafia_ic10");
		IC10Program.write(tag, instructions);
		tag.put("state", state.serialize());
		return super.writeToNBT(tag, sys);
	}
}
