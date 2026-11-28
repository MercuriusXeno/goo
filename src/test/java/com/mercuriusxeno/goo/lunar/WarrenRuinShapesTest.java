package com.mercuriusxeno.goo.lunar;

import com.mercuriusxeno.goo.lunar.WarrenRuinShapes.Part;
import com.mercuriusxeno.goo.lunar.WarrenRuinShapes.Placement;
import com.mercuriusxeno.goo.lunar.WarrenRuinShapes.Shape;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The warrens' mini structures: at least six shapes, no two laying the same blocks, and each laying at least two
 * different members of the alien structure family, so no ruin is one block repeated.
 * decision warrens-hold-xeno-rich-alien-blocks
 */
class WarrenRuinShapesTest {

    private static final int FEWEST_SHAPES = 6;
    private static final int FEWEST_MEMBERS_PER_SHAPE = 2;

    static List<Shape> shapes() {
        return WarrenRuinShapes.ALL;
    }

    @Test
    void theWarrensHoldAtLeastSixDistinctShapes() {
        Set<Set<Placement>> layouts = shapes().stream().map(shape -> Set.copyOf(shape.blocks()))
                .collect(Collectors.toCollection(HashSet::new));
        assertTrue(shapes().size() >= FEWEST_SHAPES, "the warrens hold " + shapes().size() + " shapes");
        assertEquals(shapes().size(), layouts.size(), "two shapes lay the same blocks");
    }

    @ParameterizedTest
    @MethodSource("shapes")
    void eachShapeLaysAtLeastTwoFamilyMembers(Shape shape) {
        Set<Part> members = shape.blocks().stream().map(placement -> placement.part().member())
                .filter(Objects::nonNull).collect(Collectors.toSet());
        assertTrue(members.size() >= FEWEST_MEMBERS_PER_SHAPE, shape.name() + " lays only " + members);
    }
}
