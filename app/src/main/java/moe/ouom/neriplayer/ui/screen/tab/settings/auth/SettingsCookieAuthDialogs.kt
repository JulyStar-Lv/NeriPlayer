@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package moe.ouom.neriplayer.ui.screen.tab.settings.auth

/*
 * NeriPlayer - A unified Android player for streaming music and videos from multiple online platforms.
 * Copyright (C) 2025-2025 NeriPlayer developers
 * https://github.com/cwuom/NeriPlayer
 *
 * This software is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this software.
 * If not, see <https://www.gnu.org/licenses/>.
 *
 * File: moe.ouom.neriplayer.ui.screen.tab.settings.auth/SettingsCookieAuthDialogs
 * Updated: 2026/3/23
 */

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.ouom.neriplayer.R
import moe.ouom.neriplayer.activity.BiliWebLoginActivity
import moe.ouom.neriplayer.activity.YouTubeWebLoginActivity
import moe.ouom.neriplayer.ui.component.bottomSheetDragBlocker
import moe.ouom.neriplayer.ui.viewmodel.auth.BiliAuthViewModel
import moe.ouom.neriplayer.ui.viewmodel.auth.YouTubeAuthViewModel

@Composable
internal fun SettingsBiliAuthDialogs(
    showSheet: Boolean,
    initialTab: Int,
    onDismissSheet: () -> Unit,
    inlineMsg: String?,
    onInlineMsgChange: (String?) -> Unit,
    vm: BiliAuthViewModel,
    showCookieDialog: Boolean,
    cookieText: String,
    onDismissCookieDialog: () -> Unit,
    showSavedCookieDialog: Boolean = false,
    onDismissSavedCookieDialog: () -> Unit = {},
    onOpenSheetAtTab: (Int) -> Unit = {},
    onLogout: (() -> Unit)? = null,
    onBrowserLogin: (() -> Unit)? = null,
    onManageSource: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val webLoginLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val json = result.data?.getStringExtra(BiliWebLoginActivity.RESULT_COOKIE) ?: "{}"
            vm.importCookiesFromMap(vm.parseJsonToMap(json))
        } else {
            onInlineMsgChange(context.getString(R.string.settings_cookie_cancelled))
        }
    }
    val launchBrowserLogin: () -> Unit = {
        onInlineMsgChange(null)
        val injectedBrowserLogin = onBrowserLogin
        if (injectedBrowserLogin != null) {
            injectedBrowserLogin()
        } else {
            webLoginLauncher.launch(Intent(context, BiliWebLoginActivity::class.java))
        }
    }

    if (showSavedCookieDialog) {
        SavedCookieActionSheet(
            sourceName = stringResource(R.string.platform_bilibili),
            sourceIconResId = R.drawable.ic_bilibili,
            onDismiss = onDismissSavedCookieDialog,
            onManageSource = onManageSource?.let { manage ->
                {
                    onDismissSavedCookieDialog()
                    manage()
                }
            },
            onRelogin = {
                onDismissSavedCookieDialog()
                launchBrowserLogin()
            },
            onLogout = {
                onDismissSavedCookieDialog()
                onLogout?.invoke()
            }
        )
    }

    if (showSheet) {
        LaunchedEffect(showSheet) {
            launchBrowserLogin()
            onDismissSheet()
        }
    }

    if (showCookieDialog) {
        CookieTextDialog(
            title = stringResource(R.string.settings_bili_login_success),
            cookieText = cookieText,
            onDismiss = onDismissCookieDialog
        )
    }
}

@Composable
internal fun SettingsYouTubeAuthDialogs(
    showSheet: Boolean,
    initialTab: Int,
    onDismissSheet: () -> Unit,
    inlineMsg: String?,
    onInlineMsgChange: (String?) -> Unit,
    vm: YouTubeAuthViewModel,
    showCookieDialog: Boolean,
    cookieText: String,
    onDismissCookieDialog: () -> Unit,
    showSavedCookieDialog: Boolean = false,
    onDismissSavedCookieDialog: () -> Unit = {},
    onOpenSheetAtTab: (Int) -> Unit = {},
    onLogout: (() -> Unit)? = null,
    onBrowserLogin: (() -> Unit)? = null,
    onManageSource: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val webLoginLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val json = result.data?.getStringExtra(YouTubeWebLoginActivity.RESULT_AUTH_JSON) ?: "{}"
            vm.importAuthFromJson(json)
        } else {
            onInlineMsgChange(context.getString(R.string.settings_cookie_cancelled))
        }
    }
    val launchBrowserLogin: () -> Unit = {
        onInlineMsgChange(null)
        val injectedBrowserLogin = onBrowserLogin
        if (injectedBrowserLogin != null) {
            injectedBrowserLogin()
        } else {
            webLoginLauncher.launch(Intent(context, YouTubeWebLoginActivity::class.java))
        }
    }

    if (showSavedCookieDialog) {
        SavedCookieActionSheet(
            sourceName = stringResource(R.string.common_youtube),
            sourceIconResId = R.drawable.ic_youtube,
            onDismiss = onDismissSavedCookieDialog,
            onManageSource = onManageSource?.let { manage ->
                {
                    onDismissSavedCookieDialog()
                    manage()
                }
            },
            onRelogin = {
                onDismissSavedCookieDialog()
                launchBrowserLogin()
            },
            onLogout = {
                onDismissSavedCookieDialog()
                onLogout?.invoke()
            }
        )
    }

    if (showSheet) {
        LaunchedEffect(showSheet) {
            launchBrowserLogin()
            onDismissSheet()
        }
    }

    if (showCookieDialog) {
        CookieTextDialog(
            title = stringResource(R.string.settings_youtube_login_success),
            cookieText = cookieText,
            onDismiss = onDismissCookieDialog
        )
    }
}

@Composable
internal fun SavedCookieActionSheet(
    sourceName: String,
    sourceIconResId: Int,
    onDismiss: () -> Unit,
    onManageSource: (() -> Unit)?,
    onRelogin: () -> Unit,
    onLogout: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .bottomSheetDragBlocker()
                .padding(start = 16.dp, end = 16.dp, bottom = 48.dp, top = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = sourceIconResId),
                    contentDescription = sourceName,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(sourceName, style = MaterialTheme.typography.titleLarge)
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (onManageSource != null) {
                SavedCookieActionItem(
                    text = stringResource(R.string.settings_saved_cookie_manage_source),
                    onClick = onManageSource
                )
            }
            SavedCookieActionItem(
                text = stringResource(R.string.settings_saved_cookie_relogin),
                onClick = onRelogin
            )
            SavedCookieActionItem(
                text = stringResource(R.string.settings_saved_cookie_logout),
                onClick = onLogout
            )
        }
    }
}

@Composable
private fun SavedCookieActionItem(
    text: String,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(text) },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}
