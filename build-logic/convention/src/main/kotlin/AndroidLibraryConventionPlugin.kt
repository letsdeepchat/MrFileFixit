import com.android.build.api.dsl.LibraryExtension
import com.mrfilefixit.buildlogic.configureAndroidLibrary
import com.mrfilefixit.buildlogic.configureDetekt
import com.mrfilefixit.buildlogic.configureJUnit5
import com.mrfilefixit.buildlogic.configureKotlin
import com.mrfilefixit.buildlogic.configureNetworkIsolation
import com.mrfilefixit.buildlogic.libs
import com.mrfilefixit.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Android library. Every library is network-isolated. */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("android-library"))
        extensions.configure<LibraryExtension> {
            configureAndroidLibrary(rootDir.resolve("config/lint/lint.xml"))
        }
        configureKotlin()
        configureJUnit5()
        configureDetekt()
        configureNetworkIsolation("releaseRuntimeClasspath")
    }
}
