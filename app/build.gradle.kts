plugins { id("com.android.application") }

android {
    namespace = "com.ruslan.fitnesscouch"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.ruslan.fitnesscouch"
        minSdk = 23
        targetSdk = 36
        versionCode = 201
        versionName = "2.0.1"
    }
    // Общий отладочный ключ: сборки с GitHub и из Android Studio подписаны одинаково
    // и ставятся друг поверх друга без удаления приложения. Для публикации в магазине нужен отдельный ключ.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
}
