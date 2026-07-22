# Add project-specific ProGuard rules here.
# Hilt, Room, and Compose ship their own consumer rules — keep this minimal.

# LockScreen reflectively clears biometric 1.1.0's stuck isPromptShowing flag
# (item 6, docs/build/v1.6.1.md) — keep the class + members it looks up by name.
-keep class androidx.biometric.BiometricViewModel { *; }

# Glance instantiates ActionCallback subclasses via reflection (Class.getDeclaredConstructor with no
# args, by class name from the tap Intent) — a call R8's static graph can't see. Glance's own consumer
# rule (`-keep public class * extends ActionCallback`, no braces) only protects the class from removal/
# renaming, not its constructor under R8 full mode, so the no-arg <init> got stripped: every widget
# button (eye, refresh) threw NoSuchMethodException and silently no-op'd on release builds only —
# the debug/emulator build has no shrinking, so this never showed up there (Alvin's phone, v1.6.8).
-keepclassmembers class * extends androidx.glance.appwidget.action.ActionCallback {
    <init>();
}
