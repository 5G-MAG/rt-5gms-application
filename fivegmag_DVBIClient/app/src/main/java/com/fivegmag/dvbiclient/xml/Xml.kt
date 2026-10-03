/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient.xml

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

/** Thrown when a document is not well-formed XML or not the document type expected. */
class XmlFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Small DOM helpers. Elements are matched by local name, so a document parses whichever prefix it
 * binds to a namespace; the caller checks the root namespace where it matters.
 */
object Xml {

    /** Parses [text] as a namespace-aware DOM document. Document type declarations are refused. */
    fun parse(text: String): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        // Not every DOM implementation knows every feature; one it does not know is skipped.
        for ((feature, value) in listOf(
            "http://apache.org/xml/features/disallow-doctype-decl" to true,
            "http://xml.org/sax/features/external-general-entities" to false,
            "http://xml.org/sax/features/external-parameter-entities" to false,
        )) {
            try {
                factory.setFeature(feature, value)
            } catch (_: Exception) {
            }
        }
        try {
            return factory.newDocumentBuilder().parse(InputSource(StringReader(text)))
        } catch (e: Exception) {
            throw XmlFormatException("not well-formed XML: ${e.message}", e)
        }
    }
}

/** Child elements of this node, in document order. */
fun Node.childElements(): List<Element> {
    val out = ArrayList<Element>()
    var n = firstChild
    while (n != null) {
        if (n is Element) out.add(n)
        n = n.nextSibling
    }
    return out
}

/** The local name of an element, whether or not it is in a namespace. */
val Element.local: String get() = localName ?: tagName.substringAfter(':')

/** Direct children with local name [name]. */
fun Element.children(name: String): List<Element> = childElements().filter { it.local == name }

/** The first direct child with local name [name], or null. */
fun Element.child(name: String): Element? = childElements().firstOrNull { it.local == name }

/** Descendants with local name [name], in document order. */
fun Element.descendants(name: String): List<Element> {
    val out = ArrayList<Element>()
    fun walk(e: Element) {
        for (c in e.childElements()) {
            if (c.local == name) out.add(c)
            walk(c)
        }
    }
    walk(this)
    return out
}

/** The first descendant with local name [name], or null. */
fun Element.descendant(name: String): Element? = descendants(name).firstOrNull()

/** Trimmed text content. */
val Element.text: String get() = (textContent ?: "").trim()

/** Trimmed text of the first direct child named [name], or "" when there is none. */
fun Element.childText(name: String): String = child(name)?.text ?: ""

/** The attribute [name] (no namespace), or null when it is absent. */
fun Element.attr(name: String): String? = if (hasAttribute(name)) getAttribute(name) else null

/** The xml:lang attribute, or "" when absent. */
val Element.xmlLang: String
    get() = getAttributeNS("http://www.w3.org/XML/1998/namespace", "lang") ?: ""

/**
 * An XML Schema boolean attribute ("true", "false", "1", "0"), or [default] when absent or not a
 * boolean.
 */
fun Element.boolAttr(name: String, default: Boolean): Boolean = when (attr(name)?.trim()) {
    "true", "1" -> true
    "false", "0" -> false
    else -> default
}
