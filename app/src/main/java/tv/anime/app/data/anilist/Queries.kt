package tv.anime.app.data.anilist

object Queries {

    // "Recent" = shows that had a new episode air recently (within a window).
    // We query AiringSchedule entries in a [from, to] unix-time range and map
    // them to the Media they belong to.
    val RECENT = """
        query (${'$'}page: Int, ${'$'}perPage: Int, ${'$'}airingAtGreater: Int, ${'$'}airingAtLesser: Int) {
          Page(page: ${'$'}page, perPage: ${'$'}perPage) {
            airingSchedules(
              airingAt_greater: ${'$'}airingAtGreater,
              airingAt_lesser: ${'$'}airingAtLesser,
              sort: TIME_DESC
            ) {
              airingAt
              episode
              media {
                id
                isAdult
                status
                title { romaji english }
                coverImage { large }
              }
            }
          }
        }
    """.trimIndent()

    val POPULAR = """
        query (${'$'}page: Int, ${'$'}perPage: Int) {
          Page(page: ${'$'}page, perPage: ${'$'}perPage) {
            media(type: ANIME, sort: POPULARITY_DESC, isAdult: false) {
              id
              title { romaji english }
              coverImage { large }
            }
          }
        }
    """.trimIndent()

    val TRENDING = """
        query (${'$'}page: Int, ${'$'}perPage: Int) {
          Page(page: ${'$'}page, perPage: ${'$'}perPage) {
            media(type: ANIME, sort: TRENDING_DESC, isAdult: false) {
              id
              title { romaji english }
              coverImage { large }
            }
          }
        }
    """.trimIndent()

    // Hero slider: trending anime with banner images + richer metadata for the top section.
    val HERO_TRENDING = """
    query (${'$'}page: Int, ${'$'}perPage: Int) {
      Page(page: ${'$'}page, perPage: ${'$'}perPage) {
        media(type: ANIME, sort: TRENDING_DESC, isAdult: false) {
          id
          status
          title { romaji english }
          description(asHtml: false)
          bannerImage
          coverImage { extraLarge }
          genres
          averageScore
          episodes
          season
          seasonYear
        }
      }
    }
""".trimIndent()

    val DETAILS = """
        query (${'$'}id: Int) {
          Media(id: ${'$'}id, type: ANIME) {
            id
            title { romaji english }
            description(asHtml: false)
            bannerImage
            coverImage { extraLarge }
            genres
            averageScore
            episodes
            format
            status
            season
            seasonYear
          }
        }
    """.trimIndent()

    val SEARCH = """
        query (${'$'}page: Int, ${'$'}perPage: Int, ${'$'}search: String) {
          Page(page: ${'$'}page, perPage: ${'$'}perPage) {
            pageInfo {
              currentPage
              hasNextPage
            }
            media(type: ANIME, search: ${'$'}search, sort: POPULARITY_DESC, isAdult: false) {
              id
              title { romaji english }
              coverImage { large }
            }
          }
        }
    """.trimIndent()

    val SEASONS = """
    query (${'$'}id: Int) {
      Media(id: ${'$'}id, type: ANIME) {
        id
        title { romaji english }
        season
        seasonYear
        episodes
        coverImage { large }
        relations {
          edges {
            relationType
            node {
              id
              type
              format
              season
              seasonYear
              episodes
              title { romaji english }
              coverImage { large }
            }
          }
        }
      }
    }
""".trimIndent()
}
