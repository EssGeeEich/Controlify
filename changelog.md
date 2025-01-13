# Controlify {version}

This version has the following targets:
{targets}

[![](https://short.isxander.dev/bisect-img)](https://short.isxander.dev/bisect)

## New features

- Add deadzone for analogue boat control
  - Makes holding the boat directly straight a lot easier.
- Added Legacy Console Edition Mode - makes the look input and vmouse feel and behave like Legacy Console Edition,
  for people who prefer it. ([by Permdog99](https://github.com/isXander/Controlify/pull/455))
- Add another server policy: `keyboardLikeMovement`, to make servers enforce this, for anti-cheats.
- A new, more advanced, scoped logging system is being added throughout the mod to make debugging easier.

## Changes

- A new algorithm is being used to compute the unique ID of each controller
  - This comes with the unfortunate side effect of causing all controller-specific configs to be reset, as they're now
    treated as new controllers when upgrading from beta 21 or below.
  - This algorithm should be more stable, correctly identifying the same physical controller through different
    setups, like bluetooth vs wired, or system configuration.
- Give exact battery percentage in low battery notification
  - This notification now only appears if the controller is unplugged.
- DualSense HD haptics functionality has been temporarily disabled
- Remove access wideners and access transformers - no longer needed
- Provide the framework for PojavLauncher support - PojavLauncher needs an update (that is in progress) to make
  use of Controlify.
- The amount of jar-in-jar dependencies has been reduced. Instead, Controlify shades and relocates them under its
  own package.
- Identify Xbox 360 controller via Wireless Adapter correctly as an Xbox controller (instead of generic) (fix [#403](https://github.com/isXander/Controlify/issue/403))

## Bug fixes

- Fix not being able to break blocks in surivival mode after an out-of-focus client grabs mouse (fix [#436](https://github.com/isXander/Controlify/issue/436))
  - This presents when you close any GUI whilst in-game
- Fix Steam Deck controls stopping after the game is suspended (sleeps, or out of focus for a while)
- Fix crash when opening recipe book screens in versions >=1.21.2
- Fix not being able to use the mouse to click on Settings and Use buttons on carousel entries
- Fix crash with Xander's Sodium Options
