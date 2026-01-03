package tv.anime.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

@Composable
fun SimpleTextScreen(
    title: String,
    body: String = "Coming soon"
) {
    Surface {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = SideMenuContentInsetStart,
                    end = 24.dp,
                    top = 24.dp,
                    bottom = 24.dp
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.headlineLarge)
            Text(body, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
