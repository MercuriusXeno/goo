package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.GooValueRegistry;
import com.mercuriusxeno.goo.data.GooValues;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Items;

/**
 * The goo value registry lives for its server's life: a stop drops it, and
 * the next start stands a fresh one that reads values again
 * (decision type-package-and-per-server-holders).
 */
public final class GooValueLifecycleTests {

    private static final String STOPPED_KEPT_VALUES = "A stopped server should answer no goo values";
    private static final String START_REUSED_REGISTRY = "A start should stand a fresh registry, not the stopped one";
    private static final String FRESH_LACKS_VALUE = "The fresh registry should value cobblestone";

    private GooValueLifecycleTests() {
    }

    /**
     * Runs the server's stop and start value handling in one tick, so no other
     * test reads between them: after the stop the level answers no values,
     * and after the start it answers cobblestone's value from a new registry.
     *
     * @param helper the gametest helper
     */
    public static void freshRegistryReadsAfterStop(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GooValueRegistry stopped = GooValues.registryOf(server);
        GooValues.detach(server);
        int valuesAfterStop;
        try {
            valuesAfterStop = GooValues.of(helper.getLevel()).size();
        } finally {
            GooValues.attach(server);
        }
        helper.assertTrue(valuesAfterStop == 0, STOPPED_KEPT_VALUES);
        helper.assertTrue(GooValues.registryOf(server) != stopped, START_REUSED_REGISTRY);
        GooValue cobblestone = GooValues.of(helper.getLevel())
                .lookup(BuiltInRegistries.ITEM.getKey(Items.COBBLESTONE));
        helper.assertTrue(cobblestone != null && !cobblestone.isEmpty(), FRESH_LACKS_VALUE);
        helper.succeed();
    }
}
