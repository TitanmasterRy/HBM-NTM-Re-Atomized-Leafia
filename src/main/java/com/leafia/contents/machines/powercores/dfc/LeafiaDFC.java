package com.leafia.contents.machines.powercores.dfc;

import javax.annotation.Nullable;

import com.hbm.blockentity.machine.BlockEntityCoreEmitter;
import com.hbm.blockentity.machine.BlockEntityCoreReceiver;
import com.hbm.blocks.BlockEntityOverrides;
import com.hbm.blocks.ModBlocks;
import com.hbm.blocks.machine.CoreComponent;
import com.hbm.blocks.machine.CoreCore;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.items.machine.IItemFluidIdentifier;
import com.hbm.sound.NtmSounds;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.machines.powercores.dfc.components.DFCParts;
import com.leafia.contents.machines.powercores.dfc.components.absorber.LeafiaCoreReceiverTE;
import com.leafia.contents.machines.powercores.dfc.components.emitter.LeafiaCoreEmitterTE;
import com.leafia.contents.machines.powercores.dfc.components.injector.LeafiaCoreInjectorTE;
import com.leafia.contents.machines.powercores.dfc.components.stabilizer.LeafiaCoreStabilizerTE;
import com.leafia.contents.machines.powercores.dfc.core.LeafiaCoreTE;
import com.leafia.init.LeafiaBlockEntities;
import com.leafia.lib.LeafiaRef;
import com.leafia.overwrite_contents.interfaces.IMixinTileEntityCoreEmitter;
import com.leafia.overwrite_contents.interfaces.IMixinTileEntityCoreReceiver;
import com.leafia.overwrite_contents.other.LCEItemCatalyst;

import api.hbm.item.IDesignatorItem;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * How the DFC overhaul is put into the base mod: overrides on the base core's block and the four base
 * component blocks, each read at the moment a block entity is made, and the part of upstream's
 * {@code MixinCoreComponent} that is about clicking and breaking a component.
 *
 * <p>Upstream swaps the base core's and components' code for its own with mixins applied only when
 * {@code disableAddonDFC} is false ({@code MixinConfigPlugin}). Here the base mod's {@code CoreCore} and
 * {@code CoreComponent} ask {@link BlockEntityOverrides} first in {@code newBlockEntity} (master's M1 seam,
 * D-2195), and this class answers with the addon's subclass while {@link LeafiaFeatures#dfcOverhaul} is on
 * and declines otherwise, so the block makes the base mod's own. The answer is read per creation, which
 * is what lets a GameTest switch the overhaul on for one body and off again.
 *
 * <p>Two consequences worth knowing. A block entity already in a world keeps the type it was saved with,
 * so turning the overhaul on or off changes parts placed (or chunk-loaded fresh) afterwards, not parts that
 * exist; and a part saved as the addon's type in a world later loaded with the overhaul's content not
 * registered is dropped by the chunk loader as an unknown block entity, inventory with it. Both are the
 * price of a switch that changes a base block's behaviour, and the reason it defaults off.
 *
 * <h2>MixinCoreComponent, here (D-3014)</h2>
 *
 * <ul>
 * <li><b>Its block entity swap</b> is the overrides above, plus the addon's own blocks built with their own
 *     types ({@code LeafiaBlocks}); {@code AddonCoreComponent} dissolves.</li>
 * <li><b>The placement target</b> ({@code onBlockPlacedBy}: one block along the piston-rule facing) is taken
 *     by each part on its first tick, from the same {@code FACING}.</li>
 * <li><b>A designator</b> right-clicked on any part retargets it to the designator's
 *     {@code xCoord/yCoord/zCoord}, refused with a "no" when that is the part itself ({@link #useOnPart}).</li>
 * <li><b>A fluid identifier</b> naming cryogel or cold perfluoromethyl switches an overhauled emitter's or
 *     absorber's coolant, and its output tank to pyrogel or hot perfluoromethyl, with a chat line.</li>
 * <li><b>Removing a part drops its inventory</b> (upstream's {@code breakBlock}), by any cause, through M10's
 *     removal seam ({@link #onPartRemoved}; until D-3031 only a player's break and an explosion did, through events).</li>
 * <li>The tooltip and the look-at panel are client-side, in {@code LeafiaDFCClient}.</li>
 * </ul>
 *
 * Two sounds are not the same: upstream's {@code buttonYes}/{@code buttonNo} are NTM-CE's and in neither the
 * base mod nor the addon, so the "yes" is the base mod's {@code techboop} and the "no" vanilla's villager.
 */
public final class LeafiaDFC {

	private LeafiaDFC() {}

	/** Common setup: the catalysts' melting points, and the overrides when their types exist. */
	public static void commonSetup() {
		LCEItemCatalyst.registerMeltingPoints();
		if(LeafiaBlockEntities.DFC_CORE == null) return;
		BlockEntityOverrides.register(ModBlocks.dfc_core.get(), LeafiaBlockEntities.DFC_CORE,
				(pos, state) -> LeafiaFeatures.dfcOverhaul ? new LeafiaCoreTE(LeafiaBlockEntities.DFC_CORE.get(), pos, state) : null);
		BlockEntityOverrides.register(ModBlocks.dfc_emitter.get(), LeafiaBlockEntities.DFC_EMITTER,
				(pos, state) -> LeafiaFeatures.dfcOverhaul ? new LeafiaCoreEmitterTE(LeafiaBlockEntities.DFC_EMITTER.get(), pos, state) : null);
		BlockEntityOverrides.register(ModBlocks.dfc_receiver.get(), LeafiaBlockEntities.DFC_RECEIVER,
				(pos, state) -> LeafiaFeatures.dfcOverhaul ? new LeafiaCoreReceiverTE(LeafiaBlockEntities.DFC_RECEIVER.get(), pos, state) : null);
		BlockEntityOverrides.register(ModBlocks.dfc_injector.get(), LeafiaBlockEntities.DFC_INJECTOR,
				(pos, state) -> LeafiaFeatures.dfcOverhaul ? new LeafiaCoreInjectorTE(LeafiaBlockEntities.DFC_INJECTOR.get(), pos, state) : null);
		BlockEntityOverrides.register(ModBlocks.dfc_stabilizer.get(), LeafiaBlockEntities.DFC_STABILIZER,
				(pos, state) -> LeafiaFeatures.dfcOverhaul ? new LeafiaCoreStabilizerTE(LeafiaBlockEntities.DFC_STABILIZER.get(), pos, state) : null);
		CoreCore.addCollisionShapeHandler(LeafiaDFC::coreCollision);
		CoreComponent.addRemovalHandler(LeafiaDFC::onPartRemoved);
	}

	/**
	 * {@code MixinCoreCore}'s {@code getCollisionBoundingBox}: {@code NULL_AABB}, so a player walks into the core. Through
	 * M10's {@code CoreCore.addCollisionShapeHandler} (D-2202), answered while {@link LeafiaFeatures#dfcOverhaul} is on and
	 * declined (null, the base's full cube) while it is off: upstream applies it whenever its DFC mixins are, and here it
	 * changes a base block, so it rides the overhaul's switch (D-1000, D-3031). The outline a player aims at stays the cube.
	 */
	@Nullable
	public static VoxelShape coreCollision(CoreCore block, BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return LeafiaFeatures.dfcOverhaul ? Shapes.empty() : null;
	}

	/**
	 * Upstream's {@code breakBlock}: an overhauled part's inventory goes on the ground on <em>any</em> removal - a player,
	 * an explosion, a command, a piston, another mod's {@code setBlock} - through M10's {@code CoreComponent.addRemovalHandler}
	 * (D-2202, D-3031). A base part is declined, so the base keeps its own behaviour (upstream's base drops nothing).
	 */
	public static boolean onPartRemoved(CoreComponent block, BlockState state, Level level, BlockPos pos, BlockState newState, @Nullable BlockEntity be) {
		if(!isOverhauledPart(be)) return false;
		CoreComponent.dropInventory(level, pos, be);
		return true;
	}

	/** Whether this block entity is a DFC part the overhaul's click and break rules apply to. */
	public static boolean isOverhauledPart(@Nullable BlockEntity be) {
		return be instanceof IDFCBase && be.getBlockState().getBlock() instanceof CoreComponent;
	}

	/**
	 * What a right click with {@code stack} on the part at {@code pos} does, upstream's order: a designator
	 * that has a target retargets the part; an identifier naming one of the two coolants switches an
	 * overhauled emitter or absorber to it. Answers {@code null} when the click is not this rule's, and the
	 * result to cancel the click with otherwise. Acts only on the server; the client gets the same answer so
	 * it does not also run the item.
	 */
	@Nullable
	public static InteractionResult useOnPart(Level level, BlockPos pos, Player player, ItemStack stack) {
		if(stack.isEmpty() || player.isShiftKeyDown()) return null;
		BlockEntity te = level.getBlockEntity(pos);
		if(!isOverhauledPart(te)) return null;
		if(stack.getItem() instanceof IDesignatorItem && stack.has(DataComponents.CUSTOM_DATA)) {
			CompoundTag nbt = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
			BlockPos target = new BlockPos(nbt.getInt("xCoord"), nbt.getInt("yCoord"), nbt.getInt("zCoord"));
			if(level.isClientSide) return InteractionResult.SUCCESS;
			if(target.equals(pos)) {
				level.playSound(null, pos, SoundEvents.VILLAGER_NO, SoundSource.BLOCKS, 1, 1);
				return InteractionResult.SUCCESS;
			}
			((IDFCBase) te).leafia$setTargetPosition(target);
			level.playSound(null, pos, NtmSounds.ITEM_TECH_BOOP.get(), SoundSource.BLOCKS, 1, 1);
			return InteractionResult.SUCCESS;
		} else if(stack.getItem() instanceof IItemFluidIdentifier identifier) {
			FluidType fluid = identifier.getType(level, pos, stack);
			FluidType outputFluid;
			if(fluid == Fluids.CRYOGEL) outputFluid = DFCParts.pyrogel();
			else if(fluid == Fluids.PERFLUOROMETHYL_COLD) outputFluid = Fluids.PERFLUOROMETHYL_HOT;
			else return null;
			FluidTank tank;
			FluidTank output;
			if(te instanceof BlockEntityCoreEmitter emitter && te instanceof IMixinTileEntityCoreEmitter mixin) {
				tank = emitter.tank;
				output = mixin.leafia$getOutputTank();
			} else if(te instanceof BlockEntityCoreReceiver receiver && te instanceof IMixinTileEntityCoreReceiver mixin) {
				tank = receiver.tank;
				output = mixin.leafia$getOutputTank();
			} else return null;
			if(tank.getTankType() == fluid) return null;
			if(level.isClientSide) return InteractionResult.SUCCESS;
			tank.setTankType(fluid);
			output.setTankType(outputFluid);
			te.setChanged();
			player.sendSystemMessage(Component.literal("Changed type to ").withStyle(ChatFormatting.YELLOW)
					.append(Component.translatable(fluid.getConditionalName())).append(Component.literal("!")));
			return InteractionResult.SUCCESS;
		}
		return null;
	}

	@EventBusSubscriber(modid = LeafiaRef.MODID, bus = EventBusSubscriber.Bus.GAME)
	public static final class Events {

		private Events() {}

		@SubscribeEvent
		public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
			InteractionResult result = useOnPart(event.getLevel(), event.getPos(), event.getEntity(), event.getItemStack());
			if(result != null) {
				event.setCanceled(true);
				event.setCancellationResult(result);
			}
		}
	}
}
