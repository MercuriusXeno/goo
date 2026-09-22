package com.mercuriusxeno.goo.fluid;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Carries the goo type of one in-world goo fluid block. Decision
 * generic-goo-fluids: fluid state and block state cannot hold a datapack
 * type, so the type rides here, stamped on placement and copied on spread,
 * and synced to clients so tint and map color read it.
 */
public class GooFluidBlockEntity extends BlockEntity {

    private static final String TAG_TYPE = "goo_type";
    private static final String NO_TYPE = "";

    private @Nullable ResourceKey<GooTypeDefinition> typeKey;

    /**
     * @param pos   the block position
     * @param state the fluid block state
     */
    public GooFluidBlockEntity(BlockPos pos, BlockState state) {
        super(GooBlockEntities.GOO_FLUID.get(), pos, state);
    }

    /**
     * The type key stamped on this block, or null before any stamp.
     *
     * @return the registry key of the goo type here
     */
    public @Nullable ResourceKey<GooTypeDefinition> typeKey() {
        return typeKey;
    }

    /**
     * Stamps the type and syncs it to clients.
     *
     * @param key the registry key of the goo type placed here
     */
    public void stamp(ResourceKey<GooTypeDefinition> key) {
        typeKey = key;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 0);
        }
    }

    /**
     * The definition of the type here, from the registries in hand.
     *
     * @param registries the registry access of the level
     * @return the definition, or null before any stamp or when the key resolves to nothing
     */
    public @Nullable GooTypeDefinition definition(HolderLookup.Provider registries) {
        return typeKey == null ? null : registries.get(typeKey).map(holder -> holder.value()).orElse(null);
    }

    /**
     * The type key of the goo fluid block at a position, for callers holding
     * a block getter.
     *
     * @param level the block getter
     * @param pos   the position
     * @return the key, or null where no stamped goo fluid block entity sits
     */
    public static @Nullable ResourceKey<GooTypeDefinition> typeAt(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof GooFluidBlockEntity be ? be.typeKey : null;
    }

    /**
     * The definition of the goo fluid block at a position.
     *
     * @param level      the block getter
     * @param pos        the position
     * @param registries the registry access of the level
     * @return the definition, or null where none is stamped
     */
    public static @Nullable GooTypeDefinition definitionAt(BlockGetter level, BlockPos pos,
                                                          HolderLookup.Provider registries) {
        return level.getBlockEntity(pos) instanceof GooFluidBlockEntity be ? be.definition(registries) : null;
    }

    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        String id = input.getStringOr(TAG_TYPE, NO_TYPE);
        typeKey = id.isEmpty() ? null : ResourceKey.create(GooTypes.REGISTRY, Identifier.parse(id));
    }

    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        if (typeKey != null) {
            output.putString(TAG_TYPE, typeKey.identifier().toString());
        }
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public @NonNull CompoundTag getUpdateTag(HolderLookup.@NonNull Provider registries) {
        return saveCustomOnly(registries);
    }
}
