import com.mrfilefixit.buildlogic.lib
import com.mrfilefixit.buildlogic.libs
import com.mrfilefixit.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Hilt with KSP (no kapt). */
class AndroidHiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("ksp"))
        pluginManager.apply(libs.pluginId("hilt"))
        dependencies {
            add("implementation", libs.lib("hilt-android"))
            add("ksp", libs.lib("hilt-compiler"))
            add("androidTestImplementation", libs.lib("hilt-android-testing"))
            add("kspAndroidTest", libs.lib("hilt-compiler"))
        }
    }
}
