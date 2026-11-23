# Extra rules applied only to minified *debug* builds.
#
# The debug variant is minified for size (an unminified dex is ~75 MB). Keeping the app's own
# classes unobfuscated costs very little and buys readable stack traces — and it removes any
# reflection risk in Glance/WorkManager entry points that resolve our classes by name.
-keep class dev.easonhuang.garpulse.** { *; }

# Keep line numbers so a crash report from a phone still points at a real source line.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
