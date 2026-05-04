package app.goloom.client.data

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.robolectric.annotation.Config

/**
 * Покрывает парсинг production-формата connstr (extended), legacy
 * (без WG-полей), а также классические ошибки ввода.
 *
 * Robolectric нужен только потому, что под капотом ConnStrParser
 * использует [android.util.Base64], который вне эмулятора без shadows
 * бросает RuntimeException("Stub!").
 */
@Config(manifest = Config.NONE, sdk = [33])
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
class ConnStrTest {

    /**
     * Реальная строка с production-сервера (получена через админку).
     * Содержит все 5 WG-полей: wgcp/wgsp/wga/wge/wgd.
     */
    private val realConnStr =
        "goloom://eyJtIjoiaHR0cHM6Ly90ZWxlbW9zdC55YW5kZXgucnUvai84NDEwMDIxOTkzMDk3NiIs" +
        "InRhZyI6Im1haW4iLCJ3Z2NwIjoiYUpqM2I5WjIwRGxPdnpFVjJsS1NSWFdZMEdoUnYyZGtUU1ludDQ1T2Rraz0i" +
        "LCJ3Z3NwIjoiMDhSNW1sNVZac3l5cGVzTXdIK1pkVHdzaTNWZS8wQ0I3YzJPS0ZTRXMxOD0iLCJ3Z2EiOiIxMC42" +
        "Ni4xLjIvMjQiLCJ3Z2UiOiIxMjcuMC4wLjE6NTE4MjAiLCJ3Z2QiOiIxLjEuMS4xLDguOC44LjgifQ"

    @Test
    fun `parses production connstr with full WG fields`() {
        val r = ConnStrParser.parse(realConnStr) as ConnStrParser.Result.Ok
        assertThat(r.value.meeting).isEqualTo("https://telemost.yandex.ru/j/84100219930976")
        assertThat(r.value.tag).isEqualTo("main")
        assertThat(r.value.wgClientPrivate).isEqualTo("aJj3b9Z20DlOvzEV2lKSRXWY0GhRv2dkTSYnt45Odkk=")
        assertThat(r.value.wgServerPublic).isEqualTo("08R5ml5VZsyypesMwH+ZdTwsi3Ve/0CB7c2OKFSEs18=")
        assertThat(r.value.wgClientAddress).isEqualTo("10.66.1.2/24")
        assertThat(r.value.wgEndpoint).isEqualTo("127.0.0.1:51820")
        assertThat(r.value.wgDns).isEqualTo("1.1.1.1,8.8.8.8")
        assertThat(r.value.hasWireGuard).isTrue
    }

    @Test
    fun `renders wireguard config from full connstr`() {
        val parsed = (ConnStrParser.parse(realConnStr) as ConnStrParser.Result.Ok).value
        val wg = parsed.toWireGuardConfig()
        assertThat(wg)
            .isNotNull
            .contains("[Interface]")
            .contains("PrivateKey = aJj3b9Z20DlOvzEV2lKSRXWY0GhRv2dkTSYnt45Odkk=")
            .contains("Address = 10.66.1.2/24")
            .contains("DNS = 1.1.1.1,8.8.8.8")
            .contains("[Peer]")
            .contains("PublicKey = 08R5ml5VZsyypesMwH+ZdTwsi3Ve/0CB7c2OKFSEs18=")
            .contains("Endpoint = 127.0.0.1:51820")
            .contains("AllowedIPs = 0.0.0.0/1, 128.0.0.0/1")
            .contains("PersistentKeepalive = 25")
    }

    @Test
    fun `legacy connstr without WG returns null wg config`() {
        val legacy = "goloom://eyJtIjoiaHR0cHM6Ly90ZWxlbW9zdC55YW5kZXgucnUvai84NDEwMDIxOTkzMDk3NiIsInRhZyI6Im1haW4ifQ"
        val r = ConnStrParser.parse(legacy) as ConnStrParser.Result.Ok
        assertThat(r.value.meeting).isEqualTo("https://telemost.yandex.ru/j/84100219930976")
        assertThat(r.value.hasWireGuard).isFalse
        assertThat(r.value.toWireGuardConfig()).isNull()
    }

    @Test
    fun `wrong scheme is rejected with helpful error`() {
        val r = ConnStrParser.parse("https://example.com/foo") as ConnStrParser.Result.Error
        assertThat(r.reason).contains("goloom://")
    }

    @Test
    fun `garbage base64 is rejected`() {
        val r = ConnStrParser.parse("goloom://!!!not-base64!!!") as ConnStrParser.Result.Error
        assertThat(r.reason).containsIgnoringCase("base64")
    }

    @Test
    fun `missing meeting field is rejected`() {
        // base64url('{"tag":"main"}') = eyJ0YWciOiJtYWluIn0
        val r = ConnStrParser.parse("goloom://eyJ0YWciOiJtYWluIn0") as ConnStrParser.Result.Error
        assertThat(r.reason).containsIgnoringCase("meeting")
    }

    @Test
    fun `whitespace around input is trimmed`() {
        val padded = "  $realConnStr\n"
        val r = ConnStrParser.parse(padded) as ConnStrParser.Result.Ok
        assertThat(r.value.meeting).isEqualTo("https://telemost.yandex.ru/j/84100219930976")
    }

    @Test
    fun `parseOrNull returns null on error`() {
        assertThat(ConnStrParser.parseOrNull("not a goloom link")).isNull()
        assertThat(ConnStrParser.parseOrNull(realConnStr)).isNotNull
    }

    @Test
    fun `suggestedProfileName uses tag when present`() {
        val parsed = (ConnStrParser.parse(realConnStr) as ConnStrParser.Result.Ok).value
        assertThat(parsed.suggestedProfileName()).isEqualTo("Main")
    }

    @Test
    fun `suggestedProfileName falls back to host when tag empty`() {
        // base64url('{"m":"https://telemost.yandex.ru/j/foo"}')
        val noTag = "goloom://eyJtIjoiaHR0cHM6Ly90ZWxlbW9zdC55YW5kZXgucnUvai9mb28ifQ"
        val parsed = (ConnStrParser.parse(noTag) as ConnStrParser.Result.Ok).value
        assertThat(parsed.suggestedProfileName()).isEqualTo("telemost.yandex.ru")
    }
}
