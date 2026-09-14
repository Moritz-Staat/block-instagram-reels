# R8/ProGuard rules for the release build.
#
# Deliberately near-empty for now. The rules this project actually needs - keeping the
# AccessibilityService (referenced only from the manifest and from XML), kotlinx.serialization
# serializers, Hilt and Compose - arrive with the issues that introduce those dependencies.
# Adding speculative -keep rules now would hide real R8 problems later.

# Keep line numbers so a stack trace from a release build is readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
