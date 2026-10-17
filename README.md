# Leafia's Cursed Addon for HBM's Nuclear Tech Re-Atomized

An optional second jar for **HBM's Nuclear Tech Re-Atomized**, the Minecraft **1.21.1 / NeoForge**
port of Hbm's Nuclear Tech Mod. It ports [NTM Cursed (Addon Edition)](https://github.com/ntmleafia/NTM-Cursed-Addon)
from Minecraft 1.12.2.

* Mod id: `leafia`
* Minecraft 1.21.1, NeoForge 21.1.244 or later
* Licence: LGPL-3.0 (see [`LICENSE.LESSER`](LICENSE.LESSER), [`LICENSE`](LICENSE) and [`NOTICE`](NOTICE))

It adds the addon's elevators, modular turbines, the reworked DFC, the PWR, LFTR and other
reactors, the Custom Control Panel, new fluids, building blocks and more. Every feature has a
switch in `config/leafia-features.toml`, and a feature that is switched off registers nothing at
all, so turning one off cannot leave unknown blocks in a world.

## Installing

1. Install NeoForge for Minecraft 1.21.1.
2. Put the base mod, **HBM's Nuclear Tech Re-Atomized** (`hbm`, 0.3.0 or later), in your `mods` folder.
   The addon does nothing without it and will refuse to load.
3. Put this jar (`leafia-<version>.jar`) beside it.

JEI is supported but optional.

## Building

You need a Java 21 JDK.

```
./gradlew build                # the jar lands in build/libs/
./gradlew runGameTestServer    # runs the addon's GameTests headless
./gradlew runClient            # a development client with both mods loaded
```

The base mod is compiled against, and loaded in the development runs, from
`libs/hbm-<version>.jar`. To build against a newer base mod, replace that jar and set
`hbm_version` in `gradle.properties` to match. Pass `-Paddon_no_jei` to run without JEI.

## Credits

* **Leafia's Cursed Addon** (NTM Cursed, Addon Edition) by **ntmleafia**, with **movblock**, who
  helped kickstart it. This port is not affiliated with or endorsed by its authors.
* **NTM Community Edition**, by HbmMods and the NTM Community Edition contributors, from which
  the Custom Control Panel is adapted.
* **HBM's Nuclear Tech Mod** by **HbmMods (The Bobcat)** and its contributors.
* 1.21.1 NeoForge port by TitanmasterRy.

The original addon is dual-licensed GPL-3.0 and LGPL-3.0; this port is taken under the LGPL-3.0,
matching the mod it extends. See [`NOTICE`](NOTICE) for the full attribution.
