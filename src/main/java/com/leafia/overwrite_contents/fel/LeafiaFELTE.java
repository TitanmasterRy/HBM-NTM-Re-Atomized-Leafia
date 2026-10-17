package com.leafia.overwrite_contents.fel;

import java.util.List;

import com.hbm.blockentity.machine.BlockEntityFEL;
import com.hbm.blockentity.machine.BlockEntitySILEX;
import com.hbm.blocks.ModBlocks;
import com.hbm.handler.radiation.ChunkRadiationManager;
import com.hbm.items.machine.ItemFELCrystal;
import com.hbm.items.machine.ItemFELCrystal.EnumWavelengths;
import com.hbm.lib.Library;
import com.hbm.util.ContaminationUtil;
import com.hbm.util.ContaminationUtil.ContaminationType;
import com.hbm.util.ContaminationUtil.HazardType;
import com.leafia.dev.optimization.LeafiaParticlePacket.DFCBlastParticle;
import com.leafia.init.LeafiaBlockEntities;
import com.leafia.overwrite_contents.interfaces.IMixinTileEntityCore;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The free-electron laser overhaul, upstream's {@code MixinTileEntityFEL} ("fuck up the dfc exploit"): an
 * {@code @Overwrite} of the laser's tick that reprices the wavelengths, gives each its own way of burning through
 * blocks, and turns a beam into a DFC core into fuel for it rather than a hole in it.
 *
 * <p>A subclass the base {@code MachineFEL} makes through {@code BlockEntityOverrides} while
 * {@code overhauls.felOverhaul} is on (D-1002, D-1097: a rebalance, off). The client half is the base mod's, which
 * upstream's overwrite repeats unchanged (the hum's ramp still reads {@code 1250 x 2^n}).
 *
 * <h2>Against the base laser (hbm-src)</h2>
 * <ul>
 * <li><b>Cost</b> {@code 1250 x 4^n} a tick (IR 5,000, visible 20,000, UV 80,000, gamma 320,000, DRX 1,280,000)
 *     instead of {@code 3^n}. Short of it, the buffer is emptied <em>and the wavelength drops to nothing</em> for that
 *     tick.</li>
 * <li><b>What it does to the living</b>, with no fall-through: IR nothing, visible blinds (no burning), UV ten
 *     seconds of fire, gamma 25 RAD, DRX 0.1 digamma.</li>
 * <li><b>The beam</b> walks blocks 3 to 23 as the base does and passes what is not opaque. A SILEX is lit or broken
 *     as the base does. Any other block stops it at that distance, by wavelength and by the block's blast
 *     resistance: IR always; visible under 10, one tick in forty, sets it alight (vanilla fire) and passes, else
 *     stops; UV under 100, one in twenty; gamma under 3,000, one in five, and leaves balefire; DRX always passes,
 *     leaving digamma fire on digamma ash. Liquids get no special case (the base laser boils them).</li>
 * <li><b>A DFC core</b> always stops the beam and, when it is the addon's overhauled core, gains
 *     {@code cost / 5000} incoming SPK and {@code 0.4 x} 15 (IR), 7 (visible), 8.5 (UV), 32.5 (gamma - with a green
 *     blast particle and 5 RAD into the chunk, to 50) or 75.5 (DRX - with a red blast and 15 digamma radiating 64
 *     blocks) of heat. A base core, which has neither, simply stops it.</li>
 * </ul>
 */
public class LeafiaFELTE extends BlockEntityFEL {

	public static final int RANGE = 24;
	public static final double DFC_SCALE = 0.4;

	public LeafiaFELTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state, 2);
	}

	public LeafiaFELTE(BlockPos pos, BlockState state) {
		this(LeafiaBlockEntities.FEL.get(), pos, state);
	}

	/** {@code 1250 x 4^n}, nothing without a crystal. */
	@Override
	public int getPowerCost(EnumWavelengths mode) {
		return (int) (POWER_REQ * ((mode.ordinal() == 0) ? 0 : Math.pow(4, mode.ordinal())));
	}

	/** Upstream's switch, every case its own. */
	@Override
	public void hitEntity(LivingEntity entity, EnumWavelengths mode) {
		switch(mode) {
			case IR -> { }
			case VISIBLE -> entity.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60 * 60 * 65536, 0));
			case UV -> entity.igniteForSeconds(10);
			case GAMMA -> ContaminationUtil.contaminate(entity, HazardType.RADIATION, ContaminationType.CREATIVE, 25);
			case DRX -> ContaminationUtil.applyDigammaData(entity, 0.1F);
			default -> { }
		}
	}

	/** The heat a wavelength gives a core, before {@link #DFC_SCALE}; 0 for none. */
	public static double coreHeat(EnumWavelengths mode) {
		return switch(mode) {
			case IR -> 15;
			case VISIBLE -> 7;
			case UV -> 8.5;
			case GAMMA -> 32.5;
			case DRX -> 75.5;
			default -> 0;
		};
	}

	@Override
	public void updateEntity() {
		if(level.isClientSide) {
			super.updateEntity();
			return;
		}

		Direction dir = facing();
		this.autoPort(getPorts());
		this.power = Library.chargeTEFromItems(slots, 0, power, getMaxPower());

		if(this.isOn && !slots[1].isEmpty()) {
			if(slots[1].getItem() instanceof ItemFELCrystal crystal) {
				this.mode = crystal.wavelength;
			} else { this.mode = EnumWavelengths.NULL; }
		} else { this.mode = EnumWavelengths.NULL; }

		int range = RANGE;
		boolean silexSpacing = false;
		int x0 = worldPosition.getX(), y0 = worldPosition.getY(), z0 = worldPosition.getZ();
		if(this.isOn && this.mode != EnumWavelengths.NULL) {
			if(this.power < getPowerCost(mode)) {
				this.mode = EnumWavelengths.NULL;
				this.power = 0;
			} else {
				int distance = this.distance - 1;
				double blx = Math.min(x0, x0 + (double) dir.getStepX() * distance) + 0.2;
				double bux = Math.max(x0, x0 + (double) dir.getStepX() * distance) + 0.8;
				double bly = Math.min(y0, 1 + y0 + (double) dir.getStepY() * distance) + 0.2;
				double buy = Math.max(y0, 1 + y0 + (double) dir.getStepY() * distance) + 0.8;
				double blz = Math.min(z0, z0 + (double) dir.getStepZ() * distance) + 0.2;
				double buz = Math.max(z0, z0 + (double) dir.getStepZ() * distance) + 0.8;

				List<LivingEntity> list = level.getEntitiesOfClass(LivingEntity.class, new AABB(blx, bly, blz, bux, buy, buz));
				for(LivingEntity entity : list)
					hitEntity(entity, this.mode);

				this.power -= getPowerCost(mode);
				for(int i = 3; i < range; i++) {
					int x = x0 + dir.getStepX() * i;
					int y = y0 + 1;
					int z = z0 + dir.getStepZ() * i;
					BlockPos pos = new BlockPos(x, y, z);
					BlockState b = level.getBlockState(pos);

					if(!isOpaque(b)) {
						this.distance = range;
						silexSpacing = false;
						continue;
					}

					if(b.getBlock() == ModBlocks.machine_silex.get()) {
						BlockPos silexPos = new BlockPos(x + dir.getStepX(), y0, z + dir.getStepZ());
						if(level.getBlockEntity(silexPos) instanceof BlockEntitySILEX silex) {
							if(rotationIsValid(silex.getBlockState().getValue(com.hbm.blocks.BlockDummyable.DIR), dir) && i >= 5 && !silexSpacing) {
								if(silex.mode != this.mode) {
									silex.mode = this.mode;
									this.missingValidSilex = false;
									silexSpacing = true;
								}
							} else {
								level.removeBlock(silexPos, false);
								level.addFreshEntity(new ItemEntity(level, x + 0.5, y + 0.5, z + 0.5, new ItemStack(ModBlocks.machine_silex.get())));
							}
						}
					} else if(!b.isAir()) {
						this.distance = i;
						if(hitBlock(pos, b))
							break;
					}
				}
			}
		}

		this.networkPackNT(250);
	}

	/** One block in the beam's way, past the transparent ones and the SILEX; whether it stops the beam. */
	public boolean hitBlock(BlockPos pos, BlockState b) {
		double x = pos.getX(), y = pos.getY(), z = pos.getZ();
		float hardness = b.getBlock().getExplosionResistance();
		boolean blocked = false;
		if(b.getBlock() != ModBlocks.dfc_core.get()) {
			switch(this.mode) {
				case IR -> blocked = true; // opaque, or glass, which never reaches here
				case VISIBLE -> {
					if(hardness < 10 && level.random.nextInt(40) == 0) {
						burn(pos, Blocks.FIRE.defaultBlockState());
					} else blocked = true;
				}
				case UV -> {
					if(hardness < 100 && level.random.nextInt(20) == 0) {
						burn(pos, Blocks.FIRE.defaultBlockState());
					} else blocked = true;
				}
				case GAMMA -> {
					if(hardness < 3000 && level.random.nextInt(5) == 0) {
						burn(pos, ModBlocks.balefire.get().defaultBlockState());
					} else blocked = true;
				}
				case DRX -> {
					// upstream's polaroidID == 11 digamma matter is not ported, as elsewhere
					burn(pos, ModBlocks.fire_digamma.get().defaultBlockState());
					level.setBlock(pos.below(), ModBlocks.ash_digamma.get().defaultBlockState(), Block.UPDATE_ALL);
				}
				default -> { }
			}
		} else {
			if(level.getBlockEntity(pos) instanceof IMixinTileEntityCore core) {
				double power = getPowerCost(mode) / 5000d;
				core.setDFCIncomingSpk(core.getDFCIncomingSpk() + power);
				core.setDFCTemperature(core.getDFCTemperature() + coreHeat(mode) * DFC_SCALE);
				if(level instanceof ServerLevel server) {
					Vec3 centre = new Vec3(x + 0.5, y + 0.5, z + 0.5);
					if(mode == EnumWavelengths.GAMMA) {
						new DFCBlastParticle(0.4f, 1f, 0.2f, 5).emit(server, centre, new Vec3(0, 1, 0), 200);
						ChunkRadiationManager.incrementRad(level, pos, 5, 50);
					} else if(mode == EnumWavelengths.DRX) {
						new DFCBlastParticle(1, 0.2f, 0.2f, 10).emit(server, centre, new Vec3(0, 1, 0), 200);
						ContaminationUtil.radiate(level, x + 0.5, y + 0.5, z + 0.5, 64, 0, 15);
					}
				}
			}
			blocked = true;
		}
		return blocked;
	}

	private void burn(BlockPos pos, BlockState with) {
		level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 1.0F);
		level.setBlock(pos, with, Block.UPDATE_ALL);
	}
}
