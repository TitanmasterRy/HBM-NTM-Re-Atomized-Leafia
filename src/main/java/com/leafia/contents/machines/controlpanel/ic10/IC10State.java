package com.leafia.contents.machines.controlpanel.ic10;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.jetbrains.annotations.Nullable;

import com.hbm.blockentity.network.RTTYSystem;
import com.leafia.LeafiaMod;
import com.leafia.config.LeafiaConfig;
import com.leafia.contents.machines.controlpanel.core.ControlEventSystem;
import com.leafia.contents.machines.controlpanel.core.IControllable;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValueComposite;
import com.leafia.contents.machines.controlpanel.ic10.IC10.IC10Instruction;
import com.leafia.contents.machines.controlpanel.ic10.IC10.IC10Type;
import com.leafia.contents.machines.controlpanel.nodes.pack.NodeIC10;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * The running state of one IC10 node: its registers, stack, labels and aliases, its output pack and where it is in the program
 * (CCP-IC10, D-3073). Adapted from Leafia's {@code IC10State}; the {@code update} loop is Leafia's, and what is added is the
 * bound on it ({@link IC10Limits}) and the handling of states a hostile or a damaged save can describe.
 *
 * <h2>What changed from Leafia, each deliberate</h2>
 * <ol>
 * <li><b>The instruction ceiling.</b> An update takes at most {@link IC10Limits#STEPS_PER_UPDATE} steps (Leafia's own bound,
 * which NTM-CE did not have) from a pool of {@link IC10Limits#TICK_BUDGET} shared by every IC10 node in the server tick, so
 * {@code j 0} costs 128 steps a tick however many nodes there are. A step is one line, a blank line and a label included.</li>
 * <li><b>The stack works.</b> {@code sp} was written as an {@code Integer} and read back through a path that accepted only a
 * {@code Double}, so the second {@code push} of any program failed with {@code IncorrectVariable} and the 512-deep stack
 * could not be reached. A number in a register is a number whatever its box, and an unset {@code sp} or {@code ra} reads 0, as it
 * does in Stationeers.</li>
 * <li><b>A jump out of the program stops it</b> ({@code OutOfInstructions}) where Leafia threw from {@code ArrayList.get} on the
 * server thread; a program's own exceptions are caught for the whole update and not only per instruction.</li>
 * <li><b>An error ends the update.</b> Leafia set {@code error} and kept running to the end of its 128 steps.</li>
 * <li><b>The stack is saved as a stack.</b> Leafia wrote its entries into the registry compound, over the registers of the
 * same number, and read them back from there.</li>
 * <li><b>What a program builds is bounded</b>: text, output keys, variables, chat lines ({@link IC10Limits}), and a radio
 * frequency is the radio nodes' 128 characters.</li>
 * </ol>
 */
public class IC10State {

	/** Registers a saved state may hold: 0..64, {@code sp} and {@code ra}, and room. */
	public static final int MAX_SAVED_REGISTERS = 128;
	/** Stack entries a saved state may hold. */
	public static final int MAX_SAVED_STACK = 1024;

	public NodeIC10 node;
	public DataValueComposite output = new DataValueComposite();
	public Map<Integer, Object> register = new HashMap<>();
	public Map<String, Object> labels = new HashMap<>();
	public Map<String, String> aliases = new HashMap<>();
	public Object[] stack = new Object[Math.max(0, Math.min(4096, LeafiaConfig.ic10MaxStack))];
	public String error = null;
	public boolean compiled = false;
	public int line = 0;
	public static final int register_sp = -1;
	public static final int register_ra = -2;
	public int yield = 0;

	/** Steps the last {@link #update} executed, and whether the tick's pool was too empty to give it all it asked for. */
	public int lastSteps = 0;
	public boolean throttled = false;
	private int steps = 0;
	private int prints = 0;

	public Map<String, String> pulseQueue = new HashMap<>();

	public IC10State(NodeIC10 node) {
		this.node = node;
	}

	// ------------------------------------------------------------------ saving

	public CompoundTag serialize() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("line", line);
		tag.putInt("yield", yield);
		if(error != null)
			tag.putString("error", error);
		CompoundTag regs = new CompoundTag();
		for(Entry<Integer, Object> entry : register.entrySet())
			put(regs, Integer.toString(entry.getKey()), entry.getValue());
		tag.put("registry", regs);
		CompoundTag pulse = new CompoundTag();
		for(Entry<String, String> entry : pulseQueue.entrySet())
			pulse.putString(entry.getKey(), entry.getValue());
		tag.put("pulseQueue", pulse);
		CompoundTag stacks = new CompoundTag();
		for(int index = 0; index < stack.length; index++) {
			if(stack[index] != null)
				put(stacks, Integer.toString(index), stack[index]);
		}
		tag.put("stack", stacks);
		return tag;
	}

	private static void put(CompoundTag into, String key, Object value) {
		if(value instanceof String s)
			into.putString(key, s);
		else if(value instanceof Number n)
			into.putDouble(key, n.doubleValue());
	}

	@Nullable
	private static Object get(Tag base) {
		if(base instanceof StringTag s)
			return IC10Limits.clip(s.getAsString());
		if(base instanceof NumericTag n)
			return n.getAsDouble();
		return null;
	}

	/** Reads a saved state into this fresh one. A tag a hand or a hostile client made is read as far as it makes sense and no further. */
	public void deserialize(CompoundTag tag) {
		if(compiled)
			throw new IllegalStateException("Already compiled!");
		line = Math.max(0, tag.getInt("line"));
		yield = Math.max(0, Math.min(1_000_000, tag.getInt("yield")));
		if(tag.contains("error", Tag.TAG_STRING))
			error = clipTo(tag.getString("error"), IC10Limits.MAX_PRINT_CHARS);
		CompoundTag regs = tag.getCompound("registry");
		for(String s : regs.getAllKeys()) {
			Integer index = tonumber(s);
			Object value = get(regs.get(s));
			if(index != null && value != null && index >= register_ra && index <= LeafiaConfig.ic10MaxRegisters + 1)
				register.put(index, value);
		}
		CompoundTag stacks = tag.getCompound("stack");
		for(String s : stacks.getAllKeys()) {
			Integer index = tonumber(s);
			Object value = get(stacks.get(s));
			if(index != null && value != null && index >= 0 && index < stack.length)
				stack[index] = value;
		}
		CompoundTag pulse = tag.getCompound("pulseQueue");
		for(String s : pulse.getAllKeys()) {
			if(pulseQueue.size() >= IC10Limits.MAX_OUTPUT_KEYS)
				break;
			pulseQueue.put(clipTo(s, IC10Limits.MAX_KEY), IC10Limits.clip(pulse.getString(s)));
		}
	}

	private static String clipTo(String s, int max) {
		return s.length() > max ? s.substring(0, max) : s;
	}

	// ------------------------------------------------------------------ registers

	public void setRegister(int index, Object value) {
		if(value instanceof String s)
			value = IC10Limits.clip(s);
		if(index < -2 || index > LeafiaConfig.ic10MaxRegisters + 1)
			error = "OutOfRegisterBounds";
		else
			register.put(index, value);
	}

	public int getRegisterIndex(String str) {
		if(labels.containsKey(str)) {
			error = "IncorrectVariable";
			return 0;
		}
		String out = aliases.getOrDefault(str, str);
		if(out.equals("sp")) return register_sp;
		if(out.equals("ra")) return register_ra;
		if(out.startsWith("r")) {
			Integer index = tonumber(out.substring(1));
			if(index == null) {
				error = "IncorrectVariable";
				return 0;
			} else if(index < 0 || index > LeafiaConfig.ic10MaxRegisters) {
				error = "OutOfRegisterBounds";
				return 0;
			}
			return index;
		} else {
			error = "IncorrectVariable";
			return 0;
		}
	}

	public Object getValue(String str) {
		if(labels.containsKey(str)) return labels.get(str);
		String out = aliases.get(str);
		if(out == null) return str;
		if(out.equals("sp")) return register.getOrDefault(register_sp, 0d);
		if(out.equals("ra")) return register.getOrDefault(register_ra, 0d);
		if(out.startsWith("r")) {
			Integer index = tonumber(out.substring(1));
			if(index == null)
				error = "IncorrectVariable";
			else if(index < 0 || index > LeafiaConfig.ic10MaxRegisters)
				error = "OutOfRegisterBounds";
		} else
			return str;
		return "";
	}

	public double getNumber(String str) {
		Object object = getValue(str);
		if(object instanceof Number n)
			return n.doubleValue();
		else if(object instanceof String s) {
			Object v = s;
			if(labels.containsKey(s))
				v = labels.get(s);
			if(aliases.containsKey(s))
				s = aliases.get(s);
			if(s.equals("ra"))
				v = register.getOrDefault(register_ra, 0d);
			else if(s.equals("sp"))
				v = register.getOrDefault(register_sp, 0d);
			else if(s.startsWith("r")) {
				Integer idx = tonumber(s.substring(1));
				if(idx != null && idx >= 0 && idx <= LeafiaConfig.ic10MaxRegisters)
					v = register.getOrDefault(idx, 0d);
			}
			if(v instanceof String vs) {
				Double d = tonumberd(vs);
				if(d == null)
					error = "IncorrectVariable";
				else
					return d;
			} else if(v instanceof Number n)
				return n.doubleValue();
			else
				error = "IncorrectVariable";
			return 0;
		} else {
			error = "IncorrectVariable";
			return 0;
		}
	}

	// ------------------------------------------------------------------ what a program can reach

	/** The node's input pack, read at {@code key}. */
	public String input(String key) {
		if(node.inputs.isEmpty())
			return "";
		DataValue value = node.inputs.get(0).evaluate();
		if(value instanceof DataValueComposite composite)
			return composite.getValueOf(key);
		else
			return "";
	}

	/** Writes the output pack. A blank value removes the key (the pack's rule); a new key past the ceiling is ignored. */
	public void output(String key, String value) {
		if(key.length() > IC10Limits.MAX_KEY)
			return;
		if(output.getValueOf(key).isEmpty() && !value.isBlank() && output.values().size() >= IC10Limits.MAX_OUTPUT_KEYS)
			return;
		output.setValueOf(key, IC10Limits.clip(value));
	}

	/** The level the node's panel stands in, or null. */
	@Nullable
	public Level level() {
		return com.leafia.contents.machines.controlpanel.nodes.AddonNodeSupport.levelOf(node.ctrl);
	}

	/** The linked block under this tag, if it is loaded and controllable; never loads a chunk. */
	@Nullable
	public IControllable linkTarget(String tag) {
		Level world = level();
		if(world == null || node.ctrl == null || !node.ctrl.taggedLinks.containsKey(tag))
			return null;
		return ControlEventSystem.get(world).resolve(node.ctrl.taggedLinks.get(tag));
	}

	/** Every linked block that is loaded and controllable, in link order. A control holds at most 64 links (the guard). */
	public List<IControllable> linked() {
		List<IControllable> out = new ArrayList<>();
		Level world = level();
		if(world == null || node.ctrl == null)
			return out;
		ControlEventSystem system = ControlEventSystem.get(world);
		for(BlockPos pos : node.ctrl.taggedLinks.values()) {
			IControllable te = system.resolve(pos);
			if(te != null)
				out.add(te);
		}
		return out;
	}

	/** {@code sr} and {@code snr}: publishes on the control's own dimension's radio bus. Nothing is sent from a client or on a bad frequency. */
	public void broadcast(String frequency, Object signal) {
		Level world = level();
		if(world == null || world.isClientSide() || frequency.isEmpty() || frequency.length() > IC10Limits.MAX_FREQUENCY)
			return;
		if(signal instanceof String s)
			signal = IC10Limits.clip(s);
		RTTYSystem.broadcast(world, frequency, signal);
	}

	/** {@code slv}, {@code sgv} and friends: a variable is written only if its name is sane and the table has room. */
	public void putVar(Map<String, DataValue> vars, String name, DataValue value) {
		if(name.isEmpty() || name.length() > IC10Limits.MAX_VAR_NAME)
			return;
		if(!vars.containsKey(name) && vars.size() >= IC10Limits.MAX_VARS)
			return;
		vars.put(name, value);
	}

	/** {@code print}: a green label and the message to the players within a block of the panel, at most {@link IC10Limits#MAX_PRINTS} lines an update. */
	public void print(String label, Object message) {
		Level world = level();
		if(world == null || world.isClientSide() || node.ctrl.panel == null || node.ctrl.panel.parent == null || prints >= IC10Limits.MAX_PRINTS)
			return;
		prints++;
		String msg = message == null ? "null" : message.toString();
		label = clipTo(label, IC10Limits.MAX_PRINT_CHARS);
		msg = clipTo(msg, IC10Limits.MAX_PRINT_CHARS);
		BlockPos pos = node.ctrl.panel.parent.getControlPos();
		Component text = Component.literal(label + ": ").withStyle(net.minecraft.ChatFormatting.GREEN)
				.append(Component.literal(msg).withStyle(net.minecraft.ChatFormatting.RESET));
		for(Player player : world.getEntitiesOfClass(Player.class, new AABB(pos).inflate(1.25)))
			player.sendSystemMessage(text);
	}

	// ------------------------------------------------------------------ utility

	@Nullable
	public static Integer tonumber(String s) {
		try {
			return Integer.parseInt(s);
		} catch(NumberFormatException ignored) {}
		return null;
	}

	@Nullable
	public static Double tonumberd(String s) {
		try {
			return Double.parseDouble(s);
		} catch(NumberFormatException ignored) {}
		return null;
	}

	public static Object parseConstant(String s) {
		if(s.equals("ra"))
			return "ra";
		else if(s.equals("sp"))
			return "sp";
		else if(s.startsWith("r")) {
			Integer idx = tonumber(s.substring(1));
			if(idx != null && idx >= 0 && idx <= LeafiaConfig.ic10MaxRegisters)
				return s;
		}
		if(s.startsWith("\"") && s.endsWith("\""))
			return s.substring(1, s.length() - 1);
		else {
			try {
				return Double.parseDouble(s);
			} catch(NumberFormatException e) {
				return null;
			}
		}
	}

	public Object parseValue(String s) {
		if(labels.containsKey(s))
			return labels.get(s);
		if(aliases.containsKey(s))
			s = aliases.get(s);
		if(s.equals("ra"))
			return register.getOrDefault(register_ra, 0d);
		else if(s.equals("sp"))
			return register.getOrDefault(register_sp, 0d);
		else if(s.startsWith("r")) {
			Integer idx = tonumber(s.substring(1));
			if(idx != null && idx >= 0 && idx <= LeafiaConfig.ic10MaxRegisters)
				return register.getOrDefault(idx, "");
		}
		if(s.startsWith("\"") && s.endsWith("\""))
			return s.substring(1, s.length() - 1);
		else {
			try {
				return Double.parseDouble(s);
			} catch(NumberFormatException e) {
				return null;
			}
		}
	}

	// ------------------------------------------------------------------ running

	void compile() {
		compiled = true;
		int ln = 0;
		for(ArrayList<Object> insn : node.instructions) {
			int args = 0;
			String mode = null;
			String name = null;
			for(Object o : insn) {
				if(o instanceof String s) {
					args++;
					if(args == 1) { // first argument
						if(s.length() >= 2 && s.endsWith(":")) { // jump labels
							labels.put(s.substring(0, s.length() - 1), labels.getOrDefault(s.substring(0, s.length() - 1), ln));
							break;
						} else if(s.equals("define")) // constants
							mode = "labels";
						else if(s.equals("alias")) // alias
							mode = "aliases";
					} else if(mode != null) {
						if(args == 2) // memorize name
							name = s;
						else if(args == 3) { // actually set value at third argument
							Object value = parseConstant(s);
							if(value == null) {
								error = "CompileError";
								return;
							}
							if(mode.equals("labels"))
								labels.put(name, s);
							else if(mode.equals("aliases"))
								aliases.put(name, s);
						}
					} else // if its unrelated instruction
						break;
				}
			}
			ln++;
		}
	}

	/**
	 * Runs the program for one evaluation of the node: the queued pulses first, then, unless the node is sleeping, up to
	 * {@link IC10Limits#STEPS_PER_UPDATE} lines, fewer when the server tick's pool ({@link IC10Limits#take}) has less. A node that
	 * got none keeps its place and does nothing.
	 */
	public void update() {
		lastSteps = 0;
		throttled = false;
		steps = 0;
		prints = 0;
		if(!compiled)
			compile();
		if(error != null) return;

		for(Entry<String, String> entry : pulseQueue.entrySet())
			output(entry.getKey(), entry.getValue());
		pulseQueue.clear();
		if(yield > 0) {
			yield--;
			if(yield > 0)
				return;
		}
		int granted = IC10Limits.take(IC10Limits.STEPS_PER_UPDATE);
		throttled = granted < IC10Limits.STEPS_PER_UPDATE;
		try {
			run(granted);
		} catch(RuntimeException e) {
			error = "InternalError (See minecraft logs)";
			LeafiaMod.LOGGER.warn("IC10 threw at line {}", line - 1, e);
		} finally {
			IC10Limits.refund(granted - steps);
			lastSteps = steps;
		}
	}

	private void run(int granted) {
		while(steps < granted) {
			if(yield > 0)
				break;
			if(error != null)
				return;
			if(line < 0 || line >= node.instructions.size()) {
				error = "OutOfInstructions";
				return;
			}
			steps++;
			ArrayList<Object> insn = node.instructions.get(line);
			line++;
			int args = 0;
			IC10Instruction insnType = null;
			Object[] pack = null;
			for(Object o : insn) {
				if(o instanceof String s) {
					if(s.equals("#")) break;
					args++;
					if(args == 1) {
						if(s.endsWith(":")) break;
						if(s.equals("define")) break;
						insnType = IC10.instructions.get(s);
						if(insnType == null) {
							error = "UnrecognizedInstruction";
							return;
						}
						pack = new Object[insnType.args.size()];
					} else {
						if(args - 2 >= insnType.args.size()) {
							error = "IncorrectArgumentCount";
							return;
						}
						Object value = s;
						IC10Type argType = insnType.args.get(args - 2).type;
						if(argType != IC10Type.REGISTER && argType != IC10Type.NAME) {
							value = parseValue(s);
							if(value == null) {
								error = "IncorrectVariable";
								return;
							}
							if(argType == IC10Type.NUMBER || argType == IC10Type.INTEGER) {
								if(value instanceof String vs) {
									if(vs.isEmpty())
										value = 0;
									else {
										try {
											value = Double.parseDouble(vs);
										} catch(NumberFormatException e) {
											error = "IncorrectVariable";
											return;
										}
									}
								}
								if(argType == IC10Type.INTEGER) {
									if(value instanceof Double vd) {
										if(vd.intValue() != vd) {
											error = "IncorrectVariable";
											return;
										}
										value = vd.intValue();
									}
								}
							}
						} else if(argType == IC10Type.REGISTER) {
							value = getRegisterIndex(s);
							if(error != null)
								return;
						}
						if(argType == IC10Type.NAME || argType == IC10Type.STRING) {
							if(!(value instanceof String))
								value = value.toString();
						}
						if(value instanceof Integer vi && argType == IC10Type.NUMBER)
							value = vi.doubleValue();
						pack[args - 2] = value;
					}
				}
			}
			if(insnType != null) {
				if(args - 2 < insnType.args.size() - 1) {
					error = "IncorrectArgumentCount";
					return;
				}
				try {
					insnType.function.accept(this, pack);
				} catch(RuntimeException e) {
					error = "InternalError (See minecraft logs)";
					LeafiaMod.LOGGER.warn("IC10 threw an unexpected error at line {} ({}): {}", line - 1, IC10.instructionNames.get(insnType), e.toString());
					return;
				}
			}
		}
	}
}
