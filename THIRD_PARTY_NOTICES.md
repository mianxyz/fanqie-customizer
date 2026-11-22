# Third-party notices

The libs directory contains official Maven Central artifacts by libxposed:

- io.github.libxposed:api:102.0.0 (compile-only, excluded from APK DEX).
- io.github.libxposed:service:102.0.0 (included in APK).
- io.github.libxposed:interface:102.0.0 (included in APK).

All are distributed under Apache License 2.0 according to the published Maven POMs. Copies are unmodified apart from extraction of classes.jar from the AAR. Publication source: https://repo.maven.apache.org/maven2/io/github/libxposed/

LiquidGlassRenderer uses an independent native Android AGSL implementation informed by Kyant0/AndroidLiquidGlass (Kyant, Apache-2.0), kmp commit eb4a0ea2e17542ea28c2e71088f7dd9acf77aad5: effects/Lens.kt and internal/Shaders.kt (rounded-rectangle SDF, circle-map edge refraction and dispersion). No upstream Compose library is embedded into the host. Source: https://github.com/Kyant0/AndroidLiquidGlass

See LICENSE-APACHE-2.0.txt. Decompiled Fanqie and LSPosed framework sources are not redistributed.
