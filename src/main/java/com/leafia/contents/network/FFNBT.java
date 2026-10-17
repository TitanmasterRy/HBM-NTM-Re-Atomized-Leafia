package com.leafia.contents.network;

import javax.annotation.Nullable;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.leafia.contents.fluids.ff.LeafiaFF;
import com.leafia.contents.fluids.traits.FT_LFTRCoolant;
import com.leafia.contents.machines.reactors.lftr.components.MSRTEBase;

import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * What a Forge stack's NBT means to a transfer: whether two tagged stacks may meet, and what the
 * receiving stack's tag becomes when they do.
 *
 * <p>Upstream: {@code com/leafia/contents/network/FFNBT.java}. A 1.12.2 {@code FluidStack.tag} is
 * the stack's {@code CUSTOM_DATA} component here, through {@link LeafiaFF#getTag}/{@link LeafiaFF#setTag}
 * (D-3003); "no tag" is "no component". The one tag anything writes is the LFTR salt's
 * ({@link MSRTEBase#nbtProtocol}: heat and the fuel mixture), and the one fluid it rides is any
 * with {@link FT_LFTRCoolant}.
 *
 * <h2>{@link #transferTags} is ported reference for reference</h2>
 *
 * Upstream mutates the stacks' tag objects in place and keeps a local reference across a call that
 * rewrites them, so what it does depends on which object each name still points at. Followed
 * through:
 *
 * <ul>
 * <li>Receiving stack <b>with</b> a tag: the coolant case mixes both tags
 *     ({@link MSRTEBase#transferStats} with a divisor of 1, so the heat difference is halved) and the
 *     receiver keeps the mixed tag.</li>
 * <li>Receiving stack <b>without</b> one: the coolant case still mixes, against an empty mixture at
 *     heat 0, which halves the <em>sender's</em> heat and scales its mixture by its share of the
 *     combined amount - and then the receiver's tag is overwritten with a copy of the sender's tag
 *     as it was <em>before</em> that mixing. So the receiver gets the sender's original salt and
 *     the sender loses half its heat. Kept, and pinned by a GameTest; upstream's own dead branch
 *     in {@code transferStats} suggests the author never saw it.</li>
 * </ul>
 */
public final class FFNBT {

	private FFNBT() {}

	/**
	 * Should be called while calculating fluid distribution in networks to see if tank contents are
	 * compatible: an empty or untagged receiver takes anything, equal tags meet, and any coolant salt
	 * meets any other.
	 */
	public static boolean areTagsCompatible(FluidStack sending, FluidTank receiving) {
		if(receiving.getFluid().isEmpty()) return true;
		CompoundTag sendingTag = tagOrNull(sending);
		CompoundTag receivingTag = tagOrNull(receiving.getFluid());
		if(receivingTag == null) return true;
		if(receivingTag.equals(sendingTag)) return true;
		FluidType ntmf = LeafiaFF.fromFF(sending.getFluid());
		if(!ntmf.equals(Fluids.NONE)) {
			if(ntmf.hasTrait(FT_LFTRCoolant.class)) return true;
		}
		return false;
	}

	/**
	 * Should be called whilst doing a transfer: the receiving stack's tag after the sending stack has
	 * been poured into it. See the class note for what the two cases do.
	 */
	public static void transferTags(FluidTank sending, FluidTank receiving) {
		if(sending.getFluid().isEmpty()) return;
		if(receiving.getFluid().isEmpty()) return;
		CompoundTag sendingTag = tagOrNull(sending.getFluid());
		if(sendingTag == null) return;
		boolean receiverHadTag = LeafiaFF.hasTag(receiving.getFluid());
		// Upstream copies the sender's entries into a fresh tag here, before the mixing below rewrites
		// the sender's; that copy is what the receiver is left holding.
		CompoundTag receivingTag = receiverHadTag ? null : sendingTag.copy();
		FluidType ntmf = LeafiaFF.fromFF(sending.getFluid().getFluid());
		if(!ntmf.equals(Fluids.NONE)) {
			if(ntmf.hasTrait(FT_LFTRCoolant.class))
				MSRTEBase.transferStats(sending.getFluid(), receiving.getFluid(), 1);
		}
		if(!receiverHadTag) LeafiaFF.setTag(receiving.getFluid(), receivingTag);
	}

	/** Upstream's {@code stack.tag}: the tag, or null if the stack has none. */
	@Nullable
	public static CompoundTag tagOrNull(FluidStack stack) {
		return !stack.isEmpty() && LeafiaFF.hasTag(stack) ? LeafiaFF.getTag(stack) : null;
	}
}
