package com.mercuriusxeno.goo.tools.architecture;

/**
 * One method or constructor a scanned type declares, as its class file records it.
 *
 * @param name           the method's name, {@code <init>} for a constructor
 * @param descriptor     the method's descriptor, parameters and return type
 * @param parameterCount how many parameters the descriptor declares
 * @param isStatic       whether the method belongs to the type rather than an instance
 */
public record ScannedMethod(String name, String descriptor, int parameterCount, boolean isStatic) {

    private static final String CONSTRUCTOR_NAME = "<init>";

    /**
     * Whether this member is a constructor.
     *
     * @return true for {@code <init>}
     */
    public boolean isConstructor() {
        return CONSTRUCTOR_NAME.equals(name);
    }
}
