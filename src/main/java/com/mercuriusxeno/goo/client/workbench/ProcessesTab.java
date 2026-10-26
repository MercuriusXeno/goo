package com.mercuriusxeno.goo.client.workbench;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.workbench.Processes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * The Processes tab's state with no Screen: the draft's conversions and post-conversions as the processes operation
 * describes them, each entry's form and the parser's verdict, and the edits and deletes the draft takes, as
 * index.html's processes section holds them (decision goo-workbench-opens-the-in-game-manager-screen).
 */
public final class ProcessesTab {
    private static final String SECTIONS = "sections";
    private static final String SECTION = "section";
    private static final String PROCESSES = "processes";
    private static final String KEY = "key";
    private static final String KIND = "kind";
    private static final String TEXT = "text";
    private static final String PARSED = "parsed";
    private static final String ERROR = "error";
    private static final String PARSED_VERDICT = "parsed";
    private static final String REFUSED_VERDICT = "the parser refuses it";
    private static final String ENTRIES_SUFFIX = " entries";
    private static final String REFUSED_START = ", ";
    private static final String REFUSED_SUFFIX = " refused";
    private static final String NO_TEXT = "";
    private static final String GROUPS = "_groups";
    private static final String GROUP_MARK = "#";
    private static final String NOTHING_TARGET = "0";
    private static final String DIVIDE_OPERATOR = "/";
    private static final String TIMES_OPERATOR = "*";
    private static final int ROW_LINES = 2;
    private static final int HEAD_LINE_HEIGHT = 13;
    // The text line draws goo types as 16px icons (decision goo-types-draw-as-icons-named-on-hover).
    private static final int TEXT_LINE_HEIGHT = 18;

    private final ManagerDraft draft;
    private final List<ProcessSection> sections = new ArrayList<>();

    /**
     * @param draft the draft every tab of the manager edits
     */
    public ProcessesTab(ManagerDraft draft) {
        this.draft = draft;
    }

    /**
     * One entry of a block.
     *
     * @param key     the entry's key
     * @param kind    its form, lower case: formula, additive, stack or assignment
     * @param text    its text unquoted
     * @param parsed  whether the game's conversion parser accepts it
     * @param verdict parsed, or the parser's refusal
     */
    public record ProcessRow(String key, String kind, String text, boolean parsed, String verdict) {
    }

    /**
     * One block.
     *
     * @param section _conversions or _post_conversions
     * @param summary how many entries it holds and how many the parser refuses
     * @param rows    its entries in the order the parser applies them
     */
    public record ProcessSection(String section, String summary, List<ProcessRow> rows) {
    }

    /**
     * Where a row draws its text: the key, the form, the verdict and Delete on the first line, the text alone on the
     * second at the row's full width, so it draws whole and its edit box spans the row.
     *
     * @param lines     how many lines the row takes
     * @param height    the row's height
     * @param textX     the text's left edge
     * @param textY     the text line's top
     * @param textWidth the width the text draws in
     */
    public record RowLayout(int lines, int height, int textX, int textY, int textWidth) {
    }

    /**
     * @param left  the row's left edge
     * @param right the row's right edge
     * @param top   the row's top
     * @return where the row draws its text
     */
    public static RowLayout layoutRow(int left, int right, int top) {
        // decision mutator-rows-take-an-extra-line
        return new RowLayout(ROW_LINES, HEAD_LINE_HEIGHT + TEXT_LINE_HEIGHT, left, top + HEAD_LINE_HEIGHT,
                right - left);
    }

    /**
     * @param describedJson the processes operation's reply over the draft
     */
    public void readDescribed(String describedJson) {
        sections.clear();
        for (JsonElement element : JsonParser.parseString(describedJson).getAsJsonObject().getAsJsonArray(SECTIONS)) {
            JsonObject section = element.getAsJsonObject();
            List<ProcessRow> rows = section.getAsJsonArray(PROCESSES).asList().stream()
                    .map(JsonElement::getAsJsonObject).map(ProcessesTab::rowOf).toList();
            long refused = rows.stream().filter(row -> !row.parsed()).count();
            String summary = rows.size() + ENTRIES_SUFFIX + (refused == 0 ? NO_TEXT : REFUSED_START + refused
                    + REFUSED_SUFFIX);
            sections.add(new ProcessSection(section.get(SECTION).getAsString(), summary, rows));
        }
    }

    private static ProcessRow rowOf(JsonObject process) {
        boolean parsed = process.get(PARSED).getAsBoolean();
        JsonElement error = process.get(ERROR);
        String verdict = parsed ? PARSED_VERDICT
                : error == null || error.isJsonNull() ? REFUSED_VERDICT : error.getAsString();
        return new ProcessRow(process.get(KEY).getAsString(),
                process.get(KIND).getAsString().toLowerCase(Locale.ROOT),
                ManagerDraft.shownText(process.get(TEXT).getAsString()), parsed, verdict);
    }

    /**
     * @return each block with its entries
     */
    public List<ProcessSection> sections() {
        return List.copyOf(sections);
    }

    /**
     * @param section the entry's block
     * @param key     the entry to drop
     */
    public void delete(String section, String key) {
        JsonArray entries = draft.sectionEntries(section);
        for (int index = 0; index < entries.size(); index++) {
            if (key.equals(entries.get(index).getAsJsonObject().get(KEY).getAsString())) {
                entries.remove(index);
                return;
            }
        }
    }

    /**
     * @param section _conversions or _post_conversions
     * @param key     the formula's key
     * @param type    the goo type it converts from
     */
    public void setFormulaSource(String section, String key, String type) {
        changeFormula(section, key, f -> new Processes.Formula(type, f.sourceDivisor(), f.target(), f.operator(),
                f.amount()));
    }

    /**
     * @param section _conversions or _post_conversions
     * @param key     the formula's key
     * @param divisor the source type's divisor
     */
    public void setFormulaDivisor(String section, String key, int divisor) {
        changeFormula(section, key, f -> new Processes.Formula(f.source(), divisor, f.target(), f.operator(),
                f.amount()));
    }

    /**
     * @param section _conversions or _post_conversions
     * @param key     the formula's key
     * @param type    the goo type it converts into, or "0" for nothing
     */
    public void setFormulaTarget(String section, String key, String type) {
        changeFormula(section, key, f -> new Processes.Formula(f.source(), f.sourceDivisor(), type, f.operator(),
                NOTHING_TARGET.equals(type) ? 0 : Math.max(1, f.amount())));
    }

    /**
     * Switches the target's amount between dividing and multiplying.
     *
     * @param section _conversions or _post_conversions
     * @param key     the formula's key
     */
    public void toggleFormulaOperator(String section, String key) {
        changeFormula(section, key, f -> new Processes.Formula(f.source(), f.sourceDivisor(), f.target(),
                DIVIDE_OPERATOR.equals(f.operator()) ? TIMES_OPERATOR : DIVIDE_OPERATOR, f.amount()));
    }

    /**
     * @param section _conversions or _post_conversions
     * @param key     the formula's key
     * @param amount  the target's divisor or multiplier
     */
    public void setFormulaAmount(String section, String key, int amount) {
        changeFormula(section, key, f -> new Processes.Formula(f.source(), f.sourceDivisor(), f.target(),
                f.operator(), amount));
    }

    /**
     * @param section _conversions or _post_conversions
     * @param key     the additive's key
     * @param prefab  the tree prefab whose value it adds
     */
    public void setAdditive(String section, String key, String prefab) {
        change(section, key, form -> form instanceof Processes.Additive ? new Processes.Additive(prefab) : form);
    }

    /**
     * @param section _conversions or _post_conversions
     * @return the keys of the block's processes a stack or a chain names, in order, the assignments left out
     */
    public List<String> processNames(String section) {
        return draft.sectionEntries(section).asList().stream().map(entry -> entry.getAsJsonObject().get(KEY)
                .getAsString()).filter(key -> !key.startsWith(GROUP_MARK)).toList();
    }

    /**
     * @param section _conversions or _post_conversions
     * @param key     the stack's key
     * @param formula the formula it applies
     */
    public void setStackFormula(String section, String key, String formula) {
        change(section, key, form -> form instanceof Processes.Stack stack
                ? new Processes.Stack(stack.count(), formula) : form);
    }

    /**
     * @param section _conversions or _post_conversions
     * @param key     the stack's key
     * @param count   how many times the formula applies
     */
    public void setStackCount(String section, String key, int count) {
        change(section, key, form -> form instanceof Processes.Stack stack
                ? new Processes.Stack(count, stack.formula()) : form);
    }

    /**
     * @return the groups the draft's _groups names, an assignment's sources
     */
    public List<String> groupNames() {
        return draft.sectionEntries(GROUPS).asList().stream().map(entry -> entry.getAsJsonObject().get(KEY)
                .getAsString()).toList();
    }

    /**
     * @param section _conversions or _post_conversions
     * @param key     the assignment's key, its target group
     * @param group   the group whose values it copies
     */
    public void setAssignmentSource(String section, String key, String group) {
        changeAssignment(section, key, a -> new Processes.Assignment(group, a.multiplier(), a.divisor(), a.chain()));
    }

    /**
     * @param section    _conversions or _post_conversions
     * @param key        the assignment's key
     * @param multiplier the scale's multiplier, 1 for none
     * @param divisor    the scale's divisor, 1 for none
     */
    public void setAssignmentScale(String section, String key, int multiplier, int divisor) {
        changeAssignment(section, key, a -> new Processes.Assignment(a.source(), multiplier, divisor, a.chain()));
    }

    /**
     * @param section _conversions or _post_conversions
     * @param key     the assignment's key
     * @param link    a stack as "@name" or an additive as "+$name", applied after the chain's last
     */
    public void addAssignmentLink(String section, String key, String link) {
        changeAssignment(section, key, a -> {
            List<String> chain = new ArrayList<>(a.chain());
            chain.add(link);
            return new Processes.Assignment(a.source(), a.multiplier(), a.divisor(), List.copyOf(chain));
        });
    }

    /**
     * @param section _conversions or _post_conversions
     * @param key     the assignment's key
     * @param index   the chain link's index
     */
    public void removeAssignmentLink(String section, String key, int index) {
        changeAssignment(section, key, a -> {
            List<String> chain = new ArrayList<>(a.chain());
            chain.remove(index);
            return new Processes.Assignment(a.source(), a.multiplier(), a.divisor(), List.copyOf(chain));
        });
    }

    /**
     * @param section _conversions or _post_conversions
     * @param key     an entry's key
     * @return the entry's form, unread where the block holds no such entry
     */
    public Processes.Form formOf(String section, String key) {
        return entryOf(section, key).map(entry -> Processes.parse(key, entry.get(TEXT).getAsString()))
                .orElseGet(() -> new Processes.Unread(NO_TEXT));
    }

    private void changeFormula(String section, String key, UnaryOperator<Processes.Formula> edit) {
        change(section, key, form -> form instanceof Processes.Formula formula ? edit.apply(formula) : form);
    }

    private void changeAssignment(String section, String key, UnaryOperator<Processes.Assignment> edit) {
        change(section, key, form -> form instanceof Processes.Assignment assignment ? edit.apply(assignment) : form);
    }

    // Writes the entry's changed form through Processes' writers, so the draft holds what the parser reads.
    private void change(String section, String key, UnaryOperator<Processes.Form> edit) {
        entryOf(section, key).ifPresent(entry -> entry.addProperty(TEXT,
                Processes.write(edit.apply(Processes.parse(key, entry.get(TEXT).getAsString())))));
    }

    private Optional<JsonObject> entryOf(String section, String key) {
        return draft.sectionEntries(section).asList().stream().map(JsonElement::getAsJsonObject)
                .filter(entry -> key.equals(entry.get(KEY).getAsString())).findFirst();
    }
}
