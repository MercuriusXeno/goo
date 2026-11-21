package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.SpikeTrapFixture;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The damage a damage step hurts with, read from the shipped ability JSON: its type through the kind's mapping,
 * its amount, knockback and the immunity it leaves.
 */
class DamageStepTest {

    private static final Identifier GOO_STALAGMITE = Identifier.parse("goo:stalagmite");
    /** The immunity a vanilla melee hit leaves its target. */
    private static final int IMMUNE_AFTER_A_MELEE_HIT = 10;
    /** The immunity vanilla's hurt leaves its target, LivingEntity's twenty ticks. */
    private static final int IMMUNE_AFTER_A_VANILLA_HURT = 20;

    /**
     * The spike trap's stab deals goo:stalagmite, which no fall tag holds, so a fall-immune chicken takes it.
     */
    // decision urchin-rests-bristled-and-spears
    @Test
    void spikeTrapsStabDealsGooStalagmiteDamage() {
        assertEquals(Optional.of(GOO_STALAGMITE), DamageStep.gooTypeOf(spikeTrapsStab().source()).map(ResourceKey::identifier));
    }

    /**
     * Bore's strike is the thrower's attack, keeping the target's motion and naming the immunity it leaves.
     */
    @Test
    void boresStrikeIsAnAttackKeepingMotionAndNamingItsImmunity() {
        DamageStep strike = boresStrike();
        assertEquals(DamageKind.ATTACK, strike.source());
        assertEquals(false, strike.knockback());
        assertTrue(strike.invulnerableTicks().isPresent());
    }

    /**
     * Bore's hit leaves its target the immunity it names.
     */
    @Test
    void boresHitLeavesItsTargetTheImmunityItNames() {
        LivingEntity cow = struck(Vec3.ZERO);
        TargetHost host = mock(TargetHost.class);
        when(host.target()).thenReturn(cow);

        DamageStep strike = boresStrike();
        strike.tick(new StepContext(host, 0, 0));

        assertEquals(strike.invulnerableTicks().orElseThrow().evaluateInt(Variables.NONE), cow.damageCooldownTime);
    }

    /**
     * A goo hit naming no immunity leaves its target none, so a melee hit right after it lands in full
     * (decision attack-key-touches-plus-punches).
     */
    @Test
    void aGooHitNamingNoImmunityLeavesItsTargetNone() {
        LivingEntity villager = struck(Vec3.ZERO);
        // vanilla's hurt leaves its target immune, so the step's reset after the hurt is what reads here
        when(villager.hurtServer(any(), any(), anyFloat())).thenAnswer(call -> {
            villager.damageCooldownTime = IMMUNE_AFTER_A_VANILLA_HURT;
            return true;
        });

        new DamageStep(Expr.literal(1), DamageKind.MAGIC).tick(new StepContext(hostOn(villager), 0, 0));

        assertEquals(0, villager.damageCooldownTime);
    }

    /**
     * A goo hit clears its target's immunity before it hurts, so it lands through a melee hit just taken
     * (decision attack-key-touches-plus-punches).
     */
    @Test
    void aGooHitClearsItsTargetsImmunityBeforeItHurts() {
        LivingEntity villager = struck(Vec3.ZERO);
        villager.damageCooldownTime = IMMUNE_AFTER_A_MELEE_HIT;
        int[] immuneAtTheHurt = {-1};
        when(villager.hurtServer(any(), any(), anyFloat())).thenAnswer(call -> {
            immuneAtTheHurt[0] = villager.damageCooldownTime;
            return true;
        });

        new DamageStep(Expr.literal(1), DamageKind.MAGIC).tick(new StepContext(hostOn(villager), 0, 0));

        assertEquals(0, immuneAtTheHurt[0]);
    }

    /**
     * Bore's hit gives its target back the motion the hit found, so the strike pushes nothing.
     */
    @Test
    void boresHitRestoresTheMotionItFound() {
        Vec3 drifting = new Vec3(0.1, 0, -0.2);
        LivingEntity cow = struck(drifting);

        boresStrike().tick(new StepContext(hostOn(cow), 0, 0));

        verify(cow).setDeltaMovement(drifting);
    }

    // A mocked mob in a mocked server level, drifting at the motion given.
    private static LivingEntity struck(Vec3 motion) {
        LivingEntity target = mock(LivingEntity.class);
        when(target.level()).thenReturn(mock(ServerLevel.class));
        when(target.damageSources()).thenReturn(mock(DamageSources.class));
        when(target.getDeltaMovement()).thenReturn(motion);
        return target;
    }

    private static TargetHost hostOn(LivingEntity target) {
        TargetHost host = mock(TargetHost.class);
        when(host.target()).thenReturn(target);
        return host;
    }
    private static DamageStep spikeTrapsStab() {
        FieldEffectStep field = LingerStep.bodyOf(SpikeTrapFixture.definition().behaviors()).orElseThrow().stream()
                .filter(FieldEffectStep.class::isInstance).map(FieldEffectStep.class::cast).findFirst().orElseThrow();
        return (DamageStep) field.strike().getFirst();
    }

    private static DamageStep boresStrike() {
        BoreStep bore = (BoreStep) AbilityJson.decode("rock_bore").behaviors().getFirst();
        return (DamageStep) bore.strike().getFirst();
    }
}
