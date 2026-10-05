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
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture

class StopwatchTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<Tile> {
        Stopwatch.load(this)
        Settings.load(this)

        // Un clic n'est pris en compte qu'une fois (nonce dans l'id du bouton)
        val id = requestParams.currentState.lastClickableId
        val parts = id.split(":")
        if (parts.size == 2 && parts[1].toIntOrNull() == Stopwatch.nonce) {
            Stopwatch.quietly {
                when (parts[0]) {
                    "right" -> Stopwatch.toggle(this)
                    "left" -> if (Stopwatch.running) Stopwatch.lap(this)
                }
                Stopwatch.bumpNonce(this)
            }
        }

        val tile = Tile.Builder()
            .setResourcesVersion("2")
            .setFreshnessIntervalMillis(if (Stopwatch.running) (if (Settings.eco) 5000L else 1000L) else 0L)
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
        val eco = Settings.eco
        val pal = makePalette(Settings.rgb)

        val rightBg: Long = if (running) {
            if (eco) 0xFF3A3F47 else pal.inverseDark.argbLong()
        } else {
            if (eco) 0xFFFFFFFF else pal.accent.argbLong()
        }
        val rightFg: Long = if (running) 0xFFFFFFFF
        else (if (eco) 0xFF000000 else pal.onAccent.argbLong())

        // Le Reset n'existe que dans l'appli (appui long 3 s) : le tile n'affiche que Start, ou Tour + Stop
        val row = LayoutElementBuilders.Row.Builder()
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
        if (running) {
            row.addContent(button("left", S.LAP.t(), 0xFF2B2F36, 0xFFFFFFFF))
            row.addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(12f)).build())
        }
        row.addContent(button("right", if (running) S.STOP.t() else S.START.t(), rightBg, rightFg))

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
            .addContent(row.build())
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
