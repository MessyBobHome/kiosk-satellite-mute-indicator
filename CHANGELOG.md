# Changelog

## 1.0.1

- Fixed: the preview could stay on screen indefinitely. Kiosk Satellite counts the settings page as open while it sits on the kiosk, even under the screensaver, or in any Remote Admin tab, and the editing preview lasted as long as the page did. It now shows for 15 seconds when the page opens and after each change, then hides on its own, and at once when the page closes.
- Fixed: **Preview the indicator for 10 seconds** now always ends after 10 seconds, including when run from the plugin's own page, and no longer depends on the device clock. Running it again restarts the 10 seconds.
- **Preview while this page is open** is now called **Preview while editing**. Your saved choice is kept.

## 1.0.0

First release.

- A mute symbol with its own position, size, nudge offsets, colour, opacity and optional circle or rounded-square background.
- A gradient glow from any edge, all edges or any corner, with its own size, colour and opacity.
- Show the icon, the gradient or both. Show them everywhere, only on the screensaver or only while it's off.
- Fades in on mute and out on unmute, with an adjustable duration and an optional pulse.
- Follows the kiosk's own Voice Satellite mute, or any Home Assistant entity with your choice of muted states.
- Previews while the settings page is open, and from a **Preview the indicator for 10 seconds** action.
- Every touch passes through, and back never dismisses it.
- Needs Kiosk Satellite 2026.10.40 or newer.
