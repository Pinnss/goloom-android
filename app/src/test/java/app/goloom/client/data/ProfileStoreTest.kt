package app.goloom.client.data

import androidx.test.core.app.ApplicationProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Покрывает CRUD ProfileStore + смену активного профиля. Использует реальный
 * SharedPreferences из Robolectric. Между тестами префы чистим в @BeforeEach,
 * чтобы тесты были независимы.
 */
@Config(manifest = Config.NONE, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class ProfileStoreTest {

    private val sampleConnStr =
        "goloom://eyJtIjoiaHR0cHM6Ly90ZWxlbW9zdC55YW5kZXgucnUvai84NDEwMDIxOTkzMDk3NiIs" +
        "InRhZyI6Im1haW4iLCJ3Z2NwIjoiYUpqM2I5WjIwRGxPdnpFVjJsS1NSWFdZMEdoUnYyZGtUU1ludDQ1T2Rraz0i" +
        "LCJ3Z3NwIjoiMDhSNW1sNVZac3l5cGVzTXdIK1pkVHdzaTNWZS8wQ0I3YzJPS0ZTRXMxOD0iLCJ3Z2EiOiIxMC42" +
        "Ni4xLjIvMjQiLCJ3Z2UiOiIxMjcuMC4wLjE6NTE4MjAiLCJ3Z2QiOiIxLjEuMS4xLDguOC44LjgifQ"

    private lateinit var store: ProfileStore

    @BeforeEach
    fun reset() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.getSharedPreferences("goloom_profiles", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        // Сбрасываем синглтон, чтобы загрузка была свежая.
        val instance = ProfileStore::class.java.getDeclaredField("INSTANCE")
        instance.isAccessible = true
        instance.set(null, null)
        store = ProfileStore.get(ctx)
    }

    @Test
    fun `add stores profile and updates active to first added`() {
        val p = Profile(name = "Tokyo", connStr = sampleConnStr)
        store.add(p)
        assertThat(store.profiles.value).hasSize(1).extracting("id").contains(p.id)
        assertThat(store.activeId.value).isEqualTo(p.id)
    }

    @Test
    fun `delete removes profile and reassigns active when needed`() {
        val a = Profile(name = "A", connStr = sampleConnStr)
        val b = Profile(name = "B", connStr = sampleConnStr)
        store.add(a)
        store.add(b)
        // a — активный (первый добавлен)
        assertThat(store.activeId.value).isEqualTo(a.id)
        store.delete(a.id)
        assertThat(store.profiles.value).extracting("id").containsExactly(b.id)
        assertThat(store.activeId.value).isEqualTo(b.id)
    }

    @Test
    fun `update mutates name and bumps updatedAt`() {
        val p = Profile(name = "Old", connStr = sampleConnStr)
        store.add(p)
        val before = store.byId(p.id)!!.updatedAt
        Thread.sleep(2)
        store.update(p.copy(name = "New"))
        val after = store.byId(p.id)!!
        assertThat(after.name).isEqualTo("New")
        assertThat(after.updatedAt).isGreaterThan(before)
    }

    @Test
    fun `survives reload from prefs`() {
        val p = Profile(name = "Persisted", connStr = sampleConnStr)
        store.add(p)
        // Симулируем перезапуск процесса.
        val instance = ProfileStore::class.java.getDeclaredField("INSTANCE")
        instance.isAccessible = true
        instance.set(null, null)
        val store2 = ProfileStore.get(ApplicationProvider.getApplicationContext())
        assertThat(store2.profiles.value).extracting("name").contains("Persisted")
    }

    @Test
    fun `setActive null clears active id`() {
        val p = Profile(name = "P", connStr = sampleConnStr)
        store.add(p)
        store.setActive(null)
        assertThat(store.activeId.value).isNull()
    }
}
