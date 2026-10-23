package com.mercuriusxeno.goo.command;

import com.mercuriusxeno.goo.data.GooConversionFlows;
import com.mercuriusxeno.goo.type.GooTypes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static com.mercuriusxeno.goo.command.GooAuditRecipeText.recipeText;

/**
 * CONVERSION FLOWS: per source-to-target goo type pair, each recipe whose craft loses the source type and gains
 * the target, with the goo lost and gained per craft, the pair holding the largest gain first. It flags nothing:
 * no threshold separates a loophole from a fair recipe, so the operator judges each path.
 * decision workbench-lists-type-to-type-paths-worst-first
 */
@AuditRule(order = 130)
public final class ConversionFlowsAuditRule implements GooAuditRule {
    /**
     * The section's name, which its header leads with.
     */
    public static final String SECTION = "CONVERSION FLOWS";
    private static final String HEADER_OPEN = " (";
    private static final String HEADER_SUFFIX = " type pairs)";
    private static final String INDENT = "  ";
    private static final String SPACE = " ";
    private static final String PAIR_ARROW = " -> ";
    private static final String LOSES = ": loses ";
    private static final String GAINS = ", gains ";
    private static final String PER_CRAFT = " per craft";

    @Override
    public GooAuditSection run(GooAuditInputs inputs) {
        Map<String, List<String>> linesByPair = new LinkedHashMap<>();
        for (GooConversionFlows.Flow flow : GooConversionFlows.flowsOf(inputs.values())) {
            String source = GooTypes.id(flow.source());
            String target = GooTypes.id(flow.target());
            linesByPair.computeIfAbsent(source + PAIR_ARROW + target, pair -> new ArrayList<>())
                    .add(INDENT + recipeText(flow.recipe()) + LOSES + flow.sourceLost() + SPACE + source + GAINS
                            + flow.targetGained() + SPACE + target + PER_CRAFT);
        }
        List<String> lines = new ArrayList<>();
        linesByPair.forEach((pair, flows) -> {
            lines.add(pair);
            lines.addAll(flows);
        });
        return new GooAuditSection(SECTION, SECTION + HEADER_OPEN + linesByPair.size() + HEADER_SUFFIX,
                List.copyOf(lines), 0, List.of());
    }
}
