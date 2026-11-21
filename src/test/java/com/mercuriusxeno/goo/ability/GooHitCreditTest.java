package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.ability.yarn.Downed;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

/**
 * A credited goo hit names its player as the cause and keeps the uncredited hit's type with no direct entity,
 * while goo's own listeners read it as the uncredited hit it stands in for: no attacker, and no attack for the
 * downed rule.
 */
class GooHitCreditTest {

    private Holder<DamageType> magic;
    private ServerPlayer player;
    private Mob cow;

    // Minecraft 26.3 seals Holder, so the hit's type is a real direct holder rather than a mock.
    @BeforeEach
    void standUpTheHit() {
        magic = Holder.direct(new DamageType("magic", 0.0F));
        player = mock(ServerPlayer.class);
        cow = mock(Mob.class);
    }

    @Test
    void aCreditedHitNamesThePlayerAsItsCauseKeepingItsType() {
        DamageSource credited = GooHitCredit.credit(new DamageSource(magic), cow, player);

        assertSame(player, credited.getEntity());
        assertNull(credited.getDirectEntity());
        assertEquals(magic, credited.typeHolder());
    }

    /** A hunk's strike keeps the hunk as its direct entity, so vanilla pushes the mob from the hunk as before. */
    @Test
    void aCreditedHitKeepsTheUncreditedHitsDirectEntityAndItsCauseForGoo() {
        Entity hunk = mock(Entity.class);
        DamageSource credited = GooHitCredit.credit(new DamageSource(magic, hunk), cow, player);

        assertSame(player, credited.getEntity());
        assertSame(hunk, credited.getDirectEntity());
        assertSame(hunk, GooHitCredit.causeOf(credited));
    }

    @Test
    void goosListenersReadACreditedHitAsNamingNoAttacker() {
        assertNull(GooHitCredit.causeOf(GooHitCredit.credit(new DamageSource(magic), cow, player)));
    }

    @Test
    void goosListenersReadAVanillaHitsCause() {
        assertSame(player, GooHitCredit.causeOf(new DamageSource(magic, player, player)));
    }

    /** decision no-attack-lands-on-a-downed-mob */
    @Test
    void theDownedRuleReadsACreditedHitAsNoAttack() {
        assertFalse(Downed.isAttack(GooHitCredit.credit(new DamageSource(magic), cow, player)));
    }
}
