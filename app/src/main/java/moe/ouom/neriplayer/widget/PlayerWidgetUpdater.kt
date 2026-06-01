package moe.ouom.neriplayer.widget

/*
 * NeriPlayer - A unified Android player for streaming music and videos from multiple online platforms.
 * Copyright (C) 2025-2025 NeriPlayer developers
 * https://github.com/cwuom/NeriPlayer
 *
 * This software is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 */

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.widget.RemoteViews
import androidx.core.graphics.drawable.toBitmap
import coil.Coil
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.ouom.neriplayer.R
import moe.ouom.neriplayer.activity.MainActivity
import moe.ouom.neriplayer.core.player.PlayerManager
import moe.ouom.neriplayer.data.model.displayArtist
import moe.ouom.neriplayer.data.model.displayCoverUrl
import moe.ouom.neriplayer.data.model.displayName
import moe.ouom.neriplayer.data.model.stableKey
import moe.ouom.neriplayer.ui.viewmodel.playlist.SongItem
import moe.ouom.neriplayer.util.NPLogger
import androidx.core.graphics.createBitmap

private const val TAG = "NERI-Widget"
private const val COVER_BITMAP_SIZE_PX = 256
private const val COMPACT_BLUR_BITMAP_WIDTH_PX = 360
private const val COMPACT_BLUR_BITMAP_HEIGHT_PX = 180
private const val COMPACT_BLUR_SAMPLE_WIDTH_PX = 2
private const val COMPACT_BLUR_SAMPLE_HEIGHT_PX = 1

enum class PlayerWidgetStyle {
    Large,
    Compact,
    Cover
}

object PlayerWidgetActions {
    const val ACTION_TOGGLE_PLAYBACK = "moe.ouom.neriplayer.widget.action.TOGGLE_PLAYBACK"
    const val ACTION_PREVIOUS = "moe.ouom.neriplayer.widget.action.PREVIOUS"
    const val ACTION_NEXT = "moe.ouom.neriplayer.widget.action.NEXT"
}

object PlayerWidgetUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var started = false

    fun start(context: Context) {
        if (started) return
        started = true
        val appContext = context.applicationContext
        combine(
            PlayerManager.currentSongFlow,
            PlayerManager.playbackControlPlayingFlow
        ) { song, isPlaying ->
            ObservedPlayerState.from(song, isPlaying)
        }
            .distinctUntilChanged()
            .onEach { updateAll(appContext) }
            .launchIn(scope)
    }

    fun updateAll(context: Context, onFinished: (() -> Unit)? = null) {
        scope.launch {
            try {
                updateAllSuspending(context.applicationContext)
            } finally {
                onFinished?.invoke()
            }
        }
    }

    fun update(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
        style: PlayerWidgetStyle,
        onFinished: (() -> Unit)? = null
    ) {
        scope.launch {
            try {
                val binding = bindings.first { it.style == style }
                val snapshot = buildSnapshot(context.applicationContext)
                updateBinding(context.applicationContext, appWidgetManager, binding, appWidgetIds, snapshot)
            } finally {
                onFinished?.invoke()
            }
        }
    }

    private suspend fun updateAllSuspending(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val targetBindings = bindings.mapNotNull { binding ->
            val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, binding.providerClass))
            if (ids.isEmpty()) null else binding to ids
        }
        if (targetBindings.isEmpty()) return

        val snapshot = buildSnapshot(context)
        targetBindings.forEach { (binding, ids) ->
            updateBinding(context, appWidgetManager, binding, ids, snapshot)
        }
    }

    private suspend fun buildSnapshot(context: Context): PlayerWidgetSnapshot {
        val song = PlayerManager.currentSongFlow.value
        val coverUrl = withContext(Dispatchers.IO) {
            song?.displayCoverUrl(context)
        }
        val coverBitmap = loadCoverBitmap(
            context = context,
            coverUrl = coverUrl,
            width = COVER_BITMAP_SIZE_PX,
            height = COVER_BITMAP_SIZE_PX
        )
        return PlayerWidgetSnapshot(
            title = song?.displayName()?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.player_no_playback),
            subtitle = song?.displayArtist()?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.app_name),
            isPlaying = PlayerManager.playbackControlPlayingFlow.value,
            coverBitmap = coverBitmap,
            compactBlurBitmap = coverBitmap?.let(::createCompactBlurBackdrop)
        )
    }

    private suspend fun loadCoverBitmap(
        context: Context,
        coverUrl: String?,
        width: Int,
        height: Int
    ): Bitmap? {
        if (coverUrl.isNullOrBlank()) return null
        return runCatching {
            withContext(Dispatchers.IO) {
                val cacheKey = "widget-cover:$coverUrl:$width:$height"
                val request = ImageRequest.Builder(context)
                    .data(coverUrl)
                    .allowHardware(false)
                    .bitmapConfig(Bitmap.Config.ARGB_8888)
                    .size(width, height)
                    .precision(Precision.INEXACT)
                    .memoryCacheKey(cacheKey)
                    .diskCacheKey(cacheKey)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build()
                val drawable = Coil.imageLoader(context).execute(request).drawable ?: return@withContext null
                drawable.toBitmap(
                    width = width,
                    height = height,
                    config = Bitmap.Config.ARGB_8888
                )
            }
        }.onFailure { error ->
            NPLogger.d(TAG, "Widget cover load failed: ${error.message}")
        }.getOrNull()
    }

    private fun createCompactBlurBackdrop(source: Bitmap): Bitmap {
        val sample = createBitmap(COMPACT_BLUR_SAMPLE_WIDTH_PX, COMPACT_BLUR_SAMPLE_HEIGHT_PX)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        drawCenterCrop(
            canvas = Canvas(sample),
            source = source,
            targetWidth = COMPACT_BLUR_SAMPLE_WIDTH_PX,
            targetHeight = COMPACT_BLUR_SAMPLE_HEIGHT_PX,
            paint = paint
        )

        val blurred = createBitmap(COMPACT_BLUR_BITMAP_WIDTH_PX, COMPACT_BLUR_BITMAP_HEIGHT_PX)
        Canvas(blurred).drawBitmap(
            sample,
            null,
            Rect(0, 0, COMPACT_BLUR_BITMAP_WIDTH_PX, COMPACT_BLUR_BITMAP_HEIGHT_PX),
            paint
        )
        sample.recycle()
        return blurred
    }

    private fun drawCenterCrop(
        canvas: Canvas,
        source: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
        paint: Paint
    ) {
        val sourceAspect = source.width.toFloat() / source.height.toFloat()
        val targetAspect = targetWidth.toFloat() / targetHeight.toFloat()
        val sourceRect = if (sourceAspect > targetAspect) {
            val cropWidth = (source.height * targetAspect).toInt().coerceAtLeast(1)
            val left = ((source.width - cropWidth) / 2).coerceAtLeast(0)
            Rect(left, 0, (left + cropWidth).coerceAtMost(source.width), source.height)
        } else {
            val cropHeight = (source.width / targetAspect).toInt().coerceAtLeast(1)
            val top = ((source.height - cropHeight) / 2).coerceAtLeast(0)
            Rect(0, top, source.width, (top + cropHeight).coerceAtMost(source.height))
        }
        canvas.drawBitmap(
            source,
            sourceRect,
            Rect(0, 0, targetWidth, targetHeight),
            paint
        )
    }

    private fun updateBinding(
        context: Context,
        appWidgetManager: AppWidgetManager,
        binding: PlayerWidgetBinding,
        appWidgetIds: IntArray,
        snapshot: PlayerWidgetSnapshot
    ) {
        appWidgetIds.forEach { widgetId ->
            val views = RemoteViews(context.packageName, binding.layoutId)
            when (binding.style) {
                PlayerWidgetStyle.Large -> bindLarge(context, views, binding.style, snapshot)
                PlayerWidgetStyle.Compact -> bindCompact(context, views, binding.style, snapshot)
                PlayerWidgetStyle.Cover -> bindCover(context, views, binding.style, snapshot)
            }
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    private fun bindLarge(
        context: Context,
        views: RemoteViews,
        style: PlayerWidgetStyle,
        snapshot: PlayerWidgetSnapshot
    ) {
        bindTextAndOpenAction(context, views, style, snapshot)
        bindCover(views, snapshot)
        views.setImageViewResource(
            R.id.widget_play_pause,
            if (snapshot.isPlaying) {
                R.drawable.widget_pause_dark_24
            } else {
                R.drawable.widget_play_arrow_dark_24
            }
        )
        views.setOnClickPendingIntent(
            R.id.widget_previous,
            widgetActionPendingIntent(context, style, PlayerWidgetActions.ACTION_PREVIOUS)
        )
        views.setOnClickPendingIntent(
            R.id.widget_play_pause,
            widgetActionPendingIntent(context, style, PlayerWidgetActions.ACTION_TOGGLE_PLAYBACK)
        )
        views.setOnClickPendingIntent(
            R.id.widget_next,
            widgetActionPendingIntent(context, style, PlayerWidgetActions.ACTION_NEXT)
        )
        setControlDescriptions(context, views)
    }

    private fun bindCompact(
        context: Context,
        views: RemoteViews,
        style: PlayerWidgetStyle,
        snapshot: PlayerWidgetSnapshot
    ) {
        bindTextAndOpenAction(context, views, style, snapshot)
        bindCompactBlurCover(views, snapshot)
        views.setImageViewResource(
            R.id.widget_play_pause,
            if (snapshot.isPlaying) {
                R.drawable.round_pause_24
            } else {
                R.drawable.round_play_arrow_24
            }
        )
        views.setOnClickPendingIntent(
            R.id.widget_play_pause,
            widgetActionPendingIntent(context, style, PlayerWidgetActions.ACTION_TOGGLE_PLAYBACK)
        )
        views.setContentDescription(
            R.id.widget_play_pause,
            context.getString(if (snapshot.isPlaying) R.string.player_pause else R.string.player_play)
        )
    }

    private fun bindCover(
        context: Context,
        views: RemoteViews,
        style: PlayerWidgetStyle,
        snapshot: PlayerWidgetSnapshot
    ) {
        bindTextAndOpenAction(context, views, style, snapshot)
        bindCover(views, snapshot)
        views.setImageViewResource(
            R.id.widget_play_pause,
            if (snapshot.isPlaying) {
                R.drawable.round_pause_24
            } else {
                R.drawable.round_play_arrow_24
            }
        )
        views.setOnClickPendingIntent(
            R.id.widget_play_pause,
            widgetActionPendingIntent(context, style, PlayerWidgetActions.ACTION_TOGGLE_PLAYBACK)
        )
        views.setContentDescription(
            R.id.widget_play_pause,
            context.getString(if (snapshot.isPlaying) R.string.player_pause else R.string.player_play)
        )
    }

    private fun bindTextAndOpenAction(
        context: Context,
        views: RemoteViews,
        style: PlayerWidgetStyle,
        snapshot: PlayerWidgetSnapshot
    ) {
        views.setTextViewText(R.id.widget_title, snapshot.title)
        views.setTextViewText(R.id.widget_subtitle, snapshot.subtitle)
        views.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent(context, style))
        views.setContentDescription(
            R.id.widget_root,
            context.getString(R.string.widget_player_open_app)
        )
    }

    private fun bindCover(views: RemoteViews, snapshot: PlayerWidgetSnapshot) {
        val bitmap = snapshot.coverBitmap
        if (bitmap != null) {
            views.setImageViewBitmap(R.id.widget_cover, bitmap)
        } else {
            views.setImageViewResource(R.id.widget_cover, R.mipmap.ic_launcher)
        }
    }

    private fun bindCompactBlurCover(views: RemoteViews, snapshot: PlayerWidgetSnapshot) {
        val bitmap = snapshot.compactBlurBitmap ?: snapshot.coverBitmap
        if (bitmap != null) {
            views.setImageViewBitmap(R.id.widget_cover, bitmap)
        } else {
            views.setImageViewResource(R.id.widget_cover, R.drawable.widget_compact_background)
        }
    }

    private fun setControlDescriptions(context: Context, views: RemoteViews) {
        views.setContentDescription(R.id.widget_previous, context.getString(R.string.player_previous))
        views.setContentDescription(R.id.widget_next, context.getString(R.string.player_next))
        views.setContentDescription(
            R.id.widget_play_pause,
            context.getString(
                if (PlayerManager.playbackControlPlayingFlow.value) {
                    R.string.player_pause
                } else {
                    R.string.player_play
                }
            )
        )
    }

    private fun openAppPendingIntent(context: Context, style: PlayerWidgetStyle): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            1000 + style.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun widgetActionPendingIntent(
        context: Context,
        style: PlayerWidgetStyle,
        action: String
    ): PendingIntent {
        val intent = Intent(context, style.providerClass).apply {
            this.action = action
            setPackage(context.packageName)
        }
        return PendingIntent.getBroadcast(
            context,
            style.ordinal * 10 + action.requestCodeOffset(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private val PlayerWidgetStyle.providerClass: Class<out AppWidgetProvider>
        get() = bindings.first { it.style == this }.providerClass
}

private data class PlayerWidgetSnapshot(
    val title: String,
    val subtitle: String,
    val isPlaying: Boolean,
    val coverBitmap: Bitmap?,
    val compactBlurBitmap: Bitmap?
)

private data class ObservedPlayerState(
    val songKey: String?,
    val title: String?,
    val artist: String?,
    val coverUrl: String?,
    val customCoverUrl: String?,
    val mediaUri: String?,
    val isPlaying: Boolean
) {
    companion object {
        fun from(song: SongItem?, isPlaying: Boolean): ObservedPlayerState {
            return ObservedPlayerState(
                songKey = song?.stableKey(),
                title = song?.displayName(),
                artist = song?.displayArtist(),
                coverUrl = song?.coverUrl,
                customCoverUrl = song?.customCoverUrl,
                mediaUri = song?.mediaUri,
                isPlaying = isPlaying
            )
        }
    }
}

private data class PlayerWidgetBinding(
    val style: PlayerWidgetStyle,
    val providerClass: Class<out AppWidgetProvider>,
    val layoutId: Int
)

private val bindings = listOf(
    PlayerWidgetBinding(
        PlayerWidgetStyle.Large,
        PlayerLargeWidgetProvider::class.java,
        R.layout.widget_player_large
    ),
    PlayerWidgetBinding(
        PlayerWidgetStyle.Compact,
        PlayerCompactWidgetProvider::class.java,
        R.layout.widget_player_compact
    ),
    PlayerWidgetBinding(
        PlayerWidgetStyle.Cover,
        PlayerCoverWidgetProvider::class.java,
        R.layout.widget_player_cover
    )
)

private fun String.requestCodeOffset(): Int {
    return when (this) {
        PlayerWidgetActions.ACTION_TOGGLE_PLAYBACK -> 1
        PlayerWidgetActions.ACTION_PREVIOUS -> 2
        PlayerWidgetActions.ACTION_NEXT -> 3
        else -> 0
    }
}
