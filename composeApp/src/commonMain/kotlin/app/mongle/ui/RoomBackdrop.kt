package app.mongle.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import app.mongle.logic.RoomSurfaces
import app.mongle.model.Placement
import org.jetbrains.compose.resources.painterResource

/**
 * The room art plus its surface items (wallpaper / floor). Each surface is a precomputed full-room
 * RGBA overlay (tools/room_surfaces.py): the tile texture perspective-mapped onto that theme's back
 * walls / floor, already multiplied with the theme's lighting & shadows, with windows, fixtures and
 * rugs cut out. Drawn with the same [contentScale] as the room, so they line up exactly.
 * Order: room → floor → wallpaper; furniture and 몽이 are drawn on top by the caller.
 */
@Composable
internal fun RoomBackdrop(theme: String, night: Boolean, placements: List<Placement>, contentScale: ContentScale, modifier: Modifier = Modifier.fillMaxSize()) {
    Image(painterResource(Art.room(theme, night, square = false)), null, modifier, contentScale = contentScale)
    val tod = if (night) "night" else "day"
    listOfNotNull(RoomSurfaces.floor(placements), RoomSurfaces.wall(placements)).forEach { id ->
        Image(painterResource(Art.drawable("surf_${theme}_${tod}_$id")), null, modifier, contentScale = contentScale)
    }
}
