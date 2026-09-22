package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.registry.GooFluids;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jspecify.annotations.Nullable;

/**
 * Immutable single-fluid data component for canister items.
 * Stores one fluid resource and its volume in microblobs (mB).
 * Accepts any fluid resource: a goo type is the goo fluid stamped with its
 * type component (decision generic-goo-fluids), and vanilla fluids are bare.
 *
 * @param resource the stored fluid resource, or {@link FluidResource#EMPTY} if none
 * @param amount   the volume in microblobs (mB), 0 if empty
 */
public record CanisterFluidContent(FluidResource resource, int amount) {

    /** Empty canister with no fluid. */
    public static final CanisterFluidContent EMPTY = new CanisterFluidContent(FluidResource.EMPTY, 0);

    /** Persistent codec: the resource with its components, amount as int. */
    public static final Codec<CanisterFluidContent> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            FluidResource.OPTIONAL_CODEC.fieldOf("fluid").forGetter(CanisterFluidContent::resource),
            Codec.INT.fieldOf("amount").forGetter(CanisterFluidContent::amount)
        ).apply(instance, CanisterFluidContent::new)
    );

    /** Network codec: the resource, then the amount as VAR_INT. */
    public static final StreamCodec<RegistryFriendlyByteBuf, CanisterFluidContent> STREAM_CODEC =
        StreamCodec.composite(
            FluidResource.STREAM_CODEC, CanisterFluidContent::resource,
            ByteBufCodecs.VAR_INT, CanisterFluidContent::amount,
            CanisterFluidContent::new);

    /** Normalizes: empty resource or non-positive amount both produce EMPTY state. */
    public CanisterFluidContent {
        if (resource.isEmpty() || amount <= 0) {
            resource = FluidResource.EMPTY;
            amount = 0;
        }
    }

    /**
     * Returns true if no fluid is stored.
     *
     * @return true if empty
     */
    public boolean isEmpty() {
        return resource.isEmpty() || amount <= 0;
    }

    /**
     * The stored fluid without its components, for render and tooltip code
     * that tells vanilla fluids apart.
     *
     * @return the fluid, or the empty fluid
     */
    public Fluid fluid() {
        return resource.getFluid();
    }

    /**
     * Returns the goo type if this holds a bundled goo type, or null for vanilla
     * fluids and datapack types.
     *
     * @return the goo type, or null
     */
    @Nullable
    public GooType getGooType() {
        return GooFluids.typeOf(resource);
    }

    /**
     * Returns a new content with the given volume added. The resource must
     * match the current one (or current must be empty).
     *
     * @param addResource the resource to add
     * @param addAmount   the volume to add in microblobs
     * @return new content with the addition, or this if incompatible
     */
    public CanisterFluidContent withAdded(FluidResource addResource, int addAmount) {
        if (addAmount <= 0) { return this; }
        if (isEmpty()) { return new CanisterFluidContent(addResource, addAmount); }
        if (!resource.equals(addResource)) { return this; }
        return new CanisterFluidContent(resource, amount + addAmount);
    }

    /**
     * Returns a new content with the given volume removed.
     *
     * @param removeAmount the volume to remove in microblobs
     * @return new content with the removal applied
     */
    public CanisterFluidContent withRemoved(int removeAmount) {
        if (removeAmount <= 0 || isEmpty()) { return this; }
        int remaining = amount - removeAmount;
        return remaining > 0 ? new CanisterFluidContent(resource, remaining) : EMPTY;
    }

    /**
     * Returns a new content with the given volume added, capped by capacity.
     *
     * @param addResource the resource to add
     * @param addAmount   the requested volume
     * @param capacity    the total capacity of the container
     * @return new content with the capped addition
     */
    public CanisterFluidContent withCappedAdd(FluidResource addResource, int addAmount, int capacity) {
        int accepted = cappedAddAmount(addResource, addAmount, capacity);
        if (accepted <= 0) { return this; }
        FluidResource target = isEmpty() ? addResource : resource;
        int currentAmount = isEmpty() ? 0 : amount;
        return new CanisterFluidContent(target, currentAmount + accepted);
    }

    /**
     * Returns how much of the requested amount would be accepted by withCappedAdd.
     *
     * @param addResource the resource to add
     * @param addAmount   the requested volume
     * @param capacity    the total capacity of the container
     * @return the amount that would be accepted
     */
    public int cappedAddAmount(FluidResource addResource, int addAmount, int capacity) {
        if (addAmount <= 0) { return 0; }
        if (!canAccept(addResource)) { return 0; }
        int currentAmount = isEmpty() ? 0 : amount;
        int space = capacity - currentAmount;
        if (space <= 0) { return 0; }
        return Math.min(addAmount, space);
    }

    /**
     * Returns true if this content is empty or already holds the given resource.
     *
     * @param candidate the resource to test
     * @return true if the candidate is compatible
     */
    private boolean canAccept(FluidResource candidate) {
        return isEmpty() || resource.equals(candidate);
    }
}
