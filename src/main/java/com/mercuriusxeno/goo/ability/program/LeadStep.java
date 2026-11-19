package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.OwnMinions;
import com.mercuriusxeno.goo.ability.yarn.LeadArmor;
import com.mercuriusxeno.goo.ability.yarn.LeadEvents;
import com.mercuriusxeno.goo.ability.yarn.Led;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Leads the host's target for the caster and finishes: until the leader
 * strikes it or casts Lead on it again, the mob strikes the caster's enemies
 * for the named damage, its max health and speed lifted by the named shares,
 * and wearing the named share of the caster's armor and toughness over an
 * armor floor. A cast with no caster behind it leads nothing. The leader's
 * own second cast releases the mob unhurt; a mob {@link OwnMinions#leadRefuses}
 * refuses, charmed or another caster's, keeps what it holds. Yarn's Lead is
 * {@code lead damage=6 health=1.5 speed=0.5 armor_share=0.5 armor_floor=4},
 * thrown at one mob.
 * zoo-rally-arms-the-peaceful
 * zoo-rally-holds-for-the-brew
 * rally-is-a-mob-effect-on-one-target
 * rally-remembers-the-first-rallier
 * rally-minions-get-half-your-armor-and-a-floor
 * rally-cast-on-your-rallied-mob-releases-it
 * charmed-mobs-are-immune-to-rally
 * other-players-rally-and-charm-refuse-your-minions
 *
 * @param damage    the damage one strike deals
 * @param health     the share max health grows by
 * @param speed      the share movement speed grows by
 * @param armorShare the share of the leader's armor and toughness the mob wears
 * @param armorFloor the armor the mob wears however bare its leader stands
 */
public record LeadStep(Expr damage, Expr health, Expr speed, Expr armorShare, Expr armorFloor)
        implements Step {

    private static final String NAME = "lead";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<LeadStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf("damage").forGetter(LeadStep::damage),
            Expr.CODEC.fieldOf("health").forGetter(LeadStep::health),
            Expr.CODEC.fieldOf("speed").forGetter(LeadStep::speed),
            Expr.CODEC.fieldOf("armor_share").forGetter(LeadStep::armorShare),
            Expr.CODEC.fieldOf("armor_floor").forGetter(LeadStep::armorFloor)
    ).apply(inst, LeadStep::new));

    /**
     * The registered type.
     */
    public static final StepType<LeadStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<LeadStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        TargetHost host = context.hostAs(TargetHost.class);
        Entity caster = host.thrower();
        if (caster != null && takesEntity(host.target()) && !refusesFor(caster, host.target())) {
            Mob mob = (Mob) host.target();
            if (mob.hasData(GooAttachments.LED)) {
                // rally-cast-on-your-rallied-mob-releases-it: the release deals no damage
                LeadEvents.endLead(mob);
            } else {
                leadFor(context, mob, caster);
            }
        }
        return true;
    }

    private void leadFor(StepContext context, Mob mob, Entity caster) {
        LeadArmor armor = new LeadArmor(armorShare.evaluateFloat(context), armorFloor.evaluateFloat(context));
        // slots-follow-rally-order-size-and-count: the lead's game time sets the mob's slot
        mob.setData(GooAttachments.LED, new Led(caster.getUUID(), damage.evaluateFloat(context),
                Optional.empty(), armor, mob.level().getGameTime()));
        LeadEvents.buff(mob, health.evaluate(context), speed.evaluate(context));
        if (caster instanceof LivingEntity leader) {
            LeadEvents.wearLeadersArmor(mob, leader, armor);
        }
    }

    /**
     * Lead takes a mob.
     * decision steps-declare-the-entities-they-take
     *
     * @param entity the candidate
     * @return true where the step acts on it
     */
    @Override
    public boolean takesEntity(Entity entity) {
        return entity instanceof Mob;
    }

    /**
     * Lead refuses a charmed mob and a mob another caster led; its
     * leader's own led mob takes the cast, which releases it.
     * refused-rally-or-charm-spends-no-goo
     *
     * @param caster the caster the Lead would act for
     * @param entity the candidate
     * @return true where the Lead leaves the entity untouched
     */
    @Override
    public boolean refusesFor(Entity caster, Entity entity) {
        return OwnMinions.leadRefuses(caster, entity);
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
