package com.mercuriusxeno.goo.tools.architecture;

/**
 * One field a scanned type declares, as its class file records it.
 *
 * @param name       the field's name
 * @param descriptor the field's type descriptor, such as {@code I} or {@code Ljava/lang/String;}
 * @param isStatic   whether the field belongs to the type rather than an instance
 * @param isFinal    whether the field is assigned once
 */
public record ScannedField(String name, String descriptor, boolean isStatic, boolean isFinal) {

    /**
     * Whether the field is a constant: static and final, enum constants included.
     *
     * @return true for a static final field
     */
    public boolean isConstant() {
        return isStatic && isFinal;
    }
}
