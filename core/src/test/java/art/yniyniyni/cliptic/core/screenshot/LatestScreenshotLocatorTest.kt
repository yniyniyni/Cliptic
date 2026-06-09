package art.yniyniyni.cliptic.core.screenshot

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LatestScreenshotLocatorTest {

    @Test
    fun matchesScreenshotsFolderCaseInsensitively() {
        assertTrue(LatestScreenshotLocator.isScreenshotPath("Pictures/Screenshots/"))
        assertTrue(LatestScreenshotLocator.isScreenshotPath("DCIM/screenshots/"))
    }

    @Test
    fun rejectsNonScreenshotPaths() {
        assertFalse(LatestScreenshotLocator.isScreenshotPath("Pictures/Camera/"))
        assertFalse(LatestScreenshotLocator.isScreenshotPath(""))
    }
}
