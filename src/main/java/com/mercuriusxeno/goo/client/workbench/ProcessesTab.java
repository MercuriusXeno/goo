package com.mercuriusxeno.goo.client.workbench;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
     * Writes an entry's text as the JSON string the file holds, as the page's field does.
     *
     * @param section the entry's block
     * @param key     the entry's key
     * @param typed   the field's text
     */
    public void edit(String section, String key, String typed) {
        for (JsonElement element : draft.sectionEntries(section)) {
            JsonObject entry = element.getAsJsonObject();
            if (key.equals(entry.get(KEY).getAsString())) {
                entry.addProperty(TEXT, new JsonPrimitive(typed.trim()).toString());
                return;
            }
        }
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
}
