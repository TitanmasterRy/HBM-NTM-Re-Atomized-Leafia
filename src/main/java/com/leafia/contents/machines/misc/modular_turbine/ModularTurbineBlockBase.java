package com.leafia.contents.machines.misc.modular_turbine;

import java.util.ArrayList;
import java.util.List;

import com.hbm.blocks.BlockDummyable;
import com.hbm.blocks.ILookOverlay;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.items.machine.IItemFluidIdentifier;
import com.hbm.util.BobMathUtil;
import com.hbm.util.i18n.I18nUtil;
import com.leafia.contents.machines.misc.modular_turbine.core.MTCoreBlock;
import com.leafia.contents.machines.misc.modular_turbine.core.MTCoreTE;
import com.leafia.contents.machines.misc.modular_turbine.ports.IMTPortBlock;
import com.leafia.contents.machines.misc.modular_turbine.ports.MTComponentPortTE;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.dev.machine.MachineTooltip;
import com.llib.math.SIPfx;

import api.hbm.block.IToolable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Every block of the modular turbine: the core, and each part that stacks along its shaft.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/misc/modular_turbine/ModularTurbineBlockBase.java}.
 *
 * <h2>What a part declares</h2>
 *
 * Four numbers: its {@link #size()} (3 for a 3x3 part), which sizes it {@link #canConnectTo()},
 * its {@link #weight()} — the rotor inertia it adds, in upstream's "WU" — and its
 * {@link #shaftHeight()}, how far above its own core block the shaft passes, which is how a 9x9
 * part and a 3x3 part line their shafts up. Plus a {@link #componentType()}, which decides whether
 * it belongs to a steam stage or separates two of them.
 *
 * <h2>Its base class dissolves, for the fourth time</h2>
 *
 * Upstream extends {@code AddonBlockDummyable}; this extends the base mod's {@link BlockDummyable}
 * directly, as the wind turbine and the HP boiler already do (D-1028). Upstream's
 * {@code setHardness(5)}/{@code setResistance(50)} and its machine creative tab are registration
 * arguments here, and its {@code ModularTurbines.ALL_COMPONENTS_FOR_RENDER.add(this)} is the
 * renderer's bookkeeping and lands with the renderer (T6).
 *
 * <h2>Placement keeps upstream's sneak</h2>
 *
 * {@link #setPlacedBy} is overridden rather than inherited because upstream's is not the base
 * mod's: sneaking turns the part round <em>and</em> shifts it forward by its own depth, so a part
 * can be placed facing back at the player against the block they are looking at. Everything else
 * — the refund when the space is blocked, the core-then-dummies order — is the base mod's.
 *
 * <h2>What waits for a later phase</h2>
 *
 * <ul>
 * <li>The <b>blade cursor</b>: with blades in sight upstream cancels the crosshair and draws an
 *     arrow rotated to the steam's flow. This port's {@link ILookOverlay} draws into a
 *     {@link GuiGraphics} and cannot cancel the crosshair, so it lands with the blades (T4), where
 *     there is something to point at.</li>
 * <li>{@code IPersistentNBT.onBlockPlacedBy}, which the base mod's own {@code setPlacedBy} still
 *     has as a TODO. No turbine part keeps anything when picked up, so nothing is lost.</li>
 * </ul>
 */
public abstract class ModularTurbineBlockBase extends BlockDummyable
		implements IToolable, ILookOverlay, IMTStageUpgradeContributor, IMTMachineUpgradeContributor {

	public ModularTurbineBlockBase(Properties properties) {
		super(properties);
	}

	/** How many blocks above its core block this part's shaft runs. */
	public abstract int shaftHeight();

	public enum TurbineComponentType {
		BLADES(true),
		SEPARATOR, PORT(true),
		INLINE_PORT, // special
		FLYWHEEL,
		SHAFT_ONLY,
		GENERATOR,
		;

		/**
		 * Whether the part <em>ends</em> a steam stage rather than joining one. Note the inversion:
		 * the constructor argument is "needs separation", and this is its negation — upstream's
		 * naming, kept so the assembly walk reads the same as upstream's.
		 */
		public final boolean isSeparator;

		TurbineComponentType(boolean needsSeparation) {
			this.isSeparator = !needsSeparation;
		}

		TurbineComponentType() {
			isSeparator = true;
		}
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		MachineTooltip.addBeta(tooltip);
		MachineTooltip.addMultiblock(tooltip);
		MachineTooltip.addModular(tooltip);
		if(this instanceof MTCoreBlock)
			MachineTooltip.addCore(tooltip);
		if(variant().equals("glass")) {
			for(String line : I18nUtil.resolveKey("info.turbine._tooltip.glass").split("\\$"))
				tooltip.add(Component.literal(line));
		}
		tooltip.add(Component.literal(ChatFormatting.AQUA + I18nUtil.resolveKey("info.turbine.weight", "+" + weight() + " WU")));
		super.appendHoverText(stack, context, tooltip, flag);
	}

	/** What this part is. The core answers null — it is not on the shaft it walks. */
	public abstract TurbineComponentType componentType();

	public String variant() {
		return "default";
	}

	/**
	 * Sizes this component can connect to.
	 *
	 * <p>Empty array means all components.
	 */
	public abstract int[] canConnectTo();

	public abstract int size();

	public abstract double weight();

	/**
	 * Upstream's placement, which differs from the base mod's in one respect: sneaking.
	 *
	 * <p>Upstream turns the yaw into one of four facings with {@code floor(yaw * 4 / 360 + 0.5) & 3}
	 * and maps 0, 1, 2, 3 to north, east, south, west — which is exactly
	 * {@code placer.getDirection().getOpposite()}, the base mod's own expression. Sneaking adds 180
	 * degrees, so it is {@code getDirection()} unturned, and then moves the whole part forward by
	 * {@code getDimensions()[2] - o} along the new facing.
	 */
	@Override
	public void setPlacedBy(Level world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {

		if(!(placer instanceof Player player)) return;

		safeRem = true;
		world.removeBlock(pos, false);
		safeRem = false;

		boolean sneaking = player.isShiftKeyDown();
		Direction facing = placer.getDirection();
		Direction dir = getDirModified(sneaking ? facing : facing.getOpposite());

		int o = -getOffset();
		BlockPos placeAt = pos.above(getHeightOffset());

		if(sneaking)
			placeAt = placeAt.relative(dir, getDimensions()[2] - o);

		BlockPos corePos = placeAt.relative(dir, o);

		if(!checkRequirement(world, placeAt, dir, o)) {
			if(!player.isCreative()) {
				ItemStack refund = new ItemStack(this);
				if(!player.getInventory().add(refund)) {
					player.drop(refund, false);
				}
			}
			return;
		}

		if(!world.isClientSide) {
			world.setBlock(corePos, getStateForCore(world, corePos, player,
					defaultBlockState().setValue(PART, Part.CORE).setValue(DIR, dir)), Block.UPDATE_ALL);
			fillSpace(world, placeAt, dir, o);
		}
	}

	@OnlyIn(Dist.CLIENT)
	void addCoreInfo(MTCoreTE c, List<String> texts) {
		if(c.turbulence > 20) {
			texts.add("&[" + (BobMathUtil.getBlink() ? 0xff0000 : 0xffff00) + "&]" + I18nUtil.resolveKey("info.turbine.turbulence.warning"));
			if(c.turbulenceReasonInputSurge || c.turbulenceReasonInverseBlades || c.turbulenceReasonTooManyBlades) {
				texts.add(ChatFormatting.GOLD + I18nUtil.resolveKey("info.turbine.turbulence.reasons"));
				if(c.turbulenceReasonInputSurge)
					texts.add(ChatFormatting.GOLD + "- " + I18nUtil.resolveKey("info.turbine.turbulence.reason.surge"));
				if(c.turbulenceReasonInverseBlades)
					texts.add(ChatFormatting.GOLD + "- " + I18nUtil.resolveKey("info.turbine.turbulence.reason.wrongblades"));
				if(c.turbulenceReasonTooManyBlades)
					texts.add(ChatFormatting.GOLD + "- " + I18nUtil.resolveKey("info.turbine.turbulence.reason.toomanyblades"));
			}
		}
		texts.add(I18nUtil.resolveKey("info.turbine.rps", String.format("%01.2f", c.rps)));
		texts.add(ChatFormatting.GRAY + "- " + I18nUtil.resolveKey("info.turbine.overdrive", "+" + MTCoreTE.getRPSBoost(c.overdrive)));
		if(c.overdrive > 0)
			texts.add(ChatFormatting.GRAY + "- " + I18nUtil.resolveKey("info.turbine.downgrade"));
		texts.add(I18nUtil.resolveKey("info.turbine.weight", String.format("%01.2f WU", c.weight)));
		texts.add(I18nUtil.resolveKey("info.turbine.turbulence", String.format("%01.2f%%", c.turbulence)));
	}

	/**
	 * The panel beside the crosshair: the shaft's speed, turbulence and why, its weight, and on a
	 * port which steam it takes and how full the stage's two buffers are.
	 *
	 * <p>Upstream's blade cursor is not drawn here yet — see the class doc.
	 */
	@Override
	@OnlyIn(Dist.CLIENT)
	public void printHook(GuiGraphics gui, Level world, BlockPos pos) {
		List<String> texts = new ArrayList<>();
		BlockPos core = findCore(world, pos);
		if(core == null) return;

		if(world.getBlockEntity(core) instanceof MTCoreTE te) {
			if(te.components.isEmpty())
				texts.add("&[" + (BobMathUtil.getBlink() ? 0xff0000 : 0xffff00) + "&]" + I18nUtil.resolveKey("info.turbine.assembly.unassembled"));
			else
				addCoreInfo(te, texts);
		} else if(world.getBlockEntity(core) instanceof ModularTurbineComponentTE te) {
			MTCoreTE c = te.core;
			if(c == null)
				texts.add("&[" + (BobMathUtil.getBlink() ? 0xff0000 : 0xffff00) + "&]" + I18nUtil.resolveKey("info.turbine.assembly.unassembled"));
			else
				addCoreInfo(c, texts);
			if(te instanceof MTComponentPortTE port) {
				texts.add(I18nUtil.resolveKey("info.turbine.identifier", port.identifier != null ? port.identifier.getLocalizedName() : "N/A"));
				if(!port.decompress)
					texts.add(I18nUtil.resolveKey("info.turbine.decompression", I18nUtil.resolveKey(port.decompress ? "info.turbine.state.enabled" : "info.turbine.state.disabled")));
				if(te.assembly != null) {
					texts.add(ChatFormatting.GREEN + "-> " + ChatFormatting.RESET + te.assembly.input.getTankType().getLocalizedName() + ": " + te.assembly.input.getFill() + "/" + te.assembly.input.getMaxFill() + "mB");
					texts.add(ChatFormatting.RED + "<- " + ChatFormatting.RESET + te.assembly.output.getTankType().getLocalizedName() + ": " + te.assembly.output.getFill() + "/" + te.assembly.output.getMaxFill() + "mB");
				}
			}
			if(te.core != null)
				texts.add(ChatFormatting.RED + "-> " + ChatFormatting.RESET + SIPfx.auto(te.core.displayPowerGenerated) + "HE");
		}

		ILookOverlay.printGeneric(gui, I18nUtil.resolveKey(getDescriptionId()), 0xFF55FF, 0x3F153F, texts);
	}

	/**
	 * A port takes a fluid identifier: point one at a steam the turbine knows and the port's
	 * stage takes that steam. Anything else falls through.
	 *
	 * <p>Upstream's {@code onBlockActivated}. Changing the identifier disassembles the turbine,
	 * because the stage's tanks are sized for the steam they were built for.
	 */
	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level world, BlockPos pos,
			Player player, InteractionHand hand, BlockHitResult hit) {
		if(this instanceof IMTPortBlock) {
			BlockPos core = findCore(world, pos);
			if(core != null && world.getBlockEntity(core) instanceof MTComponentPortTE port) {
				if(stack.getItem() instanceof IItemFluidIdentifier identifier) {
					FluidType type = identifier.getType(world, core, stack);
					if(MTCoreTE.steamTypes.contains(type)) {
						if(port.identifier == null || !port.identifier.equals(type)) {
							if(!world.isClientSide) {
								port.identifier = type;
								LeafiaPacket._start(port)
										.__write(MTPacketId.PORT_IDENTIFIER.id, port.identifier.getName())
										.__sendToAffectedClients();
							}
							if(!world.isClientSide && port.core != null)
								port.core.disassemble();
							return ItemInteractionResult.sidedSuccess(world.isClientSide);
						}
					}
				}
			}
		}
		return super.useItemOn(stack, state, world, pos, player, hand, hit);
	}

	/**
	 * Sneak with a tool on a port clears its identifier. Upstream does not check which tool, so
	 * neither does this — a blowtorch clears it as well as a screwdriver would.
	 *
	 * <p>The un-sneaked branch toggled decompression and is commented out upstream, with the
	 * comment "why would you want to disable decompression". Left out the same way.
	 */
	@Override
	public boolean onScrew(Level world, Player player, BlockPos pos, Direction side, Vec3 hit, ToolType tool) {
		if(this instanceof IMTPortBlock) {
			BlockPos core = findCore(world, pos);
			if(core != null && world.getBlockEntity(core) instanceof MTComponentPortTE port) {
				boolean changed = false;
				if(player != null && player.isShiftKeyDown()) {
					if(port.identifier != null) {
						changed = true;
						if(!world.isClientSide) {
							port.identifier = null;
							LeafiaPacket._start(port)
									.__write(MTPacketId.PORT_IDENTIFIER.id, null)
									.__sendToAffectedClients();
						}
					}
				}
				if(changed) {
					if(!world.isClientSide && port.core != null)
						port.core.disassemble();
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * A turbine part placed against this one disassembles the turbine, so the next click on the
	 * core re-walks the shaft. On the client it only tells the renderer to look for blades again.
	 */
	@Override
	protected void neighborChanged(BlockState state, Level world, BlockPos pos, Block blockIn, BlockPos fromPos, boolean movedByPiston) {
		super.neighborChanged(state, world, pos, blockIn, fromPos, movedByPiston);
		BlockPos core = findCore(world, pos);
		if(core != null && world.getBlockEntity(core) instanceof ModularTurbineComponentTE te) {
			if(world.isClientSide)
				te.local$firstRender = true;
			else {
				if(world.getBlockState(fromPos).getBlock() instanceof ModularTurbineBlockBase) {
					if(te.core != null)
						te.core.disassemble();
				}
			}
		}
	}

}
