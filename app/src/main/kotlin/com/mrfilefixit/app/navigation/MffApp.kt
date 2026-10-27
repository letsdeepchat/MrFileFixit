package com.mrfilefixit.app.navigation

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mrfilefixit.app.BuildConfig
import com.mrfilefixit.app.R
import com.mrfilefixit.app.home.HomeScreen
import com.mrfilefixit.app.settings.SettingsScreen
import com.mrfilefixit.app.support.SupportScreen
import com.mrfilefixit.core.ui.navigation.InnerScreenTracker
import com.mrfilefixit.core.ui.navigation.SwipePolicy
import com.mrfilefixit.core.ui.navigation.SwipeableTabs
import com.mrfilefixit.feature.exam.ui.ExamListDetail
import com.mrfilefixit.feature.image.ui.ImageRoute
import com.mrfilefixit.feature.image.ui.ImageTab
import com.mrfilefixit.feature.image.ui.imageScreen
import com.mrfilefixit.feature.ocr.ui.OcrRoute
import com.mrfilefixit.feature.ocr.ui.ocrScreen
import com.mrfilefixit.feature.pdf.ui.PdfListDetail
import com.mrfilefixit.feature.pdf.ui.PdfRoute
import com.mrfilefixit.feature.pdf.ui.pdfScreen
import com.mrfilefixit.feature.resume.ui.ResumeTab
import com.mrfilefixit.feature.scan.ui.ScanTab
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/** The six main tabs, side by side in one pager. */
@Serializable
data object MainTabsRoute

@Serializable
data object SettingsRoute

@Serializable
data object SupportRoute

/**
 * [label] is the full name used on home cards; [shortLabel] is the one-word caption for the navigation
 * bar, so all six items fit at 320 dp (53 dp each) without wrapping or pushing the last item off screen.
 */
enum class TopLevelDestination(
    val icon: ImageVector,
    @StringRes val label: Int,
    @StringRes val shortLabel: Int,
) {
    HOME(Icons.Filled.Home, R.string.nav_home, R.string.nav_home),
    EXAM(Icons.Filled.Badge, R.string.nav_exam, R.string.nav_exam_short),
    PDF(Icons.Filled.PictureAsPdf, R.string.nav_pdf, R.string.nav_pdf_short),
    IMAGE(Icons.Filled.Image, R.string.nav_image, R.string.nav_image_short),
    SCAN(Icons.Filled.DocumentScanner, R.string.nav_scan, R.string.nav_scan),
    RESUME(Icons.Filled.Description, R.string.nav_resume, R.string.nav_resume_short),
}

/**
 * App shell. [NavigationSuiteScaffold] picks a bottom bar on phones and a navigation rail on tablets and
 * foldables. The six main tabs live in one swipeable pager ([SwipeableTabs]) that follows the bar and drives
 * it; Settings, Support, text recognition and tools opened from an exam are pushed on top with a back button.
 */
@Composable
fun MffApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val pagerState = rememberPagerState { TopLevelDestination.entries.size }
    val scope = rememberCoroutineScope()
    val selected = selectedTab(destination, pagerState)
    NavigationSuiteScaffold(
        // Exposes test tags as resource ids so UiAutomator (Baseline Profile, Macrobenchmark) can find them.
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { top ->
                item(
                    selected = selected == top,
                    onClick = {
                        // From a pushed screen, go back to the tabs first.
                        if (destination?.hasRoute(MainTabsRoute::class) == false) {
                            navController.popBackStack(MainTabsRoute, inclusive = false)
                        }
                        scope.showTab(pagerState, top.ordinal)
                    },
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
        NavHost(navController = navController, startDestination = MainTabsRoute) {
            composable<MainTabsRoute> { MainTabs(navController, pagerState, scope) }
            // Exam shortcuts open the tool on its own screen with the exam's limits; Back returns to the exam.
            imageScreen(onOpenOcr = { navController.openOcr() }, onExit = { navController.popBackStack() })
            pdfScreen(onOpenOcr = { navController.openOcr() }, onExit = { navController.popBackStack() })
            ocrScreen(onBack = { navController.popBackStack() })
            composable<SettingsRoute> {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenSupport = { navController.navigate(SupportRoute) { launchSingleTop = true } }
                        .takeIf { BuildConfig.DONATIONS_ENABLED },
                )
            }
            if (BuildConfig.DONATIONS_ENABLED) {
                composable<SupportRoute> { SupportScreen(onBack = { navController.popBackStack() }) }
            }
        }
    }
}

@Composable
private fun MainTabs(navController: NavHostController, pagerState: PagerState, scope: CoroutineScope) {
    val trackers = remember { List(TopLevelDestination.entries.size) { InnerScreenTracker() } }
    // Home's biodata card opens the Resume & Biodata tab straight on the biodata templates.
    var openBiodata by rememberSaveable { mutableStateOf(false) }
    val current = pagerState.currentPage
    val backTarget = SwipePolicy.backTarget(current, trackers[current].isInner, TopLevelDestination.HOME.ordinal)
    // Registered before the pages, so a page's own back handling (inner screens) always wins.
    BackHandler(enabled = backTarget != null) { backTarget?.let { scope.showTab(pagerState, it) } }
    SwipeableTabs(pagerState, trackers) { page ->
        when (TopLevelDestination.entries[page]) {
            TopLevelDestination.HOME -> HomeScreen(
                onOpen = { scope.showTab(pagerState, it.ordinal) },
                onOpenSettings = { navController.navigate(SettingsRoute) { launchSingleTop = true } },
                onCreateBiodata = {
                    openBiodata = true
                    scope.showTab(pagerState, TopLevelDestination.RESUME.ordinal)
                },
                onOpenSupport = { navController.navigate(SupportRoute) { launchSingleTop = true } }
                    .takeIf { BuildConfig.DONATIONS_ENABLED },
            )
            TopLevelDestination.EXAM -> ExamListDetail(
                onPrepareInImageTools = { examId, kind ->
                    navController.navigate(ImageRoute(examId = examId, document = kind.name)) {
                        launchSingleTop = true
                    }
                },
                onCompressPdf = { minKb, maxKb ->
                    navController.navigate(PdfRoute(tool = PDF_COMPRESS, minKb = minKb, maxKb = maxKb)) {
                        launchSingleTop = true
                    }
                },
            )
            TopLevelDestination.PDF -> PdfListDetail(PdfRoute(), onOpenOcr = { navController.openOcr() })
            TopLevelDestination.IMAGE -> ImageTab(onOpenOcr = { navController.openOcr() })
            TopLevelDestination.SCAN -> ScanTab(onOpenOcr = { navController.openOcr(it) })
            TopLevelDestination.RESUME -> ResumeTab(
                openBiodata = openBiodata,
                onBiodataOpened = { openBiodata = false },
            )
        }
    }
}

/** The bar item to highlight: the pager's tab, or the tab a pushed tool screen belongs to. */
private fun selectedTab(destination: NavDestination?, pagerState: PagerState): TopLevelDestination? = when {
    destination == null || destination.hasRoute(MainTabsRoute::class) ->
        TopLevelDestination.entries[pagerState.targetPage]
    destination.hasRoute(ImageRoute::class) -> TopLevelDestination.IMAGE
    destination.hasRoute(PdfRoute::class) -> TopLevelDestination.PDF
    destination.hasRoute(SettingsRoute::class) || destination.hasRoute(SupportRoute::class) -> TopLevelDestination.HOME
    else -> TopLevelDestination.entries[pagerState.currentPage]
}

/** Neighbouring tabs slide in; farther ones jump, so the pages in between are not built for nothing. */
private fun CoroutineScope.showTab(pagerState: PagerState, page: Int) {
    launch {
        if (abs(page - pagerState.currentPage) <= 1) {
            pagerState.animateScrollToPage(page)
        } else {
            pagerState.scrollToPage(page)
        }
    }
}

private fun NavHostController.openOcr(uris: List<String> = emptyList()) {
    navigate(OcrRoute(uris)) { launchSingleTop = true }
}

private const val PDF_COMPRESS = "COMPRESS"

/** 11 sp medium with slight tracking: readable, and short enough that every caption stays on one line. */
private val NavLabelStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp)
