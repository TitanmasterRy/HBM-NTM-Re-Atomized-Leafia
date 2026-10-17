package com.leafia.eventbuses;

import com.leafia.contents.machines.reactors.lftr.components.arbitrary.MSRArbitraryBlock;
import com.leafia.contents.machines.reactors.lftr.components.ejector.MSREjectorBlock;
import com.leafia.contents.machines.reactors.lftr.components.element.MSRElementBlock;
import com.leafia.contents.machines.reactors.lftr.components.plug.MSRPlugBlock;
import com.leafia.contents.machines.reactors.pwr.blocks.components.PWRComponentBlock;

import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.machines.controlpanel.core.IControllable;
import com.leafia.contents.machines.controlpanel.core.block.ControlPanelBlock;
import com.leafia.contents.machines.controlpanel.core.reach.BaseReach;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The reactor-component line under a PWR or LFTR block's tooltip (D-3040).
 *
 * <p>Upstream: the first branch of {@code LeafiaClientListener.HandlerClient.drawTooltip}. A block that takes part in a PWR's
 * assembly ({@code PWRComponentBlock}: the channels, conductors, exchangers, control rods, elements, ports, hatches, the
 * terminal and the computer) reads {@code [PWR Component]} and "Conductor for PWR assembly"; the four molten-salt reactor blocks
 * the salt flows through (element, plug, ejector, arbitrary - not the control rod or its extension) read
 * {@code [LFTR Component]} and "Fuels flow through this block". The lines are appended after the item's own, as upstream's
 * tooltip event did. Each block exists only behind its own feature switch, so this needs none.
 *
 * <p>The same handler's structural integrity lines are {@link #integrityLines}: with {@code features.structuralIntegrity} on,
 * every block item says what it weighs and how well it holds ({@code StructuralIntegrityHandler.getMass}/{@code getGlue} for
 * its default state, {@code ?} where the handler has no figure). The creative-tab-id line under advanced tooltips is a debugging aid
 * and stays closed (D-3038). The "[CCP Compatible]" line is {@link #ccpLines} (D-3072).
 *
 * <p>The listener is client-only ({@code LeafiaComponentTooltipsClient}); the lines are built here, where a test can read them.
 */
public final class LeafiaComponentTooltips {

	private LeafiaComponentTooltips() {}

	/** The two lines for {@code item}, or none. */
	public static List<Component> linesFor(Item item) {
		if(!(item instanceof BlockItem blockItem)) return List.of();
		Block block = blockItem.getBlock();
		if(block instanceof PWRComponentBlock) return lines("trait.leafia.component.pwr");
		if(block instanceof MSRArbitraryBlock || block instanceof MSRPlugBlock || block instanceof MSREjectorBlock || block instanceof MSRElementBlock)
			return lines("trait.leafia.component.lftr");
		return List.of();
	}

	/** Upstream's {@code RED "Structural Integrity:"}, {@code DARK_RED " - Mass: "} and {@code " - Glue: "}; none while the switch is off. */
	public static List<Component> integrityLines(Item item) {
		if(!com.leafia.unsorted.StructuralIntegrityHandler.automatic() || !(item instanceof BlockItem blockItem)) return List.of();
		net.minecraft.world.level.block.state.BlockState state = blockItem.getBlock().defaultBlockState();
		int mass = com.leafia.unsorted.StructuralIntegrityHandler.getMass(state, null, null);
		int glue = com.leafia.unsorted.StructuralIntegrityHandler.getGlue(state, null, null);
		return List.of(
				Component.literal("Structural Integrity:").withStyle(ChatFormatting.RED),
				Component.literal(" - Mass: " + (mass != -1 ? mass : "?")).withStyle(ChatFormatting.DARK_RED),
				Component.literal(" - Glue: " + (glue != -1 ? glue : "?")).withStyle(ChatFormatting.DARK_RED));
	}

	private static final Map<Block, Boolean> CCP = new ConcurrentHashMap<>();

	/**
	 * Upstream's {@code GREEN + "[CCP Compatible]"}, the last line of a block item whose block entity the Custom Control Panel can link to
	 * (D-3072): one that implements {@link IControllable} itself (the addon's retrofitted machines, the control torch) or is one the addon
	 * answers for through an adapter ({@link BaseReach}, the base mod's doors, tanks, turbines, turrets and RBMK columns). The panel is
	 * not marked. Upstream built a block entity with {@code createNewTileEntity(world, 12)} and swallowed anything it threw; so does this,
	 * once per block (the answer is kept). Off with {@code features.controlPanel}.
	 */
	public static List<Component> ccpLines(Item item) {
		if(!LeafiaFeatures.controlPanel || !(item instanceof BlockItem blockItem)) return List.of();
		Block block = blockItem.getBlock();
		if(block instanceof ControlPanelBlock) return List.of();
		if(!CCP.computeIfAbsent(block, LeafiaComponentTooltips::ccpCompatible)) return List.of();
		return List.of(Component.literal("[CCP Compatible]").withStyle(ChatFormatting.GREEN));
	}

	private static boolean ccpCompatible(Block block) {
		if(!(block instanceof EntityBlock entityBlock)) return false;
		try {
			BlockEntity be = entityBlock.newBlockEntity(BlockPos.ZERO, block.defaultBlockState());
			return be != null && (be instanceof IControllable || BaseReach.registeredTypes().contains(be.getType()));
		} catch(Throwable ignored) {
			return false;
		}
	}

	/** Upstream's {@code GRAY + "[" + name + "]"} and {@code GRAY + "-::" + WHITE + desc}. */
	private static List<Component> lines(String key) {
		return List.of(
				Component.literal("[").append(Component.translatable(key)).append("]").withStyle(ChatFormatting.GRAY),
				Component.literal("-::").withStyle(ChatFormatting.GRAY).append(Component.translatable(key + ".desc").withStyle(ChatFormatting.WHITE)));
	}
}
