package ru.feskolech.libriatv.ui.auth

import ru.feskolech.libriatv.ui.components.DialogButton
import ru.feskolech.libriatv.ui.components.AppDialog
import androidx.compose.runtime.withFrameNanos
import ru.feskolech.libriatv.ui.components.DrawerBrowsing
import androidx.compose.ui.draw.clip
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import ru.feskolech.libriatv.ui.components.AccentButton as Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import ru.feskolech.libriatv.R
import ru.feskolech.libriatv.ui.components.makeQr
import ru.feskolech.libriatv.data.repo.AuthState

private const val LINK_URL = "https://aniliberty.top/app/auth/otp/linkDevice"
private val accent: Color @Composable get() = MaterialTheme.colorScheme.primary

@Composable
fun AuthScreen(onOpenMenu: () -> Unit, onContentFocus: () -> Unit, viewModel: AuthViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.start() }
    BackHandler(state.passwordMode) { viewModel.showPassword(false) }
    Surface(
        modifier = Modifier.fillMaxSize().onFocusChanged { if (it.hasFocus) onContentFocus() },
        colors = androidx.tv.material3.SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    ) {
        Box(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 27.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.auth_eyebrow), color = Color(0xFFCF6D6D), style = MaterialTheme.typography.labelLarge)
                Text(if (state.auth is AuthState.Authorized) stringResource(R.string.profile) else stringResource(R.string.auth_title), style = MaterialTheme.typography.headlineLarge)
            }
            when (val auth = state.auth) {
                is AuthState.Authorized -> ProfileContent(auth.user.nickname, auth.user.avatarUrl, onOpenMenu, viewModel::logout)
                is AuthState.Error -> Column(Modifier.padding(top = 150.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text(auth.message, style = MaterialTheme.typography.titleLarge)
                    Button(onClick = viewModel::retryProfile) { Text(stringResource(R.string.retry)) }
                }
                AuthState.Loading -> Text(stringResource(R.string.auth_loading_profile), modifier = Modifier.padding(top = 150.dp))
                else -> if (state.passwordMode) PasswordContent(state, viewModel::login, { viewModel.showPassword(false) }, onOpenMenu)
                else CodeContent(state, viewModel::newCode, { viewModel.showPassword(true) }, onOpenMenu)
            }
        }
    }
}

@Composable
private fun CodeContent(state: AuthUiState, newCode: () -> Unit, password: () -> Unit, onOpenMenu: () -> Unit) {
    val focus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    LaunchedEffect(state.expired, state.loadingCode) { withFrameNanos { }; if (!state.loadingCode && !DrawerBrowsing.active) runCatching { focus.requestFocus() } }
    Row(
        Modifier.fillMaxSize().padding(top = 112.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        Column(
            Modifier.weight(1.25f).fillMaxHeight().background(Color(0xFF1D1D1F), RoundedCornerShape(20.dp)).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.auth_code_heading), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.auth_code_hint), color = Color(0xFFBABABA), style = MaterialTheme.typography.bodyLarge)
            if (state.loadingCode) Text(stringResource(R.string.auth_loading_code), style = MaterialTheme.typography.headlineMedium)
            else Text(state.code?.chunked(3)?.joinToString("  ") ?: "···  ···", fontSize = 60.sp, lineHeight = 70.sp, fontWeight = FontWeight.Bold, letterSpacing = 5.sp, color = Color.White)
            Text(
                when {
                    state.expired -> stringResource(R.string.auth_expired)
                    state.code != null -> stringResource(R.string.auth_timer, state.secondsLeft / 60, state.secondsLeft % 60)
                    else -> stringResource(R.string.auth_waiting)
                },
                color = if (state.expired) Color(0xFFFFA1A1) else Color(0xFFB7B7B7),
            )
            if (state.error != null) Text(state.error, color = Color(0xFFFFA1A1), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            Button(onClick = newCode, modifier = Modifier.focusRequester(focus).focusProperties { right = passwordFocus }) {
                Text(stringResource(if (state.code == null || state.expired) R.string.auth_new_code else R.string.auth_refresh_code))
            }
        }
        Column(
            Modifier.weight(0.82f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().weight(1f).background(Color(0xFF252527), RoundedCornerShape(20.dp)).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    val qr = remember { makeQr(LINK_URL) }
                    Image(qr.asImageBitmap(), contentDescription = stringResource(R.string.auth_qr_description), modifier = Modifier.size(130.dp).background(Color.White, RoundedCornerShape(8.dp)).padding(7.dp))
                    Text(stringResource(R.string.auth_scan), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Text(stringResource(R.string.auth_url), style = MaterialTheme.typography.bodySmall, color = Color(0xFFBDBDBD))
            }
            Text(stringResource(R.string.auth_site_hint), style = MaterialTheme.typography.bodyMedium, color = Color(0xFFBDBDBD))
            Button(onClick = password, modifier = Modifier.fillMaxWidth().focusRequester(passwordFocus)) { Text(stringResource(R.string.auth_password_button)) }
        }
    }
}

@Composable
private fun PasswordContent(state: AuthUiState, submit: (String, String) -> Unit, back: () -> Unit, onOpenMenu: () -> Unit) {
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { withFrameNanos { }; if (!DrawerBrowsing.active) runCatching { focus.requestFocus() } }
    Row(Modifier.fillMaxSize().padding(top = 112.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        Column(
            Modifier.weight(1.1f).fillMaxHeight().background(Color(0xFF1D1D1F), RoundedCornerShape(20.dp)).padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.auth_password_heading), style = MaterialTheme.typography.headlineSmall)
            AuthField(login, { login = it }, stringResource(R.string.auth_login_label), Modifier.focusRequester(focus))
            AuthField(password, { password = it }, stringResource(R.string.auth_password_label), password = true)
            if (state.error != null) Text(state.error, color = Color(0xFFFFA1A1))
            Button(onClick = { submit(login, password) }, enabled = login.isNotBlank() && password.isNotBlank() && !state.submitting) {
                Text(stringResource(if (state.submitting) R.string.auth_signing_in else R.string.auth_sign_in))
            }
        }
        Column(Modifier.weight(0.75f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(stringResource(R.string.auth_keyboard_hint), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.auth_password_note), color = Color(0xFFBDBDBD))
            Button(onClick = back) { Text(stringResource(R.string.auth_back_to_code)) }
        }
    }
}

@Composable
private fun AuthField(value: String, change: (String) -> Unit, label: String, modifier: Modifier = Modifier, password: Boolean = false) {
    var focused by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(label, color = Color(0xFFBDBDBD))
        BasicTextField(
            value = value,
            onValueChange = change,
            singleLine = true,
            visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else KeyboardType.Text),
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 24.sp),
            modifier = modifier.fillMaxWidth().height(56.dp).onFocusChanged { focused = it.isFocused }
                .border(if (focused) 3.dp else 1.dp, if (focused) accent else Color(0xFF656565), RoundedCornerShape(9.dp))
                .background(Color(0xFF303033), RoundedCornerShape(9.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
            decorationBox = { inner -> Box { if (value.isEmpty()) Text(label, color = Color(0xFF8B8B8B)); inner() } },
        )
    }
}

@Composable
private fun ProfileContent(name: String, avatarUrl: String?, onOpenMenu: () -> Unit, logout: () -> Unit) {
    val focus = remember { FocusRequester() }
    var confirmLogout by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { withFrameNanos { }; if (!DrawerBrowsing.active) runCatching { focus.requestFocus() } }
    // The screen title stays top-left; the account itself sits in the middle of the screen.
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterVertically)) {
        Box(Modifier.size(112.dp).clip(RoundedCornerShape(56.dp)).background(accent), contentAlignment = Alignment.Center) {
            Text(name.take(1).uppercase(), fontSize = 54.sp, fontWeight = FontWeight.Bold)
            if (avatarUrl != null) AsyncImage(model = avatarUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
        }
        Text(name, style = MaterialTheme.typography.headlineMedium)
        Button(onClick = { confirmLogout = true }, modifier = Modifier.focusRequester(focus)) { Text(stringResource(R.string.auth_logout)) }
    }
    if (confirmLogout) {
        // Signing out is easy to hit by accident with a remote, so ask first; Cancel is focused.
        AppDialog(onDismiss = { confirmLogout = false }, title = stringResource(R.string.logout_question), width = 480.dp,
            actions = {
                DialogButton(stringResource(R.string.logout_confirm), onClick = { confirmLogout = false; logout() })
                DialogButton(stringResource(R.string.cancel), onClick = { confirmLogout = false }, initialFocus = true)
            })
    }
}
