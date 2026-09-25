package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.block.crucible.CrucibleBlock;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** CompiledStateDefinition reads a block's state properties from its class file, in the order the block adds them. */
class CompiledStateDefinitionTest {

    @Test
    void crucibleReadsFacingPoweredLitAndGasket() {
        assertEquals(List.of(
                        Map.entry("facing", List.of("north", "south", "west", "east")),
                        Map.entry("powered", List.of("true", "false")),
                        Map.entry("lit", List.of("true", "false")),
                        Map.entry("has_gasket", List.of("true", "false"))),
                List.copyOf(CompiledStateDefinition.of(CrucibleBlock.class).entrySet()));
    }
}
