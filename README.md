# Flux Overdrive

### Homepage [CurseForge](https://www.curseforge.com/minecraft/mc-mods/flux-overdrive)
For any suggestions or bugs, open a new issue

### Requirements

- Minecraft 1.21.1
- NeoForge 21.1.219 or newer for Minecraft 1.21.1
- Java 21
- Flux Networks 8.x
- Jade 15.x or newer is optional

### Introduction
![](https://media.forgecdn.net/attachments/description/null/description_9a130844-fd20-4739-a127-7781840e7109.png)
![](https://media.forgecdn.net/attachments/description/null/description_d38cfabd-00b4-4f47-9af9-24719ef7f8e7.png)
![](https://media.forgecdn.net/attachments/description/null/description_f6a20a84-187f-4546-b805-832873412b32.png)

Two new tiers are provided, along with its materials and Flux Storage.

![](https://media.forgecdn.net/attachments/description/null/description_98ccc964-1954-449f-8d3b-58e6add2c643.png)

The new storage blocks support dynamic capacity and can hold up to approximately 9.22E18 FE, subject to the signed 64-bit capacity limit.

![](https://media.forgecdn.net/attachments/description/null/description_d23c91ba-367c-464c-a1f3-636d84b3f14a.png)

It also provides a new block that allows you to more conveniently use mods like SFM to utilize the energy in Flux Networks.

![](https://media.forgecdn.net/attachments/description/null/description_ad9433b1-32f9-489d-959f-06b1057acc47.png)
![](https://media.forgecdn.net/attachments/description/null/description_0f294d9d-a435-44cf-b2a4-09912e923fdb.png)

Added a wireless device, similar in use to the wireless crystal from Draconic Evolution, which supports interacting with different faces of different blocks.

The Flux Link Crystal sends network energy to manually linked machines, while the Flux Extract Crystal performs the reverse operation for manually linked energy sources. The Flux Auto Link Crystal and Flux Auto Extract Crystal provide automatic discovery variants. Extract crystals take energy from their selected faces and deposit it into the selected Flux Network. All crystals support manual links with the Flux Link Tool; automatic crystals also support redstone-triggered rescans.

![](https://media.forgecdn.net/attachments/description/null/description_e141e65b-6dc4-43e9-8aaf-aea22331b368.png)

This mod is also compatible with GTCeu Modern's EU system.

### Miniature Universe

Miniature Universe is a Tier 3 Quantum Battery plugin with an editable factory grid, paid upgrades from 3×3 to 11×11, server-validated recipes, and shared local or entanglement access. It supports items, component-preserving fluids and optional Mekanism chemicals. Saves from the implementation preceding the rebuild are unsupported.

Input markers define batch quantities; installed machines limit throughput. The `=` button balances quantities against native recipes and split weights, then compiles the layout. Products can have separate output directions. Production runs in 20-tick rounds and charges only working machines. Right-click an ingredient in the input inventory to set its own cache limit for that factory, within the server's `miniatureInputCapacity` limit. Configured ingredients remain listed at zero stock; resetting one leaves the others unchanged. Left-click transfers input items (Shift + left-click transfers one); empty-cursor left-click selects a machine and Delete takes it out.

Supported adapters cover vanilla machines, Mekanism machines and factory tiers, and GTCEu 8 single-block machines, including reusable catalysts. JEI and native EMI support resource ghost markers; JEI also supports recipe selection. GUI inventory transfers handle items, while real fluids and chemicals use capabilities. Chance outputs, feedback loops, arbitrary unadapted recipes and EMI-native recipe transfer are outside the supported scope.

### Development

Use Java 21 and the included Gradle wrapper. Run `./gradlew build` to compile the mod and run the unit tests, and `./gradlew compileClientTestJava` to compile the client verification harness. On Windows, use `gradlew.bat`.

Texture generation utilities are kept in `tools/`. Build outputs, development runs, local logs and IDE state are excluded from version control.

### License

Flux Overdrive is available under the [MIT License](LICENSE).
