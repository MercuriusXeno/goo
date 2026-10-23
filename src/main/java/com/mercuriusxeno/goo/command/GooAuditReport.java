package com.mercuriusxeno.goo.command;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.data.GooReaction;
import com.mercuriusxeno.goo.data.GooReactionLoader;
import com.mercuriusxeno.goo.data.GooValueRegistry;
import com.mercuriusxeno.goo.data.GooValues;
import com.mercuriusxeno.goo.data.LeastNumberPerType;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.loading.FMLPaths;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds and writes the /goo audit report. Orchestrates structural checks
 * (expressions, phantom IDs, cycles, no-value items) and delegates value
 * comparison sections to {@link GooAuditValues}.
 */
public final class GooAuditReport {

    // --- Shared/template constants (package-private for GooAuditValues access) ---

    /**
     * Width of section separator lines in the audit report.
     */
    static final int SEPARATOR_WIDTH = 50;
    /**
     * Dash character for section separator lines.
     */
    static final String SEPARATOR_CHAR = "-";
    /**
     * Indent prefix for report lines.
     */
    static final String INDENT = "  ";
    /**
     * Empty line separator in report output.
     */
    static final String EMPTY_LINE = "";
    /**
     * Single space separator.
     */
    static final String SEP_SPACE = " ";
    /**
     * Closing parenthesis.
     */
    static final String MSG_CLOSE_PAREN = ")";
    /**
     * Denied source label.
     */
    static final String LABEL_DENIED = "(denied)";
    /**
     * Valued source label.
     */
    static final String LABEL_VALUED = "(valued)";

    // --- Audit orchestration constants (private) ---

    /**
     * Audit report title.
     */
    private static final String AUDIT_TITLE = "Goo Audit Report";
    /**
     * Audit report title underline.
     */
    private static final String AUDIT_UNDERLINE = "================";
    /**
     * Audit file output path.
     */
    public static final String FILE_AUDIT = "goo_audit.txt";
    /**
     * The audit's sections as data, beside the text, which the workbench's Audit tab reads.
     */
    public static final String FILE_AUDIT_JSON = "goo_audit.json";
    private static final String JSON_SECTIONS = "sections";
    private static final String JSON_SECTION = "section";
    private static final String JSON_HEADER = "header";
    private static final String JSON_LINES = "lines";
    private static final String JSON_ISSUES = "issues";
    private static final String JSON_FLAGS = "flags";
    private static final Gson JSON_WRITER = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    /**
     * All checks passed message.
     */
    private static final String MSG_ALL_PASSED = "All checks passed. No issues found.";
    /**
     * Audit issues found suffix.
     */
    private static final String MSG_ISSUES_SUFFIX = " issue(s) found. See config/goo_audit.txt";
    /**
     * Link to full report.
     */
    private static final String MSG_FULL_REPORT = "Full report: config/goo_audit.txt";
    /**
     * Log message for diagnostic file write failure.
     */
    private static final String LOG_WRITE_FAIL = "Failed to write diagnostic file: {}";

    // --- Expression warning constants (private) ---

    /**
     * Expression warnings section header prefix.
     */
    private static final String HDR_EXPR_PREFIX = "EXPRESSION WARNINGS (";
    /**
     * Expression warnings section header suffix.
     */
    private static final String HDR_EXPR_SUFFIX = " issues in base_values.json expressions)";
    /**
     * All expressions valid message.
     */
    private static final String MSG_EXPR_VALID = "  All expressions are valid.";
    /**
     * Expression warnings chat prefix.
     */
    private static final String MSG_EXPR_CHAT_PREFIX = "  Expression warnings: ";
    /**
     * Expression warnings chat suffix.
     */
    private static final String MSG_EXPR_CHAT_SUFFIX = " issue(s)";

    // --- Phantom ID constants (private) ---

    /**
     * Phantom IDs section header prefix.
     */
    private static final String HDR_PHANTOM_PREFIX = "PHANTOM IDS (";
    /**
     * Phantom IDs section header suffix.
     */
    private static final String HDR_PHANTOM_SUFFIX = " identifiers in base_values.json don't match any registered item)";
    /**
     * All identifiers valid message.
     */
    private static final String MSG_IDS_VALID = "  All identifiers are valid.";
    /**
     * Phantom IDs chat prefix.
     */
    private static final String MSG_PHANTOM_CHAT_PREFIX = "  Phantoms: ";
    /**
     * Phantom IDs chat suffix.
     */
    private static final String MSG_PHANTOM_CHAT_SUFFIX = " ID(s) in base_values.json don't exist in game";

    // --- Cycle constants (private) ---

    /**
     * Cycles section header prefix.
     */
    private static final String HDR_CYCLES_PREFIX = "CYCLES (";
    /**
     * Cycles section header middle.
     */
    private static final String HDR_CYCLES_MID = " total, ";
    /**
     * Cycles section header suffix.
     */
    private static final String HDR_CYCLES_SUFFIX = " dead)";
    /**
     * No cycles detected message.
     */
    private static final String MSG_NO_CYCLES = "  No recipe cycles detected.";
    /**
     * Cycles chat prefix.
     */
    private static final String MSG_CYCLES_CHAT_PREFIX = "  Cycles: ";
    /**
     * Cycles chat suffix.
     */
    private static final String MSG_CYCLES_CHAT_SUFFIX = " unanchored (need base values to resolve)";
    /**
     * Cycle arrow separator.
     */
    private static final String CYCLE_ARROW = " -> ";
    /**
     * Anchored cycle prefix.
     */
    private static final String CYCLE_OK_PREFIX = "  [OK]   ";
    /**
     * Anchored cycle suffix.
     */
    private static final String CYCLE_ANCHORED_BY = " (anchored by ";
    /**
     * Dead cycle prefix.
     */
    private static final String CYCLE_DEAD_PREFIX = "  [DEAD] ";
    /**
     * Dead cycle suffix.
     */
    private static final String CYCLE_DEAD_SUFFIX = " (no base values - unresolvable)";

    // --- Least value constants (private) ---

    /**
     * Least value section header prefix.
     */
    private static final String HDR_LEAST_VALUE_PREFIX = "LEAST VALUE PER TYPE (";
    /**
     * Least value section header suffix.
     */
    private static final String HDR_LEAST_VALUE_SUFFIX = " types)";
    /**
     * Least value message when no goo type stands.
     */
    private static final String MSG_NO_TYPES = "  No goo types.";
    /**
     * Heads the per-item least values inside the least value section.
     */
    private static final String LEAST_VALUE_BY_ITEM = "  least value by item:";

    // --- No-value constants (private) ---

    /**
     * No value section header prefix.
     */
    private static final String HDR_NO_VALUE_PREFIX = "NO VALUE (";
    /**
     * No value section header suffix.
     */
    private static final String HDR_NO_VALUE_SUFFIX = " registered items have no goo value)";
    /**
     * All items have values message.
     */
    private static final String MSG_ALL_VALUED = "  All registered items have goo values.";
    /**
     * No value quote-comma format prefix.
     */
    private static final String NO_VALUE_QUOTE = "\"";
    /**
     * No value quote-comma format suffix.
     */
    private static final String NO_VALUE_SUFFIX = "\",";
    /**
     * No value chat prefix.
     */
    private static final String MSG_NO_VALUE_CHAT_PREFIX = "  No value: ";
    /**
     * No value chat suffix.
     */
    private static final String MSG_NO_VALUE_CHAT_SUFFIX = " item(s) have no goo value";

    private GooAuditReport() {
    }

    /**
     * The audit as text and as data.
     *
     * @param lines  the report's text lines
     * @param json   each rule's section, lines and flags as JSON, which the workbench reads
     * @param issues the issues every rule counted
     */
    public record Built(List<String> lines, String json, int issues) {
    }

    /**
     * Walks the rules in order, each section in the text as header, separator, lines and a blank line, and
     * each as an entry in the JSON (decision each-check-is-one-rule-added-alone).
     *
     * @param inputs what the rules read
     * @param rules  the rules, in report order
     * @return the report
     */
    public static Built build(GooAuditInputs inputs, List<GooAuditRule> rules) {
        List<String> report = new ArrayList<>(List.of(AUDIT_TITLE, AUDIT_UNDERLINE, EMPTY_LINE));
        JsonArray sections = new JsonArray();
        int issues = 0;
        for (GooAuditRule rule : rules) {
            GooAuditSection section = rule.run(inputs);
            report.add(section.header());
            report.add(SEPARATOR_CHAR.repeat(SEPARATOR_WIDTH));
            report.addAll(section.lines());
            report.add(EMPTY_LINE);
            issues += section.issues();
            sections.add(sectionJson(section));
        }
        JsonObject json = new JsonObject();
        json.add(JSON_SECTIONS, sections);
        return new Built(report, JSON_WRITER.toJson(json), issues);
    }

    private static JsonObject sectionJson(GooAuditSection section) {
        JsonObject json = new JsonObject();
        json.addProperty(JSON_SECTION, section.section());
        json.addProperty(JSON_HEADER, section.header());
        json.add(JSON_LINES, stringArray(section.lines()));
        json.addProperty(JSON_ISSUES, section.issues());
        json.add(JSON_FLAGS, stringArray(section.flags()));
        return json;
    }

    private static JsonArray stringArray(List<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }

    /**
     * Runs the full audit: builds report, writes file, sends results to chat.
     *
     * @param ctx the command context
     * @return 1 on success
     */
    static int run(CommandContext<CommandSourceStack> ctx) {
        GooValueRegistry values = GooValues.registryOf(ctx.getSource().getServer());
        int issues = writeReport(values, GooReactionLoader.reactionsOf(ctx.getSource().getServer()));
        sendAuditResultToChat(ctx, issues);
        sendSectionSummaries(values, ctx);
        return 1;
    }

    /**
     * Builds the full audit report, writes it to config/goo_audit.txt, and returns the issue count.
     * The audit-dump gametest calls it headless, so the report needs no world load
     * (decision audit-dump-from-a-gametest).
     *
     * @param values    the server's goo value registry
     * @param reactions the reactions the server loaded from goo_reactions
     * @return the total number of issues found
     */
    public static int writeReport(GooValueRegistry values, List<GooReaction> reactions) {
        return writeReport(values, reactions, FMLPaths.CONFIGDIR.get());
    }

    /**
     * Builds the full audit report and writes goo_audit.txt and goo_audit.json into a folder, as the in-game
     * workbench writes them under the world (decision goo-workbench-command-serves-the-page-from-the-game).
     *
     * @param values    the goo value registry audited
     * @param reactions the reactions the server loaded from goo_reactions
     * @param folder    the folder the report lands in
     * @return the total number of issues found
     */
    public static int writeReport(GooValueRegistry values, List<GooReaction> reactions, Path folder) {
        Built report = build(new GooAuditInputs(values, reactions, BuiltInRegistries.ITEM.keySet()),
                GooAuditRules.discovered());
        writeFile(folder, FILE_AUDIT, report.lines());
        writeFile(folder, FILE_AUDIT_JSON, List.of(report.json()));
        return report.issues();
    }

    /**
     * Sends the pass/fail headline and file location to chat.
     *
     * @param ctx    the command context
     * @param issues the total issue count
     */
    private static void sendAuditResultToChat(CommandContext<CommandSourceStack> ctx, int issues) {
        String text = issues == 0 ? MSG_ALL_PASSED : issues + MSG_ISSUES_SUFFIX;
        ChatFormatting color = issues == 0 ? ChatFormatting.GREEN : ChatFormatting.YELLOW;
        ctx.getSource().sendSuccess(() -> Component.literal(text).withStyle(color), false);
    }

    /**
     * Sends per-section summaries and the file location to chat.
     *
     * @param values the server's goo value registry
     * @param ctx the command context
     */
    private static void sendSectionSummaries(GooValueRegistry values, CommandContext<CommandSourceStack> ctx) {
        sendExpressionWarningsSummary(values, ctx);
        sendPhantomIdsSummary(values, ctx);
        sendCyclesSummary(values, ctx);
        GooAuditValues.sendConflictsSummary(values, ctx);
        sendNoValueSummary(values, ctx);
        ctx.getSource().sendSuccess(() ->
                Component.literal(MSG_FULL_REPORT).withStyle(ChatFormatting.GRAY), false);
    }

    // --- Section template ---

    /**
     * Appends a standard audit section: header, separator, items (or empty message), blank line.
     *
     * @param report    the report lines list
     * @param header    the section header line
     * @param emptyMsg  the message to show when the list is empty
     * @param items     the items to format
     * @param formatter formats each item into a report line
     * @param <T>       the item type
     */
    static <T> void appendSectionBody(List<String> report, String header,
                                      String emptyMsg, List<T> items, Function<T, String> formatter) {
        report.add(header);
        report.add(SEPARATOR_CHAR.repeat(SEPARATOR_WIDTH));
        if (items.isEmpty()) {
            report.add(emptyMsg);
        } else {
            items.forEach(item -> report.add(formatter.apply(item)));
        }
        report.add(EMPTY_LINE);
    }

    // --- Utilities ---

    /**
     * Writes diagnostic lines to a file in the config directory.
     *
     * @param filename the output filename
     * @param lines    the lines to write
     */
    static void writeDiagnosticFile(String filename, List<String> lines) {
        writeFile(FMLPaths.CONFIGDIR.get(), filename, lines);
    }

    /**
     * Writes lines to a file in a folder, creating the folder where none stands.
     *
     * @param folder   the folder
     * @param filename the output filename
     * @param lines    the lines to write
     */
    static void writeFile(Path folder, String filename, List<String> lines) {
        try {
            Files.createDirectories(folder);
            Files.write(folder.resolve(filename), lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Goo.LOGGER.error(LOG_WRITE_FAIL, filename, e);
        }
    }

    // --- Expression Warnings ---

    /**
     * Appends expression validation warnings (bad constants, out-of-order refs).
     *
     * @param values the server's goo value registry
     * @param report the report lines list to append to
     * @return the number of warnings found
     */
    static int appendExpressionWarningsSection(GooValueRegistry values, List<String> report) {
        List<String> warnings = values.validateBaseValues();
        appendSectionBody(report, HDR_EXPR_PREFIX + warnings.size() + HDR_EXPR_SUFFIX,
                MSG_EXPR_VALID, warnings, w -> INDENT + w);
        return warnings.size();
    }

    /**
     * Sends expression warnings summary to chat.
     *
     * @param values the server's goo value registry
     * @param ctx the command context
     */
    private static void sendExpressionWarningsSummary(GooValueRegistry values, CommandContext<CommandSourceStack> ctx) {
        List<String> warnings = values.validateBaseValues();
        if (warnings.isEmpty()) {
            return;
        }
        ctx.getSource().sendSuccess(() ->
                Component.literal(MSG_EXPR_CHAT_PREFIX + warnings.size() + MSG_EXPR_CHAT_SUFFIX)
                        .withStyle(ChatFormatting.YELLOW), false);
    }

    // --- Phantom IDs ---

    /**
     * Finds identifiers in base_values.json that don't match any registered item.
     *
     * @param values          the server's goo value registry
     * @param registeredItems every registered item's id
     * @return sorted list of phantom identifiers
     */
    static List<Identifier> findPhantomIds(GooValueRegistry values, Set<Identifier> registeredItems) {
        List<Identifier> phantoms = new ArrayList<>();
        for (Identifier id : values.diagnostics().allReferencedIds()) {
            if (!registeredItems.contains(id)) {
                phantoms.add(id);
            }
        }
        Collections.sort(phantoms);
        return phantoms;
    }

    /**
     * Appends the phantom IDs section to the report, returns count of phantoms.
     *
     * @param values          the server's goo value registry
     * @param registeredItems every registered item's id
     * @param report the report lines list to append to
     * @return the number of phantom IDs found
     */
    static int appendPhantomIdsSection(GooValueRegistry values, Set<Identifier> registeredItems,
                                       List<String> report) {
        List<Identifier> phantoms = findPhantomIds(values, registeredItems);
        appendSectionBody(report, HDR_PHANTOM_PREFIX + phantoms.size() + HDR_PHANTOM_SUFFIX,
                MSG_IDS_VALID, phantoms, id -> INDENT + id + SEP_SPACE
                        + (values.table().isDenied(id) ? LABEL_DENIED : LABEL_VALUED));
        return phantoms.size();
    }

    /**
     * Sends a phantom ID count summary to chat.
     *
     * @param values the server's goo value registry
     * @param ctx the command context
     */
    private static void sendPhantomIdsSummary(GooValueRegistry values, CommandContext<CommandSourceStack> ctx) {
        List<Identifier> phantoms = findPhantomIds(values, BuiltInRegistries.ITEM.keySet());
        if (phantoms.isEmpty()) {
            return;
        }

        ctx.getSource().sendSuccess(() ->
                Component.literal(MSG_PHANTOM_CHAT_PREFIX + phantoms.size() + MSG_PHANTOM_CHAT_SUFFIX)
                        .withStyle(ChatFormatting.RED), false);
    }

    // --- Cycles ---

    /**
     * Appends the cycles section to the report, returns count of dead cliques.
     *
     * @param values the server's goo value registry
     * @param report the report lines list to append to
     * @return the number of dead (unanchored) cycles
     */
    static int appendCyclesSection(GooValueRegistry values, List<String> report) {
        List<GooValueRegistry.RecipeCycle> cycles = values.diagnostics().cycles();
        long deadCount = cycles.stream().filter(c -> !c.hasAnchor()).count();
        appendSectionBody(report,
                HDR_CYCLES_PREFIX + cycles.size() + HDR_CYCLES_MID + deadCount + HDR_CYCLES_SUFFIX,
                MSG_NO_CYCLES, cycles, GooAuditReport::formatCycleFileLine);
        return (int) deadCount;
    }

    /**
     * Sends a cycle count summary to chat. Only shown when unanchored cycles exist.
     *
     * @param values the server's goo value registry
     * @param ctx the command context
     */
    private static void sendCyclesSummary(GooValueRegistry values, CommandContext<CommandSourceStack> ctx) {
        List<GooValueRegistry.RecipeCycle> cycles = values.diagnostics().cycles();
        long deadCount = cycles.stream().filter(c -> !c.hasAnchor()).count();
        if (deadCount == 0) {
            return;
        }

        ctx.getSource().sendSuccess(() ->
                Component.literal(MSG_CYCLES_CHAT_PREFIX + deadCount + MSG_CYCLES_CHAT_SUFFIX)
                        .withStyle(ChatFormatting.RED), false);
    }

    /**
     * Formats a single cycle as a plain-text line.
     *
     * @param cycle the recipe cycle to format
     * @return the formatted line
     */
    private static String formatCycleFileLine(GooValueRegistry.RecipeCycle cycle) {
        String items = cycle.items().stream()
                .map(Identifier::toString)
                .collect(Collectors.joining(CYCLE_ARROW));

        if (cycle.hasAnchor()) {
            return CYCLE_OK_PREFIX + items + CYCLE_ANCHORED_BY + cycle.anchor() + MSG_CLOSE_PAREN;
        }
        return CYCLE_DEAD_PREFIX + items + CYCLE_DEAD_SUFFIX;
    }

    // --- Least Number Per Type ---

    /**
     * Appends each goo type's least values, worked from the recipe graph. It walks every recipe, so it
     * runs here alone, never at server start
     * (decision audit-works-the-least-number-per-type-from-the-recipe-graph).
     *
     * @param values the server's goo value registry
     * @param report the report lines list to append to
     */
    static void appendLeastValueSection(GooValueRegistry values, List<String> report) {
        List<LeastNumberPerType.TypeLeastValue> answers = LeastNumberPerType.work(values.valueSettingRecipes(),
                values.table().getEffectiveValues(), values.deniedItems(), GooTypes.BUNDLED);
        List<String> lines = new ArrayList<>(answers.stream().flatMap(answer -> answer.lines().stream()).toList());
        lines.add(LEAST_VALUE_BY_ITEM);
        lines.addAll(LeastNumberPerType.smallestValueLines(values.valueSettingRecipes(),
                values.table().getEffectiveValues(), values.deniedItems()));
        appendSectionBody(report, HDR_LEAST_VALUE_PREFIX + answers.size() + HDR_LEAST_VALUE_SUFFIX,
                MSG_NO_TYPES, lines, Function.identity());
    }

    // --- No Value ---

    /**
     * Appends a section listing all registered items with no effective goo value.
     *
     * @param values          the server's goo value registry
     * @param registeredItems every registered item's id
     * @param report the report lines list to append to
     */
    static void appendNoValueSection(GooValueRegistry values, Set<Identifier> registeredItems, List<String> report) {
        List<Identifier> noValue = findItemsWithNoValue(values, registeredItems);
        appendSectionBody(report, HDR_NO_VALUE_PREFIX + noValue.size() + HDR_NO_VALUE_SUFFIX,
                MSG_ALL_VALUED, noValue, id -> NO_VALUE_QUOTE + id + NO_VALUE_SUFFIX);
    }

    /**
     * Finds all registered items that have no effective goo value, excluding denied items.
     *
     * @param values          the server's goo value registry
     * @param registeredItems every registered item's id
     * @return sorted list of unvalued item identifiers
     */
    private static List<Identifier> findItemsWithNoValue(GooValueRegistry values, Set<Identifier> registeredItems) {
        List<Identifier> noValue = new ArrayList<>();
        for (Identifier id : registeredItems) {
            if (values.table().lookup(id) == null && !values.table().isDenied(id)) {
                noValue.add(id);
            }
        }
        Collections.sort(noValue);
        return noValue;
    }

    /**
     * Sends a no-value count summary to chat.
     *
     * @param values the server's goo value registry
     * @param ctx the command context
     */
    private static void sendNoValueSummary(GooValueRegistry values, CommandContext<CommandSourceStack> ctx) {
        List<Identifier> noValue = findItemsWithNoValue(values, BuiltInRegistries.ITEM.keySet());
        if (noValue.isEmpty()) {
            return;
        }

        ctx.getSource().sendSuccess(() ->
                Component.literal(MSG_NO_VALUE_CHAT_PREFIX + noValue.size() + MSG_NO_VALUE_CHAT_SUFFIX)
                        .withStyle(ChatFormatting.GRAY), false);
    }

}
