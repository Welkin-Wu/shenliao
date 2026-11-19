package com.shenliao.deepchat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.shenliao.deepchat.AppViewModel
import com.shenliao.deepchat.data.Settings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val s = vm.settings
    val clipboard = LocalClipboardManager.current

    var baseUrl by remember { mutableStateOf(s.baseUrl) }
    var apiKey by remember { mutableStateOf(s.apiKey) }
    var model by remember { mutableStateOf(s.model) }
    var systemPrompt by remember { mutableStateOf(s.systemPrompt) }
    var maxTokensText by remember { mutableStateOf(if (s.maxTokens > 0) s.maxTokens.toString() else "") }

    var keyVisible by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = AppColors.Bg,
        topBar = {
            TopAppBar(
                title = { Text("设置", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = AppColors.TextDim)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppColors.Bg,
                    titleContentColor = AppColors.TextMain,
                ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard(title = "外观", subtitle = "白天用浅色，夜里用深色；跟随系统会随手机的深色模式切换") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallChip(
                            selected = s.themeMode == THEME_SYSTEM,
                            onClick = { vm.updateSettings(vm.settings.copy(themeMode = THEME_SYSTEM)) },
                            icon = Icons.Outlined.Bolt,
                            label = "跟随系统",
                        )
                        SmallChip(
                            selected = s.themeMode == THEME_LIGHT,
                            onClick = { vm.updateSettings(vm.settings.copy(themeMode = THEME_LIGHT)) },
                            icon = Icons.Outlined.Bolt,
                            label = "浅色",
                        )
                        SmallChip(
                            selected = s.themeMode == THEME_DARK,
                            onClick = { vm.updateSettings(vm.settings.copy(themeMode = THEME_DARK)) },
                            icon = Icons.Outlined.Bolt,
                            label = "深色",
                        )
                    }
                }
            }

            item {
                SectionCard(
                    title = "接入方式",
                    subtitle = "请求从这台手机直连你填的地址，不经过任何中转服务器",
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SmallChip(
                            selected = s.preset == Settings.PRESET_DEEPSEEK,
                            onClick = {
                                baseUrl = Settings.DEFAULT_BASE_URL
                                model = Settings.DEFAULT_MODEL
                                vm.updateSettings(
                                    vm.settings.copy(
                                        preset = Settings.PRESET_DEEPSEEK,
                                        baseUrl = baseUrl,
                                        model = model,
                                    ),
                                )
                            },
                            icon = Icons.Outlined.Bolt,
                            label = "DeepSeek 官方",
                        )
                        Spacer(Modifier.width(8.dp))
                        SmallChip(
                            selected = s.preset == Settings.PRESET_CUSTOM,
                            onClick = {
                                vm.updateSettings(vm.settings.copy(preset = Settings.PRESET_CUSTOM))
                            },
                            icon = Icons.Outlined.Bolt,
                            label = "自定义",
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    FieldLabel("Base URL")
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = {
                            baseUrl = it
                            vm.updateSettings(vm.settings.copy(baseUrl = it))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("https://api.deepseek.com", color = AppColors.TextFaint, fontSize = 13.sp) },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                        colors = fieldColors(),
                    )

                    Spacer(Modifier.height(10.dp))
                    FieldLabel("API Key")
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = {
                            apiKey = it
                            vm.updateSettings(vm.settings.copy(apiKey = it))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("sk-…", color = AppColors.TextFaint, fontSize = 13.sp) },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                        visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { keyVisible = !keyVisible }) {
                                Icon(
                                    if (keyVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (keyVisible) "隐藏" else "显示",
                                    tint = AppColors.TextFaint,
                                )
                            }
                        },
                        colors = fieldColors(),
                    )

                    Spacer(Modifier.height(10.dp))
                    FieldLabel("模型")
                    OutlinedTextField(
                        value = model,
                        onValueChange = {
                            model = it
                            vm.updateSettings(vm.settings.copy(model = it))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                        colors = fieldColors(),
                    )
                    if (s.preset == Settings.PRESET_DEEPSEEK) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Settings.DEEPSEEK_MODELS.forEach { (id, label) ->
                                SmallChip(
                                    selected = model == id,
                                    onClick = {
                                        model = id
                                        vm.updateSettings(vm.settings.copy(model = id))
                                    },
                                    icon = Icons.Outlined.Bolt,
                                    label = label,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(AppColors.PrimaryDim)
                                .clickable {
                                    if (!testing) {
                                        testing = true
                                        testResult = null
                                        vm.testConnection { msg ->
                                            testResult = msg
                                            testing = false
                                        }
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                        ) {
                            Text(
                                text = if (testing) "正在测试…" else "测试连接",
                                fontSize = 13.sp,
                                color = AppColors.Primary,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        testResult?.let {
                            Text(it, fontSize = 12.sp, color = AppColors.TextDim)
                        }
                    }
                }
            }

            item {
                SectionCard(title = "回答方式") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("思考模式", fontSize = 14.sp, color = AppColors.TextMain)
                            Text(
                                "先推理再回答，更准但慢一点",
                                fontSize = 11.sp,
                                color = AppColors.TextFaint,
                            )
                        }
                        Switch(
                            checked = s.thinking,
                            onCheckedChange = { vm.updateSettings(vm.settings.copy(thinking = it)) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AppColors.Primary,
                                uncheckedTrackColor = AppColors.SurfaceHigh,
                            ),
                        )
                    }

                    if (s.thinking) {
                        Spacer(Modifier.height(12.dp))
                        FieldLabel("推理强度")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("low" to "低", "high" to "高", "max" to "最高").forEach { (id, label) ->
                                SmallChip(
                                    selected = s.effort == id,
                                    onClick = { vm.updateSettings(vm.settings.copy(effort = id)) },
                                    icon = Icons.Outlined.Bolt,
                                    label = label,
                                )
                            }
                        }
                    } else {
                        Spacer(Modifier.height(12.dp))
                        FieldLabel("温度 ${"%.2f".format(s.temperature)}")
                        Slider(
                            value = s.temperature,
                            onValueChange = { vm.updateSettings(vm.settings.copy(temperature = it)) },
                            valueRange = 0f..2f,
                            steps = 19,
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    FieldLabel("最大输出 tokens（留空 = 模型默认）")
                    OutlinedTextField(
                        value = maxTokensText,
                        onValueChange = {
                            maxTokensText = it
                            vm.updateSettings(vm.settings.copy(maxTokens = it.toIntOrNull() ?: 0))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("例如 4096", color = AppColors.TextFaint, fontSize = 13.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                        colors = fieldColors(),
                    )

                    Spacer(Modifier.height(12.dp))
                    FieldLabel("系统提示词（可选）")
                    OutlinedTextField(
                        value = systemPrompt,
                        onValueChange = {
                            systemPrompt = it
                            vm.updateSettings(vm.settings.copy(systemPrompt = it))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 6,
                        placeholder = { Text("例如：你是一个简洁的技术助手，用中文回答。", color = AppColors.TextFaint, fontSize = 13.sp) },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
                        colors = fieldColors(),
                    )
                }
            }

            item {
                SectionCard(
                    title = "联网",
                    subtitle = "由 DeepSeek 服务端执行，按成功请求计费；只在官方接口 + deepseek-flash 下生效",
                ) {
                    ToggleRow(
                        title = "联网搜索",
                        desc = if (s.webSearch) "模型自己判断要不要搜，回答会带引用来源" else "只用训练数据回答，不联网",
                        checked = s.webSearch,
                        onChange = { vm.updateSettings(vm.settings.copy(webSearch = it)) },
                    )
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = AppColors.Border.copy(alpha = 0.5f))
                    Spacer(Modifier.height(10.dp))
                    ToggleRow(
                        title = "阅读链接（关联网页）",
                        desc = if (s.readUrl) "消息里贴了网址，模型会自己去读那一页" else "贴了网址也不读，只按文字回答",
                        checked = s.readUrl,
                        onChange = { vm.updateSettings(vm.settings.copy(readUrl = it)) },
                    )
                }
            }

            item {
                SectionCard(
                    title = "公式字体",
                    subtitle = "内置两款 OFL 开源数学字体，实际渲染用它们，不再是系统黑体",
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallChip(
                            selected = s.mathFont != "lm",
                            onClick = { vm.updateSettings(vm.settings.copy(mathFont = "stix")) },
                            icon = Icons.Outlined.Bolt,
                            label = "STIX 期刊风",
                        )
                        SmallChip(
                            selected = s.mathFont == "lm",
                            onClick = { vm.updateSettings(vm.settings.copy(mathFont = "lm")) },
                            icon = Icons.Outlined.Bolt,
                            label = "Latin Modern 教科书风",
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "STIX 笔画更粗，深色背景和小字号下更清楚；Latin Modern 就是 LaTeX 默认那套字形。切换后已显示的公式会重新排版。",
                        fontSize = 11.sp,
                        lineHeight = 17.sp,
                        color = AppColors.TextFaint,
                    )
                }
            }

            item {
                SectionCard(
                    title = "文件解析",
                    subtitle = "PDF / Word / PPT 会上传解析（按页计费）；txt、md、csv、xlsx 免费",
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallChip(
                            selected = s.docMode == Settings.DOC_MODE_TEXT,
                            onClick = {
                                vm.updateSettings(vm.settings.copy(docMode = Settings.DOC_MODE_TEXT))
                            },
                            icon = Icons.Outlined.Description,
                            label = "只读文字",
                        )
                        SmallChip(
                            selected = s.docMode == Settings.DOC_MODE_RICH,
                            onClick = {
                                vm.updateSettings(vm.settings.copy(docMode = Settings.DOC_MODE_RICH))
                            },
                            icon = Icons.Outlined.Description,
                            label = "含图表",
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (s.docMode == Settings.DOC_MODE_RICH) {
                            "文档里的图表会被裁出来一起给模型，适合 PPT 和报告，token 消耗更大。"
                        } else {
                            "只把文字给模型，最省钱。图表类问题切线「含图表」。"
                        },
                        fontSize = 11.sp,
                        lineHeight = 17.sp,
                        color = AppColors.TextFaint,
                    )
                }
            }

            item {
                SectionCard(title = "数据") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("本地对话", fontSize = 14.sp, color = AppColors.TextMain)
                            Text(
                                "${vm.conversations.size} 段对话，全部存在手机里",
                                fontSize = 11.sp,
                                color = AppColors.TextFaint,
                            )
                        }
                        TextButton(onClick = { confirmClear = true }) {
                            Text("清空", color = AppColors.Danger, fontSize = 13.sp)
                        }
                    }
                }
            }

            item {
                SectionCard(
                    title = "诊断",
                    subtitle = "闪退后这里会留下堆栈，复制发我就能定位；也可以直接清掉",
                ) {
                    val crash = vm.lastCrash
                    if (crash.isNullOrBlank()) {
                        Text(
                            "没有崩溃记录",
                            fontSize = 13.sp,
                            color = AppColors.TextDim,
                        )
                    } else {
                        Text(
                            text = crash.take(1200),
                            fontSize = 10.sp,
                            lineHeight = 15.sp,
                            fontFamily = FontFamily.Monospace,
                            color = AppColors.CodeText,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(AppColors.CodeBg)
                                .padding(10.dp)
                                .verticalScroll(rememberScrollState()),
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SmallChip(
                                selected = false,
                                onClick = { clipboard.setText(AnnotatedString(crash)) },
                                icon = Icons.Outlined.ContentCopy,
                                label = "复制堆栈",
                            )
                            SmallChip(
                                selected = false,
                                onClick = { vm.dismissCrash() },
                                icon = Icons.Outlined.DeleteOutline,
                                label = "清除记录",
                            )
                        }
                    }
                }
            }

            item {
                SectionCard(title = "关于") {
                    Text(
                        "DeepChat 1.6.1",
                        fontSize = 13.sp,
                        color = AppColors.TextMain,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "一个自带油箱的 DeepSeek 手机客户端：API Key 在你自己手里，对话只存在本机，换模型只要改两个输入框。\n\n图片会压缩到长边 1400px 再上传，长对话里超过 8 张的旧图会自动省略以节省 token。",
                        fontSize = 12.sp,
                        lineHeight = 19.sp,
                        color = AppColors.TextDim,
                    )
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空所有对话？", fontSize = 16.sp) },
            text = { Text("本机保存的全部对话和图片都会被删除，无法恢复。", fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearAllConversations()
                    confirmClear = false
                }) { Text("清空", color = AppColors.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消") }
            },
            containerColor = AppColors.Surface,
            titleContentColor = AppColors.TextMain,
            textContentColor = AppColors.TextDim,
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.Surface)
            .padding(14.dp),
    ) {
        Text(title, fontSize = 13.sp, color = AppColors.TextDim, fontWeight = FontWeight.Medium)
        if (subtitle != null) {
            Text(
                subtitle,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = AppColors.TextFaint,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun ToggleRow(
    title: String,
    desc: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = AppColors.TextMain)
            Text(desc, fontSize = 11.sp, lineHeight = 16.sp, color = AppColors.TextFaint)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AppColors.Primary,
                uncheckedTrackColor = AppColors.SurfaceHigh,
            ),
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        color = AppColors.TextFaint,
        modifier = Modifier.padding(bottom = 5.dp),
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = AppColors.SurfaceHigh,
    unfocusedContainerColor = AppColors.SurfaceHigh,
    focusedBorderColor = AppColors.Primary.copy(alpha = 0.55f),
    unfocusedBorderColor = AppColors.Border,
    cursorColor = AppColors.Primary,
    focusedTextColor = AppColors.TextMain,
    unfocusedTextColor = AppColors.TextMain,
)
