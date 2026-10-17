package com.leafia.contents.miscellanous.diverter;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.hbm.blocks.ILookOverlay;
import com.hbm.blocks.ITooltipProvider;
import com.hbm.util.i18n.I18nUtil;
import com.leafia.config.LeafiaConfig;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.init.LeafiaBlockEntities;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The Meteor Protection Beacon (D-3022): {@code meteor_diverter_unlit} while it waits, {@code meteor_diverter} (lit,
 * light 7, not in the creative tab) once it works. Either one picks and drops the unlit one.
 *
 * <p>Upstream: {@code com/leafia/contents/miscellanous/diverter/DiverterBlock.java}, an {@code AddonBlockBase} of
 * {@code Material.ROCK} with no hardness set - so it breaks at once, but needs a pickaxe to drop (D-1248) - drawn as
 * the addon's radio-torch model, with a 0.2 x 0.6 x 0.2 selection box and no collision.
 *
 * <p>{@link #isProtected} is what the meteor hook asks: whether an active beacon stands within
 * {@code balance.meteor.diverterProtectionRadius} chunks of a chunk. Like upstream it loads (or generates) every chunk
 * it looks at, synchronously - a 7 x 7 square with the default radius.
 */
public class DiverterBlock extends Block implements EntityBlock, ITooltipProvider, ILookOverlay {

	/** Upstream's {@code STANDING_AABB}: 0.4 to 0.6 across, 0 to 0.6 up. */
	public static final VoxelShape STANDING = Block.box(6.4000000953674316, 0.0D, 6.4000000953674316, 9.600000381469727, 9.600000381469727, 9.600000381469727);

	private final boolean isOn;

	public DiverterBlock(Properties properties, boolean isOn) {
		super(properties);
		this.isOn = isOn;
	}

	public boolean isOn() {
		return isOn;
	}

	/** Upstream's {@code setTranslationKey("meteor_diverter")} on both: one name, "Meteor Protection Beacon". */
	@Override
	public String getDescriptionId() {
		return "block.leafia.meteor_diverter";
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		if(!level.isClientSide && placer instanceof Player plr && level.getBlockEntity(pos) instanceof DiverterTE diverter)
			diverter.setPlacerName(plr.getGameProfile().getName());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return STANDING;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.empty();
	}

	@Override
	public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
		return new ItemStack(LeafiaBlocks.diverter_unlit.get());
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		addStandardInfo("tile.meteor_diverter.desc", tooltip);
		super.appendHoverText(stack, context, tooltip, flag);
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new DiverterTE(pos, state);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if(level.isClientSide || type != LeafiaBlockEntities.DIVERTER.get()) return null;
		return (BlockEntityTicker<T>) (BlockEntityTicker<DiverterTE>) DiverterTE::tick;
	}

	/** The look-at panel: green "Active", or red "inactive" and the time left as H:MM:SS, or red "Error"; then the placer. */
	@Override
	@OnlyIn(Dist.CLIENT)
	public void printHook(GuiGraphics gui, Level world, BlockPos pos) {
		if(world.getBlockEntity(pos) instanceof DiverterTE diverter) {
			ILookOverlay.printGeneric(gui, I18nUtil.resolveKey(getDescriptionId()), 0x00FFFF, 0x004040, overlayLines(diverter));
		}
	}

	public static List<String> overlayLines(DiverterTE diverter) {
		List<String> text = new ArrayList<>();
		int color = 0x55FF55;
		if(diverter.isError) {
			color = 0xFF0000;
			text.add("&[" + color + "&]Error");
		} else {
			if(diverter.timeRemaining > 0) {
				color = 0xFF0000;
				text.add("&[" + color + "&]" + I18nUtil.resolveKey("tile.meteor_diverter.message.inactive"));
				text.add(I18nUtil.resolveKey("tile.meteor_diverter.message.time", formatTime(diverter.timeRemaining)));
			} else {
				text.add("&[" + color + "&]" + I18nUtil.resolveKey("tile.meteor_diverter.message.active"));
			}
		}
		text.add(I18nUtil.resolveKey("tile.meteor_diverter.placer", diverter.placerName));
		return text;
	}

	/** Upstream's {@code "%01d:%02d:%02d"} of whole seconds. */
	public static String formatTime(long ticks) {
		long secs = ticks / 20;
		return String.format("%01d:%02d:%02d", secs / 3600, (secs / 60) % 60, secs % 60);
	}

	/** Whether an active beacon stands within the protection radius of chunk {@code (ccX, ccZ)}. */
	public static boolean isProtected(Level world, int ccX, int ccZ) {
		int radius = LeafiaConfig.meteorDiverterProtectionRadius;
		for(int cx = ccX - radius; cx <= ccX + radius; cx++) {
			for(int cz = ccZ - radius; cz <= ccZ + radius; cz++) {
				ChunkAccess chunk = world.getChunk(cx, cz);
				if(!(chunk instanceof net.minecraft.world.level.chunk.LevelChunk loaded)) continue;
				for(BlockEntity te : loaded.getBlockEntities().values()) {
					if(!te.isRemoved() && te instanceof DiverterTE div) {
						if(div.timeRemaining <= 0) return true;
					}
				}
			}
		}
		return false;
	}
}
