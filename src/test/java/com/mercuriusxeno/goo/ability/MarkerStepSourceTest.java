package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

/**
 * A chain marker loads its program from the abilities its side holds: the
 * client from the abilities it was synced, the server from the registry
 * (decision diagnose-then-fix-marker-server-gate).
 */
class MarkerStepSourceTest {

    private static final String ABILITY_ID = "goo:rock_throw";
    private static final boolean CLIENT = true;
    private static final boolean SERVER = false;

    private static List<Step> program() {
        return List.of(mock(Step.class));
    }

    @Nested
    class OnTheClient {

        /**
         * The registry stands empty, as a dedicated server's client holds it,
         * so only the synced steps can build the program.
         */
        @Test
        void syncedStepsBuildTheProgram() {
            List<Step> synced = program();
            MarkerStepSource syncedSource = id -> ABILITY_ID.equals(id) ? synced : null;

            MarkerStepSource source = MarkerStepSource.forSide(CLIENT, syncedSource, AbilityRegistry.EMPTY);

            assertEquals(synced, source.steps(ABILITY_ID));
            assertNotNull(source.program(ABILITY_ID));
        }

        @Test
        void abilityNeverSyncedLoadsNoProgram() {
            assertNull(MarkerStepSource.forSide(CLIENT, MarkerStepSource.NONE, AbilityRegistry.EMPTY).program(ABILITY_ID));
        }
    }

    @Nested
    class OnTheServer {

        /**
         * The client source answers nothing, so a program built here came
         * from the registry.
         */
        @Test
        void registeredStepsBuildTheProgram() {
            List<Step> registered = program();
            Identifier id = Identifier.parse(ABILITY_ID);
            AbilityRegistry registry = new AbilityRegistry(Map.of(id, new AbilityDefinition(id, GooTypes.ROCK,
                    id.getPath(), "", 0, 0, AbilityDefinition.ChainConfig.DEFAULT, Delivery.ARC, registered, List.of())));

            MarkerStepSource source = MarkerStepSource.forSide(SERVER, MarkerStepSource.NONE, registry);

            assertEquals(registered, source.steps(ABILITY_ID));
            assertNotNull(source.program(ABILITY_ID));
        }
    }
}
