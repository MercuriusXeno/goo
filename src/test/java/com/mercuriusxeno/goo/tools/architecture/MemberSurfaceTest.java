package com.mercuriusxeno.goo.tools.architecture;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The member count reads what the source wrote: constants, constructors and
 * the methods javac writes for enums and records are left out.
 */
class MemberSurfaceTest {

    private static final ScannedField CONSTANT = new ScannedField("LIMIT", "I", true, true);
    private static final ScannedField COMPONENT = new ScannedField("size", "I", false, true);
    private static final ScannedMethod CONSTRUCTOR = new ScannedMethod("<init>", "(I)V", 1, false);
    private static final ScannedMethod ACCESSOR = new ScannedMethod("size", "()I", 0, false);
    private static final ScannedMethod TO_STRING = new ScannedMethod("toString", "()Ljava/lang/String;", 0, false);
    private static final ScannedMethod VALUES = new ScannedMethod("values", "()[LSample;", 0, true);
    private static final ScannedMethod WRITTEN = new ScannedMethod("grow", "(I)LSample;", 1, false);

    static Stream<Arguments> typesAndTheirMemberCounts() {
        List<ScannedField> fields = List.of(CONSTANT, COMPONENT);
        List<ScannedMethod> methods = List.of(CONSTRUCTOR, ACCESSOR, TO_STRING, VALUES, WRITTEN);
        return Stream.of(
                Arguments.of(TypeKind.CLASS, fields, methods, 1 + 4),
                Arguments.of(TypeKind.RECORD, fields, methods, 1 + 2),
                Arguments.of(TypeKind.ENUM, fields, methods, 1 + 3),
                Arguments.of(TypeKind.CLASS, List.of(CONSTANT), List.of(CONSTRUCTOR), 0));
    }

    @ParameterizedTest(name = "{0} holds {3}")
    @MethodSource("typesAndTheirMemberCounts")
    void memberCount(TypeKind kind, List<ScannedField> fields, List<ScannedMethod> methods, int expected) {
        ScannedType type = new ScannedType("sample", "Sample", kind, "Sample.java", fields, methods, Set.of(), 0);
        assertEquals(expected, MemberSurface.memberCount(type));
    }
}
