package com.leafia.contents.machines.reactors.rbmk.columns.realersim;

import com.hbm.blockentity.machine.rbmk.BlockEntityRBMKConsole;
import com.hbm.blockentity.machine.rbmk.BlockEntityRBMKRod;
import com.hbm.handler.neutron.NeutronNodeWorld;
import com.hbm.handler.neutron.NeutronNodeWorld.StreamWorld;
import com.hbm.handler.neutron.RBMKNeutronHandler;
import com.hbm.handler.neutron.RBMKNeutronHandler.RBMKNeutronNode;
import com.leafia.init.LeafiaGameRules;
import com.leafia.init.LeafiaBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The RealerSim fuel channel: a ReaSim channel whose neutrons go somewhere random, and bend.
 *
 * <p>Upstream: {@code com.leafia.contents.machines.reactors.rbmk.columns.realersim.RBMKRealerSimTE},
 * which extends NTM-CE's {@code TileEntityRBMKRodReaSim} and overrides one method,
 * {@link #spreadFlux}. Everything else — the fuel slot, the burn, the heat, the meltdown — is the
 * mainline channel's, so this extends {@link BlockEntityRBMKRod} through the constructor that
 * takes a type, exactly as the base mod's own ReaSim channel does. The two things CE's ReaSim
 * class adds besides {@code spreadFlux} are carried here: a console sees {@code FUEL_SIM}, and the
 * GUI title is the block's name (upstream's {@code getName}, {@code tile.rbmk_rod_realersim.name}).
 *
 * <h2>What a tick of flux does</h2>
 *
 * <p>The base mod's ReaSim throws eight streams on a fixed star, 0.75 of the outflux each.
 * RealerSim throws {@code dialReasimCount} streams — <b>6</b> unless a world says otherwise,
 * clamped to 1..24 — each at 0.75 of the outflux, each in a direction drawn uniformly from the
 * full circle, and each a {@link RealerSimNeutronStream}, which travels twice as far and is
 * pulled toward absorbers and control rods. So the same fuel puts 4.5 flux-units into the
 * lattice per tick where ReaSim puts 6.0 and the mainline channel 4.0, scattered rather than
 * aimed.
 *
 * <p>The dial is NTM-CE's, read from the vendored 1.5.1.8 jar: {@code KEY_REASIM_COUNT
 * ("dialReasimCount", 6)}, read through a parse-or-default and clamped to [1, 24]. Neither
 * {@code hbm-src} nor this port's base mod has it, so the addon registers it — see
 * {@link LeafiaGameRules}.
 *
 * <p>Upstream builds each stream on a <em>fresh</em> {@code makeNode} call rather than on the
 * {@code node} it has just cached, which returns the cached node anyway once one exists. Kept.
 */
public class RBMKRealerSimTE extends BlockEntityRBMKRod {

	public RBMKRealerSimTE(BlockPos pos, BlockState state) {
		super(LeafiaBlockEntities.RBMK_REALERSIM.get(), pos, state);
	}

	@Override
	public String getName() {
		return getBlockState().getBlock().getDescriptionId();
	}

	/** Upstream's {@code getReaSimCount}: the dial, clamped to 1..24. */
	public static int getReaSimCount(Level world) {
		return Mth.clamp(world.getGameRules().getInt(LeafiaGameRules.REASIM_COUNT), 1, 24);
	}

	@Override
	public void spreadFlux(double flux, double ratio) {

		BlockPos pos = this.worldPosition;

		if(flux == 0) {
			// simple way to remove the node from the cache when no flux is going into it!
			NeutronNodeWorld.removeNode(level, pos);
			return;
		}

		StreamWorld streamWorld = NeutronNodeWorld.getOrAddWorld(level);
		RBMKNeutronNode node = (RBMKNeutronNode) streamWorld.getNode(pos);

		if(node == null) {
			node = RBMKNeutronHandler.makeNode(streamWorld, this);
			streamWorld.addNode(node);
		}

		int count = getReaSimCount(level);

		for(int i = 0; i < count; i++) {
			// CE's MutableVec3d.rotateYawSelf(float) on (1, 0, 0): (cos a, 0, -sin a), through
			// the float overload and so MathHelper's sine table, which Mth still is
			float yaw = (float) (Math.PI * 2D * level.random.nextDouble());
			Vec3 neutronVector = new Vec3(Mth.cos(yaw), 0, -Mth.sin(yaw));

			new RealerSimNeutronStream(RBMKNeutronHandler.makeNode(streamWorld, this), neutronVector, flux * 0.75, ratio);
		}
	}

	@Override
	public BlockEntityRBMKConsole.ColumnType getConsoleType() {
		return BlockEntityRBMKConsole.ColumnType.FUEL_SIM;
	}
}
