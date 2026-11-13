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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The glow crystal program resolves its state from the host: facing from
 * the placed face, shape and size as named; a pick indexes its values
 * clamped to the list; the host receives value names alone.
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
                SIZE, new StateValue.Named("tiny")));
    }

    private static Expr expr(String source) {
        return Expr.parse(source).getOrThrow();
    }

    private static MarkerHost host(Direction placedFace) {
        MarkerHost host = mock(MarkerHost.class);
        when(host.placedFace()).thenReturn(placedFace);
        when(host.read(anyString())).thenReturn(OptionalDouble.empty());
        return host;
    }

    // decision place-block-ability-grows-block
    @Test
    void glowCrystalFacesThePlacedFaceAtItsOneSize() {
        MarkerHost host = host(Direction.NORTH);
        ProgramBehavior program = new ProgramBehavior(List.of(glowCrystal()));

        program.tick(host);

        verify(host).placeBlock(GLOW_CRYSTAL, Map.of(FACING, Direction.NORTH.getName(), SHAPE, "bump", SIZE, "tiny"));
        assertFalse(program.isActive());
    }

    @ParameterizedTest
    @CsvSource({"0, tiny", "2, medium", "9, large", "-1, tiny"})
    void pickIndexesItsValuesClampedToTheList(int index, String size) {
        StateValue.Pick pick = new StateValue.Pick(expr(String.valueOf(index)), SIZES);

        assertEquals(size, pick.resolve(new StepContext(host(Direction.UP), 0, 0)));
    }

    @Test
    void placedFaceValueAsksForTheFaceCapability() {
        assertEquals(Set.of(HostCapability.PLACE_BLOCK, HostCapability.PLACED_FACE), glowCrystal().requires());
        assertEquals(Set.of(HostCapability.PLACE_BLOCK),
                new PlaceBlockStep(GLOW_CRYSTAL, Map.of(SHAPE, new StateValue.Named("flat"))).requires());
    }
}
