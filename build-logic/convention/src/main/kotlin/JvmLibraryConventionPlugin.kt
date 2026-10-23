import com.mrfilefixit.buildlogic.configureDetekt
import com.mrfilefixit.buildlogic.configureJUnit5
import com.mrfilefixit.buildlogic.configureKotlin
import com.mrfilefixit.buildlogic.configureNetworkIsolation
import com.mrfilefixit.buildlogic.libs
import com.mrfilefixit.buildlogic.pluginId
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure

/** Pure Kotlin module. Used by core-domain so the domain layer cannot depend on Android. */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("kotlin-jvm"))
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
        configureKotlin()
        configureJUnit5()
        configureDetekt()
        configureNetworkIsolation("runtimeClasspath")
    }
}
