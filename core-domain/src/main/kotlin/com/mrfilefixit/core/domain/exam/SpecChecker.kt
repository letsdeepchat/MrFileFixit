package com.mrfilefixit.core.domain.exam

import com.mrfilefixit.core.domain.image.ImageFormat
import com.mrfilefixit.core.domain.image.ImageInfo

/** One line of the live pass/fail checklist. Values are raw numbers; the UI formats them per locale. */
sealed interface SpecCheck {
    val passed: Boolean

    data class Format(val actual: ImageFormat?, val allowed: Set<ImageFormat>) : SpecCheck {
        override val passed: Boolean get() = actual != null && actual in allowed
    }

    data class FileSize(val actualBytes: Long, val minBytes: Long, val maxBytes: Long) : SpecCheck {
        override val passed: Boolean get() = actualBytes in minBytes..maxBytes
    }

    data class Width(val actual: Int, val bounds: IntBounds) : SpecCheck {
        override val passed: Boolean get() = actual in bounds
    }

    data class Height(val actual: Int, val bounds: IntBounds) : SpecCheck {
        override val passed: Boolean get() = actual in bounds
    }
}

data class SpecReport(val checks: List<SpecCheck>) {
    val passed: Boolean get() = checks.all { it.passed }
    val failures: List<SpecCheck> get() = checks.filterNot { it.passed }
}

/** Pure, deterministic checker used both for the live preview and the final verdict. */
object SpecChecker {
    fun check(spec: DocumentSpec, info: ImageInfo): SpecReport = SpecReport(
        listOf(
            SpecCheck.Format(info.format, spec.formats),
            SpecCheck.FileSize(info.sizeBytes, spec.minBytes, spec.maxBytes),
            SpecCheck.Width(info.width, spec.width),
            SpecCheck.Height(info.height, spec.height),
        ),
    )
}
