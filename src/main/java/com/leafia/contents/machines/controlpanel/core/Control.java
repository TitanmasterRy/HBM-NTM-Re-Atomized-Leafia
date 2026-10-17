package com.leafia.contents.machines.controlpanel.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.leafia.contents.machines.controlpanel.core.controls.ControlType;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.AABB;

/**
 * One control on a panel: a button, a dial, a lamp. The <b>common half</b> of NTM-CE's {@code Control} (D-3061), adapted:
 * identity, position, links, variables, configuration, event routing, bounds and NBT. Nothing here names a client type.
 *
 * <p>What CE's {@code Control} also did and this class does not, because it is the client's (§2.2): {@code render},
 * {@code renderBatched}, {@code renderControl}, {@code appendGuiQuad}, {@code getModel}, {@code getGuiTexture} and
 * {@code getConfigSubElement}. Those move to {@code core.client} classes that take a {@code Control} as an argument (batch 5
 * draws the panel, batch 6 the editor's config pane); they are not abstract here, so a concrete control compiles and runs on a
 * dedicated server without them.
 *
 * <h2>NBT</h2>
 * The key names are CE's ({@code name}, {@code myName}, {@code vars}, {@code SN}, {@code RN}, {@code customvars},
 * {@code taglnk}, {@code X}, {@code Y}, {@code configs}, and the {@code ntmPlaceholderMeta} box), so a panel is the same tree in
 * both. {@link #readFromNBT} <b>throws</b> on a variable or config that does not parse, as CE did; the caller
 * ({@code ControlPanel.readControl}, batch 3) catches that and keeps the raw tag in an {@code UnknownControl} placeholder
 * instead of losing the control.
 */
public abstract class Control {

	static final String PLACEHOLDER_META_TAG = "ntmPlaceholderMeta";
	static final String PLACEHOLDER_BOX_MIN_X = "boxMinX";
	static final String PLACEHOLDER_BOX_MIN_Y = "boxMinY";
	static final String PLACEHOLDER_BOX_MAX_X = "boxMaxX";
	static final String PLACEHOLDER_BOX_MAX_Y = "boxMaxY";
	static final String PLACEHOLDER_SIZE_HEIGHT = "sizeHeight";
	static final String PLACEHOLDER_HAS_BOUNDING_BOX = "hasBoundingBox";
	static final String PLACEHOLDER_BOUNDS_MIN_X = "boundsMinX";
	static final String PLACEHOLDER_BOUNDS_MIN_Y = "boundsMinY";
	static final String PLACEHOLDER_BOUNDS_MIN_Z = "boundsMinZ";
	static final String PLACEHOLDER_BOUNDS_MAX_X = "boundsMaxX";
	static final String PLACEHOLDER_BOUNDS_MAX_Y = "boundsMaxY";
	static final String PLACEHOLDER_BOUNDS_MAX_Z = "boundsMaxZ";

	public String name;
	public final String registryName;
	public ControlPanel panel;
	/** Tag to block position this control is linked to. When an event is sent, it goes to each one. An absent tag reads as the origin. */
	public Object2ObjectLinkedOpenHashMap<String, BlockPos> taggedLinks = new Object2ObjectLinkedOpenHashMap<>();
	/** Event name to the node system run on an event this control is sending out to linked blocks. */
	public Map<String, NodeSystem> sendNodeMap = new Object2ObjectLinkedOpenHashMap<>();
	/** Event name to the node system run on an event this control receives. */
	public Map<String, NodeSystem> receiveNodeMap = new Object2ObjectLinkedOpenHashMap<>();
	/** Every variable, used by the control itself or by its node systems. */
	public Map<String, DataValue> vars = new Object2ObjectLinkedOpenHashMap<>();
	public Map<String, DataValue> varsPrev = new Object2ObjectLinkedOpenHashMap<>();
	/** The custom variables the user may remove. */
	public Set<String> customVarNames = new ObjectOpenHashSet<>();
	/** Static initial configuration of a control, e.g. colour and size. */
	public Map<String, DataValue> configMap = new Object2ObjectLinkedOpenHashMap<>();
	public float posX;
	public float posY;

	public Control(String name, String registryName, ControlPanel panel) {
		this.name = name;
		this.registryName = registryName;
		this.panel = panel;
		taggedLinks.defaultReturnValue(BlockPos.ZERO);
	}

	public abstract ControlType getControlType();

	/** Width, length and height of the control, in panel units. */
	public abstract float[] getSize();

	public Map<String, DataValue> getConfigs() {
		return configMap;
	}

	public void applyConfigs(Map<String, DataValue> configs) {
		configMap = new Object2ObjectLinkedOpenHashMap<>(configs);
		refreshConfigs();
	}

	public void refreshConfigs() {
		onConfigMapChanged();
	}

	protected void onConfigMapChanged() {}

	public List<String> getOutEvents() {
		return Collections.emptyList();
	}

	public List<String> getInEvents() {
		return Arrays.asList("tick", "initialize", "redstone_input");
	}

	public AABB getBoundingBox() {
		float width = getSize()[0];
		float length = getSize()[1];
		float height = getSize()[2];
		// offset to fix placement position error for controls not 1x1.
		return new AABB(-width / 2, 0, -length / 2, width / 2, height, length / 2)
				.move(posX + ((width > 1 ? Math.abs(1 - width) / 2 : (width - 1) / 2)), 0,
						posY + ((length > 1) ? Math.abs(1 - length) / 2 : (length - 1) / 2));
	}

	public float[] getBox() {
		float[] box = new float[4];
		fillBox(box);
		return box;
	}

	public void fillBox(float[] box) {
		float width = getSize()[0];
		float length = getSize()[1];
		box[0] = posX;
		box[1] = posY;
		box[2] = posX + width;
		box[3] = posY + length;
	}

	public abstract Control newControl(ControlPanel panel);

	public abstract void populateDefaultNodes(List<ControlEvent> receiveEvents);

	public void receiveEvent(ControlEvent evt) {
		NodeSystem sys = receiveNodeMap.get(evt.name);
		if(sys != null) {
			sys.resetCachedValues();
			sys.receiveEvent(panel, this, evt);
		}
	}

	public DataValue getVar(String name) {
		return vars.getOrDefault(name, new DataValueFloat(0));
	}

	public DataValue getGlobalVar(String name) {
		return panel.getVar(name);
	}

	public CompoundTag writeToNBT(CompoundTag tag) {
		tag.putString("name", registryName);
		tag.putString("myName", name);
		CompoundTag vars = new CompoundTag();
		for(Entry<String, DataValue> e : this.vars.entrySet()) {
			vars.put(e.getKey(), e.getValue().writeToNBT());
		}
		tag.put("vars", vars);

		CompoundTag sendNodes = new CompoundTag();
		for(Entry<String, NodeSystem> e : sendNodeMap.entrySet()) {
			sendNodes.put(e.getKey(), e.getValue().writeToNBT(new CompoundTag()));
		}
		tag.put("SN", sendNodes);

		CompoundTag receiveNodes = new CompoundTag();
		for(Entry<String, NodeSystem> e : receiveNodeMap.entrySet()) {
			receiveNodes.put(e.getKey(), e.getValue().writeToNBT(new CompoundTag()));
		}
		tag.put("RN", receiveNodes);

		CompoundTag customVarNames = new CompoundTag();
		int i = 0;
		for(String s : this.customVarNames) {
			customVarNames.putString("var" + i, s);
			i++;
		}
		tag.put("customvars", customVarNames);

		CompoundTag taggedLinks = new CompoundTag();
		for(Entry<String, BlockPos> entry : this.taggedLinks.entrySet()) {
			taggedLinks.putIntArray(entry.getKey(),
					new int[]{entry.getValue().getX(), entry.getValue().getY(), entry.getValue().getZ()});
		}
		tag.put("taglnk", taggedLinks);

		tag.putFloat("X", posX);
		tag.putFloat("Y", posY);

		CompoundTag configs = new CompoundTag();
		for(Entry<String, DataValue> e : configMap.entrySet()) {
			configs.put(e.getKey(), e.getValue().writeToNBT());
		}
		tag.put("configs", configs);

		CompoundTag placeholderMeta = new CompoundTag();
		float[] box = getBox();
		if(box != null && box.length >= 4) {
			placeholderMeta.putFloat(PLACEHOLDER_BOX_MIN_X, box[0]);
			placeholderMeta.putFloat(PLACEHOLDER_BOX_MIN_Y, box[1]);
			placeholderMeta.putFloat(PLACEHOLDER_BOX_MAX_X, box[2]);
			placeholderMeta.putFloat(PLACEHOLDER_BOX_MAX_Y, box[3]);
		}
		float[] size = getSize();
		if(size != null && size.length >= 3) {
			placeholderMeta.putFloat(PLACEHOLDER_SIZE_HEIGHT, Math.max(size[2], 0.1F));
		}
		AABB boundingBox = getBoundingBox();
		placeholderMeta.putBoolean(PLACEHOLDER_HAS_BOUNDING_BOX, boundingBox != null);
		if(boundingBox != null) {
			placeholderMeta.putDouble(PLACEHOLDER_BOUNDS_MIN_X, boundingBox.minX);
			placeholderMeta.putDouble(PLACEHOLDER_BOUNDS_MIN_Y, boundingBox.minY);
			placeholderMeta.putDouble(PLACEHOLDER_BOUNDS_MIN_Z, boundingBox.minZ);
			placeholderMeta.putDouble(PLACEHOLDER_BOUNDS_MAX_X, boundingBox.maxX);
			placeholderMeta.putDouble(PLACEHOLDER_BOUNDS_MAX_Y, boundingBox.maxY);
			placeholderMeta.putDouble(PLACEHOLDER_BOUNDS_MAX_Z, boundingBox.maxZ);
		}
		tag.put(PLACEHOLDER_META_TAG, placeholderMeta);

		return tag;
	}

	public void readFromNBT(CompoundTag tag) {
		if(tag.contains("myName")) {
			this.name = tag.getString("myName");
		}

		CompoundTag vars = tag.getCompound("vars");
		for(String k : vars.getAllKeys()) {
			Tag base = vars.get(k);
			DataValue val = DataValue.newFromNBT(base);
			if(val == null) {
				throw new IllegalStateException("Failed to deserialize variable '" + k + "' for control '" + registryName + "'");
			}
			this.vars.put(k, val);
		}

		sendNodeMap.clear();
		receiveNodeMap.clear();
		customVarNames.clear();
		taggedLinks.clear();

		CompoundTag customVarNames = tag.getCompound("customvars");
		for(int i = 0; i < customVarNames.getAllKeys().size(); i++) {
			this.customVarNames.add(customVarNames.getString("var" + i));
		}

		if(tag.contains("conset")) { // retrocompatibility
			CompoundTag connectedSet = tag.getCompound("conset");
			for(int i = 0; i < connectedSet.getAllKeys().size() / 3; i++) {
				int x = connectedSet.getInt("px" + i);
				int y = connectedSet.getInt("py" + i);
				int z = connectedSet.getInt("pz" + i);
				this.taggedLinks.put(x + ", " + y + ", " + z, new BlockPos(x, y, z));
			}
		}

		if(tag.contains("taglnk")) {
			CompoundTag taggedLinks = tag.getCompound("taglnk");
			for(String key : taggedLinks.getAllKeys()) {
				int[] p = taggedLinks.getIntArray(key);
				this.taggedLinks.put(key, new BlockPos(p[0], p[1], p[2]));
			}
		}

		CompoundTag sendNodes = tag.getCompound("SN");
		for(String s : sendNodes.getAllKeys()) {
			NodeSystem sys = new NodeSystem(this);
			sendNodeMap.put(s, sys);
			sys.readFromNBT(sendNodes.getCompound(s));
		}
		CompoundTag receiveNodes = tag.getCompound("RN");
		for(String s : receiveNodes.getAllKeys()) {
			// CE logged a receive graph that would not read and went on with the half-read system in the map. That leaves a
			// graph with sockets missing that fails at its first event, and loses the rest of it on the next save; here the
			// control fails to read, so its panel keeps it as an UnknownControl with the tag intact (D-3063).
			try {
				NodeSystem sys = new NodeSystem(this);
				receiveNodeMap.put(s, sys);
				sys.readFromNBT(receiveNodes.getCompound(s));
			} catch(RuntimeException e) {
				throw new IllegalStateException("Failed to read the receive graph '" + s + "' of control '" + registryName + "': " + e, e);
			}
		}

		this.posX = tag.getFloat("X");
		this.posY = tag.getFloat("Y");

		CompoundTag configs = tag.getCompound("configs");
		for(String e : configs.getAllKeys()) {
			DataValue value = DataValue.newFromNBT(configs.get(e));
			if(value == null) {
				throw new IllegalStateException("Failed to deserialize config '" + e + "' for control '" + registryName + "'");
			}
			configMap.put(e, value);
		}
	}
}
