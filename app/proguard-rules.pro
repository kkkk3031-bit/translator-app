# The app uses no reflection of its own; AndroidX/Material ship their own
# consumer rules (incl. keeping ViewModel constructors). Keep line numbers
# so release crash stack traces stay readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
