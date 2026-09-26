package com.mercuriusxeno.goo.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NonNull;

/**
 * A block entity whose whole saved state reaches tracking clients: the chunk
 * sync carries the full-metadata tag, and every
 * {@link BlockEntitySync#markDirtyAndSync} sends the data packet built from it
 * (decision machine-base-owns-the-lifecycle).
 */
public abstract class GooSyncedBlockEntity extends BlockEntity {

    /**
     * Creates a synced block entity.
     *
     * @param type  the block entity type
     * @param pos   the block position
     * @param state the block state
     */
    protected GooSyncedBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public final @NonNull CompoundTag getUpdateTag(HolderLookup.@NonNull Provider registries) {
        return saveWithFullMetadata(registries);
    }

    @Override
    public final Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
