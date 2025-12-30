# Required Dependencies

Download the following mod JARs and place them in this `libs/` folder for compilation.
These are required as compile-only dependencies for the SupplyLines mod.

## Required Mods (Minecraft 1.20.1)

### MineColonies Stack
| Mod | Version | Download |
|-----|---------|----------|
| MineColonies | 1.1.563-RELEASE | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/minecolonies/files/5276282) |
| Structurize | 1.0.742-RELEASE | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/structurize/files/5510044) |
| BlockUI | 1.0.156-RELEASE | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/blockui/files/5246501) |
| Domum Ornamentum | 1.0.186-RELEASE | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/domum-ornamentum/files/5510042) |
| Multi-Piston | 1.2.43-RELEASE | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/multi-piston/files/5099648) |

### Create Stack
| Mod | Version | Download |
|-----|---------|----------|
| Create | 0.5.1f | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/create/files/4835191) |
| Flywheel | 0.6.10-7 | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/flywheel/files/4833370) |
| Registrate | MC1.20-1.3.11 | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/registrate/files/4629084) |

## Instructions

1. Download each JAR file from the links above
2. Place all JAR files in this `libs/` directory
3. Run `./gradlew compileJava` to verify compilation

## Note

These dependencies are `compileOnly` - they're needed for compilation but not bundled in the output JAR.
Users must have these mods installed to use SupplyLines.
