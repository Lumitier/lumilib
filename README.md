# LumiLib

Shared foundation for Lumitier's client-side Minecraft mods, and a single hub
screen that collects them all in one place.

Fabric, Minecraft 1.21.11.

## What it is

**A hub.** Press **L** and every registered mod is listed in one window, with
its version and what it does. Click one and its settings open right there in
the hub: toggles, sliders, colour pickers, and a reset button on every row
that has a default. A mod can instead hand LumiLib its own screen, in which
case the hub routes to that.

**A library.** The pieces that were duplicated word for word across four mods
now live here once: clamping helpers, JSON config loading and saving, and the
building blocks for toggles, sliders and colour pickers — both for the hub's
own settings pages and for YACL screens.

**Not bundled.** LumiLib is installed once, on its own, not shipped inside
every mod via jar-in-jar — five copies of the same library in five jars helps
nobody. Each mod declares it as an *optional* dependency and keeps working
without it; it simply does not show up in the hub then.

**Later: server control.** An opt-in channel that lets a server ask these
mods to switch specific features off. Not in this version.

## Using it from your own mod

```java
LumiLibApi.register(new LumiMod(
    "shadowtweaks",
    Text.translatable("shadowtweaks.title"),
    "1.1.0",
    Text.translatable("shadowtweaks.summary"),
    Identifier.of("shadowtweaks", "icon.png"),   // may be null
    ShadowTweaksScreen::create));                // may be null
```

That is the whole integration. A mod that does not register simply does not
appear — the hub lists what registers, not everything installed.

The public API is in `com.lumitier.lumilib.api`. It follows one rule without
exception: **methods and record components are only ever added, never removed,
renamed or reordered.** A mod built against an older LumiLib has to keep
working when a newer one ships inside another mod, because the loader picks
the highest version for everyone.

## The account block

The top of the sidebar shows your skin head, your account name, and whether
you are signed in with a Microsoft account or running an offline session.

This is a display, not a verification. A client cannot check a session — it
only holds what the launcher handed it. The Microsoft indicator comes from
whether an Xbox user ID is present, which is a real difference, but it proves
nothing about anyone else.

Your session token is never displayed, logged, stored or sent anywhere, and
the whole block can be switched off for streaming.

## Requirements

- Minecraft **1.21.11**
- **Fabric Loader 0.20.1** or newer
- **Fabric API** — needed for the keybind
- [YetAnotherConfigLib](https://modrinth.com/mod/yacl)
- [Mod Menu](https://modrinth.com/mod/modmenu) *(optional)*

## License

Apache License 2.0.
