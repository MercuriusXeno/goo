package com.mercuriusxeno.goo.tools.architecture;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The names the architecture documents print for packages and types, which
 * drop what every node shares so the graph reads short.
 */
public final class NodeLabels {

    /** The package every goo type sits under. */
    public static final String ROOT_PACKAGE = "com.mercuriusxeno.goo";
    /** The separator between the parts of a dotted package name. */
    public static final String PACKAGE_SEPARATOR = ".";
    private static final String ROOT_LABEL = "goo";
    private static final String ROOT_PREFIX = ROOT_PACKAGE + PACKAGE_SEPARATOR;
    private static final String COLLIDING_TYPE_LABEL = "%s (%s)";

    /**
     * Holds only static members.
     */
    private NodeLabels() {
    }

    /**
     * A package's label: its name past the root package, the root itself reading {@code goo}.
     *
     * @param packageName the qualified package name
     * @return the label
     */
    public static String packageLabel(String packageName) {
        if (ROOT_PACKAGE.equals(packageName)) {
            return ROOT_LABEL;
        }
        return packageName.startsWith(ROOT_PREFIX) ? packageName.substring(ROOT_PREFIX.length()) : packageName;
    }

    /**
     * Labels for a set of packages.
     *
     * @param packageNames the qualified package names
     * @return each package mapped to its label
     */
    public static SortedMap<String, String> forPackages(Collection<String> packageNames) {
        return packageNames.stream().collect(Collectors.toMap(Function.identity(), NodeLabels::packageLabel,
                (left, right) -> left, TreeMap::new));
    }

    /**
     * Labels for a set of types: the simple name, with the package label beside
     * it where two types share a simple name.
     *
     * @param types the scan
     * @return each qualified type name mapped to its label
     */
    public static SortedMap<String, String> forTypes(List<ScannedType> types) {
        Map<String, Long> simpleNameCounts = types.stream()
                .collect(Collectors.groupingBy(ScannedType::simpleName, Collectors.counting()));
        SortedMap<String, String> labels = new TreeMap<>();
        for (ScannedType type : types) {
            boolean shared = simpleNameCounts.get(type.simpleName()) > 1;
            labels.put(type.qualifiedName(), shared
                    ? String.format(COLLIDING_TYPE_LABEL, type.simpleName(), packageLabel(type.packageName()))
                    : type.simpleName());
        }
        return labels;
    }
}
