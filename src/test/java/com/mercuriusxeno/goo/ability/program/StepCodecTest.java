package com.mercuriusxeno.goo.ability.program;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
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
    private static final Map<String, Step> SAMPLES = Map.ofEntries(
            Map.entry("wait", new WaitStep(Expr.parse("4 + stacks").getOrThrow())),
            Map.entry("await_entity", new AwaitEntityStep(SelectionShape.CUBE, Expr.literal(3),
                    List.of(EntityFilter.LIVING, EntityFilter.NOT_ITEM))),
            Map.entry("explode", new ExplodeStep(Expr.parse("2.5 + 1.0 * (stacks - 1)").getOrThrow(),
                    ExplosionMode.NONE)),
            Map.entry("damage", new DamageStep(Expr.parse("health / 2").getOrThrow(), DamageKind.CACTUS)),
            Map.entry("potion", new PotionStep(Identifier.parse("minecraft:levitation"), Expr.literal(100),
                    Expr.parse("1 + stacks").getOrThrow(), false)),
            Map.entry("target", new TargetStep(List.of(EntityFilter.NOT_BOSS),
                    List.of(new DamageStep(Expr.literal(4), DamageKind.FREEZE)))),
            Map.entry("set_health", new SetHealthStep(Expr.parse("0.5 * health / max_health").getOrThrow())),
            Map.entry("freeze_ticks",
                    new FreezeTicksStep(Expr.parse("140 * 25 / pow(health, 0.2) / 100").getOrThrow())),
            Map.entry("set_ai", new SetAiStep(false)),
            Map.entry("set_invulnerable", new SetInvulnerableStep(true)),
            Map.entry("clone_entity", new CloneEntityStep(Expr.parse("100 / pow(max_health, 0.6)").getOrThrow())),
            Map.entry("drop_item", new DropItemStep(Identifier.parse("minecraft:cobblestone"),
                    Expr.parse("1 + random(3)").getOrThrow()))
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
        PotionStep potion = assertInstanceOf(PotionStep.class,
                decode("{\"type\": \"potion\", \"effect\": \"minecraft:poison\", \"duration\": 60}").getOrThrow());
        assertEquals(0, potion.amplifier().evaluate(Variables.NONE));
        assertTrue(potion.visible());
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
    void targetStepDecodesItsChildren() {
        String json = "{\"type\": \"target\", \"where\": [\"not_boss\"], \"steps\": ["
                + "{\"type\": \"potion\", \"effect\": \"minecraft:poison\", \"duration\": 200}]}";
        TargetStep target = assertInstanceOf(TargetStep.class, decode(json).getOrThrow());
        assertEquals(List.of(EntityFilter.NOT_BOSS), target.where());
        assertInstanceOf(PotionStep.class, target.steps().get(0));
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
