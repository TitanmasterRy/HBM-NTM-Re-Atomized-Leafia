package com.leafia.contents.building.storage.broof;

import com.leafia.contents.building.storage.broof.container.BroofMenu;
import com.leafia.init.LeafiaBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * The broof's fifty-four slots.
 *
 * <p>Upstream: {@code .../building/storage/broof/BroofTE.java}. A double chest's worth of
 * storage, saved to NBT and exposed to hoppers and pipes.
 *
 * <h2>{@link MenuProvider} replaces {@code IGUIProvider}</h2>
 *
 * Upstream implements the base mod's {@code IGUIProvider}, whose two methods hand back a
 * {@code Container} and a {@code GuiScreen} built on the server and the client respectively.
 * 1.21.1 splits that: the block entity is a {@link MenuProvider} that builds the
 * <em>menu</em>, and the screen is registered separately against the menu type. So
 * {@code provideContainer} becomes {@link #createMenu} and {@code provideGUI} has no successor
 * here at all — {@code LeafiaClient} registers {@code BroofScreen} against
 * {@code LeafiaMenuTypes.BROOF} once, rather than the block entity naming it every time it is
 * opened.
 *
 * <h2>The side wrapper is dropped, and it changes nothing</h2>
 *
 * Upstream's {@code getCapability} wraps the handler in an {@code ItemStackHandlerWrapper}
 * restricted to {@code getAccessibleSlotsFromSide(facing)} — which builds an array of
 * <b>all fifty-four slots</b> for every face. The wrapper is therefore an identity, and this
 * exposes the handler directly. Recorded rather than done quietly, because a dropped
 * side-restriction is exactly the kind of thing that looks like a port bug later: there is no
 * restriction to drop.
 */
public class BroofTE extends BlockEntity implements MenuProvider {

	/** Upstream's 54, and the reason the screen is vanilla's double-chest texture. */
	public static final int SLOTS = 54;

	public final ItemStackHandler inventory = new ItemStackHandler(SLOTS) {
		@Override
		protected void onContentsChanged(int slot) {
			super.onContentsChanged(slot);
			setChanged();
		}
	};

	private AABB renderAABB;

	public BroofTE(BlockPos pos, BlockState state) {
		super(LeafiaBlockEntities.BROOF.get(), pos, state);
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.put("inventory", inventory.serializeNBT(registries));
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		if(tag.contains("inventory"))
			inventory.deserializeNBT(registries, tag.getCompound("inventory"));
	}

	/**
	 * Upstream's render box: the 3x3 footprint and two blocks of height, so the rug is not
	 * culled when the core is off screen.
	 *
	 * <p>Not an override — NeoForge 21.1 asks the renderer for this, not the block entity, the
	 * same correction D-1039 made for {@code EvBufferTE}.
	 */
	public AABB renderBounds() {
		if(renderAABB == null) {
			renderAABB = new AABB(
					worldPosition.getX() - 1, worldPosition.getY(), worldPosition.getZ() - 1,
					worldPosition.getX() + 2, worldPosition.getY() + 2, worldPosition.getZ() + 2);
		}
		return renderAABB;
	}

	// ------------------------------------------------------------------ the menu

	/**
	 * The title the screen draws.
	 *
	 * <p>Upstream's GUI draws {@code I18n.format("tile.broof.name")} centred, which is the
	 * block's own name; here the menu's title is that name and the screen draws it, so the two
	 * agree by construction rather than by a second translation key.
	 */
	@Override
	public Component getDisplayName() {
		return getBlockState().getBlock().getName();
	}

	@Override
	public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
		return new BroofMenu(containerId, playerInventory, this);
	}
}
