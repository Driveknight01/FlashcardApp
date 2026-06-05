plugins {
    alias(libs.plugins.android.application)
    // Thêm plugin Google Services để kết nối Firebase (đã khai báo ở bước 1)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.example.flashcardapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.flashcardapp"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // Bật tính năng View Binding giúp code Java ánh xạ UI sạch sẽ, an toàn
    viewBinding {
        enable = true
    }
}

dependencies {
    // Các thư viện mặc định hệ thống của bạn
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)

    // Firebase BoM và các dịch vụ đi kèm (Auth, Firestore)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)

    // Google ML Kit để làm tính năng quét chữ OCR từ tài liệu
    implementation(libs.mlkit.text.recognition)

    // Thư viện Card Stack View hỗ trợ vuốt thẻ (Swipe) như Tinder cực mượt
    implementation(libs.card.stack.view)

    // Thư viện phục vụ Unit Test mặc định
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
    implementation("com.google.code.gson:gson:2.10.1")
}