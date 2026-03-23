pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ParentalORMental"
include(":app")

// Auto-detect Android SDK if local.properties is missing or doesn't set sdk.dir
val localPropsFile = file("local.properties")
if (!localPropsFile.exists() || !localPropsFile.readText().contains("sdk.dir")) {
    val sdkDir = listOfNotNull(
        System.getenv("ANDROID_HOME"),
        System.getenv("ANDROID_SDK_ROOT"),
        "${System.getProperty("user.home")}/Android/Sdk",           // Linux default
        "${System.getProperty("user.home")}/Library/Android/sdk",   // macOS default
        "C:\\Users\\${System.getProperty("user.name")}\\AppData\\Local\\Android\\Sdk" // Windows default
    ).firstOrNull { file(it).isDirectory }

    if (sdkDir != null) {
        if (!localPropsFile.exists()) {
            localPropsFile.writeText("sdk.dir=$sdkDir\n")
        }
    } else {
        throw GradleException(
            """
            |Android SDK not found. Fix this by ONE of:
            |  1. Set the ANDROID_HOME environment variable to your SDK path
            |  2. Create a local.properties file with: sdk.dir=/path/to/your/Android/Sdk
            |  3. Install Android SDK via Android Studio (it auto-creates local.properties)
            |
            |Common SDK locations:
            |  Linux:   ~/Android/Sdk
            |  macOS:   ~/Library/Android/sdk
            |  Windows: %LOCALAPPDATA%\Android\Sdk
            """.trimMargin()
        )
    }
}
