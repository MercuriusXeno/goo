package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.client.network.ClientAbilities;
import com.mercuriusxeno.goo.client.network.ClientAbilityConnection;
import com.mercuriusxeno.goo.data.GooValueConnection;
import com.mercuriusxeno.goo.data.GooValueTable;
import com.mercuriusxeno.goo.data.IGooValueLookup;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypeOrderSource;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import java.util.List;

/**
 * Gives the client connection what goo holds for its life: the goo values and
 * abilities its server synced, and the goo types its registries hold, so a
 * disconnect drops them with the connection (decision type-package-and-per-server-holders).
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerGooMixin
        implements GooValueConnection, ClientAbilityConnection, GooTypeOrderSource {

    @Unique
    private volatile GooValueTable goo$valueTable = GooValueTable.EMPTY;

    @Unique
    private volatile ClientAbilities goo$abilities = ClientAbilities.EMPTY;

    @Unique
    private volatile @Nullable List<ResourceKey<GooTypeDefinition>> goo$typeOrder;

    /**
     * The registries the server sent this connection, which stand unchanged for its life.
     *
     * @return the registry access
     */
    @Shadow
    public abstract RegistryAccess.Frozen registryAccess();

    /**
     * {@inheritDoc}
     */
    @Override
    public IGooValueLookup gooValueLookup() {
        return goo$valueTable;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void receiveGooValues(GooValueTable table) {
        goo$valueTable = table;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ClientAbilities clientAbilities() {
        return goo$abilities;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void receiveClientAbilities(ClientAbilities abilities) {
        goo$abilities = abilities;
    }

    /**
     * Reads the types from the connection's registries on first ask and keeps them.
     *
     * @return the keys, in {@link GooTypes#ORDER}
     */
    @Override
    public List<ResourceKey<GooTypeDefinition>> gooTypeOrder() {
        List<ResourceKey<GooTypeDefinition>> order = goo$typeOrder;
        if (order == null) {
            order = GooTypes.all(registryAccess());
            goo$typeOrder = order;
        }
        return order;
    }
}
