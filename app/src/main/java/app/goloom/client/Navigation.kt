package app.goloom.client

/**
 * Navigation enum для single-Activity. По образцу референс-проекта tun:
 * простое stack-of-screens, без NavHost/Type-safe routing — для 11 экранов
 * это избыточно.
 *
 * При добавлении нового экрана добавлять и сюда, и в when-блок MainActivity.
 */
sealed class Screen {
    object Main : Screen()
    object Profiles : Screen()
    data class ProfileDetails(val profileId: String) : Screen()
    data class ProfileEdit(val profileId: String) : Screen()
    object ImportSheet : Screen()
    object Settings : Screen()
    object Logs : Screen()
    object Parameters : Screen()
    object Updates : Screen()
    object About : Screen()
    object AppRouting : Screen()
}
