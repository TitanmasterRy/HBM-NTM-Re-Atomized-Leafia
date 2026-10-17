package com.leafia.contents.machines.controlpanel.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.joml.Matrix4f;
import org.joml.Vector4f;

import com.leafia.LeafiaMod;
import com.leafia.contents.machines.controlpanel.core.packet.ControlPanelUpdatePacket.VarUpdate;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The model of a Custom Control Panel: its controls, its global variables, and the slab they sit on (height, slope and four
 * edge insets). The <b>common half</b> of NTM-CE's {@code ControlPanel} (D-3063); batch 1's stand-in, completed in place.
 *
 * <p>What CE's class also did and this one does not, because it is the client's (§2.2): {@code render}, which walks the controls
 * with GL translations. Batch 5's {@code ControlPanelRender} does that with a {@code PoseStack}. What stays common, so that a
 * GameTest on a dedicated-server classpath can run it: the panel-to-block transform and its inverse (JOML, not GL) and the
 * ray pick against the controls' boxes, which CE kept behind {@code @SideOnly(Side.CLIENT)} only because the matrices were
 * LWJGL's.
 *
 * <h2>Reading a panel</h2>
 * {@link #readFromNBT} is <b>refusing</b>, which CE's was not (§2.5): a tag that fails {@link ControlPanelGuard#scan} leaves the
 * panel empty and returns false, and a control that will not parse, or whose graph fails {@link ControlPanelGuard#graphProblem},
 * is kept as an inert {@link UnknownControl} with its tag intact. The geometry is made finite and clamped (a NaN here would
 * become a NaN collision box).
 */
public class ControlPanel {

	/** The controllable that owns this panel: the panel block entity, and what a node graph sends events from. */
	public IControllable parent;
	public List<Control> controls = new ArrayList<>();
	/** Variables that were changed in the last tick. */
	public List<VarUpdate> changedVars = new ArrayList<>();
	/** Global variables, accessible by every control as well as the owner block entity for state like redstone. */
	public Map<String, DataValue> globalVars = new Object2ObjectLinkedOpenHashMap<>();
	public Map<String, DataValue> globalVarsPrev = new Object2ObjectLinkedOpenHashMap<>();

	/** Panel local space to block space, for drawing and for picking. Null until the owner sets it. */
	public Matrix4f transform;
	public Matrix4f invTransform;

	public float height;
	/** Radians. */
	public float angle;
	public float a_off;
	public float b_off;
	public float c_off;
	public float d_off;

	public ControlPanel(IControllable parent) {
		this(parent, 0.25F, (float) Math.toRadians(20), 0, 0, 0.25F, 0);
	}

	public ControlPanel(IControllable parent, float height, float angle, float a_off, float b_off, float c_off, float d_off) {
		this.parent = parent;
		this.height = height;
		this.angle = angle;
		this.a_off = a_off;
		this.b_off = b_off;
		this.c_off = c_off;
		this.d_off = d_off;
	}

	public ControlPanel setTransform(Matrix4f transform) {
		this.transform = transform;
		this.invTransform = new Matrix4f(transform).invert();
		return this;
	}

	/** Whether the editor would accept this slab: CE's {@code SubElementPanelResize} test, applied by the server to an edit. */
	public static boolean geometryValid(float height, float angle, float a, float b, float c, float d) {
		if(!finite(height) || !finite(angle) || !finite(a) || !finite(b) || !finite(c) || !finite(d))
			return false;
		if(a < 0 || b < 0 || c < 0 || d < 0 || a > 1 || b > 1 || c > 1 || d > 1)
			return false;
		if(height < 0 || height > 1)
			return false;
		if(Math.abs(angle) > Math.toRadians(45) + 1E-4)
			return false;
		float height0 = getSlopeHeightFromZ(a, height, -angle);
		float height1 = getSlopeHeightFromZ(1 - c, height, -angle);
		return ((1 - c) > a) && ((1 - b) > d) && (Math.max(height0, height1) <= 1 + 1E-4) && (Math.min(height0, height1) >= -0.01 - 1E-4);
	}

	public boolean geometryValid() {
		return geometryValid(height, angle, a_off, b_off, c_off, d_off);
	}

	private static boolean finite(float f) {
		return !Float.isNaN(f) && !Float.isInfinite(f);
	}

	private static float sane(float v, float min, float max, float fallback) {
		if(!finite(v))
			return fallback;
		return Math.max(min, Math.min(max, v));
	}

	private static float readSane(CompoundTag tag, String key, float min, float max, float fallback) {
		return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? sane(tag.getFloat(key), min, max, fallback) : fallback;
	}

	public static float getSlopeHeightFromZ(float z, float height, float angle) {
		double halfH = 0.5 * Math.tan(angle);
		return (float) ((height - halfH) + Math.tan(angle) * z);
	}

	/** Diffs every variable against the last tick's and fills {@link #changedVars}. Server side only. */
	public void update() {
		Level level = parent.getControlWorld();
		if(level != null && !level.isClientSide) {
			changedVars.clear();
			crossCheckVars(-1, globalVars, globalVarsPrev);
			for(int i = 0; i < controls.size(); i++) {
				Control c = controls.get(i);
				crossCheckVars(i, c.vars, c.varsPrev);
			}
		}
	}

	public void crossCheckVars(int idx, Map<String, DataValue> vars, Map<String, DataValue> varsPrev) {
		for(String s : vars.keySet()) {
			if(!varsPrev.containsKey(s)) {
				changedVars.add(new VarUpdate(idx, s, vars.get(s).copy()));
			}
		}
		for(String s : varsPrev.keySet()) {
			if(!vars.containsKey(s)) {
				changedVars.add(new VarUpdate(idx, s, null));
			} else if(!varsPrev.get(s).equals(vars.get(s))) {
				changedVars.add(new VarUpdate(idx, s, vars.get(s).copy()));
			}
		}
		varsPrev.clear();
		for(Entry<String, DataValue> entry : vars.entrySet()) {
			varsPrev.put(entry.getKey(), entry.getValue().copy());
		}
	}

	/** Applies a server's variable deltas, as the client does. An index past the last control is ignored. */
	public void applyUpdates(List<VarUpdate> updates) {
		for(VarUpdate u : updates) {
			if(u.varName() == null)
				continue;
			if(u.varListIdx() == -1) {
				if(u.data() == null)
					globalVars.remove(u.varName());
				else
					globalVars.put(u.varName(), u.data());
			} else if(u.varListIdx() >= 0 && u.varListIdx() < controls.size()) {
				Control c = controls.get(u.varListIdx());
				if(u.data() == null)
					c.vars.remove(u.varName());
				else
					c.vars.put(u.varName(), u.data());
			}
		}
	}

	public CompoundTag writeToNBT(CompoundTag nbt) {
		CompoundTag globalVars = new CompoundTag();
		for(Entry<String, DataValue> e : this.globalVars.entrySet()) {
			globalVars.put(e.getKey(), e.getValue().writeToNBT());
		}

		CompoundTag controls = new CompoundTag();
		for(int i = 0; i < this.controls.size(); i++) {
			controls.put("control" + i, this.controls.get(i).writeToNBT(new CompoundTag()));
		}

		nbt.put("globalvars", globalVars);
		nbt.put("controls", controls);
		nbt.putFloat("height", height);
		nbt.putFloat("angle", angle);
		nbt.putFloat("a_offset", a_off);
		nbt.putFloat("b_offset", b_off);
		nbt.putFloat("c_offset", c_off);
		nbt.putFloat("d_offset", d_off);
		return nbt;
	}

	/**
	 * Replaces this panel's contents with a saved tree. Returns false, leaving the panel empty, when the tree is refused by
	 * {@link ControlPanelGuard#scan}. Variable diff state is reset, so the next {@link #update} reports everything as new.
	 */
	public boolean readFromNBT(CompoundTag tag) {
		this.globalVars.clear();
		this.globalVarsPrev.clear();
		this.controls.clear();
		this.changedVars.clear();

		String why = ControlPanelGuard.scan(tag);
		if(why != null) {
			LeafiaMod.LOGGER.error("Refusing to read a control panel: {}", why);
			return false;
		}

		CompoundTag globalVars = tag.getCompound("globalvars");
		for(String k : globalVars.getAllKeys()) {
			Tag base = globalVars.get(k);
			DataValue val = DataValue.newFromNBT(base);
			if(val != null) {
				this.globalVars.put(k, val);
			}
		}

		CompoundTag controls = tag.getCompound("controls");
		for(int i = 0; i < controls.getAllKeys().size(); i++) {
			CompoundTag ct = controls.getCompound("control" + i);
			this.controls.add(readControl(ct));
		}

		height = readSane(tag, "height", 0, 1, 0.25F);
		angle = readSane(tag, "angle", (float) -Math.toRadians(80), (float) Math.toRadians(80), (float) Math.toRadians(20));
		a_off = readSane(tag, "a_offset", 0, 1, 0);
		b_off = readSane(tag, "b_offset", 0, 1, 0);
		c_off = readSane(tag, "c_offset", 0, 1, 0.25F);
		d_off = readSane(tag, "d_offset", 0, 1, 0);
		return true;
	}

	/**
	 * A control from its saved tag. A name nobody registered, a tag that will not parse, or a graph that fails
	 * {@link ControlPanelGuard#graphProblem} gives an {@link UnknownControl} that keeps the tag and does nothing.
	 */
	public Control readControl(CompoundTag tag) {
		String registryName = tag.getString("name");
		Control control = ControlRegistry.getNew(registryName, this);
		if(control == null) {
			return new UnknownControl(this, tag);
		}

		try {
			control.applyConfigs(readControlDataMap(tag.getCompound("configs"), "config", registryName));
			control.readFromNBT(tag);
		} catch(Exception x) {
			LeafiaMod.LOGGER.error("Failed to deserialize control '{}', preserving raw NBT placeholder: {}", registryName, x.toString());
			return new UnknownControl(this, tag, "would not load: " + x.getMessage());
		}

		String why = ControlPanelGuard.graphProblem(control);
		if(why != null) {
			LeafiaMod.LOGGER.error("Control '{}' kept inert as a placeholder: {}", registryName, why);
			return new UnknownControl(this, tag, why);
		}
		return control;
	}

	private static Map<String, DataValue> readControlDataMap(CompoundTag tag, String kind, String registryName) {
		Map<String, DataValue> values = new Object2ObjectLinkedOpenHashMap<>();
		for(String key : tag.getAllKeys()) {
			DataValue value = DataValue.newFromNBT(tag.get(key));
			if(value == null) {
				throw new IllegalStateException("Failed to deserialize " + kind + " '" + key + "' for control '" + registryName + "'");
			}
			values.put(key, value);
		}
		return values;
	}

	public Control cloneControl(Control source) {
		return readControl(source.writeToNBT(new CompoundTag()));
	}

	/**
	 * Routes an event from {@code from} to every control linked to that position (and, for an event from the panel itself, to all
	 * of them), giving each the link's tag and index. The owner collects redstone outputs set while it runs, so a graph that
	 * writes several faces applies them together.
	 */
	public void receiveEvent(BlockPos from, ControlEvent evt) {
		IRedstonePanel redstone = parent instanceof IRedstonePanel r ? r : null;
		if(redstone != null)
			redstone.beginRedstoneOutputCollection();
		try {
			for(Control c : new ArrayList<>(controls)) {
				String tag = "";
				int fromIndex = -1;
				int i = 0;
				for(Entry<String, BlockPos> entry : c.taggedLinks.entrySet()) {
					if(entry.getValue().equals(from)) {
						tag = entry.getKey();
						fromIndex = i;
						break;
					}
					i++;
				}
				if(fromIndex != -1 || parent.getControlPos().equals(from)) {
					evt.setVar("tag", tag);
					evt.setVar("from index", fromIndex);
					c.receiveEvent(evt);
				}
			}
		} finally {
			if(redstone != null)
				redstone.finishRedstoneOutputCollection();
		}
	}

	/** Gives one control an event directly (a click), not through a link. */
	public void receiveDirectEvent(Control control, ControlEvent evt) {
		if(control == null)
			return;
		IRedstonePanel redstone = parent instanceof IRedstonePanel r ? r : null;
		if(redstone != null)
			redstone.beginRedstoneOutputCollection();
		try {
			evt.setVar("tag", "");
			control.receiveEvent(evt);
		} finally {
			if(redstone != null)
				redstone.finishRedstoneOutputCollection();
		}
	}

	public DataValue getVar(String name) {
		return globalVars.getOrDefault(name, new DataValueFloat(0));
	}

	/**
	 * The control a ray hits, nearest first, given the ray in <b>block space</b>: where it starts (world position minus the block's
	 * corner), and which way it goes. CE's {@code getSelectedControl}; it needs {@link #setTransform} to have been called.
	 */
	public Control pick(Vec3 pos, Vec3 direction) {
		if(invTransform == null)
			return null;
		Vector4f localPos = new Vector4f((float) pos.x, (float) pos.y, (float) pos.z, 1).mul(invTransform);
		Vector4f localDir = new Vector4f((float) direction.x, (float) direction.y, (float) direction.z, 0).mul(invTransform);
		Vec3 start = new Vec3(localPos.x, localPos.y, localPos.z);
		localDir.set(localDir.x * 3, localDir.y * 3, localDir.z * 3, 0);
		Vec3 end = new Vec3(localPos.x + localDir.x, localPos.y + localDir.y, localPos.z + localDir.z);
		double dist = Double.MAX_VALUE;
		Control ctrl = null;
		for(Control c : controls) {
			AABB box = c.getBoundingBox();
			if(box != null) {
				var hit = box.clip(start, end);
				if(hit.isPresent()) {
					double d = hit.get().distanceToSqr(start);
					if(d < dist) {
						dist = d;
						ctrl = c;
					}
				}
			}
		}
		return ctrl;
	}
}
