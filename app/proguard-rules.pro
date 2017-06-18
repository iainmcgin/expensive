-keep class android.arch.** { *; }
-keep class android.support.v4.widget.DrawerLayout { *; }
-keep class com.google.common.base.Preconditions { *; }
-keep class android.databinding.** { *; }

-dontwarn java.lang.**
-dontwarn javax.lang.**

## For Retrofit:

# Platform calls Class.forName on types which do not exist on Android to determine platform.
-dontnote retrofit2.Platform
# Platform used when running on Java 8 VMs. Will not be used at runtime.
-dontwarn retrofit2.Platform$Java8
# Retain generic type information for use by reflection by converters and adapters.
-keepattributes Signature
# Retain declared checked exceptions for use by a Proxy instance.
-keepattributes Exceptions

## For Okio:
-dontwarn okio.**

## For Guava:
-dontwarn javax.annotation.**
-dontwarn javax.inject.**
-dontwarn sun.misc.Unsafe