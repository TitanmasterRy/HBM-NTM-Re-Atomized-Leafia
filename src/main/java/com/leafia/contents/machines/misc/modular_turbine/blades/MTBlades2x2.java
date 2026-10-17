package com.leafia.contents.machines.misc.modular_turbine.blades;

import com.hbm.blockentity.BlockEntityProxyCombo;
import com.hbm.handler.MultiblockHandlerXR;
import com.leafia.contents.machines.misc.modular_turbine.ModularTurbineBlockBase;
import com.leafia.contents.machines.misc.modular_turbine.ModularTurbineComponentTE;
import com.leafia.init.LeafiaBlockEntities;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * `modular_turbine_2x2_blades` and its glass, smooth and glass-smooth looks: the 2x2 rotor
 * stage that steam actually turns.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/misc/modular_turbine/blades/MTBlades2x2.java}.
 * <b>Generated</b> by a script, which re-reads every number
 * below from upstream before writing - edit the table there, not this file.
 *
 * <p>Shaft 1 up from the core block, weight 5, joins only its own size.
 * 1 narrower cap row on top round the disc off.
 * Drawn by the component renderer (T6); until then it is invisible and hidden from the creative tab.
 */
public class MTBlades2x2 extends ModularTurbineBlockBase {

	/** One class, four looks: the variant is a constructor argument, so the codec carries it. */
	public static final MapCodec<MTBlades2x2> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Codec.STRING.fieldOf("variant").forGetter(MTBlades2x2::variant),
			propertiesCodec()
	).apply(i, (variant, properties) -> new MTBlades2x2(properties, variant)));

	private final String variant;

	public MTBlades2x2(Properties properties, String variant) {
		super(properties);
		this.variant = variant;
	}

	@Override
	protected MapCodec<? extends MTBlades2x2> codec() {
		return CODEC;
	}

	/** default, glass, smooth or glass_smooth. Only the renderer's texture and one tooltip read it. */
	@Override
	public String variant() {
		return variant;
	}

	@Override
	public int shaftHeight() {
		return 1;
	}

	@Override
	public TurbineComponentType componentType() {
		return TurbineComponentType.BLADES;
	}

	@Override
	public int[] canConnectTo() {
		return new int[] { 2 };
	}

	@Override
	public int size() {
		return 2;
	}

	@Override
	public double weight() {
		return 5;
	}

	@Override
	public int[] getDimensions() {
		return new int[] { 1, 0, 0, 0, 1, 1 };
	}

	@Override
	public int getOffset() {
		return 0;
	}

	/** Upstream's {@code meta >= 12} is the core and {@code meta >= extra} a fluid proxy. */
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return switch(state.getValue(PART)) {
			case CORE -> new ModularTurbineComponentTE(LeafiaBlockEntities.MODULAR_TURBINE_COMPONENT.get(), pos, state);
			case EXTRA -> new BlockEntityProxyCombo(LeafiaBlockEntities.MODULAR_TURBINE_PROXY.get(), pos, state).fluid();
			case DUMMY -> null;
		};
	}

	/** The narrower rows that round the disc off, in upstream's fill order. */
	public static final int[][] CAPS = { { 2, -2, 0, 0, 0, 0 } };

	@Override
	protected void fillSpace(Level world, BlockPos pos, Direction dir, int o) {
		super.fillSpace(world, pos, dir, o);
		for(int[] cap : CAPS)
			MultiblockHandlerXR.fillSpace(world, pos.relative(dir, o), cap, this, dir);
	}

	/** Room for every cap row as well as the disc, all measured from the core as upstream does. */
	@Override
	protected boolean checkRequirement(Level world, BlockPos pos, Direction dir, int o) {
		BlockPos core = pos.relative(dir, o);
		for(int[] cap : CAPS) {
			if(!MultiblockHandlerXR.checkSpace(world, core, cap, core, dir))
				return false;
		}
		return MultiblockHandlerXR.checkSpace(world, core, getDimensions(), core, dir);
	}
}
