# Goo

A Minecraft mod for NeoForge 26.1. Melt items down into typed goo, store it, move it around, and throw it at things.

## What it does

Most items have a goo breakdown, derived from base values through their recipes, across 26 types: Astral, Blaze, Crystal, Deep, Ender, Frost, Glow, Hex, Infinity, Jelly, Kinetic, Leaf, Metal, Nether, Opulent, Pulse, Quantum, Rock, Shroom, Typhoon, Unstable, Void, Weird, Xeno, Yore, and Zoo.

Light a **Crucible** with flint and steel, then drop or toss items in to melt them into goo. Store goo in **Canisters**, up to nine to a block; in **Hubs**, which route goo into eight canisters; and in **Vats**, which grow with the Void goo they hold. Link **Choral Gaskets** in pairs with a **Choral Tuner** to pipe goo between machines at any distance.

Grow goo into **Chrysm** in a **Crystallizer**, mix goo in a **Reactor**, and reconstitute items in a **Plexer**: set the target item, fit canisters of the right goo to its top, and pulse it with redstone.

Each goo type has its own **abilities**, up to six per type. Hold a **Goo Glove**, press G to pick a type and ability from the radial, and right-click to throw, beam, stream or self-cast it. Abilities unlock as you learn certain items. Brew a type's Chrysm into an Awkward Potion at a vanilla brewing stand to get that type's goo potion.

## Building

Requires JDK 25.

```bash
./gradlew build
```

## Authors

MercuriusXeno and gabb.png.

## License

Goo is All Rights Reserved. Copyright (c) 2026 MercuriusXeno. See [LICENSE](LICENSE).

### Third-party

The shipped mod jar bundles none of the dependencies below: NeoForge is provided by the loader at runtime, and every other entry is used only to test or analyze the build.

| Dependency | Version | Used for | License |
| --- | --- | --- | --- |
| NeoForge | 26.1.0.1-beta | Mod loader, provided at runtime | LGPL-2.1 |
| JUnit Jupiter | 5.11.4 | Tests | EPL-2.0 |
| JUnit Platform Launcher | 1.13.4 | Tests | EPL-2.0 |
| Mockito (mockito-core, mockito-junit-jupiter) | 5.23.0 | Tests | MIT |
| Instancio (instancio-junit) | 5.4.0 | Tests | Apache-2.0 |
| ArchUnit (archunit-junit5) | 1.4.1 | Tests; bundles ASM under BSD-3-Clause | Apache-2.0 |
| sb-contrib | 7.6.8 | SpotBugs plugin, static analysis | LGPL-2.1 |
| Find Security Bugs (findsecbugs-plugin) | 1.13.0 | SpotBugs plugin, static analysis | LGPL |

