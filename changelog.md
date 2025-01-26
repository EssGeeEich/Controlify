# Controlify {version}

This version has the following targets:
{targets}

[![](https://short.isxander.dev/bisect-img)](https://short.isxander.dev/bisect)

**By donating on my [Ko-Fi](https://ko-fi.com/isxander), you will gain access to builds of Controlify for snapshot
builds of Minecraft.**

*This update includes localisation updates*

Some new documentation is being written for Controlify.
It includes information on the resource pack features.

[Check it out on moddedmc.wiki](https://moddedmc.wiki/project/controlify/docs)

## Bug fixes

- Fix identical model controllers being seen as the same controller which resulted in the second one to not be connected
- Fix toggle sprint and toggle sneak options being toggle if any connected controller config is toggle,
  regardless of whether it is active, as well as the vanilla toggle setting.
- Fix crash because YACL version constraint was not strict enough
- Fix LCE mode being framerate dependant
- Fix NeoForge versions crashing due to a mixin error
- Reorganise `vInvertLook` and `isLCE` options into the input component, instead of generic settings
