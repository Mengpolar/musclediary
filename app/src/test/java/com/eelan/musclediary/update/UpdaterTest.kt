package com.eelan.musclediary.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 纯函数部分：语义化版本比较与体积格式化（App 内更新判断的核心） */
class UpdaterTest {

    @Test fun newer_patch() {
        assertTrue(Updater.isNewer("0.7.22", "0.7.23"))
        assertFalse(Updater.isNewer("0.7.23", "0.7.22"))
    }

    @Test fun newer_minor() {
        assertTrue(Updater.isNewer("0.7.9", "0.8.0"))
        assertFalse(Updater.isNewer("0.8.0", "0.7.99"))
    }

    @Test fun newer_sameVersion() {
        assertFalse(Updater.isNewer("0.7.23", "0.7.23"))
    }

    @Test fun newer_unevenLengths() {
        assertTrue(Updater.isNewer("0.7", "0.7.1"))
        assertFalse(Updater.isNewer("0.7.1", "0.7"))
        assertFalse(Updater.isNewer("1.0.0", "1.0"))
    }

    @Test fun newer_multiDigitPatch() {
        // 0.1.10 > 0.1.9：按数字比而非字典序
        assertTrue(Updater.isNewer("0.1.9", "0.1.10"))
        assertFalse(Updater.isNewer("0.1.10", "0.1.9"))
    }

    @Test fun newer_withVPrefix() {
        assertTrue(Updater.isNewer("0.7.23", "v0.8.0"))
    }

    @Test fun formatSize() {
        assertEquals("512 B", Updater.formatSize(512))
        assertEquals("1 KB", Updater.formatSize(1024))
        assertEquals("10.6 MB", Updater.formatSize((10.6 * 1024 * 1024).toLong()))
    }
}
