plugins {
    // Version-less: the Kotlin Gradle plugin is already on the classpath via the root project
    // (kotlin.android, apply false). Re-declaring a version here conflicts.
    id("org.jetbrains.kotlin.jvm")
    application
}

application {
    // ./gradlew :engine:run  -> prints invariants + a generated unique puzzle
    mainClass.set("com.spacedoku.app.engine.DemoKt")
}

dependencies {
    testImplementation(libs.junit)
}
