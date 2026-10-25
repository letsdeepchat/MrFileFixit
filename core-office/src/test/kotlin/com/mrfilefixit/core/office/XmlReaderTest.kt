package com.mrfilefixit.core.office

import com.mrfilefixit.core.office.xml.XmlReader
import com.mrfilefixit.core.office.xml.XmlWriter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class XmlReaderTest {
    @Test
    fun `resolves namespaces, attributes and entities`() {
        val xml = XmlReader(
            """<?xml version="1.0"?><w:doc xmlns:w="urn:w">""" +
                """<w:p w:val="a&amp;b"><w:t>1 &lt; 2 &#x263A;</w:t></w:p></w:doc>""",
        )
        assertEquals(XmlReader.Event.START, xml.next())
        assertEquals("doc", xml.localName)
        assertEquals("urn:w", xml.namespace)
        xml.next()
        assertTrue(xml.isStart("urn:w", "p"))
        assertEquals("a&b", xml.attr("val"))
        assertEquals("a&b", xml.attr("urn:w", "val"))
        xml.next()
        assertEquals("1 < 2 ☺", xml.readElementText())
    }

    @Test
    fun `self closing elements produce start and end`() {
        val xml = XmlReader("<a><b x='1'/><c/></a>")
        val events = mutableListOf<String>()
        while (xml.next() != XmlReader.Event.EOF) events += "${xml.event}:${xml.localName}:${xml.depth}"
        assertEquals(listOf("START:a:1", "START:b:2", "END:b:1", "START:c:2", "END:c:1", "END:a:0"), events)
    }

    @Test
    fun `forEachChild visits direct children only and skips unread ones`() {
        val xml = XmlReader("<r><a><x/></a>text<b>t</b><c><d/></c></r>")
        xml.next()
        val seen = mutableListOf<String>()
        xml.forEachChild { child ->
            seen += child.localName
            if (child.localName == "b") assertEquals("t", child.readElementText())
        }
        assertEquals(listOf("a", "b", "c"), seen)
        assertEquals(XmlReader.Event.END, xml.event)
        assertEquals(XmlReader.Event.EOF, xml.next())
    }

    @Test
    fun `refuses doctype so no entity expansion can happen`() {
        val bomb = """<?xml version="1.0"?><!DOCTYPE lolz [<!ENTITY lol "lol">]><a>&lol;</a>"""
        assertThrows<OfficeFormatException> {
            val xml = XmlReader(bomb)
            while (xml.next() != XmlReader.Event.EOF) {
                xml.depth
            }
        }
    }

    @Test
    fun `writer escapes text and drops illegal control characters`() {
        val out = XmlWriter().within("a", "q" to "\"x\" & <y>") { text("1 < 2 & \u0001ok") }.toString()
        assertTrue(out.endsWith("""<a q="&quot;x&quot; &amp; &lt;y&gt;">1 &lt; 2 &amp; ok</a>"""), out)
        val back = XmlReader(out)
        while (back.next() != XmlReader.Event.START) {
            back.depth
        }
        assertEquals("\"x\" & <y>", back.attr("q"))
        assertEquals("1 < 2 & ok", back.readElementText())
    }
}
