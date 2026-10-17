package com.leafia.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.hbm.blocks.ModBlocks;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemCatalyst;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.machines.powercores.dfc.DFCCEStats;
import com.leafia.contents.machines.powercores.dfc.LCEItemLens;
import com.leafia.contents.machines.powercores.dfc.components.pulser.CoreDetonatorScreen;
import com.leafia.contents.machines.powercores.dfc.components.pulser.CoreDetonatorTE;
import com.leafia.contents.machines.powercores.dfc.core.LeafiaCoreTE;
import com.leafia.contents.machines.powercores.dfc.particles.ParticleEyeOfHarmony;
import com.leafia.contents.machines.powercores.dfc.particles.ParticlePulserCharge;
import com.leafia.contents.machines.powercores.dfc.particles.ParticlePulserOvercharge;
import com.leafia.dev.machine.MachineTooltip;
import com.leafia.lib.LeafiaRef;
import com.leafia.overwrite_contents.interfaces.IMixinTileEntityCore;
import com.leafia.overwrite_contents.other.LCEItemCatalyst;
import com.llib.math.LeafiaColor;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.IItemDecorator;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * The client half of the addon's Dark Fusion Core: the per-tick work the base blocks do not tick on a
 * client, the bodies of the two packets, and the three things upstream's mixins did to base items.
 *
 * <p>Every method that names a client class is here, and the server-side classes call them by name
 * only from paths a client runs (D-3000's rule), so a dedicated server never loads this class.
 *
 * <h2>The client ticker</h2>
 *
 * The base mod's {@code CoreCore} and {@code CoreComponent} return no ticker on a client - the base
 * core's client tick is upstream's empty {@code //TODO: sick particle effects}. The addon's core and
 * detonator do have client work (sounds, particles, the ring and dial animations, the shocks' lifetime),
 * so each registers itself here in {@code onLoad} and is ticked at the end of every unpaused client
 * tick until it is removed. While a single-player game is paused, every collapsing core's wall clock is
 * moved forward instead, which is upstream's {@code LeafiaPassiveLocal.onTick}.
 *
 * <h2>The base items</h2>
 *
 * Upstream's {@code MixinItemCatalyst} overwrites the catalysts' tooltip (CE's modifiers and the melting
 * point) and gives them a durability bar for their melt; {@code MixinCoreCore} adds three machine badges
 * to the core's tooltip; {@code MixinModItems} makes the base lens an {@code LCEItemLens}. Here those are
 * a tooltip listener and an item decorator, active only while {@code overhauls.dfcOverhaul} is on - the
 * mixins were only applied with it on upstream ({@code MixinConfigPlugin}).
 */
@OnlyIn(Dist.CLIENT)
public final class LeafiaDFCClient {

	private LeafiaDFCClient() {}

	private static final Set<BlockEntity> TICKED = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

	/** Ticks this block entity on the client from now until it is removed. */
	public static void track(BlockEntity be) {
		synchronized(TICKED) {
			TICKED.add(be);
		}
	}

	static void tickAll() {
		Minecraft mc = Minecraft.getInstance();
		if(mc.isPaused()) {
			long now = System.currentTimeMillis();
			for(LeafiaCoreTE core : LeafiaCoreTE.SINGLEPLAYER_CORES) core.setDFCExplosionClock(now);
		}
		ClientLevel level = mc.level;
		if(level == null || mc.isPaused()) return;
		List<BlockEntity> copy;
		synchronized(TICKED) {
			TICKED.removeIf(be -> be.isRemoved() || be.getLevel() != level);
			copy = new ArrayList<>(TICKED);
		}
		for(BlockEntity be : copy) {
			if(be instanceof LeafiaCoreTE core) core.updateEntity();
			else if(be instanceof CoreDetonatorTE pulser) pulser.updateEntity();
			else if(be instanceof com.leafia.contents.machines.powercores.dfc.IDFCClientTicked part) part.leafia$clientTick();
		}
	}

	/** {@code DFCShockPacket}'s body: hand the path to the core for the renderer. */
	public static void addShock(BlockPos pos, List<Vec3> poses) {
		ClientLevel level = Minecraft.getInstance().level;
		if(level == null) return;
		if(level.getBlockEntity(pos) instanceof IMixinTileEntityCore core)
			core.getDfcShocks().add(new IMixinTileEntityCore.DFCShock(poses));
	}

	/** {@code LCEExplosionNT}'s client pass: the centre burst and, unless a landslide, up to 3,000 puffs. */
	public static void explosionParticles(double x, double y, double z, float size, int affected, boolean noParticle, boolean dfcFall) {
		ClientLevel level = Minecraft.getInstance().level;
		if(level == null || noParticle) return;
		if(size >= 2.0F)
			level.addParticle(ParticleTypes.EXPLOSION_EMITTER, x, y, z, 1.0D, 0.0D, 0.0D);
		else
			level.addParticle(ParticleTypes.EXPLOSION, x, y, z, 1.0D, 0.0D, 0.0D);
		if(dfcFall) return;
		for(int l = 0; l < Math.min(3000, affected); ++l) { //lag :/
			Vec3 v = new Vec3(level.random.nextFloat() - 0.5, level.random.nextFloat() - 0.5, level.random.nextFloat() - 0.5)
					.normalize().scale(size * level.random.nextFloat());
			double d0 = x + v.x;
			double d1 = y + v.y;
			double d2 = z + v.z;
			double d3 = v.x;
			double d4 = v.y;
			double d5 = v.z;
			double d6 = Math.sqrt(d3 * d3 + d4 * d4 + d5 * d5);
			d3 /= d6;
			d4 /= d6;
			d5 /= d6;
			double d7 = 0.5D / (d6 / (double) size + 0.1D);
			d7 *= (double) (level.random.nextFloat() * level.random.nextFloat() + 0.3F);
			d3 *= d7;
			d4 *= d7;
			d5 *= d7;
			if(level.isEmptyBlock(BlockPos.containing(d0, d1, d2))) {
				level.addParticle(ParticleTypes.CLOUD, (d0 + x) / 2.0D, (d1 + y) / 2.0D, (d2 + z) / 2.0D, d3, d4, d5);
				level.addParticle(ParticleTypes.SMOKE, d0, d1, d2, d3, d4, d5);
			}
		}
	}

	/**
	 * A collapse past two thirds pulls the local player too, and throws one vacuum particle a tick: on a
	 * random heading (yaw then pitch, each {@code rand x 360}) up to the pull range out, drawn into an
	 * {@code EntityNukeFolkvangr.VacuumInstance(centre, 0, 10, range, 0.1)}, scale {@code 2..4} and alpha
	 * {@code 0.1..0.2} - upstream's, landed with the particle (EXPL-B, D-3013).
	 */
	public static void pullLocal(LeafiaCoreTE core) {
		if(Minecraft.getInstance().player != null) core.pull(Minecraft.getInstance().player);
		ClientLevel level = Minecraft.getInstance().level;
		if(level == null) return;
		Vec3 p = Vec3.atCenterOf(core.getBlockPos());
		com.leafia.contents.effects.folkvangr.EntityNukeFolkvangr.VacuumInstance vacuum =
				new com.leafia.contents.effects.folkvangr.EntityNukeFolkvangr.VacuumInstance(p, 0, 10, LeafiaCoreTE.PULL_RANGE, 0.1);
		p = new com.leafia.dev.math.FiaMatrix(p).rotateY(level.random.nextDouble() * 360).rotateX(level.random.nextDouble() * 360)
				.translate(0, 0, level.random.nextDouble() * LeafiaCoreTE.PULL_RANGE).position;
		Minecraft.getInstance().particleEngine.add(new com.leafia.contents.effects.folkvangr.particles.ParticleFleijaVacuum(level, p.x, p.y, p.z,
				level.random.nextFloat() * 2 + 2, level.random.nextFloat() * 0.1f + 0.1f, vacuum));
	}

	public static void eyeOfHarmony(BlockPos pos, float r, float g, float b, float scale) {
		ClientLevel level = Minecraft.getInstance().level;
		if(level != null) Minecraft.getInstance().particleEngine.add(new ParticleEyeOfHarmony(level, pos, r, g, b, scale));
	}

	/** The armed detonator's sparks, and the charge at its front in the last six seconds. */
	public static void pulserParticles(CoreDetonatorTE te, boolean charging) {
		ClientLevel level = Minecraft.getInstance().level;
		if(level == null) return;
		BlockPos pos = te.getBlockPos();
		int amt2 = 2 + level.random.nextInt(5);
		for(int i = 0; i < amt2; i++) {
			Minecraft.getInstance().particleEngine.add(new ParticlePulserOvercharge(level,
					pos.getX() + 0.5 + (level.random.nextDouble() - 0.5) * 1.1,
					pos.getY() + 0.5 + (level.random.nextDouble() - 0.5) * 1.1,
					pos.getZ() + 0.5 + (level.random.nextDouble() - 0.5) * 1.1));
		}
		if(charging) {
			Vec3 p = Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(te.leafia$getTargetPosition().subtract(pos)).normalize());
			int amt = 1 + level.random.nextInt(3);
			for(int i = 0; i < amt; i++)
				Minecraft.getInstance().particleEngine.add(new ParticlePulserCharge(level, p.x, p.y, p.z));
		}
	}

	/** The detonator's answer to a typed code, for the open screen. */
	public static void pulserCodeAnswer(boolean accepted) {
		CoreDetonatorScreen gui = CoreDetonatorScreen.openGUI;
		if(gui != null) gui.answer(accepted);
	}

	/** Upstream's catalyst durability: the melt as a fraction, from the stack's {@code "damage"}. */
	public static double catalystDamage(ItemStack stack) {
		return LeafiaCoreTE.getCatalystDamage(stack) / 100d;
	}

	/** Upstream's {@code getRGBDurabilityForDisplay}: the catalyst's colour darkening, blinking red past two thirds. */
	public static int catalystBarColor(ItemStack stack) {
		LeafiaColor fiac = new LeafiaColor(((ItemCatalyst) stack.getItem()).getColor());
		double damage = catalystDamage(stack);
		fiac = fiac.lerp(new LeafiaColor(0.1, 0.1, 0.1), damage);
		if(damage > 0.666 && Math.floorMod(System.currentTimeMillis(), 400) >= 200)
			fiac = new LeafiaColor(1, 0, 0);
		return fiac.toInARGB();
	}

	@OnlyIn(Dist.CLIENT)
	@EventBusSubscriber(modid = LeafiaRef.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
	public static final class GameEvents {

		private GameEvents() {}

		@SubscribeEvent
		public static void onClientTick(ClientTickEvent.Post event) {
			tickAll();
		}

		@SubscribeEvent
		public static void onTooltip(ItemTooltipEvent event) {
			if(!LeafiaFeatures.dfcOverhaul) return;
			ItemStack stack = event.getItemStack();
			List<Component> tooltip = event.getToolTip();
			if(stack.getItem() instanceof ItemCatalyst) {
				// MixinItemCatalyst overwrites the whole tooltip: drop the base mod's two lines.
				tooltip.removeIf(c -> c.getString().equals("Adds spice to the core.") || c.getString().equals("Look at all those colors!"));
				int at = Math.min(1, tooltip.size());
				tooltip.addAll(at, catalystLines(stack));
			} else if(stack.getItem() == ModItems.ams_lens.get()) {
				LCEItemLens.addModifierLines(tooltip, 1.0F, 1.0F, 1.0F);
			} else if(stack.getItem() == ModBlocks.dfc_core.get().asItem()) {
				List<Component> badges = new ArrayList<>();
				MachineTooltip.addMultiblock(badges);
				MachineTooltip.addModular(badges);
				MachineTooltip.addCore(badges);
				tooltip.addAll(Math.min(1, tooltip.size()), badges);
			} else if(stack.getItem() instanceof net.minecraft.world.item.BlockItem blockItem
					&& blockItem.getBlock() instanceof com.hbm.blocks.machine.CoreComponent component) {
				tooltip.addAll(Math.min(1, tooltip.size()), componentBadges(component));
			}
		}

		/** Upstream's {@code MixinCoreComponent.printHook}: the coolant tanks of an overhauled emitter or absorber. */
		@SubscribeEvent
		public static void onGuiLayer(net.neoforged.neoforge.client.event.RenderGuiLayerEvent.Post event) {
			if(!event.getName().equals(net.neoforged.neoforge.client.gui.VanillaGuiLayers.CROSSHAIR)) return;
			Minecraft mc = Minecraft.getInstance();
			if(mc.level == null || !(mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit)
					|| hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) return;
			List<String> text = lookLines(mc.level.getBlockEntity(hit.getBlockPos()));
			if(!text.isEmpty())
				com.hbm.blocks.ILookOverlay.printGeneric(event.getGuiGraphics(),
						mc.level.getBlockState(hit.getBlockPos()).getBlock().getName().getString(), 0xffff00, 0x404000, text);
		}
	}

	/**
	 * Upstream's {@code MixinCoreComponent.addInformation}: every component is a multiblock part and modular;
	 * the absorbers are generators, the exchanger a boiler, and the stabilizer says it reads the core.
	 */
	public static List<Component> componentBadges(com.hbm.blocks.machine.CoreComponent component) {
		List<Component> badges = new ArrayList<>();
		MachineTooltip.addMultiblock(badges);
		MachineTooltip.addModular(badges);
		if(component == ModBlocks.dfc_receiver.get() || (com.leafia.contents.LeafiaBlocks.dfc_reinforced != null && component == com.leafia.contents.LeafiaBlocks.dfc_reinforced.get()))
			MachineTooltip.addGenerator(badges);
		else if(com.leafia.contents.LeafiaBlocks.dfc_exchanger != null && component == com.leafia.contents.LeafiaBlocks.dfc_exchanger.get())
			MachineTooltip.addBoiler(badges);
		else if(component == ModBlocks.dfc_stabilizer.get())
			for(String line : com.hbm.util.i18n.I18nUtil.resolveKey("tile.dfc_stabilizer.desc").split("\\$")) badges.add(Component.literal(line));
		return badges;
	}

	/** The look-at panel's lines for an overhauled emitter or absorber: coolant in, spent coolant out, and the rule. */
	public static List<String> lookLines(@javax.annotation.Nullable BlockEntity te) {
		List<String> text = new ArrayList<>();
		com.hbm.inventory.fluid.tank.FluidTank in;
		com.hbm.inventory.fluid.tank.FluidTank out;
		if(te instanceof com.hbm.blockentity.machine.BlockEntityCoreEmitter emitter && te instanceof com.leafia.overwrite_contents.interfaces.IMixinTileEntityCoreEmitter mixin) {
			in = emitter.tank;
			out = mixin.leafia$getOutputTank();
		} else if(te instanceof com.hbm.blockentity.machine.BlockEntityCoreReceiver receiver && te instanceof com.leafia.overwrite_contents.interfaces.IMixinTileEntityCoreReceiver mixin) {
			in = receiver.tank;
			out = mixin.leafia$getOutputTank();
		} else return text;
		text.add(ChatFormatting.GREEN + "-> " + ChatFormatting.RESET + in.getTankType().getLocalizedName() + ": " + in.getFill() + "/" + in.getMaxFill() + "mB");
		text.add(ChatFormatting.RED + "<- " + ChatFormatting.RESET + out.getTankType().getLocalizedName() + ": " + out.getFill() + "/" + out.getMaxFill() + "mB");
		text.addAll(java.util.Arrays.asList(com.hbm.util.i18n.I18nUtil.resolveKey("info.dfc.cooling").split("\\$")));
		return text;
	}

	/** Upstream's catalyst tooltip, on NTM-CE's modifiers. */
	public static List<Component> catalystLines(ItemStack stack) {
		float powerMod = DFCCEStats.getPowerMod(stack);
		float heatMod = DFCCEStats.getHeatMod(stack);
		float fuelMod = DFCCEStats.getFuelMod(stack);
		List<Component> out = new ArrayList<>();
		out.add(Component.literal("§6Melting Point: §8" + LCEItemCatalyst.getMelting(stack) + "°C"));
		out.add(Component.literal("Energy Modifier:           " + (powerMod >= 1 ? "§a+" : "§c") + (Math.round(powerMod * 1000) * .10 - 100) + "%"));
		out.add(Component.literal("Heat Modifier:               " + (heatMod > 1 ? "§c+" : "§a") + (Math.round(heatMod * 1000) * .10 - 100) + "%"));
		out.add(Component.literal("Fuel Modifier:               " + (fuelMod > 1 ? "§c+" : "§a") + (Math.round(fuelMod * 1000) * .10 - 100) + "%"));
		out.add(Component.literal(""));
		out.add(Component.literal(ChatFormatting.LIGHT_PURPLE + "Required to contain the deadly stellar core of DFC"));
		return out;
	}

	@OnlyIn(Dist.CLIENT)
	@EventBusSubscriber(modid = LeafiaRef.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
	public static final class ModEvents {

		private ModEvents() {}

		/** The catalysts' melt bar: always shown upstream ({@code showDurabilityBar} true), even when new. */
		@SubscribeEvent
		public static void onDecorations(RegisterItemDecorationsEvent event) {
			IItemDecorator bar = (graphics, font, stack, x, y) -> {
				if(!LeafiaFeatures.dfcOverhaul || !(stack.getItem() instanceof ItemCatalyst)) return false;
				int width = (int) Math.round(13.0D - catalystDamage(stack) * 13.0D);
				int i = x + 2;
				int j = y + 13;
				graphics.fill(RenderType.guiOverlay(), i, j, i + 13, j + 2, 0xFF000000);
				graphics.fill(RenderType.guiOverlay(), i, j, i + Mth.clamp(width, 0, 13), j + 1, catalystBarColor(stack) | 0xFF000000);
				return false;
			};
			for(var holder : List.of(ModItems.ams_catalyst_iron, ModItems.ams_catalyst_copper, ModItems.ams_catalyst_aluminium,
					ModItems.ams_catalyst_lithium, ModItems.ams_catalyst_beryllium, ModItems.ams_catalyst_tungsten,
					ModItems.ams_catalyst_cobalt, ModItems.ams_catalyst_niobium, ModItems.ams_catalyst_cerium,
					ModItems.ams_catalyst_thorium, ModItems.ams_catalyst_strontium, ModItems.ams_catalyst_caesium,
					ModItems.ams_catalyst_schrabidium, ModItems.ams_catalyst_euphemium, ModItems.ams_catalyst_dineutronium))
				event.register(holder.get(), bar);
		}
	}
}
