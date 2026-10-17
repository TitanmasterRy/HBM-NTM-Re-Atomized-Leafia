package com.leafia.dev.container_utility;

import net.minecraft.world.entity.player.Player;

import java.util.Collections;
import java.util.List;

/**
 * Ported from `com.leafia.dev.container_utility.LeafiaPacketReceiver`.
 *
 * <p>What a block entity implements to take part in the addon's own packet channel: an
 * identifier, a handler per side, and a radius deciding who gets sent updates. Named by 45
 * upstream files, which makes it the most-implemented interface in the addon.
 *
 * <p>{@code EntityPlayer} is {@link Player}. The {@code @SideOnly(Side.CLIENT)} on
 * {@code onReceivePacketLocal} is dropped rather than translated to {@code @OnlyIn}: NeoForge
 * keeps client-only code behind a {@code Dist.CLIENT} entry point instead of stripping methods
 * off interfaces, and this port follows that (D-003). The method is still only called on the
 * client - by the channel, not by an annotation.
 *
 * <p>{@link #onPlayerValidate} is asked for by the client's validate sweep ({@link LeafiaValidateSweep}),
 * upstream's {@code LeafiaClientListener} loop, which landed with the close-out batch (D-3040); until then
 * every implementation was dead code.
 */
public interface LeafiaPacketReceiver {
    public String getPacketIdentifier();
    public void onReceivePacketLocal(byte key,Object value);
    public void onReceivePacketServer(byte key,Object value,Player plr);

    /**
     * <tt>[SERVER]</tt> This value times 1.3 will be the packet radius when __sendToAffectedClients() is used.
     * <p><tt>[CLIENT]</tt> Used for automatically sending ._validate() packet
     * @return The range
     */
    public default double affectionRange() { return 32; }
    public default List<Player> getListeners() { return Collections.emptyList(); };

    /**
     * <tt>[SERVER]</tt> Called by client when they get into range specified by affectionRange().
     * <p><tt>[CLIENT]</tt> Never called.
     * @param plr The player, intended to for using .__sendToClient() to update the client.
     */
    public void onPlayerValidate(Player plr); // little attempt to make this mod less internet expensive..
}
