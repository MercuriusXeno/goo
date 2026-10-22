package com.mercuriusxeno.goo.tools.workbench;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mercuriusxeno.goo.data.GooBaseValuesPreview;
import com.mercuriusxeno.goo.data.GooConversion;
import java.util.List;

/**
 * The Processes pane's read: each conversion entry's form and whether the game's conversion parser accepted
 * it, block by block in the order the parser applies them (decision workbench-authors-base-values-whole).
 */
final class ProcessesJson {
    private static final String PRE = "_conversions";
    private static final String POST = "_post_conversions";
    private static final String SECTIONS = "sections";
    private static final String SECTION = "section";
    private static final String PROCESSES = "processes";
    private static final String KEY = "key";
    private static final String KIND = "kind";
    private static final String TEXT = "text";
    private static final String PARSED = "parsed";
    private static final String TAG_PREFIX = "#";
    private static final String ERROR = "error";
    private static final String NOT_REACHED = "not reached: the parser stops at ";

    private ProcessesJson() {
    }

    /**
     * @param model the model
     * @return both blocks' entries with their forms and the parser's verdict
     */
    static String described(BaseValuesModel model) {
        JsonArray sections = new JsonArray();
        for (String section : List.of(PRE, POST)) {
            List<Processes.Process> read = Processes.read(model, section);
            Verdict verdict = verdict(model, section, read);
            JsonArray processes = new JsonArray();
            for (int i = 0; i < read.size(); i++) {
                Processes.Process process = read.get(i);
                JsonObject json = new JsonObject();
                json.addProperty(KEY, process.key());
                json.addProperty(KIND, process.kind().name());
                json.addProperty(TEXT, process.text());
                boolean reached = verdict.stoppedAt() < 0 || i < verdict.stoppedAt();
                json.addProperty(PARSED, reached && accepted(process.key(), verdict.block()));
                if (i == verdict.stoppedAt()) {
                    json.addProperty(ERROR, verdict.error());
                } else if (!reached) {
                    json.addProperty(ERROR, NOT_REACHED + read.get(verdict.stoppedAt()).key());
                }
                processes.add(json);
            }
            JsonObject json = new JsonObject();
            json.addProperty(SECTION, section);
            json.add(PROCESSES, processes);
            sections.add(json);
        }
        JsonObject root = new JsonObject();
        root.add(SECTIONS, sections);
        return root.toString();
    }

    /**
     * What the parser made of a block: the parsed block, and where it threw, -1 where it read every entry.
     */
    private record Verdict(GooConversion.ParsedConversions block, int stoppedAt, String error) {
    }

    /**
     * The parser throws on a malformed formula rather than refusing that entry alone, so a throwing block is
     * read again one entry longer at a time to find the entry it stops at.
     */
    private static Verdict verdict(BaseValuesModel model, String section, List<Processes.Process> read) {
        try {
            return new Verdict(parsedBlock(model, section), -1, null);
        } catch (IllegalArgumentException whole) {
            GooConversion.ParsedConversions last = GooConversion.parseBlock(java.util.Map.of());
            BaseValuesModel prefix = withoutEntries(model, section);
            for (int i = 0; i < read.size(); i++) {
                prefix = Processes.with(prefix, section, read.get(i).key(), read.get(i).text());
                try {
                    last = parsedBlock(prefix, section);
                } catch (IllegalArgumentException stop) {
                    return new Verdict(last, i, stop.getMessage());
                }
            }
            return new Verdict(last, -1, null);
        }
    }

    private static GooConversion.ParsedConversions parsedBlock(BaseValuesModel model, String section) {
        GooBaseValuesPreview.Conversions parsed = GooBaseValuesPreview.conversions(model.serialize());
        return PRE.equals(section) ? parsed.pre() : parsed.post();
    }

    private static BaseValuesModel withoutEntries(BaseValuesModel model, String section) {
        return model.withMembers(model.members().stream()
                .map(member -> section.equals(member.key()) ? member.withEntries(List.of()) : member).toList());
    }

    private static boolean accepted(String key, GooConversion.ParsedConversions block) {
        if (key.startsWith(TAG_PREFIX)) {
            String target = key.substring(TAG_PREFIX.length());
            return block.assignments().stream().anyMatch(assignment -> assignment.target().equals(target)
                    || assignment.target().equals(key));
        }
        return block.formulas().containsKey(key) || block.additives().containsKey(key)
                || block.stacks().containsKey(key);
    }
}
