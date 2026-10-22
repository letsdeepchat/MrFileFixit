package com.mrfilefixit.buildlogic

import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

/**
 * Detekt (with the ktlint-based formatting rule set) on every module.
 * The shared config lives in config/detekt/detekt.yml and bans networking imports everywhere.
 */
internal fun Project.configureDetekt() {
    pluginManager.apply(libs.pluginId("detekt"))
    extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        allRules = false
        parallel = true
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        baseline = file("detekt-baseline.xml")
        source.setFrom("src/main/kotlin", "src/test/kotlin", "src/androidTest/kotlin")
    }
    tasks.withType<Detekt>().configureEach {
        jvmTarget = "17"
        reports {
            html.required.set(true)
            sarif.required.set(true)
            xml.required.set(false)
            txt.required.set(false)
        }
    }
    dependencies {
        add("detektPlugins", libs.lib("detekt-formatting"))
    }
    tasks.named("check").configure { dependsOn(tasks.withType<Detekt>()) }
}
