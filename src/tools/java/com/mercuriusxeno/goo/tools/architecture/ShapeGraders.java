package com.mercuriusxeno.goo.tools.architecture;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The shape graders the findings document reads, each a pure function of the
 * scan model (decision one-command-regenerates-architectural-documentation).
 * The graders say what the code holds and rank nothing as good or bad.
 */
public final class ShapeGraders {

    /** A signature with more parameters than this is a long parameter list. */
    public static final int LONG_PARAMETER_BOUND = 7;
    /** The fewest members a type holds for its shape to be compared. */
    public static final int SAME_SHAPE_MINIMUM = 3;
    /** How many types a top list names. */
    public static final int TOP_LIST_SIZE = 10;
    private static final String SELF_DESCRIPTOR = "LSelf;";
    private static final String DESCRIPTOR_PREFIX = "L";
    private static final String DESCRIPTOR_SUFFIX = ";";
    private static final String FIELD_SHAPE = "field %s %s";
    private static final String METHOD_SHAPE = "method %s %s";
    private static final String KIND_SHAPE = "kind %s";
    private static final char QUALIFIED_SEPARATOR = '.';
    private static final char INTERNAL_SEPARATOR = '/';

    /**
     * Holds only static members.
     */
    private ShapeGraders() {
    }

    /**
     * How the types split between state and behaviour.
     *
     * @param types the types to grade
     * @return the four counts
     */
    public static DataBehaviourSplit dataBehaviourSplit(List<ScannedType> types) {
        return new DataBehaviourSplit(
                countWhere(types, false, true),
                countWhere(types, true, false),
                countWhere(types, true, true),
                countWhere(types, false, false));
    }

    /**
     * How many types hold state and behaviour as asked.
     *
     * @param types     the types
     * @param state     whether the counted types hold an instance field
     * @param behaviour whether the counted types hold a drawn method
     * @return the count
     */
    private static int countWhere(List<ScannedType> types, boolean state, boolean behaviour) {
        return (int) types.stream()
                .filter(type -> MemberSurface.hasState(type) == state)
                .filter(type -> MemberSurface.hasBehaviour(type) == behaviour)
                .count();
    }

    /**
     * The types whose member count exceeds what all but a twentieth of the set hold.
     *
     * @param types the types to grade
     * @return the threshold and the types above it, widest first
     */
    public static WideSurface wideSurface(List<ScannedType> types) {
        int threshold = TwentiethRule.threshold(types.stream().map(MemberSurface::memberCount).toList());
        List<CountedType> wide = counted(types, MemberSurface::memberCount)
                .filter(entry -> entry.count() > threshold)
                .toList();
        return new WideSurface(threshold, wide);
    }

    /**
     * The constructors and methods taking more than {@link #LONG_PARAMETER_BOUND} parameters.
     *
     * @param types the types to grade
     * @return each long signature, longest first
     */
    public static List<LongSignature> longParameterLists(List<ScannedType> types) {
        return types.stream()
                .flatMap(type -> type.methods().stream()
                        .filter(method -> method.parameterCount() > LONG_PARAMETER_BOUND)
                        .map(method -> new LongSignature(type,
                                method.isConstructor() ? type.simpleName() : method.name(),
                                method.parameterCount())))
                .sorted(Comparator.comparingInt(LongSignature::parameters).reversed()
                        .thenComparing(signature -> signature.type().qualifiedName()))
                .toList();
    }

    /**
     * The groups of types built the same way under different names: the same
     * kind, and members of the same types, read without their names.
     *
     * @param types the types to grade
     * @return each group of two or more, ordered by its first type's name
     */
    public static List<List<ScannedType>> sameShapes(List<ScannedType> types) {
        Map<List<String>, List<ScannedType>> byShape = types.stream()
                .filter(type -> MemberSurface.memberCount(type) >= SAME_SHAPE_MINIMUM)
                .sorted(Comparator.comparing(ScannedType::qualifiedName))
                .collect(Collectors.groupingBy(ShapeGraders::shapeOf, HashMap::new, Collectors.toList()));
        return byShape.values().stream()
                .filter(group -> group.size() > 1)
                .sorted(Comparator.comparing(group -> group.getFirst().qualifiedName()))
                .toList();
    }

    /**
     * The types whose fan-in or fan-out exceeds what all but a twentieth of the set hold.
     *
     * @param types the types to grade; references outside them are read past
     * @return the thresholds and the hubs, most coupled first
     */
    public static CouplingHubs couplingHubs(List<ScannedType> types) {
        DirectedGraph graph = DirectedGraph.ofTypes(types);
        Map<String, Integer> fanIn = new HashMap<>();
        graph.adjacency().values().forEach(targets -> targets.forEach(target -> fanIn.merge(target, 1, Integer::sum)));
        List<CouplingHub> all = types.stream()
                .map(type -> new CouplingHub(type, fanIn.getOrDefault(type.qualifiedName(), 0),
                        graph.adjacency().get(type.qualifiedName()).size()))
                .toList();
        int inThreshold = TwentiethRule.threshold(all.stream().map(CouplingHub::fanIn).toList());
        int outThreshold = TwentiethRule.threshold(all.stream().map(CouplingHub::fanOut).toList());
        List<CouplingHub> hubs = all.stream()
                .filter(hub -> hub.fanIn() > inThreshold || hub.fanOut() > outThreshold)
                .sorted(Comparator.comparingInt((CouplingHub hub) -> hub.fanIn() + hub.fanOut()).reversed()
                        .thenComparing(hub -> hub.type().qualifiedName()))
                .toList();
        return new CouplingHubs(inThreshold, outThreshold, hubs);
    }

    /**
     * The types holding the most {@code instanceof} checks.
     *
     * @param types the types to grade
     * @return up to {@link #TOP_LIST_SIZE} types holding at least one, most first
     */
    public static List<CountedType> instanceofLadders(List<ScannedType> types) {
        return counted(types, ScannedType::instanceofChecks)
                .filter(entry -> entry.count() > 0)
                .limit(TOP_LIST_SIZE)
                .toList();
    }

    /**
     * The types that count highest by a measure.
     *
     * @param types   the types to grade
     * @param measure the count to rank by, such as member count or source lines
     * @return up to {@link #TOP_LIST_SIZE} types, highest first
     */
    public static List<CountedType> largest(List<ScannedType> types, ToIntFunction<ScannedType> measure) {
        return counted(types, measure).limit(TOP_LIST_SIZE).toList();
    }

    /**
     * Each type beside its count, highest first, ties by name.
     *
     * @param types   the types
     * @param measure the count
     * @return the counted types, sorted
     */
    private static Stream<CountedType> counted(List<ScannedType> types, ToIntFunction<ScannedType> measure) {
        return types.stream()
                .map(type -> new CountedType(type, measure.applyAsInt(type)))
                .sorted(Comparator.comparingInt(CountedType::count).reversed()
                        .thenComparing(entry -> entry.type().qualifiedName()));
    }

    /**
     * A type's shape: its kind, then each field and each method as its static
     * flag and descriptor, sorted, with the type's own name read as {@code Self}.
     *
     * @param type the type
     * @return the shape, equal for two types built the same way
     */
    private static List<String> shapeOf(ScannedType type) {
        String self = DESCRIPTOR_PREFIX + type.qualifiedName().replace(QUALIFIED_SEPARATOR, INTERNAL_SEPARATOR)
                + DESCRIPTOR_SUFFIX;
        List<String> shape = new ArrayList<>();
        MemberSurface.drawnFields(type).forEach(field -> shape.add(String.format(FIELD_SHAPE, field.isStatic(),
                field.descriptor().replace(self, SELF_DESCRIPTOR))));
        type.methods().forEach(method -> shape.add(String.format(METHOD_SHAPE, method.isStatic(),
                method.descriptor().replace(self, SELF_DESCRIPTOR))));
        shape.sort(Comparator.naturalOrder());
        shape.addFirst(String.format(KIND_SHAPE, type.kind()));
        return shape;
    }
}
