package com.mercuriusxeno.goo.ability.reserve;

/**
 * One held tick of Jelly Reserve: health and hunger leave their bars and come
 * back as reserve hearts and shanks behind them at a lossy ratio. The drain
 * stops at the floor, so it takes the player's life and food but cannot kill
 * or starve them, and stops at the cap, so draining, regenerating and draining
 * again cannot grow the reserve past it. Health drains silently by the point's
 * fraction; hunger is whole points, so the drain runs up what it owes the bar
 * and takes a point each time the debt covers one.
 * reserve-hearts-sit-behind-the-bar
 * reserve-channels-on-jelly
 *
 * @param drainHearts the hearts, and the shanks, one held tick drains
 * @param ratio       the reserve halves one drained point banks
 * @param capHearts   the most reserve hearts, and reserve shanks, that may stand
 * @param floorHearts the health and hunger the drain never takes the player under, in hearts and shanks
 */
public record ReserveDrain(float drainHearts, float ratio, float capHearts, float floorHearts) {

    /**
     * What a held tick leaves: the player's health and hunger and the reserve after it.
     *
     * @param health  the health to set, silently, with no damage event
     * @param food    the hunger to set
     * @param reserve the reserve after the tick
     */
    public record Drawn(float health, int food, Reserve reserve) {
    }

    /**
     * Runs one held tick against the player's bars and reserve. A bar the
     * tick can take nothing from, at the floor or at the cap, stays alone.
     *
     * @param standing the reserve standing
     * @param health   the player's real health
     * @param food     the player's hunger
     * @return the bars and reserve after the tick
     */
    public Drawn draw(Reserve standing, float health, int food) {
        float taken = Math.min(perTick(), health - floorPoints());
        boolean healthDrains = taken > 0f && standing.heartHalves() < capHalves();
        Reserve reserve = healthDrains ? standing.bankHealth(taken, ratio, capHalves()) : standing;
        return drawFood(reserve, healthDrains ? health - taken : health, food);
    }

    private Drawn drawFood(Reserve reserve, float health, int food) {
        boolean starved = reserve.shankHalves() >= capHalves() || food - 1 < floorPoints();
        float owed = starved ? 0f : reserve.foodOwed() + perTick();
        int takenFood = Math.max(0, Math.min((int) owed, (int) (food - floorPoints())));
        Reserve banked = takenFood > 0 ? reserve.bankFood(takenFood, ratio, capHalves()) : reserve;
        int foodAfter = food - takenFood;
        return new Drawn(health, foodAfter, banked.owing(owed - takenFood, foodAfter));
    }

    private int capHalves() {
        return Math.round(capHearts * Reserve.HALVES_PER_SLOT);
    }

    private float perTick() {
        return drainHearts * Reserve.POINTS_PER_SLOT;
    }

    private float floorPoints() {
        return floorHearts * Reserve.POINTS_PER_SLOT;
    }
}
