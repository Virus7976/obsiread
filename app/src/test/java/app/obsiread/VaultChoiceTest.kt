package app.obsiread

import app.obsi.core.*
import app.obsi.core.data.*
import app.obsi.core.obsidian.*
import app.obsi.core.ui.*
import app.obsi.core.update.*
import app.obsi.core.link.*
import app.obsi.core.share.*

import app.obsiread.data.AppSettings
import app.obsi.core.data.ClipTemplate
import app.obsiread.data.Article
import app.obsiread.data.ArticleMeta
import app.obsiread.obsidian.NoteBuilder
import org.junit.Assert.assertEquals
import org.junit.Test

class VaultChoiceTest {
    private val video = Article(ArticleMeta("abc12345678", "T", "A"))
    private val template = ClipTemplate(id = "t", name = "Big Idea", vault = "OtherDeviceVault")

    @Test fun settingsVaultBeatsTheTemplateVault() {
        val s = AppSettings(vault = "Liam's Git Vault", vaults = listOf("Liam's Git Vault"))
        assertEquals("Liam's Git Vault", NoteBuilder.build(template, video, "", s).vault)
    }

    @Test fun templateVaultIsOnlyAFallback() {
        assertEquals("OtherDeviceVault", NoteBuilder.build(template, video, "", AppSettings()).vault)
    }
}
