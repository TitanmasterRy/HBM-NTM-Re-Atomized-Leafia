package com.leafia.contents.machines.controlpanel.ic10;

import static com.leafia.contents.machines.controlpanel.ic10.IC10.IC10Type.ANY;
import static com.leafia.contents.machines.controlpanel.ic10.IC10.IC10Type.INTEGER;
import static com.leafia.contents.machines.controlpanel.ic10.IC10.IC10Type.NAME;
import static com.leafia.contents.machines.controlpanel.ic10.IC10.IC10Type.NUMBER;
import static com.leafia.contents.machines.controlpanel.ic10.IC10.IC10Type.REGISTER;
import static com.leafia.contents.machines.controlpanel.ic10.IC10.IC10Type.STRING;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiConsumer;

import org.jetbrains.annotations.Nullable;

import com.hbm.blockentity.network.RTTYSystem;
import com.hbm.blockentity.network.RTTYSystem.RTTYChannel;
import com.leafia.config.LeafiaConfig;
import com.leafia.contents.machines.controlpanel.core.IControllable;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;
import com.leafia.contents.machines.controlpanel.core.types.DataValueString;

import net.minecraft.world.level.Level;

/**
 * Leafia's IC10 instruction set: a Stationeers-style MIPS dialect over a register file (CCP-IC10, D-3073). Adapted from Leafia's
 * {@code IC10}; generated from that file by a generator script. Every instruction name, argument name and
 * type, and description is upstream's. Pure data over {@link IC10State}: nothing here names a client class.
 *
 * <p>What changed from the 1.12.2 source is only what Minecraft changed: the world, the radio bus ({@code RTTYSystem}), the
 * linked blocks (read through {@code ControlEventSystem.resolve}, which never loads a chunk and finds a base-mod machine through
 * its adapter), the chat message of {@code print} and the bounded variable writes. The instructions' own arithmetic is
 * untouched except seven bugs the owner had fixed on 2026-10-17 (D-3074): ceil rounded down, brgtz read a missing argument and
 * stopped the program, queryavg did not divide, querymul always returned 0, peek read the slot above the top, jal stored ra as its
 * own line + 2 and lnr could not read a number sent by snr.
 */
public final class IC10 {
	private IC10() {}
	public static Map<String,Object> insn_def = null;
	/// ANY doesn't include REGISTER and NAME
	public enum IC10Type {
		NUMBER,
		REGISTER,
		STRING,
		INTEGER,
		/// Doesn't include REGISTER and NAME
		ANY,
		NAME
	}
	public static class IC10Instruction {
		public List<IC10Argument> args = new ArrayList<>();
		public BiConsumer<IC10State,Object[]> function;
		public String[] desc;
		public IC10Instruction(List<IC10Argument> args,BiConsumer<IC10State,Object[]> function,String... desc) {
			this.args = args;
			this.function = function;
			this.desc = desc;
		}
	}
	public static class IC10Argument {
		public String name;
		public IC10Type type;
		protected IC10Argument(String name,IC10Type type) {
			this.name = name;
			this.type = type;
		}
	}
	/// short wrapper for new IC10Instruction
	public static IC10Instruction make(List<IC10Argument> args,BiConsumer<IC10State,Object[]> function,String... desc) {
		return new IC10Instruction(args,function,desc);
	}
	public static List<IC10Argument> args(Object... args) {
		List<IC10Argument> list = new ArrayList<>();
		int i = 0;
		String lastName = null;
		for (Object arg : args) {
			int o = i%2;
			if (o == 0)
				lastName = (String)arg;
			else {
				IC10Type type = (IC10Type)arg;
				list.add(new IC10Argument(lastName,type));
				lastName = null;
			}
			i++;
		}
		return list;
	}
	public static Map<String,Object> map(Object... objects) {
		Map<String,Object> map = new LinkedHashMap<>(); // keeps the order, which the Functions page lists in
		int i = 0;
		String key = null;
		for (Object obj : objects) {
			int o = i%2;
			if (o == 0)
				key = (String)obj;
			else {
				map.put(key,obj);
				key = null;
			}
			i++;
		}
		return map;
	}
	public static final Map<String,IC10Instruction> instructions = new HashMap<>();
	public static final Map<IC10Instruction,String> instructionNames = new HashMap<>();
	public static final Map<IC10Instruction,String> category = new HashMap<>();
	static void iter(Map<String,Object> d,String path) {
		for (Entry<String,Object> entry : d.entrySet()) {
			if (entry.getValue() instanceof Map<?,?> mop)
				iter((Map<String,Object>)mop,path.isEmpty() ? entry.getKey() : path+"/"+entry.getKey());
			else {
				instructions.put(entry.getKey(),(IC10Instruction)entry.getValue());
				instructionNames.put((IC10Instruction)entry.getValue(),entry.getKey());
				category.put((IC10Instruction)entry.getValue(),path);
			}
		}
	}
	/// CALL THIS EVERYTIME YOU MODIFY insn_def OR IT WILL NOT WORK!
	public static void rebuildInsnTable() {
		instructions.clear();
		category.clear();
		iter(insn_def,"");
	}
	public static IC10Instruction define = make(
			args("name",NAME,"num",NUMBER),
			(env,args)->{},
			"Creates a label that will be replaced",
			"throughout the program with the provided value."
	);
	static double positiveModulo(double numerator,double denominator) {
		return (numerator % denominator + denominator) % denominator;
	}
	static boolean equals(Object a,Object b) {
		if (a == null)
			return b == null;
		if (a instanceof Number numA && b instanceof String sB) {
			Double numB = tonumberd(sB);
			if (numB != null) {
				if (numA.doubleValue() == numB)
					return true;
			}
		}
		if (a instanceof String sA && b instanceof Number numB) {
			Double numA = tonumberd(sA);
			if (numA != null) {
				if (numB.doubleValue() == numA)
					return true;
			}
		}
		if (a instanceof Number numA && b instanceof Number numB) {
			if (numA.doubleValue() == numB.doubleValue())
				return true;
		}
		return a.equals(b);
	}
	@Nullable static Double tonumberd(String s) {
		try {
			return Double.parseDouble(s);
		} catch (NumberFormatException ignored) {}
		return null;
	}
	static {
		insn_def = map(
				"Setup",map(
						"alias",make(
								args("name",NAME,"target",REGISTER),
								(env,args)->{ },
								"Labels register with name."
						)
				),
				"l",make(
						args("register",REGISTER,"key",STRING),
						(env,args)->{
							String value = env.input((String)args[1]);
							env.setRegister((int)args[0],value);
						},
						"Register = value of key in Input"
				),
				"ln",make(
						args("register",REGISTER,"key",STRING),
						(env,args)->{
							String value = env.input((String)args[1]);
							Double num = tonumberd(value);
							env.setRegister((int)args[0],num != null ? num : -1D);
						},
						"Register = value of key in Input as a number",
						"-1 if invalid"
				),
				"s",make(
						args("key",STRING,"a",STRING),
						(env,args)->{
							env.output((String)args[0],(String)args[1]);
						},
						"Value of key in Output = a"
				),
				"pulse",make(
						args("key",STRING,"a",STRING,"b",STRING),
						(env,args)->{
							env.output((String)args[0],(String)args[1]);
							env.pulseQueue.put((String)args[0],(String)args[2]);
						},
						"Value of key in Output = a for 1 tick, then b"
				),
				"Math",map(
						"rand",make(
								args("register",REGISTER),
								(env,args)->{
									Level world = env.level();
									env.setRegister((int)args[0],world == null ? 0d : world.random.nextDouble());
								},
								"Register = A random decimal value between 0 and 1"
						),
						"Operators",map(
								"move",make(
										args("register",REGISTER,"a",ANY),
										(env,args)->{
											env.setRegister((int)args[0],args[1]);
										},
										"Register = a"
								),
								"add",make(
										args("register",REGISTER,"a",NUMBER,"b",NUMBER),
										(env,args)->{
											env.setRegister((int)args[0],(double)args[1]+(double)args[2]);
										},
										"Register = a + b"
								),
								"sub",make(
										args("register",REGISTER,"a",NUMBER,"b",NUMBER),
										(env,args)->{
											env.setRegister((int)args[0],(double)args[1]-(double)args[2]);
										},
										"Register = a - b"
								),
								"mul",make(
										args("register",REGISTER,"a",NUMBER,"b",NUMBER),
										(env,args)->{
											env.setRegister((int)args[0],(double)args[1]*(double)args[2]);
										},
										"Register = a * b"
								),
								"div",make(
										args("register",REGISTER,"a",NUMBER,"b",NUMBER),
										(env,args)->{
											env.setRegister((int)args[0],(double)args[1]/(double)args[2]);
										},
										"Register = a / b"
								),
								"mod",make(
										args("register",REGISTER,"a",NUMBER,"b",NUMBER),
										(env,args)->{
											env.setRegister((int)args[0],positiveModulo((double)args[1],(double)args[2]));
										},
										"Register = a mod b (positive modulo)"
								),
								"pow",make(
										args("register",REGISTER,"a",NUMBER,"b",NUMBER),
										(env,args)->{
											env.setRegister((int)args[0],Math.pow((double)args[1],(double)args[2]));
										},
										"Register = a ^ b"
								)
						),
						"Advanced",map(
								"sqrt",make(
										args("register",REGISTER,"a",NUMBER),
										(env,args)->{
											env.setRegister((int)args[0],Math.sqrt((double)args[1]));
										},
										"Register = square root of a"
								),
								"exp",make(
										args("register",REGISTER,"a",NUMBER),
										(env,args)->{
											env.setRegister((int)args[0],Math.exp((double)args[1]));
										},
										"Register = exp(a)"
								),
								"log",make(
										args("register",REGISTER,"a",NUMBER),
										(env,args)->{
											env.setRegister((int)args[0],Math.log((double)args[1]));
										},
										"Register = log(a)"
								)
						),
						"Utility",map(
								"max",make(
										args("register",REGISTER,"a",NUMBER,"b",NUMBER),
										(env,args)->{
											env.setRegister((int)args[0],Math.max((double)args[1],(double)args[2]));
										},
										"Register = max of a or b"
								),
								"min",make(
										args("register",REGISTER,"a",NUMBER,"b",NUMBER),
										(env,args)->{
											env.setRegister((int)args[0],Math.min((double)args[1],(double)args[2]));
										},
										"Register = min of a or b"
								),
								"floor",make(
										args("register",REGISTER,"a",NUMBER),
										(env,args)->{
											env.setRegister((int)args[0],(int)Math.floor((double)args[1]));
										},
										"Register = largest integer less than or equal a"
								),
								"ceil",make(
										args("register",REGISTER,"a",NUMBER),
										(env,args)->{
											env.setRegister((int)args[0],(int)Math.ceil((double)args[1]));
										},
										"Register = smallest integer greater than or equal a"
								),
								"round",make(
										args("register",REGISTER,"a",NUMBER),
										(env,args)->{
											env.setRegister((int)args[0],(int)Math.round((double)args[1]));
										},
										"Register = a rounded to nearest integer"
								)
						),
						"Trigonometry",map(
								"sin",make(
										args("register",REGISTER,"a",NUMBER),
										(env,args)->{
											env.setRegister((int)args[0],Math.sin((double)args[1]));
										},
										"Returns the sine of the specified angle (radians)"
								),
								"asin",make(
										args("register",REGISTER,"a",NUMBER),
										(env,args)->{
											env.setRegister((int)args[0],Math.asin((double)args[1]));
										},
										"Returns the angle (radians) whos sine is the specified value"
								),
								"cos",make(
										args("register",REGISTER,"a",NUMBER),
										(env,args)->{
											env.setRegister((int)args[0],Math.cos((double)args[1]));
										},
										"Returns the cosine of the specified angle (radians)"
								),
								"acos",make(
										args("register",REGISTER,"a",NUMBER),
										(env,args)->{
											env.setRegister((int)args[0],Math.acos((double)args[1]));
										},
										"Returns the angle (radians) whos cosine is the specified value"
								),
								"tan",make(
										args("register",REGISTER,"a",NUMBER),
										(env,args)->{
											env.setRegister((int)args[0],Math.tan((double)args[1]));
										},
										"Returns the tangent of the specified angle (radians)"
								),
								"atan",make(
										args("register",REGISTER,"a",NUMBER),
										(env,args)->{
											env.setRegister((int)args[0],Math.atan((double)args[1]));
										},
										"Returns the angle (radians) whos tangent is the specified value"
								),
								"atan2",make(
										args("register",REGISTER,"a",NUMBER,"b",NUMBER),
										(env,args)->{
											env.setRegister((int)args[0],Math.atan2((double)args[1],(double)args[2]));
										},
										"Returns the angle (radians) whose tangent is the\",\"quotient of two specified values: a (y) and b (x)"
								)
						)
				),
				"String",map(
						"concat",make(
								args("register",REGISTER,"a",ANY,"b",ANY),
								(env,args)->{
									env.setRegister((int)args[0],args[1].toString()+args[2].toString());
								},
								"Register = a + b (as string)"
						),
						"substr",make(
								args("register",REGISTER,"a",ANY,"b",INTEGER,"c",INTEGER),
								(env,args)->{
									String s = args[1].toString();
									int start = (int)args[2];
									int end = (int)args[3];
									if (start < 0)
										start = s.length()-Math.abs(start);
									else
										start = Math.max(0,start-1);
									if (end < 0)
										end = s.length()-Math.abs(end)+1;
									try {
										s = s.substring(start,end);
									} catch (IndexOutOfBoundsException e) {
										s = "ERROR";
									}
									env.setRegister((int)args[0],s);
								},
								"Register = a substring beginning",
								"at b and ending at c"
						)
				),
				"Conditionals",map(
						"Register",map(
								"seq",make(
										args("register",REGISTER,"a",ANY,"b",ANY),
										(env,args)->{
											env.setRegister((int)args[0],equals(args[1],args[2]) ? 1 : 0);
										},
										"Register = 1 if a == b, otherwise 0"
								),
								"seqz",make(
										args("register",REGISTER,"a",NUMBER),
										(env,args)->{
											env.setRegister((int)args[0],(double)args[1] == 0 ? 1 : 0);
										},
										"Register = 1 if a == 0, otherwise 0"
								),
								"sne",make(
										args("register",REGISTER,"a",ANY,"b",ANY),
										(env,args)->{
											env.setRegister((int)args[0],!equals(args[1],args[2]) ? 1 : 0);
										},
										"Register = 1 if a != b, otherwise 0"
								),
								"snez",make(
										args("register",REGISTER,"a",NUMBER),
										(env,args)->{
											env.setRegister((int)args[0],(double)args[1] != 0 ? 1 : 0);
										},
										"Register = 1 if a != 0, otherwise 0"
								),
								"sgt",make(
										args("register",REGISTER,"a",NUMBER,"b",NUMBER),
										(env,args)->{
											env.setRegister((int)args[0],(double)args[1] > (double)args[2] ? 1 : 0);
										},
										"Register = 1 if a > b, otherwise 0"
								),
								"sgtz",make(
										args("register",REGISTER,"a",NUMBER),
										(env,args)->{
											env.setRegister((int)args[0],(double)args[1] > 0 ? 1 : 0);
										},
										"Register = 1 if a > 0, otherwise 0"
								),
								"sge",make(
										args("register",REGISTER,"a",NUMBER,"b",NUMBER),
										(env,args)->{
											env.setRegister((int)args[0],(double)args[1] >= (double)args[2] ? 1 : 0);
										},
										"Register = 1 if a >= b, otherwise 0"
								),
								"sgez",make(
										args("register",REGISTER,"a",NUMBER),
										(env,args)->{
											env.setRegister((int)args[0],(double)args[1] >= 0 ? 1 : 0);
										},
										"Register = 1 if a >= 0, otherwise 0"
								),
								"slt",make(
										args("register",REGISTER,"a",NUMBER,"b",NUMBER),
										(env,args)->{
											env.setRegister((int)args[0],(double)args[1] < (double)args[2] ? 1 : 0);
										},
										"Register = 1 if a < b, otherwise 0"
								),
								"sltz",make(
										args("register",REGISTER,"a",NUMBER),
										(env,args)->{
											env.setRegister((int)args[0],(double)args[1] < 0 ? 1 : 0);
										},
										"Register = 1 if a < 0, otherwise 0"
								),
								"sle",make(
										args("register",REGISTER,"a",NUMBER,"b",NUMBER),
										(env,args)->{
											env.setRegister((int)args[0],(double)args[1] <= (double)args[2] ? 1 : 0);
										},
										"Register = 1 if a <= b, otherwise 0"
								),
								"slez",make(
										args("register",REGISTER,"a",NUMBER),
										(env,args)->{
											env.setRegister((int)args[0],(double)args[1] <= 0 ? 1 : 0);
										},
										"Register = 1 if a <= 0, otherwise 0"
								)
						),
						"Branch",map(
								"beq",make(
										args("a",ANY,"b",ANY,"c",INTEGER),
										(env,args)->{
											if (equals(args[0],args[1]))
												env.line = (int)args[2];
										},
										"Branch to line c if a == b"
								),
								"beqz",make(
										args("a",NUMBER,"b",INTEGER),
										(env,args)->{
											if ((double)args[0] == 0)
												env.line = (int)args[1];
										},
										"Branch to line b if a == 0"
								),
								"bne",make(
										args("a",ANY,"b",ANY,"c",INTEGER),
										(env,args)->{
											if (!equals(args[0],args[1]))
												env.line = (int)args[2];
										},
										"Branch to line c if a != b"
								),
								"bnez",make(
										args("a",NUMBER,"b",INTEGER),
										(env,args)->{
											if ((double)args[0] != 0)
												env.line = (int)args[1];
										},
										"Branch to line b if a != 0"
								),
								"bgt",make(
										args("a",NUMBER,"b",NUMBER,"c",INTEGER),
										(env,args)->{
											if ((double)args[0] > (double)args[1])
												env.line = (int)args[2];
										},
										"Branch to line c if a > b"
								),
								"bgtz",make(
										args("a",NUMBER,"b",INTEGER),
										(env,args)->{
											if ((double)args[0] > 0)
												env.line = (int)args[1];
										},
										"Branch to line b if a > 0"
								),
								"bge",make(
										args("a",NUMBER,"b",NUMBER,"c",INTEGER),
										(env,args)->{
											if ((double)args[0] >= (double)args[1])
												env.line = (int)args[2];
										},
										"Branch to line c if a >= b"
								),
								"bgez",make(
										args("a",NUMBER,"b",INTEGER),
										(env,args)->{
											if ((double)args[0] >= 0)
												env.line = (int)args[1];
										},
										"Branch to line b if a >= 0"
								),
								"blt",make(
										args("a",NUMBER,"b",NUMBER,"c",INTEGER),
										(env,args)->{
											if ((double)args[0] < (double)args[1])
												env.line = (int)args[2];
										},
										"Branch to line c if a < b"
								),
								"bltz",make(
										args("a",NUMBER,"b",INTEGER),
										(env,args)->{
											if ((double)args[0] < 0)
												env.line = (int)args[1];
										},
										"Branch to line b if a < 0"
								),
								"ble",make(
										args("a",NUMBER,"b",NUMBER,"c",INTEGER),
										(env,args)->{
											if ((double)args[0] <= (double)args[1])
												env.line = (int)args[2];
										},
										"Branch to line c if a <= b"
								),
								"blez",make(
										args("a",NUMBER,"b",INTEGER),
										(env,args)->{
											if ((double)args[0] <= 0)
												env.line = (int)args[1];
										},
										"Branch to line b if a <= 0"
								)
						),
						"Relative Branch",map(
								"breq",make(
										args("a",ANY,"b",ANY,"c",INTEGER),
										(env,args)->{
											if (equals(args[0],args[1]))
												env.line += (int)args[2];
										},
										"Skip c lines if a == b"
								),
								"breqz",make(
										args("a",NUMBER,"b",INTEGER),
										(env,args)->{
											if ((double)args[0] == 0)
												env.line += (int)args[1];
										},
										"Skip b lines if a == 0"
								),
								"brne",make(
										args("a",ANY,"b",ANY,"c",INTEGER),
										(env,args)->{
											if (!equals(args[0],args[1]))
												env.line += (int)args[2];
										},
										"Skip c lines if a != b"
								),
								"brnez",make(
										args("a",NUMBER,"b",INTEGER),
										(env,args)->{
											if ((double)args[0] != 0)
												env.line += (int)args[1];
										},
										"Skip b lines if a != 0"
								),
								"brgt",make(
										args("a",NUMBER,"b",NUMBER,"c",INTEGER),
										(env,args)->{
											if ((double)args[0] > (double)args[1])
												env.line += (int)args[2];
										},
										"Skip c lines if a > b"
								),
								"brgtz",make(
										args("a",NUMBER,"b",INTEGER),
										(env,args)->{
											if ((double)args[0] > 0)
												env.line += (int)args[1];
										},
										"Skip b lines if a > 0"
								),
								"brge",make(
										args("a",NUMBER,"b",NUMBER,"c",INTEGER),
										(env,args)->{
											if ((double)args[0] >= (double)args[1])
												env.line += (int)args[2];
										},
										"Skip c lines if a >= b"
								),
								"brgez",make(
										args("a",NUMBER,"b",INTEGER),
										(env,args)->{
											if ((double)args[0] >= 0)
												env.line += (int)args[1];
										},
										"Skip b lines if a >= 0"
								),
								"brlt",make(
										args("a",NUMBER,"b",NUMBER,"c",INTEGER),
										(env,args)->{
											if ((double)args[0] < (double)args[1])
												env.line += (int)args[2];
										},
										"Skip c lines if a < b"
								),
								"brltz",make(
										args("a",NUMBER,"b",INTEGER),
										(env,args)->{
											if ((double)args[0] < 0)
												env.line += (int)args[1];
										},
										"Skip b lines if a < 0"
								),
								"brle",make(
										args("a",NUMBER,"b",NUMBER,"c",INTEGER),
										(env,args)->{
											if ((double)args[0] <= (double)args[1])
												env.line += (int)args[2];
										},
										"Skip c lines if a <= b"
								),
								"brlez",make(
										args("a",NUMBER,"b",INTEGER),
										(env,args)->{
											if ((double)args[0] <= 0)
												env.line += (int)args[1];
										},
										"Skip b lines if a <= 0"
								)
						)
				),
				"Stack",map(
						"push",make(
								args("a",ANY),
								(env,args)->{
									if ((int)env.getNumber("sp") >= LeafiaConfig.ic10MaxStack) {
										env.error = "StackOverflow";
										return;
									}
									env.stack[(int)env.getNumber("sp")] = args[0];
									env.setRegister(IC10State.register_sp,(int)env.getNumber("sp")+1);
								},
								"Pushes the value of a to the stack at sp and increments sp"
						),
						"pop",make(
								args("reg",REGISTER),
								(env,args)->{
									env.setRegister(IC10State.register_sp,(int)env.getNumber("sp")-1);
									if ((int)env.getNumber("sp") < 0) {
										env.error = "StackUnderflow";
										return;
									}
									env.setRegister((int)args[0],env.stack[(int)env.getNumber("sp")]);
								},
								"Register = the value at the top of the stack and decrements sp"
						),
						"peek",make(
								args("reg",REGISTER),
								(env,args)->{
									if ((int)env.getNumber("sp") <= 0) {
										env.error = "StackUnderflow";
										return;
									}
									env.setRegister((int)args[0],env.stack[(int)env.getNumber("sp")-1]);
								},
								"Register = the value at the top of the stack"
						)
				),
				"j",make(
						args("a",INTEGER),
						(env,args)->{
							env.line = (int)args[0];
						},
						"Jump execution to line a"
				),
				"jr",make(
						args("a",INTEGER),
						(env,args)->{
							env.line += (int)args[0];
						},
						"Skip execution by a lines"
				),
				"jal",make(
						args("a",INTEGER),
						(env,args)->{
							env.setRegister(IC10State.register_ra,env.line);
							env.line = (int)args[0];
						},
						"Jump execution to line a and store next line number in ra"
				),
				"yield",make(
						args(),
						(env,args)->{
							env.yield = 10;
						},
						"Pauses execution for 1 stationeers tick (= half a second)",
						"assuming this node is in the tick event"
				),
				"sleep",make(
						args("a",INTEGER),
						(env,args)->{
							env.yield = (int)args[0];
						},
						"Pauses execution for a minecraft ticks",
						"assuming this node is in the tick event"
				),
				"Redstone Over Radio",map(
						"lr",make(
								args("register",REGISTER,"frequency",STRING),
								(env,args)->{
									Level world = env.level();
									RTTYChannel chan = world == null || ((String)args[1]).length() > 128 ? null : RTTYSystem.listen(world,(String)args[1]);
									if (chan != null)
										env.setRegister((int)args[0],chan.signal);
									else
										env.setRegister((int)args[0],"");
								},
								"Register = value from RoR signal with",
								"specified frequency (as string)"
						),
						"lnr",make(
								args("register",REGISTER,"frequency",STRING),
								(env,args)->{
									Level world = env.level();
									RTTYChannel chan = world == null || ((String)args[1]).length() > 128 ? null : RTTYSystem.listen(world,(String)args[1]);
									if (chan != null && (chan.signal instanceof String || chan.signal instanceof Number)) {
										Double num = chan.signal instanceof Number n ? Double.valueOf(n.doubleValue()) : tonumberd((String)chan.signal);
										env.setRegister((int)args[0],num != null ? num : -1D);
									} else
										env.setRegister((int)args[0],0);
								},
								"Register = value from RoR signal with",
								"specified frequency (as number)"
						),
						"sr",make(
								args("frequency",STRING,"a",STRING),
								(env,args)->{
									env.broadcast((String)args[0],args[1]);
								},
								"RoR signal with specified frequency",
								"= a (as string)"
						),
						"snr",make(
								args("frequency",STRING,"a",INTEGER),
								(env,args)->{
									env.broadcast((String)args[0],args[1]);
								},
								"RoR signal with specified frequency",
								"= a (as number)"
						)
				),
				"Variables",map(
						"llv",make(
								args("register",REGISTER,"name",STRING),
								(env,args)->{
									DataValue var = env.node.ctrl.getVar((String)args[1]);
									switch(var.getType()) {
										case ENUM -> env.setRegister((int)args[0],"< ENUM >");
										case NUMBER -> env.setRegister((int)args[0],(double)var.getNumber());
										case STRING -> env.setRegister((int)args[0],var.toString());
										case COMPOSITE -> env.setRegister((int)args[0],"< PACKED >");
										default -> env.setRegister((int)args[0],"< ERROR >");
									}
								},
								"Register =",
								"Local variable with specified name"
						),
						"lgv",make(
								args("register",REGISTER,"name",STRING),
								(env,args)->{
									DataValue var = env.node.ctrl.getGlobalVar((String)args[1]);
									switch(var.getType()) {
										case ENUM -> env.setRegister((int)args[0],"< ENUM >");
										case NUMBER -> env.setRegister((int)args[0],(double)var.getNumber());
										case STRING -> env.setRegister((int)args[0],var.toString());
										case COMPOSITE -> env.setRegister((int)args[0],"< PACKED >");
										default -> env.setRegister((int)args[0],"< ERROR >");
									}
								},
								"Register =",
								"Global variable with specified name"
						),
						"slv",make(
								args("name",STRING,"a",STRING),
								(env,args)->{
									env.putVar(env.node.ctrl.vars,(String)args[0],new DataValueString((String)args[1]));
								},
								"Local variable with specified name",
								"= a (as string)"
						),
						"snlv",make(
								args("name",STRING,"a",NUMBER),
								(env,args)->{
									env.putVar(env.node.ctrl.vars,(String)args[0],new DataValueFloat((float)(double)args[1]));
								},
								"Local variable with specified name",
								"= a (as number)"
						),
						"sgv",make(
								args("name",STRING,"a",STRING),
								(env,args)->{
									env.putVar(env.node.ctrl.panel.globalVars,(String)args[0],new DataValueString((String)args[1]));
								},
								"Global variable with specified name",
								"= a (as string)"
						),
						"sngv",make(
								args("name",STRING,"a",NUMBER),
								(env,args)->{
									env.putVar(env.node.ctrl.panel.globalVars,(String)args[0],new DataValueFloat((float)(double)args[1]));
								},
								"Global variable with specified name",
								"= a (as number)"
						)
				),
				"print",make(
						args("a",STRING,"b",ANY),
						(env,args)->{
							env.print((String)args[0],args[1]);
						},
						"Sends message to players in 3x3x3 area",
						"in form of a: b"
				),
				"Querying",map(
						"query",make(
								args("register",REGISTER,"link",STRING,"property",STRING),
								(env,args)->{
									DataValue var = null;
									String tag = (String)args[1];
									String property = (String)args[2];
									IControllable te = env.linkTarget(tag);
									if (te != null)
										var = te.getQueryData().get(property);
									if (var == null)
										env.setRegister((int)args[0],"< ERROR >");
									else {
										switch(var.getType()) {
											case ENUM -> env.setRegister((int)args[0],"< ENUM >");
											case NUMBER -> env.setRegister((int)args[0],(double)var.getNumber());
											case STRING -> env.setRegister((int)args[0],var.toString());
											case COMPOSITE -> env.setRegister((int)args[0],"< PACKED >");
											default -> env.setRegister((int)args[0],"< ERROR >");
										}
									}
								},
								"Register = property of linked machine"
						),
						"queryadd",make(
								args("register",REGISTER,"property",STRING),
								(env,args)->{
									String property = (String)args[1];
									Double value = null;
									for (IControllable te : env.linked()) {
										if (te != null) {
											DataValue dv = te.getQueryData().get(property);
											if (dv instanceof DataValueFloat) {
												if (value == null)
													value = 0d;
												value += dv.getNumber();
											} else {
												value = null;
												break;
											}
										}
									}
									if (value == null)
										env.setRegister((int)args[0],"< ERROR >");
									else
										env.setRegister((int)args[0],value);
								},
								"Register = bulk query ADD with property"
						),
						"queryavg",make(
								args("register",REGISTER,"property",STRING),
								(env,args)->{
									String property = (String)args[1];
									Double value = null;
									int div = 0;
									for (IControllable te : env.linked()) {
										if (te != null) {
											DataValue dv = te.getQueryData().get(property);
											if (dv instanceof DataValueFloat) {
												if (value == null)
													value = 0d;
												value += dv.getNumber();
												div++;
											} else {
												value = null;
												break;
											}
										}
									}
									if (value == null)
										env.setRegister((int)args[0],"< ERROR >");
									else
										env.setRegister((int)args[0],value / div);
								},
								"Register = bulk query AVERAGE with property"
						),
						"querymul",make(
								args("register",REGISTER,"property",STRING),
								(env,args)->{
									String property = (String)args[1];
									Double value = null;
									for (IControllable te : env.linked()) {
										if (te != null) {
											DataValue dv = te.getQueryData().get(property);
											if (dv instanceof DataValueFloat) {
												if (value == null)
													value = 1d;
												value *= dv.getNumber();
											} else {
												value = null;
												break;
											}
										}
									}
									if (value == null)
										env.setRegister((int)args[0],"< ERROR >");
									else
										env.setRegister((int)args[0],value);
								},
								"Register = bulk query MULTIPLY with property"
						),
						"queryh",make(
								args("register",REGISTER,"property",STRING),
								(env,args)->{
									String property = (String)args[1];
									Double value = null;
									for (IControllable te : env.linked()) {
										if (te != null) {
											DataValue dv = te.getQueryData().get(property);
											if (dv instanceof DataValueFloat) {
												if (value == null)
													value = (double)dv.getNumber();
												value = Math.max(dv.getNumber(),value);
											} else {
												value = null;
												break;
											}
										}
									}
									if (value == null)
										env.setRegister((int)args[0],"< ERROR >");
									else
										env.setRegister((int)args[0],value);
								},
								"Register = bulk query HIGHEST with property"
						),
						"queryl",make(
								args("register",REGISTER,"property",STRING),
								(env,args)->{
									String property = (String)args[1];
									Double value = null;
									for (IControllable te : env.linked()) {
										if (te != null) {
											DataValue dv = te.getQueryData().get(property);
											if (dv instanceof DataValueFloat) {
												if (value == null)
													value = (double)dv.getNumber();
												value = Math.min(dv.getNumber(),value);
											} else {
												value = null;
												break;
											}
										}
									}
									if (value == null)
										env.setRegister((int)args[0],"< ERROR >");
									else
										env.setRegister((int)args[0],value);
								},
								"Register = bulk query LOWEST with property"
						)
				)
		);
		rebuildInsnTable();
	}
}
