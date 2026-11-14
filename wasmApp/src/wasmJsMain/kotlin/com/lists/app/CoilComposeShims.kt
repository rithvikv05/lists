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
    contentScale: ContentScale = ContentScale.Fit,
    onError: ((coil3.compose.AsyncImagePainter.State.Error) -> Unit)? = null,
    onSuccess: ((coil3.compose.AsyncImagePainter.State.Success) -> Unit)? = null
) {
    val actualModel = if (model is ImageRequest) model.data else model
    coil3.compose.AsyncImage(
        model = actualModel,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        onError = onError,
        onSuccess = onSuccess
    )
}
