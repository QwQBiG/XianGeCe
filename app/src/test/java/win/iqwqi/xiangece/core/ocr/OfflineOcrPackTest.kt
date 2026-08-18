package win.iqwqi.xiangece.core.ocr

import org.junit.Assert.assertEquals
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
}