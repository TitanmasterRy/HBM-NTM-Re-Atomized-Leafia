package com.leafia.contents.machines.controlpanel.nodes;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Supplier;

import com.hbm.sound.NtmSounds;
import com.leafia.contents.machines.controlpanel.core.IControllable;
import com.leafia.contents.machines.controlpanel.core.NodeConnection;
import com.leafia.contents.machines.controlpanel.core.NodeDropdown;
import com.leafia.contents.machines.controlpanel.core.NodeSystem;
import com.leafia.contents.machines.controlpanel.core.modular.StockNodesRegister;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeOutput;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValue.DataType;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;
import com.leafia.init.LeafiaSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/**
 * Plays one of twelve sounds at the panel (CCP-LEAFIA-NODES, D-3070). Adapted from Leafia's {@code NodeSounder}.
 *
 * <p>When its graph runs it plays the chosen sound at the centre of the block it runs for, in the blocks channel, with
 * {@code Volume} clamped to 0..10 and {@code Pitch} to 0.5..2. The graph's player-facing effect is the sound, and nothing else
 * reaches the world.
 *
 * <p><b>Two sounds are not what upstream names.</b> {@code BUTTON_ACCEPT} and {@code BUTTON_DENY} were NTM-CE's
 * {@code buttonYes}/{@code buttonNo}, which are in neither the base mod nor the addon; they are the base mod's {@code techboop}
 * and vanilla's villager "no", as {@code LeafiaDFC}'s buttons already use them (D-3031). The other ten are upstream's events:
 * four of the addon's, four of the base mod's, and vanilla's UI click. They are looked up when the node plays, not when the class
 * loads, because a registry holder cannot be read before registration.
 *
 * <p><b>Returns true</b> from {@link #doOutput}, where Leafia's returned false. In a <i>send</i> graph false cancels the event for
 * the receiver being asked (the contract {@code IOutputNode} carries), so a sound in a send graph silently stopped the signal it
 * was meant to accompany; only {@code NodeCancelEvent} cancels. In a receive graph the value is ignored either way.
 */
public class NodeSounder extends NodeOutput {

	public NodeSnd snd = NodeSnd.BUTTON_ACCEPT;

	public enum NodeSnd {
		BUTTON_ACCEPT(() -> NtmSounds.ITEM_TECH_BOOP.get()),
		BUTTON_DENY(() -> SoundEvents.VILLAGER_NO),
		BUTTON(() -> LeafiaSounds.sbWallButton.get()),
		CLICK_ACCEPT(() -> SoundEvents.UI_BUTTON_CLICK.value()),
		CLICK_INVALID(() -> LeafiaSounds.UI_BUTTON_INVALID.get()),
		KEYPAD(() -> LeafiaSounds.UI_BUTTON_KEYPAD.get()),
		ARMED(() -> NtmSounds.WEAPON_FSTBMB_START.get()),
		TIMER(() -> NtmSounds.WEAPON_FSTBMB_PING.get()),
		BLEEP(() -> NtmSounds.ITEM_TECH_BLEEP.get()),
		POP(() -> NtmSounds.ITEM_TECH_BOOP.get()),
		SHUTDOWN(() -> NtmSounds.BLOCK_SHUTDOWN.get()),
		AZ_5(() -> LeafiaSounds.az5.get()),
		;

		private final Supplier<SoundEvent> evt;

		NodeSnd(Supplier<SoundEvent> evt) {
			this.evt = evt;
		}

		/** The sound event this entry plays. */
		public SoundEvent event() {
			return evt.get();
		}

		public static NodeSnd getByName(String s) {
			for(NodeSnd value : NodeSnd.values()) {
				if(value.name().equals(s))
					return value;
			}
			return null;
		}
	}

	public NodeSounder(float x, float y) {
		super(x, y);
		NodeDropdown sounds = new NodeDropdown(this, otherElements.size(), s -> {
			NodeSnd snd = NodeSnd.getByName(s);
			if(snd != null)
				this.snd = snd;
			return null;
		}, () -> snd.name());
		String[] list = new String[NodeSnd.values().length];
		for(int i = 0; i < list.length; i++)
			list[i] = NodeSnd.values()[i].name();
		Arrays.sort(list);
		sounds.list.addItems(list);
		otherElements.add(sounds);
		this.inputs.add(new NodeConnection("Volume", this, inputs.size(), true, DataType.NUMBER, new DataValueFloat(0.5f)));
		this.inputs.add(new NodeConnection("Pitch", this, inputs.size(), true, DataType.NUMBER, new DataValueFloat(1)));
		recalcSize();
	}

	@Override
	public void readFromNBT(CompoundTag tag, NodeSystem sys) {
		NodeSnd snd = NodeSnd.getByName(tag.getString("sound"));
		if(snd != null)
			this.snd = snd;
		super.readFromNBT(tag, sys);
	}

	@Override
	public CompoundTag writeToNBT(CompoundTag tag, NodeSystem sys) {
		tag.putString("nodeType", "sounder");
		tag.putString("sound", snd.name());
		return super.writeToNBT(tag, sys);
	}

	@Override
	public boolean doOutput(IControllable from, Map<String, NodeSystem> sendNodeMap, Map<String, BlockPos> positions) {
		Level world = from.getControlWorld();
		if(world == null || world.isClientSide() || inputs.size() < 2)
			return true;

		DataValue vol = inputs.get(0).evaluate();
		DataValue pit = inputs.get(1).evaluate();
		if(vol == null || pit == null)
			return true;
		float volume = Mth.clamp(vol.getNumber(), 0, 10);
		float pitch = Mth.clamp(pit.getNumber(), 0.5f, 2);
		if(Float.isNaN(volume) || Float.isNaN(pitch))
			return true;

		BlockPos at = from.getControlPos();
		world.playSound(null, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, snd.event(), SoundSource.BLOCKS, volume, pitch);
		return true;
	}

	@Override
	public float[] getColor() {
		return StockNodesRegister.colorOutput;
	}

	@Override
	public String getDisplayName() {
		return "Play Sound";
	}
}
