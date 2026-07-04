# Add project-specific ProGuard rules here.
# Hilt, Room, and Compose ship their own consumer rules — keep this minimal.

# LockScreen reflectively clears biometric 1.1.0's stuck isPromptShowing flag
# (item 6, docs/build/v1.6.1.md) — keep the class + members it looks up by name.
-keep class androidx.biometric.BiometricViewModel { *; }
