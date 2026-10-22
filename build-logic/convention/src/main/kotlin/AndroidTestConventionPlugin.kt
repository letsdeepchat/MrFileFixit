import com.android.build.api.dsl.TestExtension
import com.mrfilefixit.buildlogic.configureKotlin
import com.mrfilefixit.buildlogic.libs
import com.mrfilefixit.buildlogic.pluginId
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** com.android.test module used for Baseline Profiles and Macrobenchmarks. */
class AndroidTestConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("android-test"))
        extensions.configure<TestExtension> {
            compileSdk = 36
            defaultConfig {
                minSdk = 28
                targetSdk = 36
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
        }
        configureKotlin()
    }
}
