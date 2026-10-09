package com.mercuriusxeno.goo.ability.frost;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.hearts.HeartKind;
import com.mercuriusxeno.goo.ability.hearts.HeartOverlay;
import com.mercuriusxeno.goo.ability.held.HeldEffectsEvents;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooMobEffects;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import java.util.Optional;

/**
 * Iceborn's heat leech: while a player wears Iceborn's frozen hearts, fire
 * near them goes out, fireballs near them fizzle, burning things stop
 * burning, and lava freezes to obsidian and water to ice, each block kept in
 * the level's record and sent back once they leave its range; their snowballs strike with ice, doubly against the fire immune;
 * and any fire that reaches them thaws every frozen heart at once and ends
 * the effect (decision iceborn-frozen-hearts-thaw-on-fire).
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class IcebornEvents {

    /** The Iceborn ability, ended when fire thaws its hearts. */
    public static final Identifier ICEBORN = Identifier.fromNamespaceAndPath(Goo.MODID, "frost_iceborn");
    /** Blocks around the player the leech reaches. */
    static final int LEECH_RADIUS = 3;
    /** Ticks between the passes that send frozen blocks back once their player has left. */
    static final int REVERT_EVERY_TICKS = 20;
    /** Ticks between the leech's passes over the blocks around the player. */
    static final int LEECH_EVERY_TICKS = 5;
    /** The ice damage a snowball deals, and its multiplier against a fire-immune mob. */
    static final float SNOWBALL_ICE_DAMAGE = 2f;
    static final float AGGRAVATED = 2f;

    private IcebornEvents() {
    }

    /**
     * Whether a player wears Iceborn's frozen hearts.
     *
     * @param player the player
     * @return true while the Iceborn overlay stands
     */
    public static boolean isIceborn(Player player) {
        HeartOverlay overlay = player.getData(GooAttachments.HEART_OVERLAY);
        return overlay.kind() == HeartKind.ICEBORN && overlay.stands();
    }

    /**
     * The ice Iceborn leaves on water.
     *
     * @return the Iceborn ice block
     */
    static Block icebornIce() {
        return GooBlocks.ICEBORN_ICE.get();
    }

    /**
     * Sends back, every second, each block Iceborn froze whose player has left its range.
     *
     * @param event the level tick event
     */
    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level && level.getGameTime() % REVERT_EVERY_TICKS == 0) {
            IcebornFrozenBlocks.of(level).revertUnheld(level);
        }
    }

    /**
     * Leeches heat around each Iceborn player every few ticks.
     *
     * @param event the player tick event
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && isIceborn(player)
                && player.level().getGameTime() % LEECH_EVERY_TICKS == 0) {
            leech(player.level(), player);
        }
    }

    private static void leech(ServerLevel level, ServerPlayer player) {
        BlockPos center = player.blockPosition();
        IcebornFrozenBlocks frozen = IcebornFrozenBlocks.of(level);
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-LEECH_RADIUS, -LEECH_RADIUS, -LEECH_RADIUS),
                center.offset(LEECH_RADIUS, LEECH_RADIUS, LEECH_RADIUS))) {
            BlockState warm = level.getBlockState(pos);
            leechedForm(warm).ifPresent(cold -> {
                level.setBlock(pos, cold, Block.UPDATE_ALL);
                if (warm.getFluidState().isSource()) {
                    frozen.record(pos, warm.is(Blocks.LAVA), player.getUUID());
                }
            });
        }
        AABB around = player.getBoundingBox().inflate(LEECH_RADIUS);
        for (Entity entity : level.getEntities(player, around)) {
            if (entity instanceof AbstractHurtingProjectile fireball) {
                fireball.discard();
            } else if (entity.isOnFire()) {
                entity.clearFire();
            }
        }
    }

    /**
     * What a block becomes once Iceborn has leeched its heat: fire goes out,
     * still lava becomes obsidian, still water becomes Iceborn ice, anything
     * else stays.
     *
     * @param state the block
     * @return its leeched form, or empty where it keeps its heat
     */
    static Optional<BlockState> leechedForm(BlockState state) {
        if (state.is(BlockTags.FIRE)) {
            return Optional.of(Blocks.AIR.defaultBlockState());
        }
        if (!state.getFluidState().isSource()) {
            return Optional.empty();
        }
        if (state.is(Blocks.LAVA)) {
            return Optional.of(Blocks.OBSIDIAN.defaultBlockState());
        }
        return state.is(Blocks.WATER) ? Optional.of(GooBlocks.ICEBORN_ICE.get().defaultBlockState())
                : Optional.empty();
    }

    /**
     * Thaws every frozen heart at once and ends Iceborn: the held effect
     * ends, which clears its hearts, and a drunk brew's effect goes with it.
     *
     * @param player the player fire reached
     */
    public static void thaw(ServerPlayer player) {
        player.setData(GooAttachments.HEART_OVERLAY, HeartOverlay.NONE);
        if (HeldEffectsEvents.holds(player, ICEBORN)) {
            HeldEffectsEvents.end(player, ICEBORN);
        }
        player.removeEffect(GooMobEffects.BREW_EFFECTS.get(GooTypes.FROST));
    }

    /**
     * Strikes with ice when an Iceborn player's snowball hits a living thing.
     *
     * @param event the projectile impact event
     */
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (event.getRayTraceResult() instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity struck) {
            icebornThrower(event.getProjectile()).ifPresent(thrower -> struck.hurtServer(thrower.level(),
                    thrower.damageSources().freeze(), snowballDamage(struck.fireImmune())));
        }
    }

    /**
     * The Iceborn player who threw a snowball.
     *
     * @param projectile the projectile that struck
     * @return the thrower, or empty for anything but an Iceborn player's snowball
     */
    private static Optional<ServerPlayer> icebornThrower(Projectile projectile) {
        return projectile instanceof Snowball && projectile.getOwner() instanceof ServerPlayer thrower
                && isIceborn(thrower) ? Optional.of(thrower) : Optional.empty();
    }

    /**
     * The ice damage an Iceborn snowball deals: aggravated, doubled, against
     * a fire-immune mob.
     *
     * @param fireImmune whether the struck mob is immune to fire
     * @return the damage
     */
    static float snowballDamage(boolean fireImmune) {
        return fireImmune ? SNOWBALL_ICE_DAMAGE * AGGRAVATED : SNOWBALL_ICE_DAMAGE;
    }
}
