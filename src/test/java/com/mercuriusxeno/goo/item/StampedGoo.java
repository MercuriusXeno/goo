package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.registry.GooFluids;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.mockito.MockedStatic;
import java.util.HashMap;
import java.util.Map;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * Stands goo-stamped fluid resources in the unit suite, where the goo fluid is
 * unregistered: each resource is a mock whose type GooFluids answers through a
 * static mock, and a resource never stamped answers no type, as a vanilla fluid does.
 * Close it after each test to release the static mock.
 */
public final class StampedGoo implements AutoCloseable {

    static {
        try (MockedStatic<FMLLoader> loader = mockStatic(FMLLoader.class, RETURNS_DEEP_STUBS)) {
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
        }
    }

    private final Map<FluidResource, ResourceKey<GooTypeDefinition>> types = new HashMap<>();
    private final MockedStatic<GooFluids> gooFluids = mockStatic(GooFluids.class);

    /**
     * Opens the static mock of GooFluids, once the vanilla bootstrap stands the
     * registries a fluid resource's class reads.
     */
    public StampedGoo() {
        gooFluids.when(() -> GooFluids.keyOf(any())).thenAnswer(call -> types.get(call.<FluidResource>getArgument(0)));
    }

    /**
     * @param type the goo type to stamp
     * @return a resource GooFluids answers that type for
     */
    public FluidResource resource(ResourceKey<GooTypeDefinition> type) {
        FluidResource resource = mock(FluidResource.class);
        types.put(resource, type);
        return resource;
    }

    /**
     * @return a resource GooFluids answers no type for, standing in for a vanilla fluid
     */
    public FluidResource vanilla() {
        return mock(FluidResource.class);
    }

    @Override
    public void close() {
        gooFluids.close();
    }
}
