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

import android.app.Application
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import moe.ouom.neriplayer.core.player.AudioPlayerService
import moe.ouom.neriplayer.core.player.PlayerManager
import moe.ouom.neriplayer.util.NPLogger

private const val TAG = "NERI-Widget"

abstract class PlayerWidgetProvider(
    private val style: PlayerWidgetStyle
) : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            PlayerWidgetActions.ACTION_TOGGLE_PLAYBACK -> {
                handlePlaybackCommand(context) {
                    PlayerManager.togglePlayPause()
                }
                return
            }

            PlayerWidgetActions.ACTION_PREVIOUS -> {
                handlePlaybackCommand(context) {
                    PlayerManager.previous()
                }
                return
            }

            PlayerWidgetActions.ACTION_NEXT -> {
                handlePlaybackCommand(context) {
                    PlayerManager.next()
                }
                return
            }
        }
        super.onReceive(context, intent)
    }

    override fun onEnabled(context: Context) {
        val pendingResult = goAsync()
        PlayerWidgetUpdater.start(context.applicationContext)
        PlayerWidgetUpdater.updateAll(context.applicationContext) {
            pendingResult.finish()
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        PlayerWidgetUpdater.start(context.applicationContext)
        PlayerWidgetUpdater.update(context.applicationContext, appWidgetManager, appWidgetIds, style) {
            pendingResult.finish()
        }
    }

    private fun handlePlaybackCommand(context: Context, command: () -> Unit) {
        val pendingResult = goAsync()
        runCatching {
            (context.applicationContext as? Application)?.let { app ->
                PlayerManager.initialize(app)
            }
            command()
            if (PlayerManager.hasItems()) {
                AudioPlayerService.startSyncService(
                    context.applicationContext,
                    source = "widget_control",
                    forceForeground = PlayerManager.shouldRunPlaybackServiceInForeground()
                )
            }
        }.onFailure { error ->
            NPLogger.w(TAG, "Widget playback command failed", error)
        }
        PlayerWidgetUpdater.updateAll(context.applicationContext) {
            pendingResult.finish()
        }
    }
}

class PlayerLargeWidgetProvider : PlayerWidgetProvider(PlayerWidgetStyle.Large)

class PlayerCompactWidgetProvider : PlayerWidgetProvider(PlayerWidgetStyle.Compact)

class PlayerCoverWidgetProvider : PlayerWidgetProvider(PlayerWidgetStyle.Cover)
