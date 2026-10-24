package com.mrfilefixit.feature.exam.data

import com.mrfilefixit.core.domain.exam.CertificateFormat
import com.mrfilefixit.core.domain.exam.CertificateSpec
import com.mrfilefixit.core.domain.exam.CompositeSpec
import com.mrfilefixit.core.domain.exam.DocumentKind
import com.mrfilefixit.core.domain.exam.DocumentSpec
import com.mrfilefixit.core.domain.exam.ExamCategory
import com.mrfilefixit.core.domain.exam.ExamProfile
import com.mrfilefixit.core.domain.exam.IntBounds
import com.mrfilefixit.core.domain.exam.VerificationStatus
import com.mrfilefixit.core.domain.image.ImageFormat
import com.mrfilefixit.core.domain.result.AppError
import com.mrfilefixit.core.domain.result.AppResult
import com.mrfilefixit.core.domain.result.runCatchingApp
import java.time.LocalDate
import kotlinx.serialization.json.Json

/**
 * Parses and validates the bundled rules file. Pure Kotlin, so golden tests run on the JVM.
 *
 * Validation is strict: a malformed profile fails the whole file in tests and CI rather than
 * showing users wrong numbers. At runtime the error maps to [AppError.Unexpected].
 */
object ExamRulesParser {
    const val SUPPORTED_SCHEMA_VERSION = 2
    private const val HTTPS = "https://"

    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
    }

    fun parse(text: String): AppResult<List<ExamProfile>> = runCatchingApp(
        mapError = { AppError.Unexpected("ExamRules:${it.javaClass.simpleName}") },
    ) {
        val file = json.decodeFromString(ExamRulesFileDto.serializer(), text)
        require(file.schemaVersion == SUPPORTED_SCHEMA_VERSION) { "Unsupported schema ${file.schemaVersion}" }
        val profiles = file.profiles.map { it.toDomain() }
        val duplicates = profiles.groupBy { it.id }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) { "Duplicate profile ids: $duplicates" }
        profiles
    }

    private fun ExamProfileDto.toDomain(): ExamProfile {
        require(sourceUrl.startsWith(HTTPS)) { "$id: sourceUrl must be https" }
        lastVerified?.let { LocalDate.parse(it) } // throws on a malformed date
        return ExamProfile(
            id = id,
            shortName = shortName,
            name = name,
            category = ExamCategory.valueOf(category),
            conductingBody = conductingBody,
            sourceUrl = sourceUrl,
            lastVerified = lastVerified,
            verification = when (verification) {
                "verified" -> VerificationStatus.VERIFIED
                "community" -> VerificationStatus.COMMUNITY
                else -> throw IllegalArgumentException("$id: bad verification '$verification'")
            },
            documents = documents.map { it.toDomain(id) }.also { docs ->
                require(docs.map { it.kind }.toSet().size == docs.size) { "$id: duplicate document kinds" }
            },
            composite = composite?.let { CompositeSpec(it.document.toDomain(id), it.photoFraction) },
            notes = notes,
            certificates = certificates.map { it.toDomain(id) },
        )
    }

    private fun CertificateDto.toDomain(profileId: String): CertificateSpec = CertificateSpec(
        name = name,
        formats = formats.map { format ->
            requireNotNull(CertificateFormat.entries.firstOrNull { it.name.equals(format, ignoreCase = true) }) {
                "$profileId/$name: unknown format '$format'"
            }
        }.toSet(),
        minKb = sizeKb.min ?: 0,
        maxKb = requireNotNull(sizeKb.max) { "$profileId/$name: sizeKb.max is required" },
        notes = notes,
    )

    private fun DocumentDto.toDomain(profileId: String): DocumentSpec {
        val sizeMin = sizeKb.min ?: 0
        val sizeMax = requireNotNull(sizeKb.max) { "$profileId/$kind: sizeKb.max is required" }
        require(kilobyteBytes == DocumentSpec.KIB || kilobyteBytes == DocumentSpec.DECIMAL_KB) {
            "$profileId/$kind: kilobyteBytes must be 1000 or 1024"
        }
        return DocumentSpec(
            kind = DocumentKind.valueOf(kind.uppercase()),
            formats = formats.map {
                requireNotNull(ImageFormat.fromName(it)) { "$profileId/$kind: unknown format '$it'" }
            }.toSet(),
            minKb = sizeMin,
            maxKb = sizeMax,
            width = IntBounds(widthPx.min, widthPx.max),
            height = IntBounds(heightPx.min, heightPx.max),
            targetWidth = targetPx.width,
            targetHeight = targetPx.height,
            kilobyteBytes = kilobyteBytes,
            notes = notes,
            background = background,
            ink = ink,
        )
    }
}
