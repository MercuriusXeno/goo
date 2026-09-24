package com.mercuriusxeno.goo.tools.architecture;

import java.util.List;
import java.util.Set;

/**
 * One top-level goo type as the scan reads it. Nested, local and anonymous
 * classes fold into the top-level type whose source file holds them: their
 * references and instanceof checks count as the top-level type's, and the
 * members listed are the top-level type's own.
 *
 * @param packageName      the dotted package the type sits in, such as {@code com.mercuriusxeno.goo.ability}
 * @param simpleName       the type's name without its package, such as {@code AbilityMath}
 * @param sourcePath       the source file's path relative to the repository root, forward slashes
 * @param fields           the fields the top-level type declares, compiler-synthesised ones left out
 * @param methods          the methods and constructors the top-level type declares, synthetic ones left out
 * @param references       the qualified names of the other scanned types this one references
 * @param instanceofChecks how many {@code instanceof} instructions the type and its nested classes hold
 */
public record ScannedType(String packageName, String simpleName, String sourcePath, List<ScannedField> fields,
                          List<ScannedMethod> methods, Set<String> references, int instanceofChecks) {

    private static final char QUALIFIED_SEPARATOR = '.';

    /**
     * The type's dotted binary name.
     *
     * @return the package and the simple name joined by a dot
     */
    public String qualifiedName() {
        return packageName + QUALIFIED_SEPARATOR + simpleName;
    }
}
