# Radium Re-Reforged

A Forge port of Lithium, designed to improve the general performance of Minecraft without breaking things.

* [Issue tracker](https://github.com/bigenergy/radium-reforged-patched/issues)
* [CurseForge](https://www.curseforge.com/minecraft/mc-mods/radium-reforged)

## Configuration

Radium reads two files from the `config/` directory. Both are optional and are created on first launch, and
changes to either require a game restart.

### `lithium.properties`

Turns individual optimizations on or off. Every option is named after the mixin package it controls, so
`mixin.entity.collisions.movement=false` disables that one patch, and disabling a parent such as `mixin.entity`
disables everything under it.

The full list of options, their defaults and what each one does is in
[lithium-mixin-config.md](lithium-mixin-config.md). That file is generated from the source, so do not edit it by
hand.

### `radium.properties`

Settings that are not mixin rules.

| Option | Default | Description |
| --- | --- | --- |
| `logging.class_analysis_errors` | `true` | Warn when a modded class cannot be inspected, which usually means one of its methods references a `@OnlyIn(Dist.CLIENT)` type on a server. Each class is reported only once either way; set this to `false` to drop the warnings entirely. |

## Automatic compatibility overrides

Some optimizations conflict with specific mods, usually because Radium replaces a vanilla method whole and the
other mod injects into it. Radium turns those options off by itself when it sees the mod in question, and says so
in the log:

```
Force-disabling mixin '...' as option '...' (added by <modid>) disables it and children
```

This overrides the value in `lithium.properties`, so an option may stay off even after you enable it. Mods can
also request overrides themselves by declaring a `lithium:options` table in their `mods.toml`.

If every option is force-disabled at once, another mod failed to load and Radium stepped aside so the real error
stays readable.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).
