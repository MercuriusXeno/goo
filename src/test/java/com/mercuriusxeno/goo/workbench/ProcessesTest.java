package com.mercuriusxeno.goo.workbench;

import com.mercuriusxeno.goo.data.GooBaseValuesPreview;
import com.mercuriusxeno.goo.data.GooConversion;
import com.mercuriusxeno.goo.type.GooTypes;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the Processes pane's writes: each form authored through the model is what the game's conversion
 * parser reads, and the shipped _post_conversions block survives a read into the pane's forms and a write
 * back (decision workbench-authors-base-values-whole).
 */
class ProcessesTest {

    private static final String POST = "_post_conversions";
    private static final String SMALL = """
            {
                "_constants": {
                    "waxed": {"weird": 9}
                },
                "_groups": {
                    "copper": ["copper_block"]
                },
                "_restricted": [],
                "_conversions": {},
                "_post_conversions": {}
            }
            """;

    /**
     * A formula, an additive, two stacks, a parallel copy and an assignment parse back to what was written.
     */
    @Test
    void everyFormParsesAsTheGameReadsIt() {
        BaseValuesModel model = BaseValuesModel.parse(SMALL);
        model = Processes.with(model, POST, "oxidation_metal", Processes.formula("metal", 4, "yore", "/", 9));
        model = Processes.with(model, POST, "waxed", Processes.additive("waxed"));
        model = Processes.with(model, POST, "exposed_metal", Processes.stack(1, "oxidation_metal"));
        model = Processes.with(model, POST, "weathered_metal", Processes.stack(2, "oxidation_metal"));
        model = Processes.with(model, POST, "#half_copper", Processes.assignment("copper", 3, 4, List.of()));
        model = Processes.with(model, POST, "#exposed", Processes.assignment("copper", 1, 1, List.of("exposed_metal")));

        GooConversion.ParsedConversions parsed = GooBaseValuesPreview.conversions(model.serialize()).post();
        assertEquals(new GooConversion.Formula(GooTypes.METAL, 4, GooTypes.YORE, 9, 1),
                parsed.formulas().get("oxidation_metal"));
        assertTrue(parsed.additives().containsKey("waxed"));
        assertEquals(new GooConversion.Stack("oxidation_metal", 1), parsed.stacks().get("exposed_metal"));
        assertEquals(new GooConversion.Stack("oxidation_metal", 2), parsed.stacks().get("weathered_metal"));
        GooConversion.Assignment copy = parsed.assignments().stream()
                .filter(assignment -> assignment.target().endsWith("half_copper")).findFirst().orElseThrow();
        assertEquals(3, copy.scaleMultiplier());
        assertEquals(4, copy.scaleDivisor());
        GooConversion.Assignment exposed = parsed.assignments().stream()
                .filter(assignment -> assignment.target().endsWith("exposed")).findFirst().orElseThrow();
        assertTrue(exposed.parallelSource().endsWith("copper"));
        assertEquals(List.of(new GooConversion.Stack("oxidation_metal", 1)), exposed.chain());
    }

    /**
     * The pane reports every shipped process as one the game's parser accepts, and a broken one as refused.
     */
    @Test
    void paneReportsTheParsersVerdict() throws IOException {
        BaseValuesModel model = BaseValuesModel.parse(new String(BaseValuesModelTest.sampleBytes(),
                StandardCharsets.UTF_8));
        com.google.gson.JsonArray processes = com.google.gson.JsonParser.parseString(ProcessesJson.described(model))
                .getAsJsonObject().getAsJsonArray("sections").get(1).getAsJsonObject().getAsJsonArray("processes");
        assertEquals(20, processes.size());
        assertTrue(processes.asList().stream().allMatch(process -> process.getAsJsonObject().get("parsed")
                .getAsBoolean()));
        BaseValuesModel broken = Processes.with(model, POST, "melt_badly", "\"metal / -> yore\"");
        com.google.gson.JsonArray withBroken = com.google.gson.JsonParser.parseString(ProcessesJson.described(broken))
                .getAsJsonObject().getAsJsonArray("sections").get(1).getAsJsonObject().getAsJsonArray("processes");
        assertTrue(withBroken.asList().stream().anyMatch(process -> "melt_badly".equals(
                process.getAsJsonObject().get("key").getAsString())
                && !process.getAsJsonObject().get("parsed").getAsBoolean()));
    }

    /**
     * Every shipped process reads into one of the pane's forms and writes back as the file spells it.
     */
    @Test
    void shippedProcessesRoundTripThroughTheForms() throws IOException {
        byte[] shipped = BaseValuesModelTest.sampleBytes();
        BaseValuesModel model = BaseValuesModel.parse(new String(shipped, StandardCharsets.UTF_8));
        BaseValuesModel rewritten = model;
        for (Processes.Process process : Processes.read(model, POST)) {
            rewritten = Processes.with(rewritten, POST, process.key(), process.text());
        }
        assertEquals(20, Processes.read(model, POST).size());
        assertTrue(Processes.read(model, POST).stream().noneMatch(process -> process.kind() == Processes.Kind.UNREAD));
        assertArrayEquals(shipped, rewritten.serialize().getBytes(StandardCharsets.UTF_8));
    }
}
