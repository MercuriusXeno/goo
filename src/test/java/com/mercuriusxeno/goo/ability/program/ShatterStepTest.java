package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.DeliveryKind;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Shatter as crystal_shatter.json ships it lays its goo by a stream spray
 * whose one behavior is the cell laying shatter step
 * (decision shatter-is-splattered-goo-and-shards-form-over-the-splatters).
 */
class ShatterStepTest {

    private static final AbilityDefinition SHATTER = AbilityJson.decode("crystal_shatter");

    @Test
    void shatterLaysItsGooByAStreamSpray() {
        assertEquals(DeliveryKind.STREAM, SHATTER.delivery().kind());
    }

    @Test
    void shattersOneBehaviorIsTheCellLayingShatterStep() {
        assertEquals(List.of(ShatterStep.class), SHATTER.behaviors().stream().map(Object::getClass).toList());
        assertEquals(DripLanding.in(SHATTER.behaviors()).orElseThrow(), SHATTER.behaviors().getFirst());
    }
}
