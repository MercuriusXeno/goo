package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.data.IRemainderLedger;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.EntityCapability;
import net.neoforged.neoforge.capabilities.ItemCapability;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jspecify.annotations.Nullable;
import java.util.UUID;

/**
 * Custom NeoForge capabilities registered by the goo mod.
 *
 * <p>Gasket capabilities resolve a gasket UUID to the fluid handler at that
 * endpoint. Block targets (canister shelf, hub, vat) use GASKET_BLOCK;
 * entity targets (player inventory) use GASKET_ENTITY.</p>
 *
 * <p>The remainder capabilities serve one shared IRemainderLedger from every
 * store of goo: a block, the player and the canister item.</p>
 * decision one-remainder-capability-injected-into-every-fluid-capability
 */
public final class GooCapabilities {

    /**
     * Block capability for gasket endpoints. Context is the target gasket UUID.
     */
    @SuppressWarnings("unchecked")
    public static final BlockCapability<ResourceHandler<FluidResource>, UUID> GASKET_BLOCK =
            BlockCapability.create(
                    Identifier.fromNamespaceAndPath(Goo.MODID, "gasket_block"),
                    (Class<ResourceHandler<FluidResource>>) (Class<?>) ResourceHandler.class,
                    UUID.class);

    /**
     * Entity capability for gasket endpoints. Context is the target gasket UUID.
     */
    @SuppressWarnings("unchecked")
    public static final EntityCapability<ResourceHandler<FluidResource>, UUID> GASKET_ENTITY =
            EntityCapability.create(
                    Identifier.fromNamespaceAndPath(Goo.MODID, "gasket_entity"),
                    (Class<ResourceHandler<FluidResource>>) (Class<?>) ResourceHandler.class,
                    UUID.class);

    /**
     * The remainder ledger of a block's goo store: the crucible, the vat, the hub and
     * the plexer answer their own store for a null context, and every canister-slot
     * block answers the canister in the slot the context names.
     */
    public static final BlockCapability<IRemainderLedger, @Nullable Integer> REMAINDER_BLOCK =
            BlockCapability.create(
                    Identifier.fromNamespaceAndPath(Goo.MODID, "remainder_block"),
                    IRemainderLedger.class, Integer.class);

    /** The remainder ledger an entity holds, which the player serves from their attachment. */
    public static final EntityCapability<IRemainderLedger, @Nullable Void> REMAINDER_ENTITY =
            EntityCapability.createVoid(
                    Identifier.fromNamespaceAndPath(Goo.MODID, "remainder_entity"),
                    IRemainderLedger.class);

    /** The remainder ledger an item carries, which the canister serves from its content. */
    public static final ItemCapability<IRemainderLedger, ItemAccess> REMAINDER_ITEM =
            ItemCapability.create(
                    Identifier.fromNamespaceAndPath(Goo.MODID, "remainder_item"),
                    IRemainderLedger.class, ItemAccess.class);

    private static final String NO_BLOCK_LEDGER = "No remainder ledger served at ";
    private static final String NO_ENTITY_LEDGER = "No remainder ledger served by ";

    private GooCapabilities() {
    }

    /**
     * The remainder ledger a block entity's own goo store serves.
     *
     * @param store the block entity, placed in a level
     * @return its ledger
     * @throws IllegalStateException where the block serves no remainder ledger
     */
    public static IRemainderLedger remainderLedgerOf(BlockEntity store) {
        IRemainderLedger ledger = store.getLevel() == null ? null : store.getLevel().getCapability(
                REMAINDER_BLOCK, store.getBlockPos(), store.getBlockState(), store, null);
        if (ledger == null) {
            throw new IllegalStateException(NO_BLOCK_LEDGER + store.getBlockPos());
        }
        return ledger;
    }

    /**
     * The remainder ledger an entity serves.
     *
     * @param holder the entity
     * @return its ledger
     * @throws IllegalStateException where the entity serves no remainder ledger
     */
    public static IRemainderLedger remainderLedgerOf(Entity holder) {
        IRemainderLedger ledger = holder.getCapability(REMAINDER_ENTITY);
        if (ledger == null) {
            throw new IllegalStateException(NO_ENTITY_LEDGER + holder);
        }
        return ledger;
    }
}
