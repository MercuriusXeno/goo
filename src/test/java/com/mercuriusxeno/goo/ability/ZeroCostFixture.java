package com.mercuriusxeno.goo.ability;

/**
 * A made-up ability costing nothing up front, naming its cost_mechanics key and its petal formula, for the load
 * rules on zero-cost abilities to run on without reading a shipped file.
 */
final class ZeroCostFixture {

    static final String NAME = "made_up_zero_cost";
    static final String COST_MECHANICS = "goo.ability.made_up.cost_mechanics";
    static final String JSON = """
            {
              "gooType": "rock",
              "displayName": "goo.ability.made_up",
              "cost_mechanics": "%s",
              "badge": "held",
              "cost": 0,
              "cost_formula": "%%s",
              "delivery": { "kind": "self", "ticks_per_charge": 20 },
              "behaviors": [],
              "tags": ["self"]
            }
            """.formatted(COST_MECHANICS);

    private ZeroCostFixture() {
    }
}
