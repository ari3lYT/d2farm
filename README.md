# D2Farm

D2Farm is a server-side Fabric farming extension built entirely from vanilla blocks and items. Players do not need a client mod or resource pack.

## Features

| Tool | Action |
| --- | --- |
| Sowing hoe | Tills and plants a 3×3 area using seeds from the off hand |
| Reaping hoe | Harvests a mature 3×3 crop area and replants from the real drops |
| Garden hoe | Harvests and replants one crop; prevents the holder from trampling their own farmland |
| Compost | Adds five boosted harvest charges to farmland |

Shift-use limits the area tools to one block. Wheat, carrots, potatoes, and beetroot are supported.

Prepared farmland receives a 50% natural growth chance bonus during rain when exposed to the sky. Vanilla light, hydration, and growth rules still apply; stages are not skipped, bone meal is unchanged, and ordinary farmland is unaffected.

## Design and safety

- Replanting consumes seeds from the actual harvest. It never creates free planting material.
- Durability is charged per affected block and an operation stops when the tool breaks.
- Existing enchantments, custom names, and damage survive recipe upgrades.
- Range, line of sight, spawn protection, and Fabric block-use/break callbacks are checked per block.
- Fertility lives in the world's `PersistentState`; no world scan or per-crop background task is used.
- Optional Ledger integration is discovered through reflection. If the installed Ledger API cannot record a change, D2Farm fails closed and blocks the extra farming action.

The mod currently operates only in the Overworld.

## Compatibility

- Minecraft `1.21.11`
- Fabric Loader `0.18.4+`
- Fabric API
- Java 21

## Build and test

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew test build
```

For the isolated client game test:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
LIBGL_ALWAYS_SOFTWARE=1 ALSOFT_DRIVERS=null \
xvfb-run -a -s '-screen 0 1280x720x24 -nolisten tcp' \
./gradlew runClientGameTest
```

The distributable JAR is written to `build/libs/`.

## Installation

1. Back up the world.
2. Install Fabric Loader and Fabric API on the server.
3. Put the D2Farm JAR in `mods/`.
4. Restart the server normally.

Removing the mod does not require block conversion: the world contains only vanilla blocks and items. Fertility metadata becomes unused.

## License

GPL-3.0-or-later. See [LICENSE](LICENSE).
