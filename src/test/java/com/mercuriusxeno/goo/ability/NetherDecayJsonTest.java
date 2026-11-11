package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.ability.program.EntityFilter;
import com.mercuriusxeno.goo.ability.program.FieldEffectStep;
import com.mercuriusxeno.goo.ability.program.Variables;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * nether_decay.json, read from the classpath as the loader reads it: Decay's
 * bite seeks the nearest mob within 45 degrees either side of its aim and 8
 * blocks out, past the 6 it paints blocks within (decisions
 * decay-gnats-degrade-each-block-once, decay-paints-the-one-block-under-the-crosshair),
 * and the hive its hold makes of a prism strikes mobs within 5 blocks
 * (decision hive-prism-pillar-eats-the-living).
 */
class NetherDecayJsonTest {

    private static final double SEEK_CONE_DEGREES = 90;
    private static final double SEEK_RANGE = 8;
    private static final double PAINT_RANGE = 6;
    private static final double HIVE_RADIUS = 5;
    private static final double DELTA = 1e-9;

    private final AbilityDefinition definition = AbilityJson.decode("nether_decay");
    private final Delivery decay = definition.delivery();

    @Test
    void theBiteSeeksWithinFortyFiveDegreesEitherSideOfTheAim() {
        assertEquals(SEEK_CONE_DEGREES, decay.coneDegrees(), DELTA);
    }

    @Test
    void theBiteSeeksTwoBlocksPastWhereTheSwarmPaints() {
        assertEquals(SEEK_RANGE, decay.livingReach(), DELTA);
        assertEquals(PAINT_RANGE, decay.range(), DELTA);
    }

    /**
     * The hive's field keeps living mobs still alive, the operator's ruling of mobs only, within five blocks.
     */
    @Test
    void theHiveStrikesMobsWithinFiveBlocks() {
        FieldEffectStep hive = (FieldEffectStep) definition.onPrism().getFirst();
        assertEquals(List.of(EntityFilter.LIVING, EntityFilter.ALIVE, EntityFilter.MOB), hive.where());
        assertEquals(HIVE_RADIUS, hive.radius().evaluate(Variables.NONE), DELTA);
    }
}
