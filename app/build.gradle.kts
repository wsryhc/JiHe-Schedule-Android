plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    // 🔥 KSP 插件 (用于 Room 数据库代码生成)
    id("com.google.devtools.ksp") version "2.0.21-1.0.27"
    id("kotlin-parcelize")
}

android {
    namespace = "com.example.jihe_schedule"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.jihe_schedule"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.0.1b"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 🔥🔥🔥 修改点 1：只打包 arm64-v8a 架构
        // 这会移除 x86 (电脑模拟器用) 和 armeabi-v7a (老旧手机) 的库文件
        // 打包体积通常能立减 50% 以上
        ndk {
            abiFilters.add("arm64-v8a")
            //以下编译为老架构平台使用
            //abiFilters.add("armeabi-v7a")
        }
    }

    buildTypes {
        release {
            // 🔥🔥🔥 修改点 2：开启代码混淆和资源压缩
            // 开启后，编译器会自动删除没用到的代码和资源图片
            isMinifyEnabled = true
            isShrinkResources = true

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
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.play.services.mlkit.text.recognition.chinese)

    // --- 测试依赖 ---
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Room数据库依赖
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version") // 支持 Kotlin 协程
    ksp("androidx.room:room-compiler:$room_version")        // 代码生成器
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    // Glance：用于编写桌面小组件 (类 Compose 语法)
    implementation("androidx.glance:glance-appwidget:1.1.0")
    implementation("androidx.glance:glance-material3:1.1.0")
    // WorkManager: 负责后台定时任务
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("androidx.compose.material:material-icons-extended:1.7.6")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
}