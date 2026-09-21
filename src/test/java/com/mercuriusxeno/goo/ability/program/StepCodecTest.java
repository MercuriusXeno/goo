package com.mercuriusxeno.goo.ability.program;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
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
            "damage", new DamageStep(Expr.parse("health / 2").getOrThrow(), DamageKind.CACTUS)
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
