package com.leafia.contents.machines.controlpanel.core;

import java.util.Map;
import java.util.Set;

import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValue.DataType;
import com.leafia.contents.machines.controlpanel.core.types.DataValueComposite;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;
import com.leafia.contents.machines.controlpanel.core.types.DataValueString;

/**
 * The variable list's rules, with no widget in them (CCP-NODE-EDITOR, D-3067): what NTM-CE's {@code SubElementVariables} did to
 * the panel's global variables and a control's local ones when its buttons were pressed. The page is {@code
 * client.SubElementVariables}; it calls these, and a test calls them too.
 *
 * <p>A <b>global</b> variable belongs to the panel and every one can be deleted. A <b>local</b> variable belongs to a control:
 * the ones its type declares ({@code Control.vars} from the constructor) are not deletable or clearable, and only the ones the
 * player made ({@link Control#customVarNames}) are. Three kinds can be made: a number, a string and a pack (composite); an enum
 * variable can exist (a control may declare one) but is shown, not edited.
 *
 * <p>Differences from CE, each for a value a client can author (D-1093 #4): a name is trimmed and may not be empty, so a blank
 * variable cannot be made and then never found again; a name is at most {@link #MAX_NAME} characters; and a number that is
 * NaN or infinite is refused (it would round-trip through NBT as a number nothing could compare). Parsing is CE's
 * {@code tryParseFloat}.
 */
public final class ControlVariables {

	private ControlVariables() {}

	/** A variable name's length ceiling. CE had none; the page's name box shows about ten characters. */
	public static final int MAX_NAME = 64;

	/** CE's {@code tryParseFloat}: null for empty text, a lone sign or point, or anything that is not a finite float. */
	public static Float parseNumber(String text) {
		String normalized = text == null ? "" : text.trim();
		if(normalized.isEmpty() || "-".equals(normalized) || ".".equals(normalized) || "-.".equals(normalized))
			return null;
		try {
			float f = Float.parseFloat(normalized);
			return Float.isNaN(f) || Float.isInfinite(f) ? null : f;
		} catch(NumberFormatException e) {
			return null;
		}
	}

	/**
	 * True for the characters CE let into a number's text box: digits, a sign and a point. An exponent mark is allowed too ({@code
	 * E}, {@code e}, {@code +}), because a float prints as {@code 1.0E-5} and the box must be able to show the value it holds.
	 */
	public static boolean isNumericText(String text) {
		for(int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if(!(c >= '0' && c <= '9') && c != '-' && c != '.' && c != 'E' && c != 'e' && c != '+')
				return false;
		}
		return true;
	}

	/**
	 * Makes a variable. {@code custom} is the control's custom-name set for a local variable, or null for a global one. Returns
	 * false, and changes nothing, if the name is empty or too long, or a number's text does not parse.
	 */
	public static boolean create(Map<String, DataValue> vars, Set<String> custom, String name, DataType type, String dataText) {
		if(name == null)
			return false;
		name = name.trim();
		if(name.isEmpty() || name.length() > MAX_NAME)
			return false;
		DataValue value;
		switch(type) {
			case NUMBER: {
				Float parsed = parseNumber(dataText);
				if(parsed == null)
					return false;
				value = new DataValueFloat(parsed);
				break;
			}
			case STRING:
				value = new DataValueString(dataText == null ? "" : dataText);
				break;
			case COMPOSITE:
				value = new DataValueComposite();
				break;
			default:
				return false;
		}
		vars.put(name, value);
		if(custom != null)
			custom.add(name);
		return true;
	}

	/** Whether a variable can be deleted: any global one, and a local one the player made. */
	public static boolean canDelete(Set<String> custom, String name) {
		return custom == null || custom.contains(name);
	}

	/** Deletes one variable. A local variable the control declares is left alone. Returns whether anything was removed. */
	public static boolean delete(Map<String, DataValue> vars, Set<String> custom, String name) {
		if(!canDelete(custom, name))
			return false;
		boolean removed = vars.remove(name) != null;
		if(custom != null)
			custom.remove(name);
		return removed;
	}

	/** CE's Clear: every global variable, or every local one the player made. */
	public static void clear(Map<String, DataValue> vars, Set<String> custom) {
		if(custom == null) {
			vars.clear();
		} else {
			for(String s : custom)
				vars.remove(s);
		}
	}

	/**
	 * Applies text typed into a variable's box. A number takes it only if it parses (CE: the old value stays while the text is
	 * half-typed), a string takes it whole; a pack and an enum are not editable here. Returns whether the variable changed.
	 */
	public static boolean setFromText(Map<String, DataValue> vars, String name, String text) {
		DataValue old = vars.get(name);
		if(old == null)
			return false;
		switch(old.getType()) {
			case NUMBER: {
				Float parsed = parseNumber(text);
				if(parsed == null)
					return false;
				vars.put(name, new DataValueFloat(parsed));
				return true;
			}
			case STRING:
				vars.put(name, new DataValueString(text));
				return true;
			default:
				return false;
		}
	}

	/** Whether the page lets the player type into this variable's box: numbers and strings only. */
	public static boolean isEditable(DataValue v) {
		return v != null && (v.getType() == DataType.NUMBER || v.getType() == DataType.STRING);
	}
}
