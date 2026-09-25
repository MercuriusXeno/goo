package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.GooTypes;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import static com.mercuriusxeno.goo.data.TestRecipeBuilder.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A reader of goo values holds a consistent snapshot while a regen derivation
 * or a received sync replaces the values on another thread
 * (decision diagnose-then-fix-server-link-and-value-race).
 */
class GooValueSnapshotTest {

    private static final int ITEM_COUNT = 4000;
    private static final int REPLACEMENTS = 100;
    private static final Identifier KNOWN_ITEM = id("goo:base_0");

    @Test
    void readerSeesEveryValueWhileRegenAndSyncReplaceThem() throws InterruptedException {
        GooValueRegistry registry = new GooValueRegistry();
        Map<Identifier, GooValue> baseValues = baseValues();
        registry.baseValues.putAll(baseValues);
        registry.receiveClientValues(baseValues);
        List<RecipeInput> recipes = recipes();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread replacer = new Thread(() -> {
            for (int i = 0; i < REPLACEMENTS; i++) {
                registry.deriveFromRecipeInputs(recipes, false);
                registry.receiveClientValues(baseValues);
            }
        });
        replacer.start();
        while (replacer.isAlive() && failure.get() == null) {
            readEveryValue(registry, failure);
        }
        replacer.join();

        assertNull(failure.get(), () -> "a reader saw an inconsistent snapshot: " + failure.get());
    }

    private static void readEveryValue(GooValueRegistry registry, AtomicReference<Throwable> failure) {
        try {
            if (registry.lookup(KNOWN_ITEM) == null) {
                failure.set(new AssertionError("lookup of " + KNOWN_ITEM + " answered no value"));
                return;
            }
            long blobs = 0;
            for (GooValue value : registry.getEffectiveValues().values()) {
                blobs += value.totalBlobs();
            }
            assertTrue(blobs > 0);
        } catch (RuntimeException | AssertionError e) {
            failure.set(e);
        }
    }

    private static Map<Identifier, GooValue> baseValues() {
        Map<Identifier, GooValue> values = new HashMap<>();
        for (int i = 0; i < ITEM_COUNT; i++) {
            values.put(id("goo:base_" + i), goo(GooTypes.METAL, i + 1));
        }
        return values;
    }

    private static List<RecipeInput> recipes() {
        List<RecipeInput> recipes = new ArrayList<>();
        for (int i = 0; i < ITEM_COUNT; i++) {
            recipes.add(recipe("goo:derived_" + i, 1, slot("goo:base_" + i), slot("goo:base_" + i)));
        }
        return recipes;
    }
}
