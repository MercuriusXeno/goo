package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The glow crystal program resolves its state from the host: facing from
 * the placed face, shape as named, size from the stack count clamped to
 * the size list; the host receives value names alone.
 */
class PlaceBlockStepTest {

    private static final Identifier GLOW_CRYSTAL = Identifier.parse("goo:glow_crystal");
    private static final String FACING = "facing";
    private static final String SHAPE = "shape";
    private static final String SIZE = "size";
    private static final List<String> SIZES = List.of("tiny", "small", "medium", "large");

    private static PlaceBlockStep glowCrystal() {
        return new PlaceBlockStep(GLOW_CRYSTAL, Map.of(
                FACING, new StateValue.PlacedFace(),
                SHAPE, new StateValue.Named("bump"),
                SIZE, new StateValue.Pick(expr("stacks - 1"), SIZES)));
    }

    private static Expr expr(String source) {
        return Expr.parse(source).getOrThrow();
    }

    private static MarkerHost host(Direction placedFace, int stacks) {
        MarkerHost host = mock(MarkerHost.class);
        when(host.placedFace()).thenReturn(placedFace);
        when(host.read(HostVariables.STACKS)).thenReturn(OptionalDouble.of(stacks));
        return host;
    }

    @ParameterizedTest
    @CsvSource({
            "NORTH, 1, tiny",
            "UP, 2, small",
            "EAST, 4, large",
            "DOWN, 9, large",
            "WEST, 0, tiny"})
    void stateResolvesFromTheHost(Direction face, int stacks, String size) {
        MarkerHost host = host(face, stacks);
        ProgramBehavior program = new ProgramBehavior(List.of(glowCrystal()));

        program.tick(host);

        verify(host).placeBlock(GLOW_CRYSTAL, Map.of(FACING, face.getName(), SHAPE, "bump", SIZE, size));
        assertFalse(program.isActive());
    }

    @Test
    void namedValuePassesThrough() {
        MarkerHost host = host(Direction.SOUTH, 1);
        PlaceBlockStep step = new PlaceBlockStep(GLOW_CRYSTAL, Map.of(SHAPE, new StateValue.Named("flat")));

        new ProgramBehavior(List.of(step)).tick(host);

        verify(host).placeBlock(GLOW_CRYSTAL, Map.of(SHAPE, "flat"));
    }

    @Test
    void placedFaceValueAsksForTheFaceCapability() {
        assertEquals(Set.of(HostCapability.PLACE_BLOCK, HostCapability.PLACED_FACE), glowCrystal().requires());
        assertEquals(Set.of(HostCapability.PLACE_BLOCK),
                new PlaceBlockStep(GLOW_CRYSTAL, Map.of(SHAPE, new StateValue.Named("flat"))).requires());
    }
}
