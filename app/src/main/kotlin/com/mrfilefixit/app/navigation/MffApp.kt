package com.mrfilefixit.app.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mrfilefixit.app.R
import com.mrfilefixit.app.home.HomeScreen
import com.mrfilefixit.app.settings.SettingsScreen
import com.mrfilefixit.feature.exam.ui.ExamRoute
import com.mrfilefixit.feature.exam.ui.examScreen
import com.mrfilefixit.feature.image.ui.ImageRoute
import com.mrfilefixit.feature.image.ui.imageScreen
import com.mrfilefixit.feature.pdf.ui.PdfRoute
import com.mrfilefixit.feature.pdf.ui.pdfScreen
import com.mrfilefixit.feature.scan.ui.ScanRoute
import com.mrfilefixit.feature.scan.ui.scanScreen
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data object SettingsRoute

/**
 * [label] is the full name used on home cards; [shortLabel] is the one-word caption for the navigation
 * bar, so five items fit at 320 dp without wrapping or pushing the last item off screen.
 */
enum class TopLevelDestination(
    val route: Any,
    val icon: ImageVector,
    @StringRes val label: Int,
    @StringRes val shortLabel: Int,
) {
    HOME(HomeRoute, Icons.Filled.Home, R.string.nav_home, R.string.nav_home),
    EXAM(ExamRoute, Icons.Filled.Badge, R.string.nav_exam, R.string.nav_exam_short),
    PDF(PdfRoute(), Icons.Filled.PictureAsPdf, R.string.nav_pdf, R.string.nav_pdf_short),
    IMAGE(ImageRoute(), Icons.Filled.Image, R.string.nav_image, R.string.nav_image_short),
    SCAN(ScanRoute, Icons.Filled.DocumentScanner, R.string.nav_scan, R.string.nav_scan),
}

/**
 * App shell. [NavigationSuiteScaffold] picks a bottom bar on phones, a navigation rail on tablets and
 * foldables, and adapts live to split-screen and rotation.
 */
@Composable
fun MffApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    NavigationSuiteScaffold(
        // Exposes test tags as resource ids so UiAutomator (Baseline Profile, Macrobenchmark) can find them.
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { top ->
                item(
                    selected = destination?.hasRoute(top.route::class) == true,
                    onClick = { navController.navigateTopLevel(top) },
                    icon = { Icon(top.icon, contentDescription = null) },
                    label = {
                        Text(
                            stringResource(top.shortLabel),
                            style = NavLabelStyle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    modifier = Modifier.testTag("nav_${top.name.lowercase()}"),
                )
            }
        },
    ) {
        NavHost(navController = navController, startDestination = HomeRoute) {
            composable<HomeRoute> {
                HomeScreen(
                    onOpen = { navController.navigateTopLevel(it) },
                    onOpenSettings = { navController.navigate(SettingsRoute) { launchSingleTop = true } },
                )
            }
            examScreen(
                onPrepareInImageTools = { examId, kind ->
                    navController.navigateToTool(ImageRoute(examId = examId, document = kind.name))
                },
                onCompressPdf = { minKb, maxKb ->
                    navController.navigateToTool(PdfRoute(tool = PDF_COMPRESS, minKb = minKb, maxKb = maxKb))
                },
            )
            pdfScreen()
            imageScreen()
            scanScreen()
            composable<SettingsRoute> { SettingsScreen(onBack = { navController.popBackStack() }) }
        }
    }
}

/**
 * Opens a tool tab with arguments (from an exam). Unlike [navigateTopLevel] it does not restore the
 * tab's saved state, so the new arguments win; Back returns to the start destination.
 */
private fun NavHostController.navigateToTool(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
    }
}

private const val PDF_COMPRESS = "COMPRESS"

/** 11 sp medium with slight tracking: readable, and short enough that every caption stays on one line. */
private val NavLabelStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp)

private fun NavHostController.navigateTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
