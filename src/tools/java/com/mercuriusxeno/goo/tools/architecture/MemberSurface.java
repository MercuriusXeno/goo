package com.mercuriusxeno.goo.tools.architecture;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The members of a type its source wrote, which every shape grader counts:
 * its fields less the constants, and its methods less the constructors and the methods javac
 * writes for enums ({@code values}, {@code valueOf}) and records (component
 * accessors, {@code equals}, {@code hashCode}, {@code toString}).
 */
public final class MemberSurface {

    private static final Set<String> ENUM_WRITTEN = Set.of("values", "valueOf");
    private static final Set<String> RECORD_WRITTEN = Set.of("equals", "hashCode", "toString");
    private static final String NO_PARAMETERS = "()";

    /**
     * Holds only static members.
     */
    private MemberSurface() {
    }

    /**
     * The methods a type's source wrote, constructors left out.
     *
     * @param type the scanned type
     * @return its drawn methods
     */
    public static List<ScannedMethod> drawnMethods(ScannedType type) {
        Set<String> instanceFieldNames = type.fields().stream()
                .filter(field -> !field.isStatic())
                .map(ScannedField::name)
                .collect(Collectors.toSet());
        return type.methods().stream()
                .filter(method -> !method.isConstructor())
                .filter(method -> !isWrittenByJavac(type.kind(), method, instanceFieldNames))
                .toList();
    }

    /**
     * The fields a type's source wrote, constants left out: goo names every
     * literal as a constant, so a constant is a literal rather than surface.
     *
     * @param type the scanned type
     * @return its fields that are not static final
     */
    public static List<ScannedField> drawnFields(ScannedType type) {
        return type.fields().stream().filter(field -> !field.isConstant()).toList();
    }

    /**
     * How many members a type's source wrote.
     *
     * @param type the scanned type
     * @return its drawn fields plus its drawn methods
     */
    public static int memberCount(ScannedType type) {
        return drawnFields(type).size() + drawnMethods(type).size();
    }

    /**
     * Whether a type holds state: an instance field.
     *
     * @param type the scanned type
     * @return true when any field is not static
     */
    public static boolean hasState(ScannedType type) {
        return type.fields().stream().anyMatch(field -> !field.isStatic());
    }

    /**
     * Whether a type holds behaviour: a drawn method.
     *
     * @param type the scanned type
     * @return true when its source wrote a method other than a constructor
     */
    public static boolean hasBehaviour(ScannedType type) {
        return !drawnMethods(type).isEmpty();
    }

    /**
     * Whether javac wrote a method rather than the source.
     *
     * @param kind               the declaring type's kind
     * @param method             the method
     * @param instanceFieldNames the declaring type's instance field names
     * @return true for an enum's values and valueOf, and a record's accessors and object methods
     */
    private static boolean isWrittenByJavac(TypeKind kind, ScannedMethod method, Set<String> instanceFieldNames) {
        return switch (kind) {
            case ENUM -> method.isStatic() && ENUM_WRITTEN.contains(method.name());
            case RECORD -> RECORD_WRITTEN.contains(method.name()) || isAccessor(method, instanceFieldNames);
            default -> false;
        };
    }

    /**
     * Whether a method reads as a record component accessor.
     *
     * @param method             the method
     * @param instanceFieldNames the record's component names
     * @return true for a parameterless instance method named for a component
     */
    private static boolean isAccessor(ScannedMethod method, Set<String> instanceFieldNames) {
        return !method.isStatic()
                && method.descriptor().startsWith(NO_PARAMETERS)
                && instanceFieldNames.contains(method.name());
    }
}
