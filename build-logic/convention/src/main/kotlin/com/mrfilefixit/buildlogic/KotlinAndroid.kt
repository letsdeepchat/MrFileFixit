package com.mrfilefixit.buildlogic

import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

internal val JAVA_VERSION = JavaVersion.VERSION_17

/**
 * Kotlin compiler settings shared by Android and JVM modules.
 * Warnings are errors so CI stays at zero warnings.
 */
internal fun Project.configureKotlin() {
    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            allWarningsAsErrors.set(true)
            freeCompilerArgs.addAll(
                "-Xjsr305=strict",
                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            )
        }
    }
}

/** JUnit 5 (Jupiter) for every local unit test task, Android or JVM. */
internal fun Project.configureJUnit5() {
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        // Tests must never depend on the network either.
        systemProperty("java.net.useSystemProxies", "false")
    }
    dependencies {
        add("testImplementation", platform(libs.lib("junit5-bom")))
        add("testImplementation", libs.findBundle("unit-test").get())
        add("testRuntimeOnly", libs.lib("junit5-launcher"))
    }
}
