package com.mercuriusxeno.goo.tools.architecture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The shape graders over hand-built type models: an outlier among twenty
 * peers is graded, a signature past seven parameters is graded, types built
 * the same way under different names are grouped, and a type every other
 * type references is a hub.
 */
class ShapeGradersTest {

    private static final String PACKAGE = "com.mercuriusxeno.goo.sample";
    private static final int PEERS = 20;
    private static final int PEER_MEMBERS = 10;
    private static final int WIDE_MEMBERS = 30;

    private static ScannedType type(String name, List<ScannedField> fields, List<ScannedMethod> methods,
                                    Set<String> references) {
        return new ScannedType(PACKAGE, name, TypeKind.CLASS, "src/main/java/sample/" + name + ".java",
                fields, methods, references, 0);
    }

    private static ScannedType typeWithMethods(String name, int methodCount) {
        List<ScannedMethod> methods = IntStream.range(0, methodCount)
                .mapToObj(index -> new ScannedMethod("m" + index, "()V", 0, false))
                .toList();
        return type(name, List.of(), methods, Set.of());
    }

    private static List<ScannedType> peersAnd(ScannedType extra) {
        List<ScannedType> types = new ArrayList<>();
        IntStream.range(0, PEERS).forEach(index -> types.add(typeWithMethods("Peer" + index, PEER_MEMBERS)));
        types.add(extra);
        return types;
    }

    static Stream<Arguments> extrasAndWhetherWide() {
        return Stream.of(
                Arguments.of(WIDE_MEMBERS, List.of("Extra")),
                Arguments.of(PEER_MEMBERS, List.of()),
                Arguments.of(PEER_MEMBERS + 1, List.of("Extra")));
    }

    @ParameterizedTest(name = "{0} members among ten-member peers")
    @MethodSource("extrasAndWhetherWide")
    void wideSurface(int extraMembers, List<String> expectedWide) {
        WideSurface wide = ShapeGraders.wideSurface(peersAnd(typeWithMethods("Extra", extraMembers)));
        assertEquals(PEER_MEMBERS, wide.threshold());
        assertEquals(expectedWide, wide.types().stream().map(entry -> entry.type().simpleName()).toList());
    }

    @ParameterizedTest(name = "{0} parameters, graded {1}")
    @CsvSource({"9, true", "8, true", "7, false", "0, false"})
    void longParameterLists(int parameters, boolean graded) {
        ScannedType type = type("Wide", List.of(), List.of(
                new ScannedMethod("<init>", "(" + "I".repeat(parameters) + ")V", parameters, false)), Set.of());
        List<LongSignature> expected = graded ? List.of(new LongSignature(type, "Wide", parameters)) : List.of();
        assertEquals(expected, ShapeGraders.longParameterLists(List.of(type)));
    }

    @Test
    void sameShapesGroupsTypesBuiltAlikeUnderDifferentNames() {
        List<ScannedField> fields = List.of(new ScannedField("amount", "I", false, true));
        List<ScannedMethod> methods = List.of(new ScannedMethod("apply", "(I)Z", 1, false),
                new ScannedMethod("reset", "()V", 0, false));
        ScannedType first = type("FirstStep", fields, methods, Set.of());
        ScannedType second = type("SecondStep", List.of(new ScannedField("count", "I", false, true)),
                List.of(new ScannedMethod("test", "(I)Z", 1, false), new ScannedMethod("clear", "()V", 0, false)),
                Set.of());
        ScannedType different = type("OtherStep", fields, List.of(new ScannedMethod("apply", "(J)Z", 1, false),
                new ScannedMethod("reset", "()V", 0, false)), Set.of());
        assertEquals(List.of(List.of(first, second)), ShapeGraders.sameShapes(List.of(different, second, first)));
    }

    @Test
    void couplingHubsGradesTheTypeEveryOtherTypeReferences() {
        String hubName = PACKAGE + ".Hub";
        List<ScannedType> types = new ArrayList<>();
        IntStream.range(0, PEERS).forEach(index ->
                types.add(type("Peer" + index, List.of(), List.of(), Set.of(hubName))));
        types.add(type("Hub", List.of(), List.of(), Set.of()));
        CouplingHubs hubs = ShapeGraders.couplingHubs(types);
        assertEquals(List.of(new CouplingHub(types.getLast(), PEERS, 0)), hubs.hubs());
        assertEquals(0, hubs.fanInThreshold());
        assertEquals(1, hubs.fanOutThreshold());
    }

    @Test
    void dataBehaviourSplitCountsEachCombination() {
        ScannedField state = new ScannedField("value", "I", false, false);
        ScannedMethod behaviour = new ScannedMethod("run", "()V", 0, false);
        ScannedField mutableStatic = new ScannedField("cache", "I", true, false);
        List<ScannedType> types = List.of(
                type("Behaviour", List.of(), List.of(behaviour), Set.of()),
                type("State", List.of(state), List.of(), Set.of()),
                type("Both", List.of(state), List.of(behaviour), Set.of()),
                type("Both2", List.of(state), List.of(behaviour), Set.of()),
                type("Neither", List.of(mutableStatic), List.of(), Set.of()));
        assertEquals(new DataBehaviourSplit(1, 1, 2, 1), ShapeGraders.dataBehaviourSplit(types));
    }

    @Test
    void instanceofLaddersListsHoldersMostFirst() {
        ScannedType few = new ScannedType(PACKAGE, "Few", TypeKind.CLASS, "Few.java", List.of(), List.of(),
                Set.of(), 2);
        ScannedType many = new ScannedType(PACKAGE, "Many", TypeKind.CLASS, "Many.java", List.of(), List.of(),
                Set.of(), 20);
        ScannedType none = typeWithMethods("None", 1);
        assertEquals(List.of(new CountedType(many, 20), new CountedType(few, 2)),
                ShapeGraders.instanceofLadders(List.of(few, none, many)));
    }
}
