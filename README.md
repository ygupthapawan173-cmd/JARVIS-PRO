# JARVIS PRO starter

This is a clean-room Android starter project, not a copy of the original APK.
It currently contains:
- Kotlin Android app shell with a dark JARVIS-style landing screen
- Runtime microphone permission request
- Explicit shortcut to Android Accessibility settings
- Minimal accessibility-service declaration (observe-only)
- GitHub Actions workflow to build a debug APK

Not implemented yet: Gemini Live connection, Telugu speech input/output, wake-word listening,
3D HUD, voice command tools, app launching, volume/flashlight actions. Those need separate
implementation and device testing. Accessibility permission must be enabled manually by the user.
No API keys are included. Never commit API keys to GitHub.
