package com.mercuriusxeno.goo.tools.architecture;

/**
 * What sort of type a scanned type is, which decides which of its members
 * javac wrote rather than the source.
 */
public enum TypeKind {
    /** A plain class, abstract or concrete. */
    CLASS,
    /** An interface. */
    INTERFACE,
    /** An enum, whose {@code values} and {@code valueOf} javac writes. */
    ENUM,
    /** A record, whose accessors, {@code equals}, {@code hashCode} and {@code toString} javac writes. */
    RECORD,
    /** An annotation interface. */
    ANNOTATION
}
