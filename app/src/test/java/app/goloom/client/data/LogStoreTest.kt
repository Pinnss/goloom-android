package app.goloom.client.data

import androidx.test.core.app.ApplicationProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@Config(manifest = Config.NONE, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class LogStoreTest {

    private lateinit var store: LogStore

    @BeforeEach
    fun reset() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        // Reset singleton.
        val instance = LogStore::class.java.getDeclaredField("INSTANCE")
        instance.isAccessible = true
        instance.set(null, null)
        // Удаляем файл-лога прошлых прогонов.
        File(ctx.cacheDir, "logs/goloom_tunnel.log").delete()
        store = LogStore.get(ctx)
    }

    @Test
    fun `add appends to in-memory entries`() {
        store.info(LogSource.APP, "hello")
        store.warn(LogSource.SDK, "world")
        val entries = store.entries.value
        assertThat(entries).hasSize(2)
        assertThat(entries[0].level).isEqualTo(LogLevel.INFO)
        assertThat(entries[0].source).isEqualTo(LogSource.APP)
        assertThat(entries[1].level).isEqualTo(LogLevel.WARN)
    }

    @Test
    fun `clear empties entries`() {
        store.info(LogSource.APP, "a")
        store.info(LogSource.APP, "b")
        store.clear()
        assertThat(store.entries.value).isEmpty()
    }

    @Test
    fun `snapshotFormatted joins all entries`() {
        store.info(LogSource.APP, "first")
        store.error(LogSource.WG, "second")
        val text = store.snapshotFormatted()
        assertThat(text).contains("INF").contains("first")
        assertThat(text).contains("ERR").contains("second")
        assertThat(text.lines()).hasSize(2)
    }

    @Test
    fun `LogLevel parse handles common spellings`() {
        assertThat(LogLevel.parse("INFO")).isEqualTo(LogLevel.INFO)
        assertThat(LogLevel.parse("info")).isEqualTo(LogLevel.INFO)
        assertThat(LogLevel.parse("WARNING")).isEqualTo(LogLevel.WARN)
        assertThat(LogLevel.parse("DBG")).isEqualTo(LogLevel.DEBUG)
        assertThat(LogLevel.parse("FATAL")).isNull()
    }
}
