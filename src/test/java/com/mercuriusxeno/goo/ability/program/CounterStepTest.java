package com.mercuriusxeno.goo.ability.program;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The counters a struck entity keeps between hits: they save and load
 * through their codec, a counter step adds to one on the entity host and
 * refuses the marker host, and an expression reads one by its id
 * (decision aeon-mob-ritual-drops-spawn-egg).
 */
class CounterStepTest {

    private static final Identifier RITUAL = Identifier.parse("goo:ritual");
    private static final double SAVED_RITUAL = 50.24;
    private static final String MARKER_LABEL = "marker block";

    private static Expr expr(String source) {
        return Expr.parse(source).getOrThrow();
    }

    @Test
    void countersRoundTripThroughTheAttachmentCodec() {
        EntityCounters saved = new EntityCounters(Map.of(RITUAL, SAVED_RITUAL));

        JsonElement json = EntityCounters.CODEC.codec().encodeStart(JsonOps.INSTANCE, saved).getOrThrow();
        EntityCounters loaded = EntityCounters.CODEC.codec().parse(JsonOps.INSTANCE, json).getOrThrow();

        assertEquals(SAVED_RITUAL, loaded.read(RITUAL));
        assertEquals(saved, loaded);
    }

    @Test
    void addingAccumulatesAndAnUnwrittenCounterReadsZero() {
        EntityCounters counters = EntityCounters.EMPTY.withAdded(RITUAL, 25).withAdded(RITUAL, 25);

        assertEquals(50, counters.read(RITUAL));
        assertEquals(0, counters.read(Identifier.parse("goo:other")));
    }

    @Test
    void counterStepRefusesTheMarkerHostAndLoadsForTheEntityHost() {
        List<Step> steps = List.of(CounterStep.adding(RITUAL, Expr.literal(1)));

        ProgramLoadException refusal = assertThrows(ProgramLoadException.class,
                () -> ProgramBehavior.forHost(steps, HostKind.MARKER));

        assertTrue(refusal.getMessage().contains("counter"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains(MARKER_LABEL), refusal.getMessage());
        assertDoesNotThrow(() -> ProgramBehavior.forHost(steps, HostKind.ENTITY));
    }

    @Test
    void counterStepAddsTheEvaluatedAmountToTheTargetsCounter() {
        StepHost host = mock(StepHost.class);
        when(host.kind()).thenReturn(HostKind.ENTITY);
        when(host.read("max_health")).thenReturn(OptionalDouble.of(10));
        ProgramBehavior program = ProgramBehavior.forHost(
                List.of(CounterStep.adding(RITUAL, expr("100 / pow(max_health, 0.6)"))), HostKind.ENTITY);

        program.tick(host);

        verify(host).addTargetCounter(RITUAL, 100 / Math.pow(10, 0.6));
    }

    @Test
    void anExpressionReadsACounterByItsIdOnlyWhereATargetKeepsCounters() {
        List<Step> reads = List.of(new DamageStep(expr("goo:ritual / 10"), DamageKind.MAGIC));

        assertEquals(Set.of("goo:ritual"), expr("goo:ritual / 10").variables());
        assertDoesNotThrow(() -> ProgramBehavior.forHost(reads, HostKind.ENTITY));
        List<Step> markerReads = List.of(new ExplodeStep(expr("goo:ritual"), ExplosionMode.NONE));
        ProgramLoadException refusal = assertThrows(ProgramLoadException.class,
                () -> ProgramBehavior.forHost(markerReads, HostKind.MARKER));
        assertTrue(refusal.getMessage().contains("goo:ritual"), refusal.getMessage());
    }
}
