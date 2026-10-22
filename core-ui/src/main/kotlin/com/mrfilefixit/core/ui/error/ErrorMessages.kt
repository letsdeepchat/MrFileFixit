package com.mrfilefixit.core.ui.error

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.mrfilefixit.core.domain.result.AppError
import com.mrfilefixit.core.ui.R
import com.mrfilefixit.core.ui.format.formatBytes

/** User-facing message for an [AppError]. Errors carry no file names, so neither do messages. */
@Composable
@ReadOnlyComposable
fun appErrorMessage(error: AppError): String = when (error) {
    is AppError.FileTooLarge ->
        stringResource(R.string.error_file_too_large, formatBytes(error.actualBytes), formatBytes(error.limitBytes))
    is AppError.DimensionsTooLarge -> stringResource(R.string.error_dimensions_too_large, error.width, error.height)
    is AppError.TooManyPages -> pluralStringResource(
        R.plurals.error_too_many_pages,
        error.pageCount,
        error.pageCount,
        error.limit,
    )
    is AppError.TooManyFiles -> pluralStringResource(
        R.plurals.error_too_many_files,
        error.count,
        error.count,
        error.limit,
    )
    is AppError.UnsupportedFormat -> stringResource(R.string.error_unsupported_format)
    AppError.CorruptFile -> stringResource(R.string.error_corrupt)
    AppError.EmptyFile -> stringResource(R.string.error_empty)
    AppError.PasswordRequired -> stringResource(R.string.error_password_required)
    AppError.WrongPassword -> stringResource(R.string.error_wrong_password)
    is AppError.InvalidPageSelection -> stringResource(R.string.error_invalid_pages, error.pageCount)
    is AppError.TargetSizeUnreachable -> stringResource(
        R.string.error_target_unreachable,
        formatBytes(error.minBytes),
        formatBytes(error.maxBytes),
        formatBytes(error.achievedBytes),
    )
    AppError.FileNotFound -> stringResource(R.string.error_not_found)
    AppError.PermissionDenied -> stringResource(R.string.error_permission)
    AppError.StorageFull -> stringResource(R.string.error_storage_full)
    AppError.OutOfMemory -> stringResource(R.string.error_out_of_memory)
    is AppError.FeatureUnavailable -> stringResource(R.string.error_feature_unavailable)
    is AppError.QuotaExceeded -> stringResource(R.string.error_quota)
    is AppError.Unexpected -> stringResource(R.string.error_unexpected)
}
