package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.ability.program.EntityFilter;
import com.mercuriusxeno.goo.ability.program.FieldEffectStep;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * nether_decay.json, read from the classpath as the loader reads it: the hive its hold makes of a prism strikes
 * living mobs alone (decision hive-prism-pillar-eats-the-living).
 */
class NetherDecayJsonTest {

    private final AbilityDefinition definition = AbilityJson.decode("nether_decay");

    /**
     * The hive's field keeps living mobs still alive, the operator's ruling of mobs only.
     */
    @Test
    void theHiveStrikesLivingMobsAlone() {
        FieldEffectStep hive = (FieldEffectStep) definition.onPrism().getFirst();
        assertEquals(List.of(EntityFilter.LIVING, EntityFilter.ALIVE, EntityFilter.MOB), hive.where());
    }
}
