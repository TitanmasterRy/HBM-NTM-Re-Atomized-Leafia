package com.leafia.contents.cannery.cannery;

import javax.annotation.Nullable;

import com.hbm.blocks.ModBlocks;
import com.hbm.blocks.network.FluidDuctStandard.PipeMaterial;
import com.hbm.items.ModItems;
import com.hbm.slices.Slice1;
import com.hbm.util.i18n.I18nUtil;
import com.hbm.wiaj.JarConnections;
import com.hbm.wiaj.JarScene;
import com.hbm.wiaj.JarScript;
import com.hbm.wiaj.WorldInAJar;
import com.hbm.wiaj.actions.ActionCreateActor;
import com.hbm.wiaj.actions.ActionOffsetBy;
import com.hbm.wiaj.actions.ActionRemoveActor;
import com.hbm.wiaj.actions.ActionRotateBy;
import com.hbm.wiaj.actions.ActionSetBlock;
import com.hbm.wiaj.actions.ActionSetTile;
import com.hbm.wiaj.actions.ActionSetZoom;
import com.hbm.wiaj.actions.ActionUpdateActor;
import com.hbm.wiaj.actions.ActionWait;
import com.hbm.wiaj.actions.IJarAction;
import com.hbm.wiaj.actors.ActorFancyPanel.Orientation;
import com.hbm.wiaj.actors.ActorTileEntity;
import com.hbm.wiaj.actors.ITileActorRenderer;
import com.hbm.wiaj.cannery.CanneryBase;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.LeafiaFluids;
import com.leafia.contents.cannery.CanneryActorRenders;
import com.leafia.contents.cannery.actions.ActionOffsetAndZoom;
import com.leafia.contents.cannery.actors.ActorVisFrame;
import com.leafia.contents.machines.reactors.lftr.components.control.MSRControlBlock;
import com.leafia.contents.machines.reactors.lftr.components.ejector.MSREjectorBlock;
import com.leafia.contents.machines.reactors.lftr.components.plug.MSRPlugBlock;
import com.leafia.contents.network.ff_duct.FFDuctStandard;
import com.leafia.contents.network.ff_duct.FFDuctTE;
import com.leafia.contents.network.ff_duct.uninos.IFFHandler;
import com.leafia.dev.math.FiaMatrix;
import com.leafia.init.LeafiaBlockEntities;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * The LFTR guide topic: a molten-salt reactor of reaction chambers, control rods and a fuel plug, the mixing vat that stirs fuel into
 * the salt, the special duct the salt needs, the flux every chamber receives (six sides and twelve more places), the heat limit, the
 * control rods, graphite and the PWR reflector as moderators, the arbitrary block and the fuel plug.
 *
 * <p>Upstream: {@code com/leafia/contents/cannery/cannery/CanneryLFTR.java}, scene for scene and wait for wait; every text is the
 * addon's {@code cannery.leafia.lftr.*} key. Differences of kind:
 *
 * <ul>
 * <li>A block with metadata is a block state: the fuel plug's {@code 2} is north, the ejector's {@code 3} south, a control rod's
 * metadata is its facing (down, south, east) and its extension's is an axis ({@code 0} up and down, {@code 4} east-west, {@code 8} north-south),
 * the special duct's {@code 2} is its coloured finish.</li>
 * <li>A duct's arms were worked out every time it was drawn, against the jar. A 1.21.1 duct stores them in its blockstate, which a
 * jar never refreshes, so {@link #DUCT_RESOLVER} (a {@code JarConnections} resolver) works them out against the jar when it is drawn, by
 * the duct's own rule ({@link FFDuctStandard#connect}); the stand-in machine at the pipe's end, which cannot expose a capability to a jar,
 * is one more thing the rule joins (D-3029, D-3041).</li>
 * <li>{@code ActionOffsetBy} is the base mod's own (D-2207), as upstream uses it.</li>
 * <li>The vat, the arbitrary block's overlay and the flux are actors, as upstream's; their drawing is {@link CanneryActorRenders}
 * and {@code ActorVisFrame}.</li>
 * </ul>
 */
public class CanneryLFTR extends CanneryBase {

	@Override
	public ItemStack getIcon() {
		return LeafiaBlocks.msr_element == null ? ItemStack.EMPTY : new ItemStack(LeafiaBlocks.msr_element.get());
	}

	@Override
	public String getName() {
		return "cannery.leafia.lftr";
	}

	/** {@code LFTR.control} with the facing its metadata was. */
	public static BlockState control(Direction facing) {
		return LeafiaBlocks.msr_control.get().defaultBlockState().setValue(MSRControlBlock.FACING, facing);
	}

	/** {@code LFTR.extension} with the axis its metadata was: {@code 0} is Y, {@code 4} is X, {@code 8} is Z. */
	public static BlockState extension(Direction.Axis axis) {
		return LeafiaBlocks.msr_control_extension.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
	}

	/** {@code ff_duct} at metadata 2: the coloured finish. */
	public static BlockState duct() {
		return LeafiaBlocks.ff_duct.get().defaultBlockState().setValue(FFDuctStandard.MATERIAL, PipeMaterial.COLORED);
	}

	@Override
	public JarScript createScript() {

		WorldInAJar world = new WorldInAJar(9, 12, 9);
		JarScript script = new JarScript(world);
		int center = 4;

		Block concrete = ModBlocks.concrete_smooth.get();
		Block element = LeafiaBlocks.msr_element.get();
		Block graphite = ModBlocks.block_graphite.get();
		Block arbitrary = LeafiaBlocks.msr_arbitrary.get();
		Block shielded = LeafiaBlocks.ff_duct_solid_shielded.get();
		Block reflector = LeafiaBlocks.lwr_reflector.get();
		BlockState plug = LeafiaBlocks.msr_plug.get().defaultBlockState().setValue(MSRPlugBlock.FACING, Direction.NORTH); // metadata 2

		{
			// INTRODUCTION
			JarScene scene = new JarScene(script);
			scene.add(new ActionSetZoom(1.5, 0));
			for(int x = world.sizeX - 1; x >= 0; x--) {
				for(int z = 0; z < world.sizeZ; z++)
					scene.add(new ActionSetBlock(x, 0, z, concrete));

				scene.add(new ActionWait(2));
			}
			{
				// build example LFTR
				for(int y = 1; y <= 3; y++) {
					scene.add(new ActionSetBlock(center - 1, y, center - 1, ModBlocks.deco_pipe_framed.get()));
					scene.add(new ActionSetBlock(center + 1, y, center - 1, ModBlocks.deco_pipe_framed.get()));
					scene.add(new ActionSetBlock(center - 1, y, center + 1, ModBlocks.deco_pipe_framed.get()));
					scene.add(new ActionSetBlock(center + 1, y, center + 1, ModBlocks.deco_pipe_framed.get()));
				}
				for(int rx = -1; rx <= 1; rx++) {
					for(int rz = -1; rz <= 1; rz++) {
						scene.add(new ActionSetBlock(center + rx, 4, center + rz, ModBlocks.brick_compound.get()));
						scene.add(new ActionSetBlock(center + rx, 8, center + rz, ModBlocks.brick_compound.get()));
					}
				}
				scene.add(new ActionSetBlock(center, 4, center, plug));
				scene.add(new ActionSetBlock(center, 4, center - 1, shielded));
				for(int y = 5; y <= 7; y++) {
					Block hull = (y == 6 ? ModBlocks.brick_concrete.get() : ModBlocks.brick_compound.get());
					for(int rx = -1; rx <= 1; rx++) {
						scene.add(new ActionSetBlock(center + rx, y, center - 2, hull));
						scene.add(new ActionSetBlock(center + rx, y, center + 2, hull));
					}
					for(int rz = -1; rz <= 1; rz++) {
						scene.add(new ActionSetBlock(center - 2, y, center + rz, hull));
						scene.add(new ActionSetBlock(center + 2, y, center + rz, hull));
					}
					scene.add(new ActionSetBlock(center, y, center, element));
					scene.add(new ActionSetBlock(center - 1, y, center - 1, element));
					scene.add(new ActionSetBlock(center + 1, y, center - 1, element));
					scene.add(new ActionSetBlock(center + 1, y, center + 1, element));
					scene.add(new ActionSetBlock(center - 1, y, center + 1, element));
					addArbitrary(scene, center - 1, y, center, 0);
					addArbitrary(scene, center + 1, y, center, 0);
					addArbitrary(scene, center, y, center - 1, 0);
					addArbitrary(scene, center, y, center + 1, 0);
				}
				for(int y = 8; y <= 11; y++) {
					BlockState block = (y == 8 ? control(Direction.DOWN) : extension(Direction.Axis.Y));
					scene.add(new ActionSetBlock(center, y, center, block));
					scene.add(new ActionSetBlock(center - 1, y, center - 1, block));
					scene.add(new ActionSetBlock(center + 1, y, center - 1, block));
					scene.add(new ActionSetBlock(center - 1, y, center + 1, block));
					scene.add(new ActionSetBlock(center + 1, y, center + 1, block));
				}
				for(int y = 1; y <= 3; y++)
					placePipe(scene, center, y, center);
				scene.add(new ActionSetTile(center, 0, center, generateDuctTE()));
				scene.add(new ActionSetBlock(center, 0, center, shielded));
				scene.add(new ActionSetBlock(center, 6, center, LeafiaBlocks.msr_ejector.get().defaultBlockState()
						.setValue(MSREjectorBlock.FACING, Direction.SOUTH))); // metadata 3
			}
			showText(scene, 0, -30, Orientation.BOTTOM, I18nUtil.resolveKey("cannery.leafia.lftr.0"));
			scene.add(new ActionWait(20 * 4));
			scene.add(new ActionRemoveActor(0));
			showText(scene, 0, -30, Orientation.BOTTOM, I18nUtil.resolveKey("cannery.leafia.lftr.1"));
			scene.add(new ActionWait(20 * 4));
			scene.add(new ActionRemoveActor(0));
			showText(scene, 0, -30, Orientation.BOTTOM, I18nUtil.resolveKey("cannery.leafia.lftr.2"));
			scene.add(new ActionWait(20 * 5));
			scene.add(new ActionRemoveActor(0));
			script.addScene(scene);
		}
		{
			// MIXING VAT
			JarScene scene = new JarScene(script);
			for(int y = 11; y >= 0; y--) {
				scene.add(new ActionWait(3));
				for(int x = center - 2; x <= center + 2; x++) {
					for(int z = center - 2; z <= center + 2; z++) {
						removeArbitrary(scene, x, y, z);
						scene.add(new ActionSetTile(x, y, z, null));
						scene.add(new ActionSetBlock(x, y, z, y == 0 ? concrete : Blocks.AIR));
					}
				}
			}
			scene.add(new ActionWait(20));
			scene.add(new ActionOffsetAndZoom(0, 3.5, 0, 0.5, 20));
			scene.add(new ActionWait(2));

			showText(scene, 0, 0, Orientation.CENTER, I18nUtil.resolveKey("cannery.leafia.lftr.3"));
			scene.add(new ActionWait(20 * 4));
			scene.add(new ActionRemoveActor(0));

			scene.add(new ActionWait(20));
			CompoundTag data = new CompoundTag();
			data.putDouble("x", center);
			data.putDouble("y", 1);
			data.putDouble("z", center - 1);
			data.putInt("meta", 12);
			data.putBoolean("spin", false);
			scene.add(new ActionCreateActor(1, new ActorTileEntity(new ActorMixingVat(), data)));

			scene.add(new ActionWait(10));
			showText(scene, 0, -15, Orientation.BOTTOM, I18nUtil.resolveKey("cannery.leafia.lftr.4"));
			scene.add(new ActionWait(20 * 4));
			scene.add(new ActionRemoveActor(0));

			showText(scene, 40, 9, Orientation.LEFT, new ItemStack(ModItems.nugget_th232.get(), 4), new ItemStack(ModItems.nugget_u235.get(), 2));
			scene.add(new ActionWait(30));
			scene.add(new ActionRemoveActor(0));

			scene.add(new ActionWait(10));
			scene.add(new ActionUpdateActor(1, "spin", true));
			scene.add(new ActionWait(20 * 3));
			scene.add(new ActionUpdateActor(1, "spin", false));
			scene.add(new ActionWait(20));

			showText(scene, 0, -15, Orientation.BOTTOM, I18nUtil.resolveKey("cannery.leafia.lftr.5"));
			scene.add(new ActionWait(20 * 5));
			scene.add(new ActionRemoveActor(0));

			scene.add(new ActionWait(20));
			{
				scene.add(new ActionSetTile(center - 1, 1, center + 1, new FFDummyConnector()));
				placePipe(scene, center - 2, 1, center + 1);
				placePipe(scene, center - 2, 1, center + 2);
				placePipe(scene, center - 2, 1, center + 3);
			}
			scene.add(new ActionWait(10));

			showText(scene, 0, -15, Orientation.BOTTOM, I18nUtil.resolveKey("cannery.leafia.lftr.6"));
			scene.add(new ActionWait(20 * 7));
			scene.add(new ActionRemoveActor(0));

			scene.add(new ActionWait(20));
			scene.add(new ActionRemoveActor(1));
			scene.add(new ActionSetTile(center - 1, 1, center + 1, null));
			removePipe(scene, center - 2, 1, center + 1);
			removePipe(scene, center - 2, 1, center + 2);
			removePipe(scene, center - 2, 1, center + 3);

			scene.add(new ActionWait(20));

			scene.add(new ActionOffsetBy(0, -1.5, 0, 20));
			scene.add(new ActionWait(2));
			scene.add(new ActionSetBlock(center, 3, center, element));

			scene.add(new ActionWait(10));
			for(Direction face : Direction.values()) {
				for(int i = 1; i <= 4; i++) {
					BlockPos p = new BlockPos(center, 3, center).relative(face, i);
					if(p.getY() < 1) continue;
					addFlux(scene, script, p.getX(), p.getY(), p.getZ());
				}
			}
			scene.add(new ActionWait(10));

			script.addScene(scene);
		}
		{
			// FLUX
			JarScene scene = new JarScene(script);
			scene.add(new ActionWait(10));
			showText(scene, 0, -15, Orientation.BOTTOM, I18nUtil.resolveKey("cannery.leafia.lftr.7"));
			scene.add(new ActionWait(20 * 4));
			scene.add(new ActionRemoveActor(0));

			scene.add(new ActionWait(20));
			for(Direction face : Direction.values()) {
				for(int i = 1; i <= 4; i++) {
					BlockPos p = new BlockPos(center, 3, center).relative(face, i);
					if(p.getY() < 1) continue;
					removeFlux(scene, p.getX(), p.getY(), p.getZ());
				}
			}
			scene.add(new ActionWait(20));
			FiaMatrix mat = new FiaMatrix(new Vec3(center + 0.5, 3.5, center + 0.5));
			doCornerFlux2D(scene, script, mat, false);
			doCornerFlux2D(scene, script, mat.rotateY(90), false);
			doCornerFlux2D(scene, script, mat.rotateX(90), false);

			scene.add(new ActionWait(10));
			scene.add(new ActionRotateBy(30, 0, 20));
			scene.add(new ActionWait(2));
			showText(scene, 0, -15, Orientation.BOTTOM, I18nUtil.resolveKey("cannery.leafia.lftr.8"));
			scene.add(new ActionWait(20 * 4));
			scene.add(new ActionRemoveActor(0));
			scene.add(new ActionWait(10));
			scene.add(new ActionRotateBy(-30, 0, 20));

			scene.add(new ActionWait(10));
			doCornerFlux2D(scene, script, mat, true);
			doCornerFlux2D(scene, script, mat.rotateY(90), true);
			doCornerFlux2D(scene, script, mat.rotateX(90), true);
			scene.add(new ActionWait(20));

			script.addScene(scene);
		}
		{
			// MELTDOWN
			JarScene scene = new JarScene(script);

			showText(scene, 0, -15, Orientation.BOTTOM, I18nUtil.resolveKey("cannery.leafia.lftr.9"));
			scene.add(new ActionWait(20 * 4));
			scene.add(new ActionRemoveActor(0));

			scene.add(new ActionWait(20));

			script.addScene(scene);
		}
		{
			// CONTROL RODS
			JarScene scene = new JarScene(script);
			scene.add(new ActionSetBlock(center, 3, center - 1, control(Direction.SOUTH)));
			scene.add(new ActionSetBlock(center, 3, center - 2, extension(Direction.Axis.Z))); // metadata 8
			scene.add(new ActionWait(20));
			scene.add(new ActionSetBlock(center, 3, center - 1, Blocks.AIR));
			scene.add(new ActionSetBlock(center, 3, center - 2, Blocks.AIR));
			scene.add(new ActionSetBlock(center - 1, 3, center, control(Direction.EAST)));
			scene.add(new ActionSetBlock(center - 2, 3, center, extension(Direction.Axis.X))); // metadata 4
			scene.add(new ActionWait(20));
			scene.add(new ActionSetBlock(center - 1, 3, center, Blocks.AIR));
			scene.add(new ActionSetBlock(center - 2, 3, center, Blocks.AIR));
			scene.add(new ActionSetBlock(center, 4, center, control(Direction.DOWN)));
			scene.add(new ActionSetBlock(center, 5, center, extension(Direction.Axis.Y)));
			scene.add(new ActionWait(10));

			showText(scene, 0, -32, Orientation.BOTTOM, I18nUtil.resolveKey("cannery.leafia.lftr.a"));
			scene.add(new ActionWait(20 * 5));
			scene.add(new ActionRemoveActor(0));
			showText(scene, 0, -32, Orientation.BOTTOM, I18nUtil.resolveKey("cannery.leafia.lftr.b"));
			scene.add(new ActionWait(20 * 6));
			scene.add(new ActionRemoveActor(0));

			scene.add(new ActionSetBlock(center, 4, center, Blocks.AIR));
			scene.add(new ActionSetBlock(center, 5, center, Blocks.AIR));
			scene.add(new ActionSetBlock(center, 3, center, Blocks.AIR));
			scene.add(new ActionWait(20));
			scene.add(new ActionOffsetBy(0, 1.5, 0, 20));
			scene.add(new ActionWait(20));

			script.addScene(scene);
		}
		{
			JarScene scene = new JarScene(script);
			scene.add(new ActionSetBlock(center, 1, center, graphite));
			scene.add(new ActionWait(10));

			showText(scene, 0, -5, Orientation.BOTTOM, I18nUtil.resolveKey("cannery.leafia.lftr.c"));
			scene.add(new ActionWait(20 * 5));
			scene.add(new ActionRemoveActor(0));

			scene.add(new ActionWait(20));
			scene.add(new ActionSetBlock(center, 1, center, Blocks.AIR));
			scene.add(new ActionWait(20));
			scene.add(new ActionSetBlock(center, 1, center, reflector));
			scene.add(new ActionWait(10));

			showText(scene, 0, -5, Orientation.BOTTOM, I18nUtil.resolveKey("cannery.leafia.lftr.d"));
			scene.add(new ActionWait(20 * 5));
			scene.add(new ActionRemoveActor(0));
			scene.add(new ActionSetBlock(center, 1, center, Blocks.AIR));
			scene.add(new ActionWait(20));

			script.addScene(scene);
		}
		{
			JarScene scene = new JarScene(script);

			scene.add(new ActionSetBlock(center, 1, center, arbitrary));
			scene.add(new ActionWait(10));

			showText(scene, 0, -5, Orientation.BOTTOM, I18nUtil.resolveKey("cannery.leafia.lftr.e"));
			scene.add(new ActionWait(20 * 6));
			scene.add(new ActionRemoveActor(0));
			showText(scene, 14, 18, Orientation.LEFT, new ItemStack(graphite));
			scene.add(new ActionWait(15));
			addArbitrary(scene, center, 1, center, 0);
			scene.add(new ActionWait(15));
			scene.add(new ActionRemoveActor(0));
			scene.add(new ActionWait(20));
			removeArbitrary(scene, center, 1, center);
			scene.add(new ActionWait(20));

			script.addScene(scene);
		}
		{
			JarScene scene = new JarScene(script);
			scene.add(new ActionSetBlock(center, 1, center, plug));
			scene.add(new ActionWait(10));

			showText(scene, 0, -5, Orientation.BOTTOM, I18nUtil.resolveKey("cannery.leafia.lftr.f"));
			scene.add(new ActionWait(20 * 5));
			scene.add(new ActionRemoveActor(0));
			showText(scene, 0, -5, Orientation.BOTTOM, I18nUtil.resolveKey("cannery.leafia.lftr.g"));
			scene.add(new ActionWait(20 * 5));
			scene.add(new ActionRemoveActor(0));
			scene.add(new ActionWait(20));
			scene.add(new ActionRotateBy(0, 60, 20));
			scene.add(new ActionWait(2));
			showText(scene, 0, -5, Orientation.BOTTOM, I18nUtil.resolveKey("cannery.leafia.lftr.h"));
			scene.add(new ActionWait(20 * 4));
			scene.add(new ActionRemoveActor(0));
			script.addScene(scene);
		}

		return script;
	}

	/** Upstream's {@code showText}: the copper panel, 200 wide, as actor 0, of any row of strings and stacks. */
	public void showText(JarScene scene, int x, int y, Orientation orient, Object... text) {
		scene.add(new ActionCreateActor(0, copper(metrics(), x, y, orient, 200, text)));
	}

	public void doCornerFlux2D(JarScene scene, JarScript script, FiaMatrix mat, boolean remove) {
		for(int rx = -1; rx <= 1; rx += 2) {
			for(int ry = -1; ry <= 1; ry += 2) {
				FiaMatrix offset = mat.translate(rx, ry, 0);
				if(remove)
					removeFlux(scene, (int) offset.position.x, (int) offset.position.y, (int) offset.position.z);
				else
					addFlux(scene, script, (int) offset.position.x, (int) offset.position.y, (int) offset.position.z);
			}
		}
	}

	public void addFlux(JarScene scene, JarScript script, int x, int y, int z) {
		scene.add(new ActionCreateActor(getHash(scene, x, y, z), new ActorVisFrame(script, x, y, z, 0xFFFF00, 0.25f)));
	}

	public void removeFlux(JarScene scene, int x, int y, int z) {
		scene.add(new ActionRemoveActor(getHash(scene, x, y, z)));
	}

	/** Upstream's {@code generateDuctTE}: a Forge-fluid duct's block entity carrying the salt, outside a level. */
	public FFDuctTE generateDuctTE() {
		FFDuctTE ductTE = new FFDuctTE(LeafiaBlockEntities.FF_DUCT.get(), BlockPos.ZERO, duct());
		ductTE.type = LeafiaFluids.FLUORIDE;
		return ductTE;
	}

	public void placePipe(JarScene scene, int x, int y, int z) {
		scene.add(new ActionSetTile(x, y, z, generateDuctTE()));
		scene.add(new ActionSetBlock(x, y, z, duct()));
	}

	public void removePipe(JarScene scene, int x, int y, int z) {
		scene.add(new ActionSetTile(x, y, z, null));
		scene.add(new ActionSetBlock(x, y, z, Blocks.AIR));
	}

	/** Upstream's id for the actor at a cell: the cell's index plus 300, clear of the text's small ids. */
	public int getHash(JarScene scene, int x, int y, int z) {
		int sizeX = scene.script.world.sizeX;
		int sizeY = scene.script.world.sizeY;
		int sizeZ = scene.script.world.sizeZ;
		return x + y * sizeX + z * sizeX * sizeY + 300;
	}

	public void addArbitrary(JarScene scene, int x, int y, int z, int variant) {
		int hash = getHash(scene, x, y, z);
		CompoundTag data = new CompoundTag();
		data.putDouble("x", x);
		data.putDouble("y", y);
		data.putDouble("z", z);
		data.putInt("variant", variant);
		scene.add(new ActionSetBlock(x, y, z, LeafiaBlocks.msr_arbitrary.get()));
		scene.add(new ActionCreateActor(hash, new ActorTileEntity(new ActorArbitrary(), data)));
	}

	public void removeArbitrary(JarScene scene, int x, int y, int z) {
		int hash = getHash(scene, x, y, z);
		scene.add(new ActionSetBlock(x, y, z, Blocks.AIR));
		scene.add(new ActionRemoveActor(hash));
	}

	/**
	 * Whether a duct in the jar joins what is on {@code dir}: the duct's own rule, and the stand-in machine, which is a Forge fluid
	 * handler by declaration but cannot be asked for a capability by a jar, which is no level.
	 */
	public static boolean canConnectInJar(net.minecraft.world.level.BlockGetter world, BlockPos pos, Direction dir, com.hbm.inventory.fluid.FluidType type) {
		return FFDuctStandard.canConnectTo(world, pos, dir, type) || world.getBlockEntity(pos.relative(dir)) instanceof FFDummyConnector;
	}

	/**
	 * The arms and end flags of a special duct in the jar, worked out against the jar as it is drawn. Upstream's ducts did this each
	 * time they were drawn; a 1.21.1 duct keeps the answer in its blockstate, which a jar never refreshes, so the guide asks the base
	 * mod's {@code JarConnections} (D-2207's resolver seam, which {@link AddonJars#initJars} adds this to) for the state to draw
	 * (D-3041; D-3029 had an action that rewrote the jar's blocks after each placement). Answers null for any other block, which is
	 * the seam's "not mine".
	 */
	public static final JarConnections.Resolver DUCT_RESOLVER = (world, pos, state) ->
			state.getBlock() instanceof FFDuctStandard ? FFDuctStandard.connect(state, world, pos, CanneryLFTR::canConnectInJar) : null;

	/**
	 * The machine at the end of the special pipe in the mixing vat scene: a block entity that is a Forge fluid handler with no tanks,
	 * which the pipe joins from any side. Upstream's {@code FFDummyConnector}, a tile entity answering the fluid handler capability.
	 * It has the base mod's dummy connector's type (valid for the one block, air, it is given) and no renderer.
	 */
	public static class FFDummyConnector extends BlockEntity implements IFFHandler {

		public FFDummyConnector() {
			super(Slice1.JAR_DUMMY_CONNECTOR.get(), BlockPos.ZERO, Blocks.AIR.defaultBlockState());
		}

		@Override
		public boolean canConnect(@Nullable FluidStack stack, @Nullable Direction dir) {
			return true;
		}

		@Override
		public int getTanks() {
			return 0;
		}

		@Override
		public FluidStack getFluidInTank(int tank) {
			return FluidStack.EMPTY;
		}

		@Override
		public int getTankCapacity(int tank) {
			return 0;
		}

		@Override
		public boolean isFluidValid(int tank, FluidStack stack) {
			return false;
		}

		@Override
		public int fill(FluidStack resource, FluidAction action) {
			return 0;
		}

		@Override
		public FluidStack drain(FluidStack resource, FluidAction action) {
			return FluidStack.EMPTY;
		}

		@Override
		public FluidStack drain(int maxDrain, FluidAction action) {
			return FluidStack.EMPTY;
		}
	}

	/** The older mixing vat in the jar: the blade turns 9 degrees a tick while the tag's {@code spin} is set, and starts at 115. */
	public static class ActorMixingVat implements ITileActorRenderer {

		float mixerRot = 115;
		float prevRot = mixerRot;

		@Override
		public void renderActor(WorldInAJar world, int ticks, float interp, CompoundTag data, PoseStack pose, MultiBufferSource buffers) {
			CanneryActorRenders.mixingVat(world, interp, data, prevRot, mixerRot, pose, buffers);
		}

		@Override
		public void updateActor(int ticks, CompoundTag data) {
			// a rewound scene starts the actor again at tick 0; the blade starts again with it (D-3076; upstream kept the angle, as the script creates the actor once)
			if(ticks == 0) mixerRot = 115;
			prevRot = mixerRot;
			if(data.getBoolean("spin")) {
				this.mixerRot += 9F;
				if(this.mixerRot >= 360F) {
					this.mixerRot -= 360F;
					this.prevRot -= 360F;
				}
			}
		}

		/** For the tests: the blade's angle now and a tick ago. */
		public float rotation() {
			return mixerRot;
		}

		public float previousRotation() {
			return prevRot;
		}
	}

	/** The graphite overlay on an arbitrary block in the jar: six squares of the graphite block's texture, one to a face. */
	public static class ActorArbitrary implements ITileActorRenderer {

		@Override
		public void renderActor(WorldInAJar world, int ticks, float interp, CompoundTag data, PoseStack pose, MultiBufferSource buffers) {
			CanneryActorRenders.arbitrary(world, data, pose, buffers);
		}

		@Override
		public void updateActor(int ticks, CompoundTag data) { }
	}
}
