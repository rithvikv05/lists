package coil.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.request.ImageRequest

@Composable
fun AsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    val actualModel = if (model is ImageRequest) model.data else model
    coil3.compose.AsyncImage(
        model = actualModel,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale
    )
}
