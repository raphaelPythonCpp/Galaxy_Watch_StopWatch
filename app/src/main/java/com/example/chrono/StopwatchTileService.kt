package com.example.chrono

import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.ResourceBuilders.Resources
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture

class StopwatchTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<Tile> {
        Stopwatch.load(this)

        // Les ids de boutons portent un « nonce » : un clic n'est pris en compte qu'une fois,
        // même si le système renvoie l'ancien lastClickableId lors d'un rafraîchissement.
        val id = requestParams.currentState.lastClickableId
        val parts = id.split(":")
        if (parts.size == 2 && parts[1].toIntOrNull() == Stopwatch.nonce) {
            Stopwatch.quietly {
                when (parts[0]) {
                    "right" -> Stopwatch.toggle(this)
                    "left" -> Stopwatch.left(this)
                }
                Stopwatch.bumpNonce(this)
            }
        }

        val tile = Tile.Builder()
            .setResourcesVersion("2")
            .setFreshnessIntervalMillis(if (Stopwatch.running) 1000L else 0L)
            .setTileTimeline(Timeline.fromLayoutElement(layout()))
            .build()
        return CallbackToFutureAdapter.getFuture<Tile> { c -> c.set(tile); "tile" }
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<Resources> {
        val img = ResourceBuilders.ImageResource.Builder()
            .setAndroidResourceByResId(
                ResourceBuilders.AndroidImageResourceByResId.Builder()
                    .setResourceId(R.drawable.ic_stopwatch)
                    .build()
            )
            .build()
        val res = Resources.Builder().setVersion("2").addIdToImageMapping("ic", img).build()
        return CallbackToFutureAdapter.getFuture<Resources> { c -> c.set(res); "res" }
    }

    private fun argb(v: Long) = ColorBuilders.argb(v.toInt())

    private fun text(s: String, size: Float, color: Long) =
        LayoutElementBuilders.Text.Builder()
            .setText(s)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(sp(size))
                    .setColor(argb(color))
                    .build()
            )
            .build()

    private fun button(key: String, label: String, bg: Long, fg: Long): LayoutElementBuilders.LayoutElement {
        val click = ModifiersBuilders.Clickable.Builder()
            .setId("$key:${Stopwatch.nonce}")
            .setOnClick(ActionBuilders.LoadAction.Builder().build())
            .build()
        return LayoutElementBuilders.Box.Builder()
            .setWidth(dp(58f))
            .setHeight(dp(58f))
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(bg))
                            .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(29f)).build())
                            .build()
                    )
                    .setClickable(click)
                    .build()
            )
            .addContent(text(label, 14f, fg))
            .build()
    }

    private fun layout(): LayoutElementBuilders.LayoutElement {
        val running = Stopwatch.running
        val column = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(
                LayoutElementBuilders.Image.Builder()
                    .setResourceId("ic")
                    .setWidth(dp(22f))
                    .setHeight(dp(22f))
                    .build()
            )
            .addContent(text(fmtMain(Stopwatch.elapsed()), 38f, 0xFFFFFFFF))
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(6f)).build())
            .addContent(
                LayoutElementBuilders.Row.Builder()
                    .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                    .addContent(button("left", if (running) "Tour" else "Reset", 0xFF2B2F36, 0xFFFFFFFF))
                    .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(12f)).build())
                    .addContent(
                        button(
                            "right",
                            if (running) "Stop" else "Start",
                            if (running) 0xFFB91C1C else 0xFF34D399,
                            if (running) 0xFFFFFFFF else 0xFF000000
                        )
                    )
                    .build()
            )
            .build()
        return LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(column)
            .build()
    }
}
