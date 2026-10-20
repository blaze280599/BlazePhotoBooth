package com.blaze.photobooth.data

import com.blaze.photobooth.AppContainer
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * README §11.3: a new phone set up from this one (device-to-device transfer) gets the whole album, her imported frames,
 * the database and the settings; the cloud backup (25 MB quota) takes only the settings, never album rows without
 * their pictures.
 */
class BackupRulesTest {

    private val rules = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        .parse(File("src/main/res/xml/data_extraction_rules.xml"))

    private fun entries(section: String, kind: String): Set<String> {
        val parent = rules.getElementsByTagName(section).item(0) as Element
        val nodes = parent.getElementsByTagName(kind)
        return (0 until nodes.length).map { i ->
            val e = nodes.item(i) as Element
            "${e.getAttribute("domain")}:${e.getAttribute("path")}"
        }.toSet()
    }

    @Test
    fun aNewPhoneGetsEverything() {
        assertEquals(
            setOf("file:${AppContainer.SessionsDir}/", "file:${AppContainer.FramesDir}/", "database:.", "file:datastore/"),
            entries("device-transfer", "include"),
        )
    }

    @Test
    fun theCloudGetsOnlyTheSettings() {
        assertEquals(
            setOf("file:${AppContainer.SessionsDir}/", "file:${AppContainer.FramesDir}/", "database:."),
            entries("cloud-backup", "exclude"),
        )
    }
}
