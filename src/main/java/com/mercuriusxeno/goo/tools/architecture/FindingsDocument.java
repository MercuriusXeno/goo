package com.mercuriusxeno.goo.tools.architecture;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Writes the shape findings as markdown: a header saying what was graded and
 * what was read past, then the data-behaviour split, the shapes, the same
 * shapes, the coupling hubs, the instanceof ladders, the largest types and
 * the cycles. Every item names a type's simple name and its source path.
 */
public final class FindingsDocument {

    private static final String TITLE = "# goo shape findings\n\n%s\n\n";
    private static final String GRADED_LINE = "%d production types graded, of %d the scan read.\n";
    private static final String READ_PAST_LINE = "Read past: %s and the packages under them (%d types).\n";
    private static final String READ_PAST_SEPARATOR = ", ";
    private static final String CODE_SPAN = "`%s`";
    private static final String EDGES_LINE = "%d type-to-type edges among the graded types.\n\n";
    private static final String EMPTY_LINE = "A member is a field or method the source wrote: constants, constructors"
            + " and what javac writes for enums and records are left out. Of the graded types, %d carry no member;"
            + " the split, the shapes and the largest by members read past them.\n\n";
    private static final String SPLIT_HEADING = "## The data-behaviour split\n\n";
    private static final String SPLIT_LINE = "- %s: %d (%d%%)\n";
    private static final String BEHAVIOUR_ONLY = "behaviour and no state";
    private static final String STATE_ONLY = "state and no behaviour";
    private static final String BOTH = "both together";
    private static final String NEITHER = "neither, mutable static fields alone";
    private static final String SHAPES_HEADING = "\n## Shapes\n\n";
    private static final String WIDE_HEADING = "### wide-surface (%d)\n\nMore members than the %d or fewer that all"
            + " but a twentieth of the set hold.\n\n";
    private static final String LONG_HEADING = "\n### long-parameter-list (%d)\n\nA constructor or method taking"
            + " more than %d parameters.\n\n";
    private static final String SAME_HEADING = "\n## Same shapes (%d)\n\nTypes of the same kind whose members hold"
            + " the same types under different names, %d members or more.\n\n";
    private static final String HUB_HEADING = "\n## Coupling hubs (%d)\n\nA fan-in above %d or a fan-out above %d,"
            + " what all but a twentieth of the set hold.\n\n";
    private static final String LADDER_HEADING = "\n## Instanceof ladders (%d)\n\nThe types holding the most"
            + " instanceof checks, nested classes included.\n\n";
    private static final String LARGEST_HEADING = "\n## Largest types\n\n";
    private static final String BY_MEMBERS_HEADING = "### by member count\n\n";
    private static final String BY_LINES_HEADING = "\n### by source lines\n\n";
    private static final String CYCLES_HEADING = "\n## Cycles\n\nAmong the graded types and their packages.\n\n";
    private static final String PACKAGE_CYCLES_HEADING = "### packages\n\n";
    private static final String TYPE_CYCLES_HEADING = "\n### types\n\n";
    private static final String PACKAGES_NOUN = "packages";
    private static final String TYPES_NOUN = "types";
    private static final String TYPE_ITEM = "- `%s` `%s` - %d %s\n";
    private static final String SIGNATURE_ITEM = "- `%s.%s` `%s` - %d parameters\n";
    private static final String HUB_ITEM = "- `%s` `%s` - %d in, %d out\n";
    private static final String GROUP_ITEM = "- %s - %d members each\n";
    private static final String GROUP_MEMBER = "`%s` `%s`";
    private static final String MEMBERS_UNIT = "members";
    private static final String LINES_UNIT = "lines";
    private static final String CHECKS_UNIT = "instanceof checks";
    private static final double PERCENT = 100.0;

    /**
     * Holds only static members.
     */
    private FindingsDocument() {
    }

    /**
     * Renders the findings for a scan.
     *
     * @param scan        every scanned type
     * @param readPast    the packages read past, each with the packages under it
     * @param sourceLines each source path mapped to its line count
     * @return the markdown text
     */
    public static String render(List<ScannedType> scan, List<String> readPast, Map<String, Integer> sourceLines) {
        List<ScannedType> graded = scan.stream().filter(type -> !isUnder(type.packageName(), readPast)).toList();
        List<ScannedType> drawn = graded.stream().filter(type -> MemberSurface.memberCount(type) > 0).toList();
        return String.format(TITLE, MermaidGraphDocument.GENERATED_NOTICE)
                + header(scan.size(), graded, drawn.size(), readPast)
                + splitSection(ShapeGraders.dataBehaviourSplit(drawn), graded.size())
                + shapesSection(drawn)
                + sameShapesSection(ShapeGraders.sameShapes(drawn))
                + hubSection(ShapeGraders.couplingHubs(graded))
                + ladderSection(ShapeGraders.instanceofLadders(graded))
                + largestSection(drawn, graded, sourceLines)
                + cyclesSection(graded);
    }

    /**
     * Whether a package is one of the read-past packages or sits under one.
     *
     * @param packageName the package
     * @param readPast    the read-past packages
     * @return true when read past
     */
    static boolean isUnder(String packageName, List<String> readPast) {
        return readPast.stream().anyMatch(root -> packageName.equals(root)
                || packageName.startsWith(root + NodeLabels.PACKAGE_SEPARATOR));
    }

    /**
     * The header: what was graded, what was read past, the edges, the memberless types.
     *
     * @param scanned  how many types the scan read
     * @param graded   the graded types
     * @param drawn    how many graded types carry members
     * @param readPast the read-past packages
     * @return the header's markdown
     */
    private static String header(int scanned, List<ScannedType> graded, int drawn, List<String> readPast) {
        String roots = readPast.stream().map(root -> String.format(CODE_SPAN, root))
                .collect(Collectors.joining(READ_PAST_SEPARATOR));
        return String.format(GRADED_LINE, graded.size(), scanned)
                + String.format(READ_PAST_LINE, roots, scanned - graded.size())
                + String.format(EDGES_LINE, DirectedGraph.ofTypes(graded).edgeCount())
                + String.format(EMPTY_LINE, graded.size() - drawn);
    }

    /**
     * The data-behaviour split, each count with its share of the graded types.
     *
     * @param split  the split
     * @param graded how many types were graded
     * @return the section's markdown
     */
    private static String splitSection(DataBehaviourSplit split, int graded) {
        return SPLIT_HEADING
                + splitLine(BEHAVIOUR_ONLY, split.behaviourOnly(), graded)
                + splitLine(STATE_ONLY, split.stateOnly(), graded)
                + splitLine(BOTH, split.both(), graded)
                + splitLine(NEITHER, split.neither(), graded);
    }

    /**
     * One line of the split.
     *
     * @param name  what the line counts
     * @param count the count
     * @param total the graded types
     * @return the line
     */
    private static String splitLine(String name, int count, int total) {
        long percent = total == 0 ? 0 : Math.round(PERCENT * count / total);
        return String.format(SPLIT_LINE, name, count, percent);
    }

    /**
     * The wide-surface and long-parameter-list shapes.
     *
     * @param drawn the graded types that carry members
     * @return the section's markdown
     */
    private static String shapesSection(List<ScannedType> drawn) {
        WideSurface wide = ShapeGraders.wideSurface(drawn);
        List<LongSignature> longSignatures = ShapeGraders.longParameterLists(drawn);
        StringBuilder text = new StringBuilder(SHAPES_HEADING)
                .append(String.format(WIDE_HEADING, wide.types().size(), wide.threshold()))
                .append(typeItems(wide.types(), MEMBERS_UNIT))
                .append(String.format(LONG_HEADING, longSignatures.size(), ShapeGraders.LONG_PARAMETER_BOUND));
        longSignatures.forEach(signature -> text.append(String.format(SIGNATURE_ITEM,
                signature.type().simpleName(), signature.member(), signature.type().sourcePath(),
                signature.parameters())));
        return text.toString();
    }

    /**
     * The groups of types built the same way.
     *
     * @param groups each group
     * @return the section's markdown
     */
    private static String sameShapesSection(List<List<ScannedType>> groups) {
        StringBuilder text = new StringBuilder(String.format(SAME_HEADING, groups.size(),
                ShapeGraders.SAME_SHAPE_MINIMUM));
        for (List<ScannedType> group : groups) {
            String members = group.stream()
                    .map(type -> String.format(GROUP_MEMBER, type.simpleName(), type.sourcePath()))
                    .collect(Collectors.joining(READ_PAST_SEPARATOR));
            text.append(String.format(GROUP_ITEM, members, MemberSurface.memberCount(group.getFirst())));
        }
        return text.toString();
    }

    /**
     * The coupling hubs, each with both counts.
     *
     * @param hubs the hubs and their thresholds
     * @return the section's markdown
     */
    private static String hubSection(CouplingHubs hubs) {
        StringBuilder text = new StringBuilder(String.format(HUB_HEADING, hubs.hubs().size(),
                hubs.fanInThreshold(), hubs.fanOutThreshold()));
        hubs.hubs().forEach(hub -> text.append(String.format(HUB_ITEM, hub.type().simpleName(),
                hub.type().sourcePath(), hub.fanIn(), hub.fanOut())));
        return text.toString();
    }

    /**
     * The types holding the most instanceof checks.
     *
     * @param ladders the types and their counts
     * @return the section's markdown
     */
    private static String ladderSection(List<CountedType> ladders) {
        return String.format(LADDER_HEADING, ladders.size()) + typeItems(ladders, CHECKS_UNIT);
    }

    /**
     * The largest types by member count and by source lines.
     *
     * @param drawn       the graded types that carry members
     * @param graded      the graded types
     * @param sourceLines each source path mapped to its line count
     * @return the section's markdown
     */
    private static String largestSection(List<ScannedType> drawn, List<ScannedType> graded,
                                         Map<String, Integer> sourceLines) {
        return LARGEST_HEADING
                + BY_MEMBERS_HEADING
                + typeItems(ShapeGraders.largest(drawn, MemberSurface::memberCount), MEMBERS_UNIT)
                + BY_LINES_HEADING
                + typeItems(ShapeGraders.largest(graded,
                        type -> sourceLines.getOrDefault(type.sourcePath(), 0)), LINES_UNIT);
    }

    /**
     * The package and type cycles among the graded types.
     *
     * @param graded the graded types
     * @return the section's markdown
     */
    private static String cyclesSection(List<ScannedType> graded) {
        DirectedGraph packages = DirectedGraph.ofPackages(graded);
        Map<String, String> typeLabels = graded.stream()
                .collect(Collectors.toMap(ScannedType::qualifiedName, ScannedType::simpleName));
        return CYCLES_HEADING
                + PACKAGE_CYCLES_HEADING
                + MermaidGraphDocument.cycleList(PACKAGES_NOUN, CycleFinder.find(packages),
                        NodeLabels.forPackages(packages.adjacency().keySet()))
                + TYPE_CYCLES_HEADING
                + MermaidGraphDocument.cycleList(TYPES_NOUN, CycleFinder.find(DirectedGraph.ofTypes(graded)),
                        typeLabels);
    }

    /**
     * One item per counted type.
     *
     * @param entries the counted types
     * @param unit    what the count counts
     * @return the items' markdown
     */
    private static String typeItems(List<CountedType> entries, String unit) {
        return entries.stream()
                .map(entry -> String.format(TYPE_ITEM, entry.type().simpleName(), entry.type().sourcePath(),
                        entry.count(), unit))
                .collect(Collectors.joining());
    }
}
