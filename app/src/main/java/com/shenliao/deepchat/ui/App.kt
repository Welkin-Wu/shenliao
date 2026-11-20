package com.shenliao.deepchat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.shenliao.deepchat.AppViewModel
import com.shenliao.deepchat.ui.math.LocalMathFont
import com.shenliao.deepchat.ui.math.MathFonts
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun AppRoot(vm: AppViewModel) {
    DeepChatTheme(mode = vm.settings.themeMode) {
        CompositionLocalProvider(LocalMathFont provides MathFonts.of(vm.settings.mathFont)) {
            AppRootContent(vm)
        }
    }
}

@Composable
private fun AppRootContent(vm: AppViewModel) {
    // 上次崩过就先给一条自救的路，别一开机就闪退
    val crash = vm.lastCrash
    if (crash != null) {
        CrashScreen(
            trace = crash,
            onReset = { vm.factoryReset() },
            onContinue = { vm.dismissCrash() },
        )
        return
    }

    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showSplash by rememberSaveable { mutableStateOf(true) }
    var showOnboarding by rememberSaveable { mutableStateOf(!vm.settings.ready) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // 定位权限：首次进来自动问一次，设置里也能再申请
    val appContext = androidx.compose.ui.platform.LocalContext.current
    var askLocation by rememberSaveable { mutableStateOf(false) }
    val locationLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        vm.updateSettings(vm.settings.copy(askedLocation = true, withLocation = granted))
        if (granted) {
            com.shenliao.deepchat.util.RuntimeContext.refreshLocation(appContext) {}
        }
    }
    androidx.compose.runtime.LaunchedEffect(vm.loaded) {
        if (vm.loaded && vm.settings.ready && !vm.settings.askedLocation) askLocation = true
    }
    if (askLocation) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                askLocation = false
                vm.updateSettings(vm.settings.copy(askedLocation = true))
            },
            title = { androidx.compose.material3.Text("允许读取大概位置？") },
            text = {
                androidx.compose.material3.Text(
                    "打开后模型能回答天气、附近、当地习俗这类问题。只取城市级位置（约 1 公里），" +
                        "不保存精确坐标，也不用于其他用途。你可以随时在设置里关掉。",
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        askLocation = false
                        locationLauncher.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                    },
                ) { androidx.compose.material3.Text("允许") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        askLocation = false
                        vm.updateSettings(vm.settings.copy(askedLocation = true))
                    },
                ) { androidx.compose.material3.Text("以后再说") }
            },
        )
    }

    if (showSplash) {
        SplashScreen(onFinish = { showSplash = false })
        return
    }

    if (showSettings) {
        // 系统返回键应该回到聊天页，而不是直接退出 App
        BackHandler(enabled = true) { showSettings = false }
        SettingsScreen(
            vm = vm,
            onBack = {
                showSettings = false
                showOnboarding = !vm.settings.ready
            },
        )
        return
    }

    if (showOnboarding && !vm.settings.ready) {
        OnboardingScreen(
            onOpenSettings = { showSettings = true },
            onSkip = { showOnboarding = false },
        )
        return
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(
                conversations = vm.conversations,
                currentId = vm.currentId,
                onNewChat = {
                    scope.launch { drawerState.close() }
                    vm.startNewChat()
                },
                onOpen = { id ->
                    scope.launch { drawerState.close() }
                    vm.openConversation(id)
                },
                onDelete = { id -> vm.deleteConversation(id) },
                onOpenSettings = {
                    scope.launch { drawerState.close() }
                    showSettings = true
                },
            )
        },
    ) {
        ChatScreen(
            vm = vm,
            onOpenDrawer = { scope.launch { drawerState.open() } },
            onOpenSettings = { showSettings = true },
        )
    }
}
