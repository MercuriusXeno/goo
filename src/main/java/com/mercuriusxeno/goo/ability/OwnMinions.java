package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.ability.hex.Charmed;
import com.mercuriusxeno.goo.ability.zoo.Rallied;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.world.entity.Entity;
import java.util.UUID;

/**
 * Which mobs are a caster's own minions, the mobs it rallied or charmed, and
 * which abilities spare them, read alike by the server's throw and the
 * client's aim.
 * mob-targeted-attacks-refuse-your-minions
 */
public final class OwnMinions {

    private OwnMinions() {
    }

    /**
     * Whether a mob is the caster's own minion: its standing rally or its
     * charm names the caster.
     *
     * @param caster   the caster asked about
     * @param rallied  the rally the mob holds, {@link Rallied#NONE} for none
     * @param charmed  the charm the mob holds, {@link Charmed#NONE} for none
     * @param gameTime the game time, which a rally must stand at
     * @return true for a mob the caster rallied or charmed
     */
    public static boolean ownMinionOf(UUID caster, Rallied rallied, Charmed charmed, long gameTime) {
        return (rallied.caster().equals(caster) && rallied.standsAt(gameTime)) || charmed.charmer().equals(caster);
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
        Rallied rallied = mob.hasData(GooAttachments.RALLIED) ? mob.getData(GooAttachments.RALLIED) : Rallied.NONE;
        Charmed charmed = mob.hasData(GooAttachments.CHARMED) ? mob.getData(GooAttachments.CHARMED) : Charmed.NONE;
        return ownMinionOf(caster.getUUID(), rallied, charmed, mob.level().getGameTime());
    }

    /**
     * Whether an ability spares its caster's own minions: a harmful one
     * wearing the mob badge.
     *
     * @param badge   the ability's badge
     * @param harmful whether the ability carries the harmful tag
     * @return true when the ability refuses the caster's own minions
     */
    public static boolean sparesOwnMinions(AbilityBadge badge, boolean harmful) {
        return badge == AbilityBadge.MOB && harmful;
    }
}
