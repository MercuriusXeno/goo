package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.zoo.Rallied;
import com.mercuriusxeno.goo.ability.zoo.RallyArmor;
import com.mercuriusxeno.goo.ability.zoo.RallyEvents;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Rallies the host's target for the caster and finishes: until the rallier
 * strikes it or casts Rally on it again, the mob strikes the caster's enemies
 * for the named damage, its max health and speed lifted by the named shares,
 * and wearing the named share of the caster's armor and toughness over an
 * armor floor. A cast with no caster behind it rallies nothing. The rallier's
 * own second cast releases the mob unhurt; another caster's leaves the buff,
 * the health, the first rallier and the attacker it records. Zoo's Rally is
 * {@code rally damage=6 health=1.5 speed=0.5 armor_share=0.5 armor_floor=4},
 * thrown at one mob.
 * zoo-rally-arms-the-peaceful
 * zoo-rally-holds-for-the-brew
 * rally-is-a-mob-effect-on-one-target
 * rally-remembers-the-first-rallier
 * rally-minions-get-half-your-armor-and-a-floor
 * rally-cast-on-your-rallied-mob-releases-it
 *
 * @param damage    the damage one strike deals
 * @param health     the share max health grows by
 * @param speed      the share movement speed grows by
 * @param armorShare the share of the rallier's armor and toughness the mob wears
 * @param armorFloor the armor the mob wears however bare its rallier stands
 */
public record RallyStep(Expr damage, Expr health, Expr speed, Expr armorShare, Expr armorFloor)
        implements Step {

    private static final String NAME = "rally";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<RallyStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf("damage").forGetter(RallyStep::damage),
            Expr.CODEC.fieldOf("health").forGetter(RallyStep::health),
            Expr.CODEC.fieldOf("speed").forGetter(RallyStep::speed),
            Expr.CODEC.fieldOf("armor_share").forGetter(RallyStep::armorShare),
            Expr.CODEC.fieldOf("armor_floor").forGetter(RallyStep::armorFloor)
    ).apply(inst, RallyStep::new));

    /**
     * The registered type.
     */
    public static final StepType<RallyStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<RallyStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        TargetHost host = context.hostAs(TargetHost.class);
        Entity caster = host.thrower();
        if (caster != null && takesEntity(host.target())) {
            Mob mob = (Mob) host.target();
            Rallied standing = mob.hasData(GooAttachments.RALLIED) ? mob.getData(GooAttachments.RALLIED) : null;
            if (standing != null && standing.releasedBy(caster.getUUID())) {
                // rally-cast-on-your-rallied-mob-releases-it: the release deals no damage
                RallyEvents.endRally(mob);
            } else {
                rallyFor(context, mob, caster, standing);
            }
        }
        return true;
    }

    private void rallyFor(StepContext context, Mob mob, Entity caster, @Nullable Rallied standing) {
        // rally-remembers-the-first-rallier: a second caster's rally keeps the first rallier
        UUID rallier = Rallied.rallierAfter(standing, caster.getUUID());
        RallyArmor armor = new RallyArmor(armorShare.evaluateFloat(context), armorFloor.evaluateFloat(context));
        mob.setData(GooAttachments.RALLIED, new Rallied(rallier, damage.evaluateFloat(context),
                Rallied.attackerAfter(standing), armor));
        if (standing == null) {
            // a second rally leaves the buff it already holds, healing the mob no further
            buffFreshRally(context, mob, caster, armor);
        }
    }

    private void buffFreshRally(StepContext context, Mob mob, Entity caster, RallyArmor armor) {
        RallyEvents.buff(mob, health.evaluate(context), speed.evaluate(context));
        if (caster instanceof LivingEntity rallier) {
            RallyEvents.wearRalliersArmor(mob, rallier, armor);
        }
    }

    /**
     * Rally takes a mob.
     * decision steps-declare-the-entities-they-take
     *
     * @param entity the candidate
     * @return true where the step acts on it
     */
    @Override
    public boolean takesEntity(Entity entity) {
        return entity instanceof Mob;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(damage, health, speed, armorShare, armorFloor);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TARGET);
    }
}
