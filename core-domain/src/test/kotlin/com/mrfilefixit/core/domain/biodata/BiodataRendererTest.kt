package com.mrfilefixit.core.domain.biodata

import com.mrfilefixit.core.domain.document.BorderStyle
import com.mrfilefixit.core.domain.document.DocBlock
import com.mrfilefixit.core.domain.document.DocImage
import com.mrfilefixit.core.domain.document.DocParagraph
import com.mrfilefixit.core.domain.document.DocTable
import com.mrfilefixit.core.domain.document.ListKind
import com.mrfilefixit.core.domain.document.RichDocument
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class BiodataRendererTest {
    private val today = LocalDate.of(2026, 10, 27)

    @ParameterizedTest(name = "{0} with {1} photo(s)")
    @MethodSource("templatesAndPhotoCounts")
    fun `every template renders with 1 to 5 photos and prints every photo`(templateId: String, photos: Int) {
        val template = BiodataTemplates.byId(templateId)
        val biodata = filled(template).copy(photos = photoList(photos))
        val doc = render(biodata, template)
        val images = images(doc.blocks)
        assertEquals(photos, images.size, "all photos are printed")
        assertTrue(text(doc).contains("Asha Verma"))
        assertEquals(template.border, doc.decoration?.style)
        if (template.kind == BiodataKind.MARRIAGE) {
            assertTrue(text(doc).contains("Personal details"))
            assertTrue(text(doc).contains("Family background"))
            assertTrue(text(doc).contains("Contact details"))
        } else {
            assertTrue(text(doc).contains("Wedding and portrait photographer"))
            assertTrue(text(doc).contains("Services"))
        }
        if (photos > 1) {
            val heading = if (template.kind == BiodataKind.MARRIAGE) "Photos" else "Work samples"
            assertTrue(text(doc).contains(heading))
        }
    }

    @Test
    fun `the five templates are three marriage and two portfolio designs`() {
        assertEquals(5, BiodataTemplates.all.size)
        assertEquals(3, BiodataTemplates.all.count { it.kind == BiodataKind.MARRIAGE })
        assertEquals(2, BiodataTemplates.all.count { it.kind == BiodataKind.PORTFOLIO })
        assertEquals(5, BiodataTemplates.all.map { it.id }.toSet().size)
        assertEquals(BiodataTemplates.TRADITIONAL, BiodataTemplates.byId("unknown"))
        assertEquals(BorderStyle.ORNATE, BiodataTemplates.TRADITIONAL.border)
    }

    @Test
    fun `hidden cultural details and empty fields are left out`() {
        val template = BiodataTemplates.CLASSIC
        val shown = render(filled(template).copy(photos = photoList(1)), template)
        assertTrue(text(shown).contains("Gotra"))
        assertTrue(text(shown).contains("Kashyap"))

        val hidden = BiodataEdits.setCulturalShown(filled(template).copy(photos = photoList(1)), false)
        val doc = render(hidden, template)
        assertFalse(text(doc).contains("Gotra"))
        assertFalse(text(doc).contains("Kashyap"))
        assertFalse(text(doc).contains("Cultural and astrological details"))
        // Hiding keeps the text in the draft.
        assertEquals("Kashyap", hidden.value(BiodataField.GOTRA))

        val oneHidden = BiodataEdits.setShown(filled(template), BiodataField.CASTE, false)
        assertFalse(text(render(oneHidden, template)).contains("Caste:"))
        assertTrue(text(render(oneHidden, template)).contains("Gotra"))
    }

    @Test
    fun `a section with no text disappears`() {
        val template = BiodataTemplates.MODERN
        val biodata = Biodata("a", template.id, name = "Ravi", values = mapOf(BiodataField.HEIGHT to "5 ft 9 in"))
        val doc = render(biodata, template)
        assertTrue(text(doc).contains("Personal details"))
        assertFalse(text(doc).contains("Family background"))
        assertFalse(text(doc).contains("Contact details"))
    }

    @Test
    fun `age is worked out from the date of birth when left blank`() {
        val template = BiodataTemplates.TRADITIONAL
        val biodata = Biodata("a", template.id, values = mapOf(BiodataField.DATE_OF_BIRTH to "14-02-1998"))
        assertTrue(text(render(biodata, template)).contains("28 years"))
        val typed = BiodataEdits.setValue(biodata, BiodataField.AGE, "27")
        assertFalse(text(render(typed, template)).contains("28 years"))
        assertTrue(text(render(typed, template)).contains("27"))
    }

    @Test
    fun `invocation line is printed first on marriage biodata`() {
        val template = BiodataTemplates.TRADITIONAL
        val biodata = BiodataEdits.setInvocation(filled(template), "|| Shree Ganeshaya Namah ||")
        val first = render(biodata, template).blocks.first() as DocParagraph
        assertEquals("|| Shree Ganeshaya Namah ||", first.plainText)
    }

    @Test
    fun `portfolio lists become bullet lists`() {
        val template = BiodataTemplates.CREATIVE
        val doc = render(filled(template).copy(photos = photoList(2)), template)
        val bullets = paragraphs(doc.blocks).filter { it.style.list == ListKind.BULLET }.map { it.plainText }
        assertTrue(bullets.containsAll(listOf("Weddings", "Pre-wedding shoots", "Portraits")))
        assertTrue(bullets.contains("Sony A7 IV"))
    }

    @Test
    fun `list items split by line or by comma`() {
        assertEquals(listOf("a", "b", "c"), BiodataRenderer.listItems("a, b ,c"))
        assertEquals(listOf("one, two", "three"), BiodataRenderer.listItems("- one, two\n\n• three"))
        assertEquals(emptyList<String>(), BiodataRenderer.listItems("  \n "))
    }

    @Test
    fun `photographer showcase prints the first work sample across the page`() {
        val template = BiodataTemplates.PHOTOGRAPHER
        val doc = render(filled(template).copy(photos = photoList(4)), template)
        val standalone = doc.blocks.filterIsInstance<DocImage>()
        assertEquals(1, standalone.size)
        assertTrue(standalone.single().widthPt > 400f)
    }

    @Test
    fun `missing photo files are skipped instead of failing`() {
        val template = BiodataTemplates.MODERN
        val biodata = filled(template).copy(photos = photoList(3))
        val doc = BiodataRenderer.render(biodata, template, listOf(JPEG, null, JPEG), BiodataLabels.English, today)
        assertEquals(2, images(doc.blocks).size)
    }

    @Test
    fun `labels come from the caller so the printout can be in Hindi`() {
        val hindi = object : BiodataLabels by BiodataLabels.English {
            override val title = "बायोडाटा"

            override fun section(section: BiodataSection) =
                if (section == BiodataSection.PERSONAL) "व्यक्तिगत विवरण" else BiodataLabels.English.section(section)
        }
        val template = BiodataTemplates.TRADITIONAL
        val doc = BiodataRenderer.render(filled(template), template, emptyList(), hindi, today)
        assertTrue(text(doc).contains("बायोडाटा"))
        assertTrue(text(doc).contains("व्यक्तिगत विवरण"))
    }

    @Test
    fun `tint mixes toward white`() {
        assertEquals(-0x1, BiodataRenderer.tint(0xFF000000.toInt(), 0f))
        assertEquals(0xFF000000.toInt(), BiodataRenderer.tint(0xFF000000.toInt(), 1f))
    }

    @Test
    fun `no border template has no decoration`() {
        assertNull(render(filled(BiodataTemplates.PHOTOGRAPHER), BiodataTemplates.PHOTOGRAPHER).decoration)
        assertNotNull(render(filled(BiodataTemplates.MODERN), BiodataTemplates.MODERN).decoration)
    }

    private fun render(biodata: Biodata, template: BiodataTemplate): RichDocument =
        BiodataRenderer.render(biodata, template, biodata.photos.map { JPEG }, BiodataLabels.English, today)

    companion object {
        private val JPEG = byteArrayOf(1, 2, 3)

        @JvmStatic
        fun templatesAndPhotoCounts(): List<Arguments> =
            BiodataTemplates.all.flatMap { t -> (1..5).map { Arguments.of(t.id, it) } }

        fun photoList(count: Int) = (1..count).map { BiodataPhoto("p$it", 900, 1200) }

        fun filled(template: BiodataTemplate): Biodata {
            var b = Biodata("draft-1", template.id, name = "Asha Verma")
            val values = if (template.kind == BiodataKind.MARRIAGE) {
                mapOf(
                    BiodataField.DATE_OF_BIRTH to "14-02-1998",
                    BiodataField.PLACE_OF_BIRTH to "Jaipur",
                    BiodataField.HEIGHT to "5 ft 4 in",
                    BiodataField.COMPLEXION to "Wheatish",
                    BiodataField.RELIGION to "Hindu",
                    BiodataField.CASTE to "Brahmin",
                    BiodataField.GOTRA to "Kashyap",
                    BiodataField.MANGLIK to "No",
                    BiodataField.QUALIFICATION to "M.Tech",
                    BiodataField.COMPANY to "ISRO",
                    BiodataField.FATHER_NAME to "Suresh Verma",
                    BiodataField.MOTHER_NAME to "Kavita Verma",
                    BiodataField.BROTHERS to "1 (married)",
                    BiodataField.PARTNER_EXPECTATIONS to "Well educated, kind and family oriented.",
                    BiodataField.ADDRESS to "12 Civil Lines\nJaipur 302006",
                    BiodataField.PHONE to "+91 98765 43210",
                )
            } else {
                b = BiodataEdits.setTagline(b, "Wedding and portrait photographer")
                mapOf(
                    BiodataField.BIO to "Ten years of telling stories with light.",
                    BiodataField.SKILLS to "Lighting, Retouching, Drone",
                    BiodataField.EQUIPMENT to "Sony A7 IV\nDJI Mini 4",
                    BiodataField.SERVICES to "Weddings\nPre-wedding shoots\nPortraits",
                    BiodataField.YEARS_EXPERIENCE to "10",
                    BiodataField.CLIENTS to "Taj Hotels, Fabindia",
                    BiodataField.PHONE to "+91 98765 43210",
                    BiodataField.EMAIL to "asha@example.com",
                    BiodataField.SOCIAL to "@ashaclicks",
                )
            }
            values.forEach { (field, value) -> b = BiodataEdits.setValue(b, field, value) }
            return b
        }

        fun paragraphs(blocks: List<DocBlock>): List<DocParagraph> = blocks.flatMap { block ->
            when (block) {
                is DocParagraph -> listOf(block)
                is DocTable -> paragraphs(block.rows.flatMap { r -> r.cells.flatMap { it.blocks } })
                else -> emptyList()
            }
        }

        fun images(blocks: List<DocBlock>): List<DocImage> = blocks.flatMap { block ->
            when (block) {
                is DocImage -> listOf(block)
                is DocTable -> images(block.rows.flatMap { r -> r.cells.flatMap { it.blocks } })
                else -> emptyList()
            }
        }

        fun text(doc: RichDocument): String = paragraphs(doc.blocks).joinToString("\n") { it.plainText }
    }
}
