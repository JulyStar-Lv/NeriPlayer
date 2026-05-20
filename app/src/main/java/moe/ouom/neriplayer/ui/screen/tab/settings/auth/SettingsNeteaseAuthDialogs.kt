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
 * File: moe.ouom.neriplayer.ui.screen.tab.settings.auth/SettingsNeteaseAuthDialogs
 * Updated: 2026/3/23
 */

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import moe.ouom.neriplayer.R
import moe.ouom.neriplayer.activity.NeteaseWebLoginActivity
import moe.ouom.neriplayer.ui.viewmodel.debug.NeteaseAuthViewModel
import moe.ouom.neriplayer.util.HapticTextButton
import org.json.JSONObject

@Composable
internal fun SettingsNeteaseAuthDialogs(
    showSheet: Boolean,
    initialTab: Int,
    onDismissSheet: () -> Unit,
    inlineMsg: String?,
    onInlineMsgChange: (String?) -> Unit,
    showConfirmDialog: Boolean,
    confirmPhoneMasked: String?,
    onDismissConfirmDialog: () -> Unit,
    vm: NeteaseAuthViewModel,
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
            val json = result.data?.getStringExtra(NeteaseWebLoginActivity.RESULT_COOKIE) ?: "{}"
            vm.importCookiesFromMap(parseCookieMap(json))
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
            webLoginLauncher.launch(Intent(context, NeteaseWebLoginActivity::class.java))
        }
    }

    if (showSavedCookieDialog) {
        SavedCookieActionSheet(
            sourceName = stringResource(R.string.platform_netease),
            sourceIconResId = R.drawable.ic_netease_cloud_music,
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

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = onDismissConfirmDialog,
            title = { Text(stringResource(R.string.login_confirm_send_code)) },
            text = { Text(stringResource(R.string.login_send_code_to, confirmPhoneMasked ?: "")) },
            confirmButton = {
                HapticTextButton(
                    onClick = {
                        onDismissConfirmDialog()
                        vm.sendCaptcha(ctcode = "86")
                    }
                ) {
                    Text(stringResource(R.string.action_send))
                }
            },
            dismissButton = {
                HapticTextButton(
                    onClick = {
                        onDismissConfirmDialog()
                        onInlineMsgChange(context.getString(R.string.sync_send_cancelled))
                    }
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
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
            title = stringResource(R.string.login_success),
            cookieText = cookieText,
            onDismiss = onDismissCookieDialog
        )
    }
}

@Composable
internal fun CookieTextDialog(
    title: String,
    cookieText: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = cookieText.ifBlank { stringResource(R.string.settings_empty_placeholder) },
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        confirmButton = {
            HapticTextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_ok))
            }
        }
    )
}

private fun parseCookieMap(json: String): Map<String, String> {
    return JSONObject(json).let { obj ->
        val keys = obj.keys()
        val result = linkedMapOf<String, String>()
        while (keys.hasNext()) {
            val key = keys.next()
            result[key] = obj.optString(key, "")
        }
        result
    }
}
