package net.mehvahdjukaar.selene.blocks;


import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.inventory.container.ChestContainer;
import net.minecraft.inventory.container.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SUpdateTileEntityPacket;
import net.minecraft.tileentity.LockableLootTileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.*;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.SidedInvWrapper;

import javax.annotation.Nullable;
import java.util.stream.IntStream;

public abstract class ItemDisplayTile extends LockableLootTileEntity implements ISidedInventory {
    protected NonNullList<ItemStack> stacks;

    public ItemDisplayTile(TileEntityType type) {
        this(type, 1);
    }

    public ItemDisplayTile(TileEntityType type, int slots) {
        super(type);
        this.stacks = NonNullList.withSize(slots, ItemStack.EMPTY);
    }

    //should only be server side. called when inventory has changed
    @Override
    public void setChanged() {
        if (this.level == null) return;
        this.updateTileOnInventoryChanged();
        if (this.needsToUpdateClientWhenChanged()) {
            //this saves and sends a packet to update the client tile
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Constants.BlockFlags.BLOCK_UPDATE);
        }
        super.setChanged();
    }

     //todo: legacy, remove
     @Deprecated
     public void updateOnChangedBeforePacket(){

     }

    /**
     * called every time the tile is marked dirty or loaded. Server side method.
     * Put here common logic for things that needs to react to inventory changes like updating blockState or logic
     */
    public void updateTileOnInventoryChanged() {
        //TODO: remove
        this.updateOnChangedBeforePacket();
    }

    /**
     * @return true if the tile needs to react one inventory changes on client.
     * Set to true if you are using updateClientVisualsOnLoad()
     * usually not needed for tiles that do not visually display their content
     */
    public boolean needsToUpdateClientWhenChanged() {
        return true;
    }

    /**
     * Called after the tile is loaded from packet. Client side.
     * Put here client only visual logic that needs to react to inventory changes
     */
    public void updateClientVisualsOnLoad() {
    }

    public ItemStack getDisplayedItem() {
        return this.getItem(0);
    }

    public void setDisplayedItem(ItemStack stack) {
        this.setItem(0, stack);
    }

    public ActionResultType interact(PlayerEntity player, Hand handIn) {
        return this.interact(player, handIn, 0);
    }

    public ActionResultType interact(PlayerEntity player, Hand handIn, int slot) {
        if (handIn == Hand.MAIN_HAND) {
            ItemStack handItem = player.getItemInHand(handIn);
            //remove
            if (!this.isEmpty() && handItem.isEmpty()) {
                ItemStack it = this.removeItemNoUpdate(slot);
                if (!this.level.isClientSide()) {
                    player.setItemInHand(handIn, it);
                    this.setChanged();
                } else {
                    //also update visuals on client. will get overwritten by packet tho
                    this.updateClientVisualsOnLoad();
                }
                return ActionResultType.sidedSuccess(this.level.isClientSide);
            }
            //place
            else if (!handItem.isEmpty() && this.canPlaceItem(slot, handItem)) {
                ItemStack it = handItem.copy();
                it.setCount(1);
                this.setItem(slot, it);

                if (!player.isCreative()) {
                    handItem.shrink(1);
                }
                if (!this.level.isClientSide()) {
                    this.level.playSound(null, this.worldPosition, this.getAddItemSound(), SoundCategory.BLOCKS, 1.0F, this.level.random.nextFloat() * 0.10F + 0.95F);
                    //this.setChanged();
                } else {
                    //also update visuals on client. will get overwritten by packet tho
                    this.updateClientVisualsOnLoad();
                }
                return ActionResultType.sidedSuccess(this.level.isClientSide);
            }
        }
        return ActionResultType.PASS;
    }

    public SoundEvent getAddItemSound() {
        return SoundEvents.ITEM_FRAME_ADD_ITEM;
    }

    @Override
    public void load(BlockState state, CompoundNBT compound) {
        super.load(state, compound);
        if (!this.tryLoadLootTable(compound)) {
            this.stacks = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        }
        ItemStackHelper.loadAllItems(compound, this.stacks);
        if (this.level != null){
            if(this.level.isClientSide) this.updateClientVisualsOnLoad();
            else this.updateTileOnInventoryChanged();
        }
    }

    @Override
    public CompoundNBT save(CompoundNBT compound) {
        super.save(compound);
        if (!this.trySaveLootTable(compound)) {
            ItemStackHelper.saveAllItems(compound, this.stacks);
        }
        return compound;
    }

    @Override
    public SUpdateTileEntityPacket getUpdatePacket() {
        return new SUpdateTileEntityPacket(this.worldPosition, 0, this.getUpdateTag());
    }

    @Override
    public CompoundNBT getUpdateTag() {
        return this.save(new CompoundNBT());
    }

    @Override
    public void onDataPacket(NetworkManager net, SUpdateTileEntityPacket pkt) {
        this.load(this.getBlockState(), pkt.getTag());
    }

    @Override
    public int getContainerSize() {
        return stacks.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack itemstack : this.stacks)
            if (!itemstack.isEmpty())
                return false;
        return true;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public Container createMenu(int id, PlayerInventory player) {
        return ChestContainer.threeRows(id, player, this);
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.stacks;
    }

    @Override
    public void setItems(NonNullList<ItemStack> stacks) {
        this.stacks = stacks;
    }

    @Override
    public boolean canPlaceItem(int index, ItemStack stack) {
        return this.isEmpty();
    }

    @Override
    public boolean canPlaceItemThroughFace(int index, ItemStack stack, @Nullable Direction direction) {
        return false;
    }

    @Override
    public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction direction) {
        return false;
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return IntStream.range(0, this.getContainerSize()).toArray();
    }


    private final LazyOptional<? extends IItemHandler>[] handlers = SidedInvWrapper.create(this, Direction.values());

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction facing) {
        if (!this.remove && facing != null && capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY)
            return handlers[facing.ordinal()].cast();
        return super.getCapability(capability, facing);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        for (LazyOptional<? extends IItemHandler> handler : handlers)
            handler.invalidate();
    }

}

