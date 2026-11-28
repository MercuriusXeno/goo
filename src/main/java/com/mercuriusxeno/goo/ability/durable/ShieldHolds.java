package com.mercuriusxeno.goo.ability.durable;

import com.mercuriusxeno.goo.ability.program.ShieldStep;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlocksAttacks;
import org.jspecify.annotations.Nullable;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who holds Durable's Shield up: each tick of the held channel raises it,
 * and it stands while the last raise is no older than a hold's grace, so a
 * release lowers it. A raised shield blocks through vanilla's own path as a
 * vanilla shield does, read through a vanilla shield stack, so what disables
 * a vanilla shield disables it too.
 * durable-goo-forms-a-shield-at-will-and-shield-bashes
 */
public final class ShieldHolds {

    /** The ticks a raise stands past the tick it was made, covering a tick between the hold's ticks. */
    static final long GRACE_TICKS = 2;
    private static final Map<UUID, Raise> RAISED = new ConcurrentHashMap<>();

    private ShieldHolds() {
    }

    /**
     * One tick's raise of the shield.
     *
     * @param tick   the game tick it was raised on
     * @param shield the step raising it
     */
    private record Raise(long tick, ShieldStep shield) {
    }

    /**
     * Stands the player's shield up for this tick.
     *
     * @param player the holder
     * @param shield the step raising it
     */
    public static void raise(Player player, ShieldStep shield) {
        RAISED.put(player.getUUID(), new Raise(player.level().getGameTime(), shield));
    }

    /**
     * Whether a raise still stands on a tick.
     *
     * @param raisedOn the tick of the last raise
     * @param now      the tick asked about
     * @return true while the raise is no older than the grace
     */
    static boolean stands(long raisedOn, long now) {
        return now >= raisedOn && now - raisedOn <= GRACE_TICKS;
    }

    /**
     * The shield an entity holds up, where its last raise still stands.
     *
     * @param entity the entity
     * @return the raising step, empty where no shield stands
     */
    public static Optional<ShieldStep> raisedBy(LivingEntity entity) {
        Raise raise = RAISED.get(entity.getUUID());
        return raise != null && stands(raise.tick(), entity.level().getGameTime())
                ? Optional.of(raise.shield()) : Optional.empty();
    }

    /**
     * The stack vanilla blocks with for a raised shield: a vanilla shield,
     * unless one of the entity's own blocking items is in use, or the shield
     * is disabled.
     *
     * @param entity the entity vanilla asks
     * @return the shield stack, or null where no goo shield blocks
     */
    public static @Nullable ItemStack blockingStack(LivingEntity entity) {
        if (raisedBy(entity).isEmpty() || blocksWithItsOwn(entity)) {
            return null;
        }
        ItemStack shield = new ItemStack(Items.SHIELD);
        if (entity instanceof Player player && player.getCooldowns().isOnCooldown(shield)) {
            return null;
        }
        return shield;
    }

    /**
     * Whether the entity holds up a blocking item of its own, which blocks in the goo shield's place.
     *
     * @param entity the entity
     * @return true while it uses an item that blocks attacks
     */
    public static boolean blocksWithItsOwn(LivingEntity entity) {
        BlocksAttacks own = entity.isUsingItem() ? entity.getUseItem().get(DataComponents.BLOCKS_ATTACKS) : null;
        return own != null;
    }
}
