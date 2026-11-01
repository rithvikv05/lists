package androidx.compose.ui.res

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter

@Composable
fun painterResource(id: ImageVector): Painter = rememberVectorPainter(id)
