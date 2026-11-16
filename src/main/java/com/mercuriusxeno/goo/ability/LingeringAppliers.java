package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The players who applied the lingering goo effects an afflicted entity holds, one per effect, so each later tick
 * of an effect hurts naming the player who applied it. The entity holds it as a data attachment that saves with
 * it. Each application writes its effect's applier: a player's application names that player, replacing any
 * earlier applier, and an application no player stands behind clears it. Immutable: each write answers a new
 * value for the holder to store.
 * lingering-goo-effect-credits-whoever-applied-it
 *
 * @param appliers the uuid of the player who applied each effect, by the effect's id; an effect no player
 *                 applied is absent
 */
public record LingeringAppliers(Map<Identifier, UUID> appliers) {

    private static final String FIELD_APPLIERS = "appliers";

    /** No applier for any effect, as an entity starts. */
    public static final LingeringAppliers NONE = new LingeringAppliers(Map.of());

    /** Codec the attachment saves with. */
    public static final MapCodec<LingeringAppliers> CODEC =
            Codec.unboundedMap(Identifier.CODEC, UUIDUtil.CODEC).fieldOf(FIELD_APPLIERS)
                    .xmap(LingeringAppliers::new, LingeringAppliers::appliers);

    /**
     * Copies the map so a caller's later writes cannot reach the record.
     *
     * @param appliers the uuid of the player who applied each effect, by the effect's id
     */
    public LingeringAppliers {
        appliers = Map.copyOf(appliers);
    }

    /**
     * Whether any effect names its applier, so the record is worth saving.
     *
     * @return true when an applier stands
     */
    public boolean stands() {
        return !appliers.isEmpty();
    }

    /**
     * Answers these appliers with one effect's applier written: named for a player, cleared for none.
     *
     * @param effect  the effect's id
     * @param applier the uuid of the player who applied it, or null where no player did
     * @return the appliers after the write
     */
    public LingeringAppliers appliedBy(Identifier effect, @Nullable UUID applier) {
        Map<Identifier, UUID> next = new HashMap<>(appliers);
        if (applier == null) {
            next.remove(effect);
        } else {
            next.put(effect, applier);
        }
        return new LingeringAppliers(next);
    }

    /**
     * Remembers on the afflicted entity who applied an effect to it, at the moment it is applied.
     * lingering-goo-effect-credits-whoever-applied-it
     *
     * @param afflicted the entity the effect is applied to
     * @param effect    the effect's id
     * @param applier   the entity behind the application, or null for none; only a player is remembered
     */
    public static void remember(LivingEntity afflicted, Identifier effect, @Nullable Entity applier) {
        UUID player = applier instanceof Player ? applier.getUUID() : null;
        LingeringAppliers standing = afflicted.hasData(GooAttachments.LINGERING_APPLIERS)
                ? afflicted.getData(GooAttachments.LINGERING_APPLIERS) : NONE;
        if (player != null || standing.appliers.containsKey(effect)) {
            afflicted.setData(GooAttachments.LINGERING_APPLIERS, standing.appliedBy(effect, player));
        }
    }

    /**
     * The source a tick of a lingering effect hurts with: the uncredited source naming the player who applied the
     * effect, through {@link GooHitCredit#credit}, or the uncredited source where that player is not in the
     * level or no player applied it.
     * lingering-goo-effect-credits-whoever-applied-it
     * goo-hit-keeps-its-kind-and-names-the-player-behind-it
     *
     * @param level      the level the afflicted entity stands in
     * @param uncredited the source the tick hurts with naming no one
     * @param afflicted  the entity the tick hurts
     * @param effect     the effect's id
     * @return the source to hurt with
     */
    public static DamageSource credit(ServerLevel level, DamageSource uncredited, LivingEntity afflicted,
                                      Identifier effect) {
        UUID applier = afflicted.hasData(GooAttachments.LINGERING_APPLIERS)
                ? afflicted.getData(GooAttachments.LINGERING_APPLIERS).appliers.get(effect) : null;
        Player player = applier == null ? null : level.getPlayerByUUID(applier);
        return GooHitCredit.credit(uncredited, afflicted, player);
    }
}
