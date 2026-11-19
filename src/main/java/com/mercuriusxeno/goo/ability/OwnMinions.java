package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.ability.hex.Charmed;
import com.mercuriusxeno.goo.ability.yarn.Led;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.world.entity.Entity;
import java.util.Optional;
import java.util.UUID;

/**
 * Which mobs are a caster's own minions, the mobs it led or charmed,
 * which abilities spare them, and which mobs Charm and Lead refuse, read
 * alike by the server's throw and the client's aim.
 * mob-targeted-attacks-refuse-your-minions
 */
public final class OwnMinions {

    private OwnMinions() {
    }

    /**
     * Whether a mob is the caster's own minion: its lead or its charm names
     * the caster. A lead stands until its leader strikes the mob or casts
     * Lead on it again, either of which takes the lead off it, so a lead
     * the mob holds is a standing one.
     * rally-is-a-mob-effect-on-one-target
     * rally-cast-on-your-rallied-mob-releases-it
     *
     * @param caster  the caster asked about
     * @param led the lead the mob holds, {@link Led#NONE} for none
     * @param charmed the charm the mob holds, {@link Charmed#NONE} for none
     * @return true for a mob the caster led or charmed
     */
    public static boolean ownMinionOf(UUID caster, Led led, Charmed charmed) {
        return led.caster().equals(caster) || charmed.charmer().equals(caster);
    }

    /**
     * Whether an entity is the caster's own minion, read off the mob's
     * synced lead and charm.
     *
     * @param caster the caster asked about
     * @param mob    the entity asked about
     * @return true for a mob the caster led or charmed
     */
    public static boolean ownMinionOf(Entity caster, Entity mob) {
        return ownMinionOf(caster.getUUID(), ledOf(mob), charmedOf(mob));
    }

    /**
     * Whether Charm refuses a mob for a caster: a mob holding any lead, the
     * caster's own included, or a charm another player cast. The caster's
     * own charmed mob and a mob holding neither effect take the charm.
     * rallied-mobs-are-immune-to-charm
     * other-players-rally-and-charm-refuse-your-minions
     *
     * @param caster  the caster of the Charm
     * @param led the lead the mob holds, {@link Led#NONE} for none
     * @param charmed the charm the mob holds, {@link Charmed#NONE} for none
     * @return true where the Charm leaves the mob untouched
     */
    public static boolean charmRefuses(UUID caster, Led led, Charmed charmed) {
        return holdsLead(led) || holdsCharm(charmed) && !charmed.charmer().equals(caster);
    }

    /**
     * Whether Charm refuses a mob for a caster, read off the mob's synced
     * lead and charm.
     *
     * @param caster the caster of the Charm
     * @param mob    the entity the Charm reaches
     * @return true where the Charm leaves the mob untouched
     */
    public static boolean charmRefuses(Entity caster, Entity mob) {
        return charmRefuses(caster.getUUID(), ledOf(mob), charmedOf(mob));
    }

    /**
     * Whether Lead refuses a mob for a caster: a mob holding any charm, the
     * caster's own included, or a lead another caster cast. The caster's
     * own led mob takes the cast, which releases it, and so does a mob
     * holding neither effect.
     * charmed-mobs-are-immune-to-rally
     * other-players-rally-and-charm-refuse-your-minions
     * rally-cast-on-your-rallied-mob-releases-it
     *
     * @param caster  the caster of the Lead
     * @param led the lead the mob holds, {@link Led#NONE} for none
     * @param charmed the charm the mob holds, {@link Charmed#NONE} for none
     * @return true where the Lead leaves the mob untouched
     */
    public static boolean leadRefuses(UUID caster, Led led, Charmed charmed) {
        return holdsCharm(charmed) || holdsLead(led) && !led.caster().equals(caster);
    }

    /**
     * Whether Lead refuses a mob for a caster, read off the mob's synced
     * lead and charm.
     *
     * @param caster the caster of the Lead
     * @param mob    the entity the Lead reaches
     * @return true where the Lead leaves the mob untouched
     */
    public static boolean leadRefuses(Entity caster, Entity mob) {
        return leadRefuses(caster.getUUID(), ledOf(mob), charmedOf(mob));
    }

    private static boolean holdsLead(Led led) {
        return !Led.NONE.equals(led);
    }

    private static boolean holdsCharm(Charmed charmed) {
        return !Charmed.NONE.equals(charmed);
    }

    private static Led ledOf(Entity mob) {
        return mob.hasData(GooAttachments.LED) ? mob.getData(GooAttachments.LED) : Led.NONE;
    }

    private static Charmed charmedOf(Entity mob) {
        return mob.hasData(GooAttachments.CHARMED) ? mob.getData(GooAttachments.CHARMED) : Charmed.NONE;
    }

    /**
     * Whom a minion fights for: its leader ahead of its charmer, since a
     * peaceful mob, the only kind a lead takes, strikes through the lead.
     * minion-hit-marks-the-struck-mob-as-its-players
     * charmed-mobs-take-the-rallied-kill-rule
     *
     * @param led the lead the mob holds, {@link Led#NONE} for none
     * @param charmed the charm the mob holds, {@link Charmed#NONE} for none
     * @return the leader's or the charmer's uuid, empty for a mob holding neither
     */
    public static Optional<UUID> ownerOf(Led led, Charmed charmed) {
        if (!Led.NONE.caster().equals(led.caster())) {
            return Optional.of(led.caster());
        }
        return Charmed.NONE.charmer().equals(charmed.charmer()) ? Optional.empty() : Optional.of(charmed.charmer());
    }

    /**
     * Whom an entity fights for, read off its lead and its charm.
     *
     * @param mob the entity asked about
     * @return the leader's or the charmer's uuid, empty for an entity holding neither
     */
    public static Optional<UUID> ownerOf(Entity mob) {
        return ownerOf(ledOf(mob), charmedOf(mob));
    }

    /**
     * Whether an ability spares its caster's own minions: a harmful one
     * aiming as a mob ability, Dart's held javelin among them.
     * decision dart-forms-on-right-click-and-throws-on-left-click
     *
     * @param badge   the ability's badge
     * @param kind    the ability's delivery kind
     * @param harmful whether the ability carries the harmful tag
     * @return true when the ability refuses the caster's own minions
     */
    public static boolean sparesOwnMinions(AbilityBadge badge, DeliveryKind kind, boolean harmful) {
        return badge.aimsAs(kind) == AbilityBadge.MOB && harmful;
    }
}
