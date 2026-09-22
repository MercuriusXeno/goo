package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.registry.GooFluids;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A single reactor reaction: consumes inputs, produces outputs at a
 * ratio. Loaded from datapack JSON under {@code data/<ns>/goo_reactions/}.
 *
 * @param id      the datapack resource identifier
 * @param inputs  fluids consumed per batch
 * @param outputs fluids produced per batch (amounts scaled by rate)
 * @param rate    output multiplier ratio
 */
public record GooReaction(
        Identifier id,
        List<FluidEntry> inputs,
        List<FluidEntry> outputs,
        int rate
) {

    /**
     * Placeholder id used during codec parsing; replaced by filename in the loader.
     */
    private static final Identifier PLACEHOLDER_ID = Identifier.withDefaultNamespace("unknown");

    /**
     * Codec for the reaction JSON. The id is not in the JSON; it comes from the filename.
     */
    public static final Codec<GooReaction> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            FluidEntry.CODEC.listOf().fieldOf("inputs").forGetter(GooReaction::inputs),
            FluidEntry.CODEC.listOf().fieldOf("outputs").forGetter(GooReaction::outputs),
            Codec.INT.fieldOf("rate").forGetter(GooReaction::rate)
    ).apply(inst, (inputs, outputs, rate) -> new GooReaction(PLACEHOLDER_ID, inputs, outputs, rate)));

    /**
     * Returns the set of input fluid spellings (ignoring amounts).
     *
     * @return the input spelling set
     */
    public Set<Either<ResourceKey<GooTypeDefinition>, Fluid>> inputTypeSet() {
        return inputs.stream().map(FluidEntry::fluid).collect(Collectors.toSet());
    }

    /**
     * Returns a copy with the datapack resource id set.
     *
     * @param recipeId the resource identifier
     * @return the reaction with id applied
     */
    public GooReaction withId(Identifier recipeId) {
        return new GooReaction(recipeId, inputs, outputs, rate);
    }

    /**
     * A fluid + amount pair used in recipe inputs and outputs. A goo type
     * is the generic goo fluid stamped with the type (decision
     * generic-goo-fluids), so the JSON names it by its type key under
     * {@code goo}; a vanilla fluid is named by its fluid id under
     * {@code fluid}. The entry keeps the spelling and builds the resource
     * on demand, since fluid components are unbound while datapacks load.
     *
     * @param fluid  a goo type key on the left, or a vanilla fluid on the right
     * @param amount mB consumed or produced per batch
     */
    public record FluidEntry(Either<ResourceKey<GooTypeDefinition>, Fluid> fluid, int amount) {

        /**
         * JSON key naming a goo type by its registry key.
         */
        public static final String GOO = "goo";
        /**
         * JSON key naming a vanilla fluid by its id.
         */
        public static final String FLUID = "fluid";

        /**
         * A fluid as either a goo type key or a vanilla fluid id.
         */
        public static final MapCodec<Either<ResourceKey<GooTypeDefinition>, Fluid>> FLUID_CODEC = Codec.mapEither(
                ResourceKey.codec(GooTypes.REGISTRY).fieldOf(GOO),
                BuiltInRegistries.FLUID.byNameCodec().fieldOf(FLUID));

        /**
         * Codec for a single fluid entry.
         */
        public static final Codec<FluidEntry> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                FLUID_CODEC.forGetter(FluidEntry::fluid),
                Codec.INT.fieldOf("amount").forGetter(FluidEntry::amount)
        ).apply(inst, FluidEntry::new));

        /**
         * The resource the entry names: the goo fluid stamped with the type,
         * or the bare vanilla fluid.
         *
         * @return the fluid resource containers hold for this entry
         */
        public FluidResource resource() {
            return fluid.map(GooFluids::resource, FluidResource::of);
        }
    }
}
