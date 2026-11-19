package win.iqwqi.xiangece.core.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineOcrPackTest {
    @Test
    fun chinesePackHasVerifiedDomesticAndOfficialSources() {
        val files = OfflineOcrPackCatalog.chineseMobile
        assertEquals(3, files.size)
        assertEquals(21_509_645L, OfflineOcrPackCatalog.totalBytes)
        assertEquals(
            "a431985659dc921974177a95adcfbb90fd9e51989a5e04d70d0b75f597b6e61d",
            files.first { it.localName == "det.onnx" }.sha256,
        )
        assertEquals(
            "da72dc72ca4dc220df0dfde68c1dedc31c58d3e76a25871122e5056227d50092",
            files.first { it.localName == "rec.onnx" }.sha256,
        )
        assertTrue(files.all { it.sha256.length == 64 })
        assertTrue(files.all { it.domesticUrl.startsWith("https://hf-mirror.com/") })
        assertTrue(files.all { it.officialUrl.startsWith("https://huggingface.co/") })
        assertTrue(files.all { it.downloadUrls.size >= 4 })
        assertTrue(files.all { it.downloadUrls.first() == it.domesticUrl })
        assertTrue(files.all { it.downloadUrls.last() == it.officialUrl })
    }

    @Test
    fun tiersAreDistinctAndServerMatchesOfficialMetadata() {
        assertEquals(2, OfflineOcrPackCatalog.packs.size)
        assertNull(OfflineOcrPackCatalog.find("unknown"))
        val mobile = checkNotNull(OfflineOcrPackCatalog.find(OfflineOcrPackCatalog.PP_OCRV5_MOBILE_ID))
        val server = checkNotNull(OfflineOcrPackCatalog.find(OfflineOcrPackCatalog.PP_OCRV5_SERVER_ID))
        assertEquals(OfflineOcrPackCatalog.chineseMobile, mobile.files)
        assertEquals(21_509_645L, mobile.totalBytes)
        assertEquals(172_768_163L, server.totalBytes)
        assertEquals(listOf("det.onnx", "rec.onnx", "rec.yml"), server.files.map { it.localName })
        assertEquals(listOf(88_116_791L, 84_503_027L, 148_345L), server.files.map { it.bytes })
        assertTrue(server.files.all { it.sha256.matches(Regex("[a-f0-9]{64}")) })
        assertTrue(server.files.all { !it.officialUrl.contains("/main/") })
        assertTrue(server.files.all { it.officialUrl.startsWith("https://huggingface.co/PaddlePaddle/") })
        assertTrue(server.files.zip(mobile.files).all { (large, small) -> large.sha256 != small.sha256 })
    }

    @Test
    fun mobilePreprocessingStaysUnchangedAndServerHasBoundedInput() {
        val mobile = OfflineOcrPackCatalog.packs.first()
        val server = OfflineOcrPackCatalog.packs.last()
        assertEquals("min", mobile.detLimitType)
        assertEquals(736, mobile.detLimitSideLen)
        assertEquals(4000, mobile.detMaxSideLimit)
        assertEquals("max", server.detLimitType)
        assertEquals(1280, server.detMaxSideLimit)
    }
}
