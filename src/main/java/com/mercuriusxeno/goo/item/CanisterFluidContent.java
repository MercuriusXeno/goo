package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.registry.GooFluids;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable fluid data component for canister items: each fluid the canister
 * holds with its volume (mB), in the order it arrived, on one shared capacity.
 * A goo type is the goo fluid stamped with its type component (decision
 * generic-goo-fluids), and vanilla fluids are bare.
 *
 * <p>Goo types share the canister with each other; a vanilla fluid keeps the
 * canister to itself, since the decision covers goo only.</p>
 *
 * decision canisters-hold-more-than-one-goo-type
 *
 * @param portions each held fluid with its volume, none empty and no resource twice
 */
public record CanisterFluidContent(List<Portion> portions) {

    /**
     * One fluid inside a canister and its volume.
     *
     * @param resource the fluid resource
     * @param amount   the volume (mB)
     */
    public record Portion(FluidResource resource, int amount) {

        /**
         * Persistent codec: the resource under "fluid", amount as int under "amount".
         *
         * @param fluidCodec the codec for the resource
         * @return the portion codec
         */
        static Codec<Portion> codecOver(Codec<FluidResource> fluidCodec) {
            return RecordCodecBuilder.create(instance ->
                instance.group(
                    fluidCodec.fieldOf(FLUID_FIELD).forGetter(Portion::resource),
                    Codec.INT.fieldOf(AMOUNT_FIELD).forGetter(Portion::amount)
                ).apply(instance, Portion::new));
        }

        /** Network codec: the resource, then the amount as VAR_INT. */
        public static final StreamCodec<RegistryFriendlyByteBuf, Portion> STREAM_CODEC =
            StreamCodec.composite(
                FluidResource.STREAM_CODEC, Portion::resource,
                ByteBufCodecs.VAR_INT, Portion::amount,
                Portion::new);

        /**
         * Returns the goo type stamped on this portion's resource.
         *
         * @return the goo type, or null for a vanilla fluid
         */
        @Nullable
        public ResourceKey<GooTypeDefinition> gooType() {
            return gooTypeOf(resource);
        }
    }

    /** Empty canister with no fluid. */
    public static final CanisterFluidContent EMPTY = new CanisterFluidContent(List.of());

    /** The index answered for a resource no portion holds. */
    private static final int NOT_HELD = -1;

    /** The field naming one portion's resource, in both saved shapes. */
    private static final String FLUID_FIELD = "fluid";

    /** The field naming one portion's volume, in both saved shapes. */
    private static final String AMOUNT_FIELD = "amount";

    /** The field naming every portion, in the many-type shape. */
    private static final String PORTIONS_FIELD = "fluids";

    /** Persistent codec: writes the list shape, reads the list shape or the single-fluid shape. */
    public static final Codec<CanisterFluidContent> CODEC = codecOver(FluidResource.OPTIONAL_CODEC);

    /**
     * The persistent codec over a resource codec: writes every portion under
     * "fluids", and reads that shape or the single-fluid shape a canister saved
     * before it held many types, one "fluid" and one "amount".
     *
     * @param fluidCodec the codec for each resource
     * @return the content codec
     */
    static Codec<CanisterFluidContent> codecOver(Codec<FluidResource> fluidCodec) {
        Codec<Portion> portionCodec = Portion.codecOver(fluidCodec);
        Codec<CanisterFluidContent> portionsShape = RecordCodecBuilder.create(instance ->
            instance.group(
                portionCodec.listOf().fieldOf(PORTIONS_FIELD).forGetter(CanisterFluidContent::portions)
            ).apply(instance, CanisterFluidContent::new));
        Codec<CanisterFluidContent> singleFluidShape = portionCodec.xmap(
            portion -> CanisterFluidContent.of(portion.resource(), portion.amount()),
            content -> content.portions().isEmpty()
                ? new Portion(FluidResource.EMPTY, 0)
                : content.portions().getFirst());
        return Codec.withAlternative(portionsShape, singleFluidShape);
    }

    /** Network codec: the portions as a list. */
    public static final StreamCodec<RegistryFriendlyByteBuf, CanisterFluidContent> STREAM_CODEC =
        Portion.STREAM_CODEC.apply(ByteBufCodecs.list())
            .map(CanisterFluidContent::new, CanisterFluidContent::portions);

    /** Normalizes: drops empty and non-positive portions, merging a resource listed twice. */
    public CanisterFluidContent {
        List<Portion> kept = new ArrayList<>();
        for (Portion portion : portions) {
            if (portion.resource().isEmpty() || portion.amount() <= 0) {
                continue;
            }
            int at = indexIn(kept, portion.resource());
            if (at < 0) {
                kept.add(portion);
            } else {
                kept.set(at, new Portion(portion.resource(), kept.get(at).amount() + portion.amount()));
            }
        }
        portions = List.copyOf(kept);
    }

    /**
     * Content holding one fluid.
     *
     * @param resource the fluid resource
     * @param amount   the volume (mB)
     * @return the content, EMPTY where either is empty
     */
    public static CanisterFluidContent of(FluidResource resource, int amount) {
        return new CanisterFluidContent(List.of(new Portion(resource, amount)));
    }

    /**
     * Returns true if no fluid is stored.
     *
     * @return true if empty
     */
    public boolean isEmpty() {
        return portions.isEmpty();
    }

    /**
     * The volume of every fluid together, what the shared capacity bounds.
     *
     * @return the total volume (mB)
     */
    public int totalVolume() {
        int total = 0;
        for (Portion portion : portions) {
            total += portion.amount();
        }
        return total;
    }

    /**
     * The volume of one fluid.
     *
     * @param resource the fluid resource
     * @return its volume (mB), 0 where the canister holds none
     */
    public int volumeOf(FluidResource resource) {
        int at = indexIn(portions, resource);
        return at < 0 ? 0 : portions.get(at).amount();
    }

    /**
     * The volume of one goo type.
     *
     * @param type the goo type
     * @return its volume (mB), 0 where the canister holds none
     */
    public int volumeOf(ResourceKey<GooTypeDefinition> type) {
        for (Portion portion : portions) {
            if (type.equals(portion.gooType())) {
                return portion.amount();
            }
        }
        return 0;
    }

    /**
     * Every goo type held with its volume, in arrival order; vanilla fluids are left out.
     *
     * @return the goo volumes by type
     */
    public Map<ResourceKey<GooTypeDefinition>, Integer> gooVolumes() {
        Map<ResourceKey<GooTypeDefinition>, Integer> volumes = new LinkedHashMap<>();
        for (Portion portion : portions) {
            ResourceKey<GooTypeDefinition> type = portion.gooType();
            if (type != null) {
                volumes.merge(type, portion.amount(), Integer::sum);
            }
        }
        return volumes;
    }

    /**
     * The portion with the most volume, the first to arrive on a tie.
     *
     * @return the dominant portion, or null when empty
     */
    @Nullable
    public Portion dominant() {
        Portion best = null;
        for (Portion portion : portions) {
            if (best == null || portion.amount() > best.amount()) {
                best = portion;
            }
        }
        return best;
    }

    /**
     * The resource of the dominant portion.
     *
     * @return the resource, or {@link FluidResource#EMPTY} when empty
     */
    public FluidResource dominantResource() {
        Portion best = dominant();
        return best == null ? FluidResource.EMPTY : best.resource();
    }

    /**
     * The dominant fluid without its components, for render and tooltip code
     * that tells vanilla fluids apart.
     *
     * @return the fluid, or the empty fluid
     */
    public Fluid dominantFluid() {
        return dominantResource().getFluid();
    }

    /**
     * The goo type of the dominant portion, bundled or datapack-added alike.
     *
     * @return the goo type, or null where the canister is empty or holds a vanilla fluid
     */
    @Nullable
    public ResourceKey<GooTypeDefinition> dominantGooType() {
        return gooTypeOf(dominantResource());
    }

    /**
     * The goo type stamped on a resource (decision generic-goo-fluids).
     *
     * @param resource the fluid resource
     * @return the goo type, or null for a vanilla fluid or an empty resource
     */
    @Nullable
    static ResourceKey<GooTypeDefinition> gooTypeOf(FluidResource resource) {
        return GooFluids.keyOf(resource);
    }

    /**
     * Whether a fluid may join this content: an empty canister takes anything,
     * a fluid already inside takes more of itself, and a goo type joins other goo.
     * A vanilla fluid neither joins goo nor lets goo join it.
     *
     * @param candidate the resource to test
     * @return true if the candidate may be added
     */
    public boolean canAccept(FluidResource candidate) {
        if (candidate.isEmpty()) {
            return false;
        }
        if (isEmpty() || indexIn(portions, candidate) >= 0) {
            return true;
        }
        return gooTypeOf(candidate) != null && holdsGooAlone();
    }

    /**
     * Returns how much of the requested amount withCappedAdd would accept:
     * the shared capacity less every fluid's volume.
     *
     * @param addResource the resource to add
     * @param addAmount   the requested volume
     * @param capacity    the total capacity of the canister
     * @return the amount that would be accepted
     */
    public int cappedAddAmount(FluidResource addResource, int addAmount, int capacity) {
        if (addAmount <= 0 || !canAccept(addResource)) {
            return 0;
        }
        int space = capacity - totalVolume();
        return space <= 0 ? 0 : Math.min(addAmount, space);
    }

    /**
     * Returns a new content with the given volume added, capped by the shared capacity.
     *
     * @param addResource the resource to add
     * @param addAmount   the requested volume
     * @param capacity    the total capacity of the canister
     * @return new content with the capped addition, or this where nothing fits
     */
    public CanisterFluidContent withCappedAdd(FluidResource addResource, int addAmount, int capacity) {
        int accepted = cappedAddAmount(addResource, addAmount, capacity);
        return accepted <= 0 ? this : withVolume(addResource, volumeOf(addResource) + accepted);
    }

    /**
     * Returns a new content with up to the given volume of one fluid removed.
     *
     * @param removeResource the resource to remove
     * @param removeAmount   the volume to remove
     * @return new content with the removal applied
     */
    public CanisterFluidContent withRemoved(FluidResource removeResource, int removeAmount) {
        int held = volumeOf(removeResource);
        if (removeAmount <= 0 || held <= 0) {
            return this;
        }
        return withVolume(removeResource, Math.max(0, held - removeAmount));
    }

    /**
     * Returns a new content with one fluid set to a volume, kept in its place
     * when already held and appended otherwise; a zero volume drops it.
     *
     * @param resource the resource to set
     * @param volume   its new volume (mB)
     * @return the new content
     */
    public CanisterFluidContent withVolume(FluidResource resource, int volume) {
        List<Portion> next = new ArrayList<>(portions);
        int at = indexIn(next, resource);
        Portion portion = new Portion(resource, volume);
        if (at >= 0) {
            next.set(at, portion);
        } else {
            next.add(portion);
        }
        return new CanisterFluidContent(next);
    }

    /**
     * Whether every portion is goo, so another goo type may join.
     *
     * @return true when no vanilla fluid is held
     */
    private boolean holdsGooAlone() {
        for (Portion portion : portions) {
            if (portion.gooType() == null) {
                return false;
            }
        }
        return true;
    }

    /**
     * Finds the portion holding a resource.
     *
     * @param list     the portions to search
     * @param resource the resource to find
     * @return its index, or {@link #NOT_HELD}
     */
    private static int indexIn(List<Portion> list, FluidResource resource) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).resource().equals(resource)) {
                return i;
            }
        }
        return NOT_HELD;
    }
}
