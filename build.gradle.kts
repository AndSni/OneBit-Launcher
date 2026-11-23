// R8 pinned newer than the one in AGP 8.5.2: that one gave a different classes.dex
// from one clean build to the next (Compose code, multithreaded optimisation),
// which breaks F-Droid's reproducible-build check. 8.9.35 gave the same dex in
// eight clean builds out of eight.
buildscript {
    repositories { google() }
    dependencies { classpath("com.android.tools:r8:8.9.35") }
}

plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
