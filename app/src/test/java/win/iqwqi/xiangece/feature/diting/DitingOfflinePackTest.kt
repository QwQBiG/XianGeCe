package win.iqwqi.xiangece.feature.diting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import win.iqwqi.xiangece.feature.diting.offline.DitingOfflinePackCatalog

class DitingOfflinePackTest {
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