package com.mercuriusxeno.goo.ability;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.program.Expr;
import com.mercuriusxeno.goo.ability.program.HostVariables;
import com.mercuriusxeno.goo.ability.program.LeafStep;
import com.mercuriusxeno.goo.ability.program.LeafSteps;
import com.mercuriusxeno.goo.ability.program.ProgressiveAreaStep;
import com.mercuriusxeno.goo.ability.program.PullStep;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.ability.program.Variables;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.OptionalDouble;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The area abilities' JSONs decode to the stack ceiling and start radius the
 * shape ladder reads (decisions disc-opens-circularly-per-stack,
 * tunnel-stays-3x3-ee-homage, sphere-is-frost-alone, nether-radius-one-per-stack) and the layer visuals
 * each names (decision themed-ring-before-every-layer), read from the classpath.
 */
class AreaAbilityJsonTest {

    private static final String ABILITIES = "data/goo/goo_abilities/";
    private static final int LADDER_STACKS = 6;

    private static JsonObject read(String ability) throws IOException {
        String resource = ABILITIES + ability + ".json";
        try (InputStream in = AreaAbilityJsonTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(in, "Classpath holds no " + resource);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static AbilityDefinition decode(String ability, JsonObject json) {
        String resource = ABILITIES + ability + ".json";
        return AbilityDefinition.codecFor(AbilityJson.idOfResource(resource)).parse(JsonOps.INSTANCE, json)
                .getOrThrow(message -> new IllegalStateException(resource + ": " + message));
    }

    private static AbilityDefinition decode(String ability) throws IOException {
        return decode(ability, read(ability));
    }

    @ParameterizedTest
    @ValueSource(strings = {"rock_flat", "blaze_flat", "frost_flat"})
    void flatDiscStacksSixFromRadiusZero(String ability) throws IOException {
        AbilityDefinition definition = decode(ability);
        ProgressiveAreaStep step = (ProgressiveAreaStep) definition.behaviors().getFirst();

        assertEquals(LADDER_STACKS, definition.chain().maxStacks(), ability + " maxStacks");
        assertEquals(0, step.startRadius(), ability + " start_radius");
    }

    @ParameterizedTest
    @ValueSource(strings = {"rock_tunnel", "blaze_tunnel", "frost_tunnel"})
    void tunnelStacksSix(String ability) throws IOException {
        assertEquals(LADDER_STACKS, decode(ability).chain().maxStacks(), ability + " maxStacks");
    }

    @ParameterizedTest
    @CsvSource({"rock_tunnel, rock_dust", "rock_flat, rock_dust", "blaze_tunnel, blaze_flame",
            "blaze_flat, blaze_flame", "frost_tunnel, frost_rime", "frost_flat, frost_rime"})
    void everyTunnelAndFlatRingsItsLayersThroughItsTypesVisuals(String ability, String visuals) throws IOException {
        ProgressiveAreaStep step = (ProgressiveAreaStep) decode(ability).behaviors().getFirst();

        assertEquals(visuals, step.visuals(), ability + " visuals");
    }

    @Test
    void frostBallTakesNoRing() throws IOException {
        ProgressiveAreaStep step = (ProgressiveAreaStep) decode("frost_sphere").behaviors().getFirst();

        assertEquals(LayerVisualsType.NONE, step.visuals());
    }

    @Test
    void frostBallOpensFromRadiusThreeOneBlockPerStack() throws IOException {
        ProgressiveAreaStep step = (ProgressiveAreaStep) decode("frost_sphere").behaviors().getFirst();

        assertEquals(List.of(3, 4, 5, 6), IntStream.rangeClosed(1, 4).mapToObj(step::radius).toList());
    }

    @Test
    void blackHoleConsumesThreeToSevenAndPullsThreeTimesThat() throws IOException {
        List<Step> steps = decode("nether_black_hole").behaviors().stream()
                .flatMap(AreaAbilityJsonTest::withDescendants).toList();
        Expr consume = steps.stream().filter(LeafStep.class::isInstance).map(LeafStep.class::cast)
                .filter(leaf -> leaf.leaf() == LeafSteps.CONSUME_BLOCKS).map(leaf -> (Expr) leaf.params())
                .findFirst().orElseThrow();
        List<Expr> pulls = steps.stream().filter(PullStep.class::isInstance).map(PullStep.class::cast)
                .map(PullStep::radius).toList();

        assertFalse(pulls.isEmpty(), "nether_black_hole holds no pull step");
        for (int stacks = 1; stacks <= 5; stacks++) {
            Variables scope = stacksScope(stacks);
            assertEquals(stacks + 2, consume.evaluateInt(scope), "consume radius at stacks=" + stacks);
            for (Expr pull : pulls) {
                assertEquals(3 * (stacks + 2), pull.evaluateInt(scope), "pull radius at stacks=" + stacks);
            }
        }
    }

    private static Stream<Step> withDescendants(Step step) {
        return Stream.concat(Stream.of(step), step.children().flatMap(AreaAbilityJsonTest::withDescendants));
    }

    private static Variables stacksScope(int stacks) {
        return name -> HostVariables.STACKS.equals(name) ? OptionalDouble.of(stacks) : OptionalDouble.empty();
    }

    @Test
    void progressiveAreaReadsTheStartRadiusItsJsonNames() throws IOException {
        JsonObject json = read("rock_flat");
        json.getAsJsonArray("behaviors").get(0).getAsJsonObject().addProperty("start_radius", 2);

        ProgressiveAreaStep step = (ProgressiveAreaStep) decode("rock_flat", json).behaviors().getFirst();

        assertEquals(2, step.startRadius());
    }
}
