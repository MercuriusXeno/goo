package com.mercuriusxeno.goo.ability.held;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * Kinetic's Remote on a player: while its held effect stands, the
 * player's block and entity interaction ranges are raised by the step's
 * reach, so breaking, placing, attacking and interacting enact at the
 * extended distance. It lays no overlay on the player, since Kinetic is
 * electric blue and white and no decision asked for one. The modifiers are
 * saved with the player and come off as the held effect ends.
 * telekinesis-enacts-at-extended-reach
 * kinetic-is-electric-blue-and-white
 */
public final class Remote {

    /** The entity interaction range Remote raises, with its modifier's id. */
    private static final Reach ENTITY_REACH = new Reach(Attributes.ENTITY_INTERACTION_RANGE,
            Identifier.fromNamespaceAndPath(Goo.MODID, "remote_entity_reach"));
    /** The ranges Remote raises, each with its modifier's id. */
    private static final List<Reach> REACHES = List.of(
            new Reach(Attributes.BLOCK_INTERACTION_RANGE,
                    Identifier.fromNamespaceAndPath(Goo.MODID, "remote_block_reach")),
            ENTITY_REACH);

    private Remote() {
    }

    private record Reach(Holder<Attribute> attribute, Identifier modifierId) {
    }

    /**
     * Raises a player's interaction ranges by a reach; laying it again
     * replaces the earlier reach.
     *
     * @param target the player
     * @param reach  the blocks added to each range
     */
    public static void lay(LivingEntity target, double reach) {
        for (Reach range : REACHES) {
            AttributeInstance instance = target.getAttribute(range.attribute());
            if (instance != null) {
                instance.addOrReplacePermanentModifier(new AttributeModifier(range.modifierId(), reach,
                        AttributeModifier.Operation.ADD_VALUE));
            }
        }
    }

    /**
     * The modifier Remote lays on a player's entity interaction range,
     * whose presence doubles Grab's own range (decision grab-reaches-telekinesis-range).
     *
     * @param target the player
     * @return the modifier, or null while Remote lays none
     */
    public static @Nullable AttributeModifier entityReachLaid(LivingEntity target) {
        AttributeInstance instance = target.getAttribute(ENTITY_REACH.attribute());
        return instance == null ? null : instance.getModifier(ENTITY_REACH.modifierId());
    }

    /**
     * Takes Remote off a player as its held effect ends: both ranges
     * back to their own.
     *
     * @param player the player
     */
    public static void clear(ServerPlayer player) {
        for (Reach range : REACHES) {
            AttributeInstance instance = player.getAttribute(range.attribute());
            if (instance != null) {
                instance.removeModifier(range.modifierId());
            }
        }
    }
}
