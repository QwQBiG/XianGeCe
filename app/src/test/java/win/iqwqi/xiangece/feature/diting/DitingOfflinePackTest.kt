package win.iqwqi.xiangece.feature.diting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import win.iqwqi.xiangece.feature.diting.offline.DitingOfflinePackCatalog

class DitingOfflinePackTest {
    @Test
    fun modernTiersHaveDistinctDirectoriesAndPinnedVerifiedManifests() {
        val packs = DitingOfflinePackCatalog.packs
        assertEquals(3, packs.map { it.id }.distinct().size)
        assertEquals(239_549_735L, DitingOfflinePackCatalog.senseVoice.totalBytes)
        assertEquals(1_009_605_061L, DitingOfflinePackCatalog.funAsrNano.totalBytes)
        packs.forEach { pack ->
            assertEquals(pack.totalBytes, pack.files.sumOf { it.bytes })
            assertTrue(pack.files.all { it.sha256.matches(Regex("[0-9a-f]{64}")) })
            assertEquals(pack.files.size, pack.files.map { it.name.substringAfterLast('/') }.distinct().size)
        }
        assertEquals(
            setOf("Qwen3-0.6B/merges.txt", "Qwen3-0.6B/tokenizer.json", "Qwen3-0.6B/vocab.json"),
            DitingOfflinePackCatalog.funAsrNano.files.filter { '/' in it.name }.map { it.name }.toSet(),
        )
        assertTrue(DitingOfflinePackCatalog.senseVoice.files.all {
            "/resolve/2365baeacb507f821a0c8120fcee3d484dba7a07/" in it.officialUrl
        })
        assertFalse(DitingOfflinePackCatalog.funAsrNano.files.any { "/resolve/main/" in it.officialUrl })
        assertNull(DitingOfflinePackCatalog.find("../unknown"))
    }

    @Test
    fun defaultSelectionPreservesLegacyInstallationAndExplicitUninstalledChoice() {
        val old = DitingOfflinePackCatalog.CHINESE_PARAFORMER_ID
        val light = DitingOfflinePackCatalog.SENSE_VOICE_ID
        val enhanced = DitingOfflinePackCatalog.FUN_ASR_NANO_ID
        assertEquals(light, DitingOfflinePackCatalog.initialSelection(null, emptySet()))
        assertEquals(old, DitingOfflinePackCatalog.initialSelection(null, setOf(old)))
        assertEquals(enhanced, DitingOfflinePackCatalog.initialSelection(enhanced, setOf(old)))
        assertEquals(light, DitingOfflinePackCatalog.initialSelection(light, setOf(old)))
        assertEquals(old, DitingOfflinePackCatalog.initialSelection("unknown", setOf(old)))
        assertEquals(light, DitingOfflinePackCatalog.initialSelection("unknown", emptySet()))
    }

    @Test
    fun chinesePackHasCompleteVerifiedFiles() {
        val pack = DitingOfflinePackCatalog.chineseParaformer
        assertEquals(3, pack.files.size)
        assertEquals(pack.totalBytes, pack.files.sumOf { it.bytes })
        assertEquals(
            listOf("encoder.int8.onnx", "decoder.int8.onnx", "tokens.txt"),
            pack.files.map { it.name },
        )
        assertEquals(
            listOf(165_462_184L, 71_664_561L, 75_756L),
            pack.files.map { it.bytes },
        )
        assertTrue(pack.files.all { it.sha256.length == 64 })
        assertTrue(pack.files.all { it.domesticUrl.startsWith("https://hf-mirror.com/") })
        assertTrue(pack.files.all { it.officialUrl.startsWith("https://huggingface.co/") })
        assertTrue(pack.files.all { it.downloadUrls.size >= 4 })
        assertTrue(pack.files.all { it.downloadUrls.first() == it.domesticUrl })
        assertTrue(pack.files.all { it.downloadUrls.last() == it.officialUrl })
    }
}
