// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    // Kết nối với Firebase sử dụng cấu trúc alias từ Version Catalog
    alias(libs.plugins.google.services) apply false
}