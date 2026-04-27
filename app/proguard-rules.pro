-keep class com.atomictrxn.conduit.data.api.models.** { *; }

# Tink (backing androidx.security:security-crypto) references errorprone annotations
# that are compile-only and not present at runtime.
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi
