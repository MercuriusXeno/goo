package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.ability.program.ExplodeStep;
import com.mercuriusxeno.goo.ability.program.ExplosionMarch;
import com.mercuriusxeno.goo.ability.program.ExplosionMode;
import com.mercuriusxeno.goo.ability.program.Expr;
import com.mercuriusxeno.goo.ability.program.Step;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers the preview sphere an ability's definition draws: an explosive
 * ability's sphere is sized to its explosion's max reach.
 */
class AbilityDefinitionTest {

    private static final double JSON_SIZE = 2.5;
    private static final double TOLERANCE = 1e-9;
    private static final double CONE_ANGLE = 30;
    private static final double EXPLOSION_POWER = 3;

    @ParameterizedTest
    @CsvSource({"unstable_explode, 3.0", "unstable_lurker, 2.5"})
    void explosiveSphereIsDrawnAtTheExplosionsMaxReach(String name, float power) {
        assertEquals(ExplosionMarch.maxReach(power), AbilityJson.decode(name).area().size(), TOLERANCE);
    }

    @Test
    void sphereOverAProgramThatNeverExplodesKeepsItsSize() {
        AbilityArea written = new AbilityArea(AbilityArea.Shape.SPHERE, JSON_SIZE, 0);
        assertEquals(written, AbilityDefinition.previewAtMaxReach(written, List.of()));
    }

    @Test
    void coneOverAnExplosionKeepsItsSize() {
        AbilityArea written = new AbilityArea(AbilityArea.Shape.CONE, JSON_SIZE, CONE_ANGLE);
        List<Step> explodes = List.of(new ExplodeStep(Expr.literal(EXPLOSION_POWER), ExplosionMode.TNT));
        assertEquals(written, AbilityDefinition.previewAtMaxReach(written, explodes));
    }
}
