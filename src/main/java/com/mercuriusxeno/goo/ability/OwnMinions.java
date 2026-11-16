package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.ability.hex.Charmed;
import com.mercuriusxeno.goo.ability.zoo.Rallied;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.world.entity.Entity;
import java.util.Optional;
import java.util.UUID;

/**
 * Which mobs are a caster's own minions, the mobs it rallied or charmed,
 * which abilities spare them, and which mobs Charm and Rally refuse, read
 * alike by the server's throw and the client's aim.
 * mob-targeted-attacks-refuse-your-minions
 */
public final class OwnMinions {

    private OwnMinions() {
    }

    /**
     * Whether a mob is the caster's own minion: its rally or its charm names
     * the caster. A rally stands until its rallier strikes the mob or casts
     * Rally on it again, either of which takes the rally off it, so a rally
     * the mob holds is a standing one.
     * rally-is-a-mob-effect-on-one-target
     * rally-cast-on-your-rallied-mob-releases-it
     *
     * @param caster  the caster asked about
     * @param rallied the rally the mob holds, {@link Rallied#NONE} for none
     * @param charmed the charm the mob holds, {@link Charmed#NONE} for none
     * @return true for a mob the caster rallied or charmed
     */
    public static boolean ownMinionOf(UUID caster, Rallied rallied, Charmed charmed) {
        return rallied.caster().equals(caster) || charmed.charmer().equals(caster);
    }

    /**
     * Whether an entity is the caster's own minion, read off the mob's
     * synced rally and charm.
     *
     * @param caster the caster asked about
     * @param mob    the entity asked about
     * @return true for a mob the caster rallied or charmed
     */
    public static boolean ownMinionOf(Entity caster, Entity mob) {
        return ownMinionOf(caster.getUUID(), ralliedOf(mob), charmedOf(mob));
    }

    /**
     * Whether Charm refuses a mob for a caster: a mob holding any rally, the
     * caster's own included, or a charm another player cast. The caster's
     * own charmed mob and a mob holding neither effect take the charm.
     * rallied-mobs-are-immune-to-charm
     * other-players-rally-and-charm-refuse-your-minions
     *
     * @param caster  the caster of the Charm
     * @param rallied the rally the mob holds, {@link Rallied#NONE} for none
     * @param charmed the charm the mob holds, {@link Charmed#NONE} for none
     * @return true where the Charm leaves the mob untouched
     */
    public static boolean charmRefuses(UUID caster, Rallied rallied, Charmed charmed) {
        return holdsRally(rallied) || holdsCharm(charmed) && !charmed.charmer().equals(caster);
    }

    /**
     * Whether Charm refuses a mob for a caster, read off the mob's synced
     * rally and charm.
     *
     * @param caster the caster of the Charm
     * @param mob    the entity the Charm reaches
     * @return true where the Charm leaves the mob untouched
     */
    public static boolean charmRefuses(Entity caster, Entity mob) {
        return charmRefuses(caster.getUUID(), ralliedOf(mob), charmedOf(mob));
    }

    /**
     * Whether Rally refuses a mob for a caster: a mob holding any charm, the
     * caster's own included, or a rally another caster cast. The caster's
     * own rallied mob takes the cast, which releases it, and so does a mob
     * holding neither effect.
     * charmed-mobs-are-immune-to-rally
     * other-players-rally-and-charm-refuse-your-minions
     * rally-cast-on-your-rallied-mob-releases-it
     *
     * @param caster  the caster of the Rally
     * @param rallied the rally the mob holds, {@link Rallied#NONE} for none
     * @param charmed the charm the mob holds, {@link Charmed#NONE} for none
     * @return true where the Rally leaves the mob untouched
     */
    public static boolean rallyRefuses(UUID caster, Rallied rallied, Charmed charmed) {
        return holdsCharm(charmed) || holdsRally(rallied) && !rallied.caster().equals(caster);
    }

    /**
     * Whether Rally refuses a mob for a caster, read off the mob's synced
     * rally and charm.
     *
     * @param caster the caster of the Rally
     * @param mob    the entity the Rally reaches
     * @return true where the Rally leaves the mob untouched
     */
    public static boolean rallyRefuses(Entity caster, Entity mob) {
        return rallyRefuses(caster.getUUID(), ralliedOf(mob), charmedOf(mob));
    }

    private static boolean holdsRally(Rallied rallied) {
        return !Rallied.NONE.equals(rallied);
    }

    private static boolean holdsCharm(Charmed charmed) {
        return !Charmed.NONE.equals(charmed);
    }

    private static Rallied ralliedOf(Entity mob) {
        return mob.hasData(GooAttachments.RALLIED) ? mob.getData(GooAttachments.RALLIED) : Rallied.NONE;
    }

    private static Charmed charmedOf(Entity mob) {
        return mob.hasData(GooAttachments.CHARMED) ? mob.getData(GooAttachments.CHARMED) : Charmed.NONE;
    }

    /**
     * Whom a minion fights for: its rallier ahead of its charmer, since a
     * peaceful mob, the only kind a rally takes, strikes through the rally.
     * minion-hit-marks-the-struck-mob-as-its-players
     * charmed-mobs-take-the-rallied-kill-rule
     *
     * @param rallied the rally the mob holds, {@link Rallied#NONE} for none
     * @param charmed the charm the mob holds, {@link Charmed#NONE} for none
     * @return the rallier's or the charmer's uuid, empty for a mob holding neither
     */
    public static Optional<UUID> ownerOf(Rallied rallied, Charmed charmed) {
        if (!Rallied.NONE.caster().equals(rallied.caster())) {
            return Optional.of(rallied.caster());
        }
        return Charmed.NONE.charmer().equals(charmed.charmer()) ? Optional.empty() : Optional.of(charmed.charmer());
    }

    /**
     * Whom an entity fights for, read off its rally and its charm.
     *
     * @param mob the entity asked about
     * @return the rallier's or the charmer's uuid, empty for an entity holding neither
     */
    public static Optional<UUID> ownerOf(Entity mob) {
        return ownerOf(ralliedOf(mob), charmedOf(mob));
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
