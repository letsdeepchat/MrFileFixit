package com.mrfilefixit.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Libraries that open network connections. Only `:core-ads` (and `:app`, which aggregates it) may depend on them.
 *
 * `com.google.android.datatransport` is deliberately NOT listed: ML Kit uses it for anonymous usage
 * metrics. It never carries file content. See docs/SECURITY.md, "ML Kit telemetry".
 */
private val NETWORK_GROUP_PREFIXES = listOf(
    "com.squareup.okhttp",
    "com.squareup.okhttp3",
    "com.squareup.retrofit2",
    "io.ktor",
    "com.android.volley",
    "org.chromium.net",
    "com.google.firebase",
    "com.google.android.gms:play-services-ads",
    "com.google.android.ump",
)

/**
 * Fails the build when a module that must stay offline resolves a networking library or
 * declares the INTERNET permission in its own manifest.
 */
abstract class VerifyNetworkIsolationTask : DefaultTask() {
    @get:Input
    abstract val resolvedModules: ListProperty<String>

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val manifest: RegularFileProperty

    @get:Input
    abstract val modulePath: Property<String>

    @TaskAction
    fun verify() {
        val offenders = resolvedModules.get().filter { coordinate ->
            NETWORK_GROUP_PREFIXES.any { coordinate.startsWith(it) }
        }
        val manifestFile = manifest.orNull?.asFile
        val declaresInternet = manifestFile?.takeIf { it.exists() }?.readText()
            ?.contains("android.permission.INTERNET") == true
        if (offenders.isNotEmpty() || declaresInternet) {
            throw GradleException(
                buildString {
                    appendLine("${modulePath.get()} must not use the network.")
                    if (offenders.isNotEmpty()) appendLine("Networking dependencies: ${offenders.joinToString()}")
                    if (declaresInternet) appendLine("Its AndroidManifest.xml declares android.permission.INTERNET.")
                    append("Only :core-ads may talk to the network.")
                },
            )
        }
    }
}

/** Modules allowed to use the network. `:app` only aggregates `:core-ads`. */
internal val NETWORK_ALLOWED_MODULES = setOf(":core-ads", ":app")

internal fun Project.configureNetworkIsolation(configurationName: String) {
    // Looked up lazily: Android variant configurations do not exist yet when convention plugins run.
    val configuration = provider { configurations.getByName(configurationName) }
    val task = tasks.register("verifyNetworkIsolation", VerifyNetworkIsolationTask::class.java) {
        group = "verification"
        description = "Checks that this module cannot reach the network."
        modulePath.set(project.path)
        resolvedModules.set(
            configuration.flatMap { config ->
                config.incoming.resolutionResult.rootComponent.map { root ->
                    root.dependencies.asSequence()
                        .flatMap { collectComponents(it) }
                        .mapNotNull { it as? ModuleComponentIdentifier }
                        .map { "${it.group}:${it.module}" }
                        .distinct()
                        .toList()
                }
            },
        )
        val manifestFile = file("src/main/AndroidManifest.xml")
        if (manifestFile.exists()) manifest.set(manifestFile)
    }
    tasks.named("check").configure { dependsOn(task) }
}

private fun collectComponents(
    dependency: org.gradle.api.artifacts.result.DependencyResult,
    seen: MutableSet<Any> = mutableSetOf(),
): Sequence<org.gradle.api.artifacts.component.ComponentIdentifier> = sequence {
    val resolved = dependency as? org.gradle.api.artifacts.result.ResolvedDependencyResult ?: return@sequence
    val component = resolved.selected
    if (!seen.add(component.id)) return@sequence
    yield(component.id)
    component.dependencies.forEach { yieldAll(collectComponents(it, seen)) }
}
