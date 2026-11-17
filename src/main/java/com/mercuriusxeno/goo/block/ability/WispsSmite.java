package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.GooHitCredit;
import com.mercuriusxeno.goo.ability.program.UndeadInversion;
import com.mercuriusxeno.goo.ability.program.WispsStep;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * What a Wisps cell does to an undead touching it: sets it on fire and, once
 * each cadence the laying step names, deals it holy damage of the step's per
 * layer amount for each layer the cell holds. Undead is goo's one reading of
 * it, a heal harming the entity ({@link UndeadInversion#inverts}), so an
 * undead player under nether's Undead burns as a zombie does. Holy damage is
 * goo:holy, a type of its own rather than fire, so a fire-immune undead such
 * as a wither skeleton still takes it.
 * undead-touching-wisps-goo-erupt-in-flames-and-take-holy-damage-by-fill
 */
public final class WispsSmite {

    /**
     * Wisps' holy damage type.
     * undead-touching-wisps-goo-erupt-in-flames-and-take-holy-damage-by-fill
     */
    public static final ResourceKey<DamageType> HOLY =
            ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(Goo.MODID, "holy"));

    /** The seconds an undead burns after its last touch of the goo, as long as a zombie burns after the sun sets. */
    private static final int BURN_SECONDS = 8;

    private WispsSmite() {
    }

    /**
     * Sets an undead touching a Wisps cell on fire and deals it the cell's
     * holy damage when the cadence comes round, crediting the player who
     * placed the cell; anything else is spared.
     * standing-goo-thing-credits-whoever-placed-it
     *
     * @param level  the level the cell stands in
     * @param entity the entity touching the cell
     * @param layers the layers the cell holds
     * @param wisps  the laying step, naming the damage per layer and the cadence
     * @param placer the player who placed the cell, or null for none
     */
    public static void touch(ServerLevel level, Entity entity, int layers, WispsStep wisps, @Nullable Entity placer) {
        if (!(entity instanceof LivingEntity living) || !smites(living)) {
            return;
        }
        living.igniteForSeconds(BURN_SECONDS);
        if (level.getGameTime() % wisps.holyEvery() == 0) {
            DamageSource holy = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)
                    .getOrThrow(HOLY));
            living.hurtServer(level, GooHitCredit.credit(holy, living, placer),
                    (float) holyDamage(layers, wisps.holyPerLayer()));
        }
    }

    /**
     * Whether a Wisps cell smites an entity touching it.
     *
     * @param entity the entity touching the cell
     * @return true for an undead, which burns; false for the living, spared
     */
    static boolean smites(LivingEntity entity) {
        return UndeadInversion.inverts(entity);
    }

    /**
     * The holy damage a Wisps cell deals an undead touching it, rising with its fill.
     *
     * @param layers   the layers the cell holds
     * @param perLayer the holy damage each layer deals
     * @return the damage of one holy hit
     */
    static double holyDamage(int layers, double perLayer) {
        return layers * perLayer;
    }
}
