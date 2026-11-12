package com.mercuriusxeno.goo.ability.program;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.AbilityJson;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The damage a damage step hurts with, read from the shipped ability JSON: its type through the kind's mapping,
 * its amount, knockback and the immunity it leaves, and the vanilla tags that name goo's types.
 */
class DamageStepTest {

    private static final Identifier GOO_STALAGMITE = Identifier.parse("goo:stalagmite");
    private static final Identifier GOO_EXPIRE = Identifier.parse("goo:expire");
    private static final double EXPIRE_DAMAGE = 1_000_000;
    private static final double BORE_DAMAGE = 2;
    private static final int BORE_IMMUNE_TICKS = 10;
    private static final double DELTA = 1e-9;
    /** The immunity a vanilla melee hit leaves its target. */
    private static final int IMMUNE_AFTER_A_MELEE_HIT = 10;

    /**
     * Urchin's stab deals goo:stalagmite, which no fall tag holds, so a fall-immune chicken takes it.
     */
    // decision urchin-rests-bristled-and-spears
    @Test
    void urchinsStabDealsGooStalagmiteDamage() {
        assertEquals(Optional.of(GOO_STALAGMITE), DamageStep.gooTypeOf(urchinsStab().source()).map(ResourceKey::identifier));
    }

    /**
     * Expire's blow is a million of goo:expire, past any mob's health.
     */
    // decision expire-kills-the-struck-mob
    @Test
    void expiresBlowIsAMillionGooExpireDamage() {
        DamageStep blow = expiresBlow();
        assertEquals(Optional.of(GOO_EXPIRE), DamageStep.gooTypeOf(blow.source()).map(ResourceKey::identifier));
        assertEquals(EXPIRE_DAMAGE, blow.amount().evaluate(Variables.NONE), DELTA);
    }

    /**
     * The bypass tags name goo:expire, so the blow lands through a mob's armor and its Resistance.
     *
     * @param tag the vanilla damage type tag
     */
    // decision expire-kills-the-struck-mob
    @ParameterizedTest
    @ValueSource(strings = {"bypasses_armor", "bypasses_resistance"})
    void theBypassTagNamesGooExpire(String tag) {
        assertEquals(List.of(GOO_EXPIRE.toString()), damageTypeTag(tag));
    }

    /**
     * Bore's strike is the thrower's attack for 2, keeping the target's motion, leaving 10 ticks of immunity.
     */
    @Test
    void boresStrikeIsTwoAttackDamageKeepingMotionAndLeavingTenTicksOfImmunity() {
        DamageStep strike = boresStrike();
        assertEquals(DamageKind.ATTACK, strike.source());
        assertEquals(BORE_DAMAGE, strike.amount().evaluate(Variables.NONE), DELTA);
        assertEquals(false, strike.knockback());
        assertEquals(Optional.of((double) BORE_IMMUNE_TICKS),
                strike.invulnerableTicks().map(ticks -> ticks.evaluate(Variables.NONE)));
    }

    /**
     * Bore's hit leaves its target the 10 ticks of immunity it names.
     */
    @Test
    void boresHitLeavesItsTargetTenTicksOfImmunity() {
        LivingEntity cow = mock(LivingEntity.class);
        when(cow.level()).thenReturn(mock(ServerLevel.class));
        when(cow.damageSources()).thenReturn(mock(DamageSources.class));
        when(cow.getDeltaMovement()).thenReturn(Vec3.ZERO);
        TargetHost host = mock(TargetHost.class);
        when(host.target()).thenReturn(cow);

        boresStrike().tick(new StepContext(host, 0, 0));

        assertEquals(BORE_IMMUNE_TICKS, cow.invulnerableTime);
    }

    /**
     * A goo hit naming no immunity leaves its target none, so a melee hit right after it lands in full
     * (decision attack-key-touches-plus-punches).
     */
    @Test
    void aGooHitNamingNoImmunityLeavesItsTargetNone() {
        LivingEntity villager = struck(Vec3.ZERO);
        villager.invulnerableTime = IMMUNE_AFTER_A_MELEE_HIT;

        new DamageStep(Expr.literal(1), DamageKind.MAGIC).tick(new StepContext(hostOn(villager), 0, 0));

        assertEquals(0, villager.invulnerableTime);
    }

    /**
     * A goo hit clears its target's immunity before it hurts, so it lands through a melee hit just taken
     * (decision attack-key-touches-plus-punches).
     */
    @Test
    void aGooHitClearsItsTargetsImmunityBeforeItHurts() {
        LivingEntity villager = struck(Vec3.ZERO);
        villager.invulnerableTime = IMMUNE_AFTER_A_MELEE_HIT;
        int[] immuneAtTheHurt = {-1};
        when(villager.hurtServer(any(), any(), anyFloat())).thenAnswer(call -> {
            immuneAtTheHurt[0] = villager.invulnerableTime;
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
    private static DamageStep urchinsStab() {
        FieldEffectStep field = LingerStep.bodyOf(AbilityJson.decode("metal_spikes").behaviors()).orElseThrow().stream()
                .filter(FieldEffectStep.class::isInstance).map(FieldEffectStep.class::cast).findFirst().orElseThrow();
        return (DamageStep) field.strike().getFirst();
    }

    private static DamageStep expiresBlow() {
        TargetStep target = (TargetStep) AbilityJson.decode("yore_expire").behaviors().getFirst();
        return target.steps().stream().filter(DamageStep.class::isInstance).map(DamageStep.class::cast)
                .findFirst().orElseThrow();
    }

    private static DamageStep boresStrike() {
        BoreStep bore = (BoreStep) AbilityJson.decode("rock_bore").behaviors().getFirst();
        return (DamageStep) bore.strike().getFirst();
    }

    private static List<String> damageTypeTag(String tag) {
        String path = "/data/minecraft/tags/damage_type/" + tag + ".json";
        try (InputStream in = DamageStepTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path + " should be on the classpath");
            JsonElement values = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject().get("values");
            return StreamSupport.stream(values.getAsJsonArray().spliterator(), false)
                    .map(JsonElement::getAsString).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
