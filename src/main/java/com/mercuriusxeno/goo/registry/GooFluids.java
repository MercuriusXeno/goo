package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.fluid.GooFluid;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jspecify.annotations.Nullable;

/**
 * Registers the one goo source fluid and its flowing variant (decision
 * generic-goo-fluids). A goo type is not a fluid but a
 * {@link GooDataComponents#GOO_TYPE} component on a {@link FluidResource}
 * of that fluid, so containers key goo by resource, and the in-world type
 * rides on the fluid block's block entity. Molten iron, lunar's core liquid,
 * registers here beside goo as a fluid of its own.
 */
public final class GooFluids {
    /**
     * Deferred register for vanilla fluids.
     */
    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(Registries.FLUID, Goo.MODID);

    /**
     * Registry path of the source fluid, the fluid type and the liquid block.
     */
    public static final String GOO_PATH = "goo";

    /**
     * The goo source fluid.
     */
    public static final DeferredHolder<Fluid, GooFluid.Source> SOURCE =
            FLUIDS.register(GOO_PATH, () -> new GooFluid.Source(fluidProperties()));

    /**
     * The goo flowing fluid.
     */
    public static final DeferredHolder<Fluid, GooFluid.Flowing> FLOWING =
            FLUIDS.register(GOO_PATH + "_flowing", () -> new GooFluid.Flowing(fluidProperties()));

    /**
     * Registry path of molten iron's source fluid, its fluid type and its liquid block.
     */
    public static final String MOLTEN_IRON_PATH = "molten_iron";

    /**
     * Molten iron's source fluid, sitting where it is placed as goo does.
     * decision lunar-matter-is-anorthosite-basalt-regolith-and-an-iron-core
     */
    public static final DeferredHolder<Fluid, GooFluid.Source> MOLTEN_IRON_SOURCE =
            FLUIDS.register(MOLTEN_IRON_PATH, () -> new GooFluid.Source(moltenIronProperties()));

    /**
     * Molten iron's flowing fluid.
     */
    public static final DeferredHolder<Fluid, GooFluid.Flowing> MOLTEN_IRON_FLOWING =
            FLUIDS.register(MOLTEN_IRON_PATH + "_flowing", () -> new GooFluid.Flowing(moltenIronProperties()));

    private GooFluids() {
    }

    /**
     * The resource that is one goo type: the source fluid stamped with the
     * type's key.
     *
     * @param key the goo type's registry key
     * @return the resource containers store and transfer for that type
     */
    public static FluidResource resource(ResourceKey<GooTypeDefinition> key) {
        return FluidResource.of(SOURCE.get()).with(GooDataComponents.GOO_TYPE, key);
    }


    /**
     * Whether a fluid is the goo fluid, source or flowing.
     *
     * @param fluid the fluid
     * @return true for either goo fluid
     */
    public static boolean isGoo(Fluid fluid) {
        return fluid == SOURCE.get() || fluid == FLOWING.get();
    }

    /**
     * The goo type key a resource carries.
     *
     * @param resource a fluid resource
     * @return the key, or null for a resource that is not stamped goo
     */
    @Nullable
    public static ResourceKey<GooTypeDefinition> keyOf(FluidResource resource) {
        return resource.isEmpty() || !isGoo(resource.getFluid())
                ? null
                : resource.getComponents().get(GooDataComponents.GOO_TYPE.get());
    }


    /**
     * Builds the shared fluid properties linking source, flowing, fluid type
     * and block. The bucket is left unset: the fluid type answers the goo
     * bucket stamped for a stack's type.
     *
     * @return the configured fluid properties
     */
    private static BaseFlowingFluid.Properties fluidProperties() {
        return new BaseFlowingFluid.Properties(GooFluidTypes.GOO, SOURCE, FLOWING)
                .block(GooBlocks.GOO_FLUID);
    }

    private static BaseFlowingFluid.Properties moltenIronProperties() {
        return new BaseFlowingFluid.Properties(GooFluidTypes.MOLTEN_IRON, MOLTEN_IRON_SOURCE, MOLTEN_IRON_FLOWING)
                .block(GooBlocks.MOLTEN_IRON)
                .bucket(GooItems.MOLTEN_IRON_BUCKET);
    }
}
