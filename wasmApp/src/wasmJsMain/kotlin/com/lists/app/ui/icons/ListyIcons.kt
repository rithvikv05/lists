package com.lists.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

object ListyIcons {
    private fun vectorFromPath(
        name: String,
        viewportWidth: Float,
        viewportHeight: Float,
        pathData: String
    ): ImageVector {
        return ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight
        ).addPath(
            pathData = PathParser().parsePathString(pathData).toNodes(),
            fill = SolidColor(Color.White)
        ).build()
    }

    val Movie = vectorFromPath(
        name = "Movie",
        viewportWidth = 960f,
        viewportHeight = 960f,
        pathData = "M240,760v40q0,17 -11.5,28.5T200,840q-17,0 -28.5,-11.5T160,800v-640q0,-17 11.5,-28.5T200,120q17,0 28.5,11.5T240,160v40h80v-40q0,-17 11.5,-28.5T360,120h240q17,0 28.5,11.5T640,160v40h80v-40q0,-17 11.5,-28.5T760,120q17,0 28.5,11.5T800,160v640q0,17 -11.5,28.5T760,840q-17,0 -28.5,-11.5T720,800v-40h-80v40q0,17 -11.5,28.5T600,840L360,840q-17,0 -28.5,-11.5T320,800v-40h-80ZM240,680h80v-80h-80v80ZM240,520h80v-80h-80v80ZM240,360h80v-80h-80v80ZM640,680h80v-80h-80v80ZM640,520h80v-80h-80v80ZM640,360h80v-80h-80v80ZM400,760h160v-560L400,200v560ZM400,200h160,-160Z"
    )

    val TvShow = vectorFromPath(
        name = "TvShow",
        viewportWidth = 960f,
        viewportHeight = 960f,
        pathData = "M160,760q-33,0 -56.5,-23.5T80,680v-480q0,-33 23.5,-56.5T160,120h640q33,0 56.5,23.5T880,200v480q0,33 -23.5,56.5T800,760L640,760v40q0,17 -11.5,28.5T600,840L360,840q-17,0 -28.5,-11.5T320,800v-40L160,760ZM160,680h640v-480L160,200v480ZM160,680v-480,480Z"
    )

    val Book = vectorFromPath(
        name = "Book",
        viewportWidth = 960f,
        viewportHeight = 960f,
        pathData = "M240,880q-33,0 -56.5,-23.5T160,800v-640q0,-33 23.5,-56.5T240,80h480q33,0 56.5,23.5T800,160v640q0,33 -23.5,56.5T720,880L240,880ZM240,800h480v-640h-80v245q0,12 -10,17.5t-20,-0.5l-49,-30q-10,-6 -20.5,-6t-20.5,6l-49,30q-10,6 -20.5,0.5T440,405v-245L240,160v640ZM240,800v-640,640Z"
    )

    val VideoGame = vectorFromPath(
        name = "VideoGame",
        viewportWidth = 960f,
        viewportHeight = 960f,
        pathData = "M160,720q-33,0 -56.5,-23.5T80,640v-320q0,-33 23.5,-56.5T160,240h640q33,0 56.5,23.5T880,320v320q0,33 -23.5,56.5T800,720L160,720ZM160,640h640v-320L160,320v320ZM280,520v40q0,17 11.5,28.5T320,600q17,0 28.5,-11.5T360,560v-40h40q17,0 28.5,-11.5T440,480q0,-17 -11.5,-28.5T400,440h-40v-40q0,-17 -11.5,-28.5T320,360q-17,0 -28.5,11.5T280,400v40h-40q-17,0 -28.5,11.5T200,480q0,17 11.5,28.5T240,520h40ZM580,600q25,0 42.5,-17.5T640,540q0,-25 -17.5,-42.5T580,480q-25,0 -42.5,17.5T520,540q0,25 17.5,42.5T580,600ZM700,480q25,0 42.5,-17.5T760,420q0,-25 -17.5,-42.5T700,360q-25,0 -42.5,17.5T640,420q0,25 17.5,42.5T700,480ZM160,640v-320,320Z"
    )

    val Pin = vectorFromPath(
        name = "Pin",
        viewportWidth = 960f,
        viewportHeight = 960f,
        pathData = "m640,480 l80,80v80L520,640v240l-40,40 -40,-40v-240L240,640v-80l80,-80v-280h-40v-80h400v80h-40v280ZM354,560h252l-46,-46v-314L400,200v314l-46,46ZM480,560Z"
    )

    val Marked = vectorFromPath(
        name = "Marked",
        viewportWidth = 12f,
        viewportHeight = 12f,
        pathData = "M6,11.601C8.997,11.601 11.479,9.114 11.479,6.122C11.479,3.125 8.992,0.644 5.995,0.644C3.003,0.644 0.521,3.125 0.521,6.122C0.521,9.114 3.008,11.601 6,11.601ZM5.398,8.749C5.216,8.749 5.065,8.673 4.926,8.485L3.578,6.831C3.497,6.724 3.449,6.605 3.449,6.482C3.449,6.24 3.637,6.042 3.878,6.042C4.034,6.042 4.152,6.09 4.287,6.267L5.377,7.674L7.67,3.99C7.772,3.829 7.912,3.743 8.052,3.743C8.288,3.743 8.508,3.904 8.508,4.156C8.508,4.274 8.438,4.398 8.374,4.511L5.85,8.485C5.737,8.663 5.581,8.749 5.398,8.749Z"
    )

    val Filter = vectorFromPath(
        name = "Filter",
        viewportWidth = 40f,
        viewportHeight = 40f,
        pathData = "M18.042,30q-0.584,0 -0.98,-0.396 -0.395,-0.396 -0.395,-0.979 0,-0.625 0.395,-1.021 0.396,-0.396 0.98,-0.396h3.916q0.584,0 0.98,0.417 0.395,0.417 0.395,1t-0.395,0.979q-0.396,0.396 -0.98,0.396ZM6.375,12.792q-0.583,0 -0.979,-0.417t-0.396,-1q0,-0.583 0.396,-0.979T6.375,10h27.25q0.583,0 0.979,0.396t0.396,0.979q0,0.625 -0.396,1.021 -0.396,0.396 -0.979,0.396ZM11.375,21.375q-0.583,0 -0.979,-0.396T10,20q0,-0.583 0.396,-0.979t0.979,-0.396h17.25q0.583,0 0.979,0.396T30,20q0,0.583 -0.396,0.979t-0.979,0.396Z"
    )
}
