package tv.anime.app.domain

data class Season(
    val mediaId: Int,
    val label: String,
    val coverUrl: String? = null,
    val episodesCount: Int? = null
)


