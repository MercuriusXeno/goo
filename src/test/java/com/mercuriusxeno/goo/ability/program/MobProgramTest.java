package com.mercuriusxeno.goo.ability.program;

import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.OptionalDouble;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Each migrated mob ability JSON is a program that, run on a Mockito
 * struck-entity host, makes the same effect and damage calls its deleted
 * handler made (task mob-effect-programs).
 */
class MobProgramTest {

    private static final String ABILITIES_DIR = "data/goo/goo_abilities/";
    private static final String JSON_SUFFIX = ".json";
    private static final String LEVITATION = "minecraft:levitation";
    private static final String SLOWNESS = "minecraft:slowness";
    private static final String POISON = "minecraft:poison";
    private static final double COW_HEALTH = 10;
    private static final int LEVITATE_DURATION = 100;
    private static final int LEVITATE_AMPLIFIER = 1;
    private static final int ENTANGLE_SLOW_DURATION = 100;
    private static final int ENTANGLE_SLOW_AMPLIFIER = 2;
    private static final int ENTANGLE_POISON_DURATION = 60;

    private static AbilityDefinition ability(String name) {
        String path = ABILITIES_DIR + name + JSON_SUFFIX;
        InputStream stream = MobProgramTest.class.getClassLoader().getResourceAsStream(path);
        assertNotNull(stream, path);
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return AbilityDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader)).getOrThrow();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static StepHost entityHost() {
        StepHost host = mock(StepHost.class);
        when(host.kind()).thenReturn(HostKind.ENTITY);
        when(host.read(HostVariables.HEALTH)).thenReturn(OptionalDouble.of(COW_HEALTH));
        when(host.read(HostVariables.MAX_HEALTH)).thenReturn(OptionalDouble.of(COW_HEALTH));
        return host;
    }

    /**
     * Runs every behavior entry of the ability on the host as the struck
     * entity path does, asserting each entry is a program.
     *
     * @param name the ability file name without its suffix
     * @param host the host to run against
     */
    private static void run(String name, StepHost host) {
        for (AbilityDefinition.BehaviorEntry entry : ability(name).behaviors()) {
            assertEquals(ProgramBehavior.TYPE_NAME, entry.type(), name);
            ProgramBehavior.forHost(entry.steps(), HostKind.ENTITY).tick(host);
        }
    }

    @Test
    void typhoonLevitateAppliesLevitation() {
        StepHost host = entityHost();

        run("typhoon_levitate", host);

        verify(host).applyPotion(Identifier.parse(LEVITATION), LEVITATE_DURATION, LEVITATE_AMPLIFIER, true);
    }

    @Test
    void leafEntangleAppliesSlownessThenPoison() {
        StepHost host = entityHost();

        run("leaf_entangle", host);

        InOrder order = inOrder(host);
        order.verify(host).applyPotion(Identifier.parse(SLOWNESS), ENTANGLE_SLOW_DURATION, ENTANGLE_SLOW_AMPLIFIER, true);
        order.verify(host).applyPotion(Identifier.parse(POISON), ENTANGLE_POISON_DURATION, 0, true);
        verifyNoMoreInteractions(host);
    }
}
