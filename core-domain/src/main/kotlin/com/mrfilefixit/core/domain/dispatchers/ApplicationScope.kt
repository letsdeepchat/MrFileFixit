package com.mrfilefixit.core.domain.dispatchers

import javax.inject.Qualifier

/**
 * Qualifies the app-wide CoroutineScope (SupervisorJob + Dispatchers.Default) provided by :app.
 * Used for work that must outlive a screen, such as deleting a job's temp files after the ViewModel is cleared.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
