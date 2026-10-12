package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityTags;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.program.DegradeStep;
import com.mercuriusxeno.goo.ability.program.ProgramLoadException;
import com.mercuriusxeno.goo.ability.program.RayStep;
import com.mercuriusxeno.goo.block.ability.PrismBlockEntity;
import com.mercuriusxeno.goo.registry.GooServerState;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;

/**
 * A channeled stream converting a bare prism: where the stream carries an
 * on_prism reaction and its look ends on a prism holding no combo, each held
 * tick charges the prism in place of running the stream's program, and held
 * for one charge's ticks unbroken the prism converts into that reaction as
 * its combo. A mob-first stream bites a mob in its cone before it charges any
 * prism. While it charges, the stream's motes fly to the prism, and a stream
 * that degrades blocks wears the prism with its degrade look by the share charged.
 * channeled-hosts-convert-prisms-over-the-hold
 */
final class StreamPrismCharge {

    private static final String LOG_COMBO_REFUSED = "Ability {} refused as a prism's combo: {}";

    private StreamPrismCharge() {
    }

    /**
     * Charges the bare prism the stream's look ends on, this tick.
     *
     * @param player  the streaming player
     * @param origin  the glove hand the client sent
     * @param gooType the stream's goo type
     * @param ability the stream ability
     * @return true when the tick charged a prism and the stream runs nothing else
     */
    static boolean charges(ServerPlayer player, Vec3 origin, ResourceKey<GooTypeDefinition> gooType,
                           AbilityDefinition ability) {
        if (ability.onPrism().isEmpty()) {
            return false;
        }
        Optional<PrismBlockEntity> aimed = barePrismInLook(player, ability.delivery().range());
        Vec3 apex = GooStreamHandler.coneApex(player, origin);
        if (aimed.isEmpty() || bitesAMobFirst(player, apex, ability)) {
            return false;
        }
        showCharging(player.level(), aimed.get(), apex, ability, ability.delivery().ticksPerCharge());
        convert(aimed.get(), player, gooType, ability);
        return true;
    }

    /**
     * Charges the bare prism a channel's ray strikes this tick, where the
     * channel carries an on_prism reaction; the ray itself lands as it does
     * on any block, refracting only at a prism already holding a combo.
     *
     * @param player   the channeling player
     * @param aimPoint the point the hold aims at
     * @param gooType  the channel's goo type
     * @param ability  the channel ability
     */
    static void chargeByRay(ServerPlayer player, Vec3 aimPoint, ResourceKey<GooTypeDefinition> gooType,
                            AbilityDefinition ability) {
        if (ability.onPrism().isEmpty()) {
            return;
        }
        ServerLevel level = player.level();
        ability.behaviors().stream().filter(RayStep.class::isInstance).map(RayStep.class::cast)
                .flatMap(ray -> ray.struckBlock(level, player, player.getEyePosition(), aimPoint).stream())
                .findFirst()
                .map(level::getBlockEntity).filter(PrismBlockEntity.class::isInstance).map(PrismBlockEntity.class::cast)
                .filter(prism -> !prism.hasCombo())
                .ifPresent(prism -> convert(prism, player, gooType, ability));
    }

    /**
     * Charges a bare prism one tick toward the ability's on_prism combo,
     * logging a combo the prism's host refuses.
     *
     * @param prism   the bare prism
     * @param player  the holding player
     * @param gooType the held ability's goo type
     * @param ability the held ability
     */
    private static void convert(PrismBlockEntity prism, ServerPlayer player, ResourceKey<GooTypeDefinition> gooType,
                                AbilityDefinition ability) {
        try {
            prism.chargeConversion(gooType, ability, ability.delivery().ticksPerCharge(), player.getUUID());
        } catch (ProgramLoadException e) {
            Goo.LOGGER.error(LOG_COMBO_REFUSED, ability.id(), e.getMessage());
        }
    }

    /**
     * The prism holding no combo the player's look ends on within the stream's reach.
     *
     * @param player the streaming player
     * @param range  the stream's reach in blocks
     * @return the prism, or empty where the look ends on anything else
     */
    private static Optional<PrismBlockEntity> barePrismInLook(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        BlockHitResult hit = player.level().clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(range)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.BLOCK
                && player.level().getBlockEntity(hit.getBlockPos()) instanceof PrismBlockEntity prism
                && !prism.hasCombo()) {
            return Optional.of(prism);
        }
        return Optional.empty();
    }

    /**
     * Whether a mob-first stream has a mob in its cone, which it bites before
     * charging any prism (decision decay-gnats-degrade-each-block-once).
     *
     * @param player  the streaming player
     * @param apex    the cone's apex
     * @param ability the stream ability
     * @return true where the stream strikes its cone this tick instead
     */
    private static boolean bitesAMobFirst(ServerPlayer player, Vec3 apex, AbilityDefinition ability) {
        if (!ability.hasTag(AbilityTags.MOB_FIRST)) {
            return false;
        }
        Delivery delivery = GooStreamHandler.chargedDelivery(ability.delivery(),
                GooStreamHandler.chargedFor(player, ability));
        return !GooStreamHandler.nearestMob(GooStreamHandler.livingInCone(player.level(), player, apex,
                player.getLookAngle(), delivery), apex).isEmpty();
    }

    /**
     * Shows the prism charging: the stream's motes fly from the glove onto
     * the prism's middle, and a stream that degrades blocks builds the prism's
     * degrade look one share of the hold.
     *
     * @param level   the server level
     * @param prism   the charging prism
     * @param apex    the cone's apex
     * @param ability the stream ability
     * @param hold    the ticks the conversion takes
     */
    private static void showCharging(ServerLevel level, PrismBlockEntity prism, Vec3 apex, AbilityDefinition ability,
                                     int hold) {
        BlockPos pos = prism.getBlockPos();
        Vec3 middle = Vec3.atCenterOf(pos);
        // the operator's ruling: the prism is the gnats' homing target until it converts fully
        GooStreamHandler.sprayParticles(level, apex, GooStreamHandler.aimFrom(apex, middle, middle.subtract(apex)),
                ability.delivery().withCone(ability.delivery().range(), 0), apex.distanceTo(middle));
        if (ability.behaviors().stream().anyMatch(DegradeStep.class::isInstance)) {
            // the operator's ruling: the decay animation plays on the prism while it charges
            GooServerState.of(level.getServer()).blockExposures().expose(level, pos, prism.getBlockState(),
                    1f / hold, DegradeStep.NETHER_MAROON);
        }
    }
}
