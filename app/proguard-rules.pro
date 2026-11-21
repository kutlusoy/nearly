# Keep line numbers for readable crash traces, the R8 mapping is published with every release
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Only the lightweight API of Bouncy Castle is used (secp256k1, scrypt, ChaCha20-Poly1305), no provider lookup
-dontwarn org.bouncycastle.**
