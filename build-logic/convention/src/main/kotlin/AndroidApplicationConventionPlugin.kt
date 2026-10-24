import com.android.build.api.dsl.ApplicationExtension
import com.mrfilefixit.buildlogic.configureAndroidApplication
import com.mrfilefixit.buildlogic.configureDetekt
import com.mrfilefixit.buildlogic.configureJUnit5
import com.mrfilefixit.buildlogic.configureKotlin
import com.mrfilefixit.buildlogic.configureNetworkIsolation
import com.mrfilefixit.buildlogic.libs
import com.mrfilefixit.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Android application with Compose. Kotlin support is built into AGP 9, so no kotlin-android plugin. */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("android-application"))
        pluginManager.apply(libs.pluginId("kotlin-compose"))
        extensions.configure<ApplicationExtension> {
            configureAndroidApplication(rootDir.resolve("config/lint/lint.xml"))
            buildFeatures { compose = true }
        }
        configureKotlin()
        configureJUnit5()
        configureDetekt()
        // The whole app is offline, so the aggregate classpath is checked too, not only each library.
        configureNetworkIsolation("releaseRuntimeClasspath")
    }
}
