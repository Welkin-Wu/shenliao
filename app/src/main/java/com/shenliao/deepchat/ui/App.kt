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
    var showOnboarding by rememberSaveable { mutableStateOf(!vm.settings.ready) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

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
