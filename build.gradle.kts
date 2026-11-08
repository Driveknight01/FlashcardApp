// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    // Google Services plugin for Firebase integration via Version Catalog
    alias(libs.plugins.google.services) apply false
}