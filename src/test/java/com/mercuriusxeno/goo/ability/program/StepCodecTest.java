package com.mercuriusxeno.goo.ability.program;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every registered step type round-trips through the dispatch codec, and
 * the codec refuses a type name or a filter it does not know.
 */
class StepCodecTest {

    /**
     * One sample per registered type, keyed by type name; the registry
     * check below fails when a type is registered without a sample here.
     */
    private static final Map<String, Step> SAMPLES = Map.of(
            "wait", new WaitStep(Expr.parse("4 + stacks").getOrThrow()),
            "await_entity", new AwaitEntityStep(SelectionShape.CUBE, Expr.literal(3),
                    List.of(EntityFilter.LIVING, EntityFilter.NOT_ITEM)),
            "explode", new ExplodeStep(Expr.parse("2.5 + 1.0 * (stacks - 1)").getOrThrow(), ExplosionMode.NONE),
            "damage", new DamageStep(Expr.parse("health / 2").getOrThrow(), DamageKind.CACTUS),
            "place_block", new PlaceBlockStep(Identifier.parse("goo:glow_crystal"), Map.of(
                    "facing", new StateValue.PlacedFace(),
                    "shape", new StateValue.Named("flat"),
                    "size", new StateValue.Pick(Expr.parse("stacks - 1").getOrThrow(), List.of("tiny", "large")))),
            "progressive_area", new ProgressiveAreaStep(AreaShape.FLAT_CIRCLE, "fortune_smelt_break",
                    "blaze_flame", "generic_explode", Expr.literal(8))
    );

    private static Step roundTrip(Step step) {
        JsonElement json = StepTypes.CODEC.encodeStart(JsonOps.INSTANCE, step).getOrThrow();
        return StepTypes.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
    }

    private static DataResult<Step> decode(String json) {
        return StepTypes.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }

    @Test
    void everyRegisteredTypeHasASample() {
        assertEquals(SAMPLES.keySet(), StepTypes.all().stream().map(StepType::name).collect(Collectors.toSet()));
    }

    @Test
    void everyRegisteredTypeRoundTrips() {
        for (Step sample : SAMPLES.values()) {
            assertEquals(sample, roundTrip(sample), sample.type().name());
        }
    }

    @Test
    void typeFieldNamesTheStep() {
        JsonElement json = StepTypes.CODEC.encodeStart(JsonOps.INSTANCE, SAMPLES.get("explode")).getOrThrow();
        assertEquals("explode", json.getAsJsonObject().get("type").getAsString());
    }

    @Test
    void optionalParamsDefault() {
        Step step = decode("{\"type\": \"await_entity\", \"radius\": 3}").getOrThrow();
        AwaitEntityStep await = assertInstanceOf(AwaitEntityStep.class, step);
        assertEquals(SelectionShape.SPHERE, await.shape());
        assertEquals(List.of(), await.where());
        ExplodeStep explode = assertInstanceOf(ExplodeStep.class,
                decode("{\"type\": \"explode\", \"power\": 2}").getOrThrow());
        assertEquals(ExplosionMode.TNT, explode.mode());
        DamageStep damage = assertInstanceOf(DamageStep.class,
                decode("{\"type\": \"damage\", \"amount\": 8}").getOrThrow());
        assertEquals(DamageKind.MAGIC, damage.source());
    }

    @Test
    void javelinStepsDecode() {
        Step step = decode("{\"type\": \"damage\", \"amount\": 8.0, \"source\": \"magic\"}").getOrThrow();
        DamageStep damage = assertInstanceOf(DamageStep.class, step);
        assertEquals(8.0, damage.amount().evaluate(Variables.NONE));
        assertEquals(DamageKind.MAGIC, damage.source());
    }

    @Test
    void proximityMineStepsDecode() {
        String json = "[{\"type\": \"await_entity\", \"shape\": \"sphere\", \"radius\": 3.0, \"where\": [\"living\"]},"
                + " {\"type\": \"explode\", \"power\": \"2.5 + 1.0 * (stacks - 1)\"}]";
        List<Step> steps = StepTypes.LIST_CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
        assertEquals(2, steps.size());
        assertInstanceOf(AwaitEntityStep.class, steps.get(0));
        assertInstanceOf(ExplodeStep.class, steps.get(1));
    }

    @Test
    void glowCrystalStepDecodes() {
        String json = "{\"type\": \"place_block\", \"block\": \"goo:glow_crystal\", \"state\": {"
                + "\"facing\": \"face\", \"shape\": {\"by\": \"flat\", \"values\": [\"bump\", \"flat\"]},"
                + " \"size\": {\"by\": \"stacks - 1\", \"values\": [\"tiny\", \"small\", \"medium\", \"large\"]}}}";
        PlaceBlockStep step = assertInstanceOf(PlaceBlockStep.class, decode(json).getOrThrow());
        assertEquals(Identifier.parse("goo:glow_crystal"), step.block());
        assertEquals(new StateValue.PlacedFace(), step.state().get("facing"));
        StateValue.Pick shape = assertInstanceOf(StateValue.Pick.class, step.state().get("shape"));
        assertEquals(List.of("bump", "flat"), shape.values());
        assertEquals(Set.of("flat", "stacks"),
                step.expressions().flatMap(expr -> expr.variables().stream()).collect(Collectors.toSet()));
    }

    @Test
    void rockTunnelStepDecodes() {
        String json = "{\"type\": \"progressive_area\", \"shape\": \"tunnel\", \"effect\": \"silk_break\","
                + " \"visuals\": \"rock_dust\", \"audio\": \"stone_break\", \"preview_delay\": 8}";
        ProgressiveAreaStep step = assertInstanceOf(ProgressiveAreaStep.class, decode(json).getOrThrow());
        assertEquals(new ProgressiveAreaStep(AreaShape.TUNNEL, "silk_break", "rock_dust", "stone_break",
                Expr.literal(8)), step);
    }

    @Test
    void progressiveAreaRefusesADelegateNoRegistryHolds() {
        String prefix = "{\"type\": \"progressive_area\", \"shape\": \"sphere\", \"preview_delay\": 8,";
        assertTrue(decode(prefix + " \"effect\": \"melt\", \"visuals\": \"none\", \"audio\": \"none\"}").isError());
        assertTrue(decode(prefix + " \"effect\": \"freeze\", \"visuals\": \"sparks\", \"audio\": \"none\"}").isError());
        assertTrue(decode(prefix + " \"effect\": \"freeze\", \"visuals\": \"none\", \"audio\": \"thunder\"}").isError());
        assertTrue(decode(prefix.replace("sphere", "cone")
                + " \"effect\": \"freeze\", \"visuals\": \"none\", \"audio\": \"none\"}").isError());
    }

    @Test
    void emptyPickRefuses() {
        assertTrue(decode("{\"type\": \"place_block\", \"block\": \"goo:glow_crystal\","
                + " \"state\": {\"size\": {\"by\": 1, \"values\": []}}}").isError());
    }

    @Test
    void unknownTypeRefuses() {
        assertTrue(decode("{\"type\": \"teleport_everyone\"}").isError());
    }

    @Test
    void unknownFilterRefuses() {
        assertTrue(decode("{\"type\": \"await_entity\", \"radius\": 3, \"where\": [\"friendly\"]}").isError());
    }

    @Test
    void badExpressionRefuses() {
        assertTrue(decode("{\"type\": \"explode\", \"power\": \"2 +\"}").isError());
    }
}
