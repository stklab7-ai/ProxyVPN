package com.proxyvpn

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.proxyvpn.tunnel.TunnelVpnService
import androidx.lifecycle.viewmodel.compose.viewModel

val DarkBg = Color(0xFF0D1117)
val CardBg = Color(0xFF161B22)
val TextPrimary = Color(0xFF58A6FF)
val TextSecondary = Color(0xFF8B949E)
val DividerColor = Color(0xFF30363D)
val WarningBg = Color(0xFF2D2200)
val WarningText = Color(0xFFD29922)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = DarkBg, surface = CardBg)) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    FreeProxyApp()
                }
            }
        }
    }
}


@Composable
fun FreeProxyApp(viewModel: MainViewModel = viewModel()) {
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = CardBg, modifier = Modifier.height(56.dp)) {
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.List, contentDescription = "Прокси") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    alwaysShowLabel = false
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Settings, contentDescription = "Настройки") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    alwaysShowLabel = false
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            if (selectedTab == 0) {
                ProxyListScreen(viewModel)
            } else {
                SettingsScreen(viewModel)
            }
        }
    }
}

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
            val forceDns by viewModel.forceDns.collectAsState()
        val encryptedDns by viewModel.encryptedDns.collectAsState()
    val byedpi by viewModel.byedpi.collectAsState()
    
    var showAddDialog by remember { mutableStateOf(false) }
    var customIp by remember { mutableStateOf("") }
    var customPort by remember { mutableStateOf("") }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Добавить свой прокси") },
            text = {
                Column {
                    OutlinedTextField(
                        value = customIp,
                        onValueChange = { customIp = it },
                        label = { Text("IP адрес") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customPort,
                        onValueChange = { customPort = it },
                        label = { Text("Порт (SOCKS5)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val p = customPort.toIntOrNull()
                    if (customIp.isNotBlank() && p != null) {
                        viewModel.addCustomProxy(customIp, p)
                        showAddDialog = false
                        customIp = ""
                        customPort = ""
                    }
                }) {
                    Text("Добавить")
                }
            },
            dismissButton = {
                Button(onClick = { showAddDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    val customDns by viewModel.customDns.collectAsState()
    var editDns by remember { mutableStateOf(customDns) }


    val customMtu by viewModel.customMtu.collectAsState()
    var editMtu by remember { mutableStateOf(customMtu) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Настройки", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Button(onClick = { showAddDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636))) {
                Text("+ Добавить прокси")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        
        ElevatedCard(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Размер пакета (MTU)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Уменьшите до 1280 или 1300, если мобильный интернет сильно режет скорость.", color = Color.Gray, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = editMtu,
                    onValueChange = { 
                        editMtu = it
                        viewModel.setCustomMtu(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }

        
        ElevatedCard(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Свой DNS сервер (IP)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Если нужно использовать специфический DNS (например для Xbox).", color = Color.Gray, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = editDns,
                    onValueChange = { 
                        editDns = it
                        viewModel.setCustomDns(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }


        ElevatedCard(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Перехват DNS (Force DNS)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Блокирует Private DNS Android (порт 853), заставляя систему использовать обычный DNS внутри туннеля.", color = Color.Gray, fontSize = 12.sp)
                }
                Switch(checked = forceDns, onCheckedChange = { viewModel.toggleForceDns() })
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Шифрованный DNS (DoH)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Перехват DNS-запросов на уровне IP и отправка по HTTPS.", color = Color.Gray, fontSize = 12.sp)
                }
                Switch(checked = encryptedDns, onCheckedChange = { viewModel.toggleEncryptedDns() })
            }
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Обход ТСПУ (ByeDPI)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Фрагментация TLS-пакетов перед отправкой в туннель (в разработке).", color = Color.Gray, fontSize = 12.sp)
                }
                Switch(checked = byedpi, onCheckedChange = { viewModel.toggleByeDpi() })
            }
        }

    val byedpiArgs by viewModel.byedpiArgs.collectAsState()
    var editByedpiArgs by remember { mutableStateOf(byedpiArgs) }

    if (byedpi) {
        ElevatedCard(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Параметры ciadpi", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = editByedpiArgs,
                    onValueChange = { 
                        editByedpiArgs = it
                        viewModel.setByedpiArgs(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3, maxLines = 5
                )
            }
        }
    }












    }
}




@Composable
fun ProxyListScreen(viewModel: MainViewModel) {
    val proxies by viewModel.proxies.collectAsState()
    val isLoading by viewModel.isLoadingProxies.collectAsState()
    val isVpnActive by viewModel.isVpnActive.collectAsState()
    val selectedProxy by viewModel.selectedProxy.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    val vpnLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            selectedProxy?.let { proxy ->
                startVpnService(context, proxy.ip, proxy.port, proxy.method, proxy.password)
                viewModel.setVpnActive(true)
            }
        }
    }

    var showFavorites by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val displayedProxies = proxies.filter { !showFavorites || it.isFavorite }

    val toggleVpnMain: () -> Unit = {
        if (isVpnActive) {
            stopVpnService(context)
            viewModel.setVpnActive(false)
        } else {
            val proxyToStart = selectedProxy ?: displayedProxies.firstOrNull()
            if (proxyToStart != null) {
                if (selectedProxy != proxyToStart) {
                    viewModel.selectProxy(proxyToStart)
                }
                val intent = android.net.VpnService.prepare(context)
                if (intent != null) {
                    vpnLauncher.launch(intent)
                } else {
                    startVpnService(context, proxyToStart.ip, proxyToStart.port, proxyToStart.method, proxyToStart.password)
                    viewModel.setVpnActive(true)
                }
            } else {
                android.widget.Toast.makeText(context, "Нет доступных серверов", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    val selectProxyFromList: (com.proxyvpn.ProxyItem) -> Unit = { proxy ->
        viewModel.selectProxy(proxy)
        if (isVpnActive) {
            stopVpnService(context)
            val intent = android.net.VpnService.prepare(context)
            if (intent != null) {
                vpnLauncher.launch(intent)
            } else {
                startVpnService(context, proxy.ip, proxy.port, proxy.method, proxy.password)
                viewModel.setVpnActive(true)
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.fetchProxies(forceRefresh = false)
    }

    androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
        androidx.compose.foundation.layout.Column(modifier = androidx.compose.ui.Modifier.fillMaxSize().padding(16.dp)) {

            androidx.compose.material3.Text(
                text = "ProxyVPN V5",
                fontSize = 28.sp,
                color = TextPrimary,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                modifier = androidx.compose.ui.Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 24.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            // Big Connect Button
            androidx.compose.foundation.layout.Box(
                modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier
                        .size(160.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = if (isVpnActive) listOf(
                                    androidx.compose.ui.graphics.Color(0xFFEF5350), 
                                    androidx.compose.ui.graphics.Color(0xFFC62828)
                                ) else listOf(
                                    androidx.compose.ui.graphics.Color(0xFF66BB6A),
                                    androidx.compose.ui.graphics.Color(0xFF2E7D32)
                                )
                            )
                        )
                        .border(
                            width = 4.dp,
                            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.2f),
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                        .clickable { toggleVpnMain() },
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    androidx.compose.material3.Text(
                        text = if (isVpnActive) "ОТКЛЮЧИТЬ" else "АВТО-ВЫБОР",
                        color = androidx.compose.ui.graphics.Color.White,
                        fontSize = 18.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }
            }

            androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.height(24.dp))

            androidx.compose.material3.Text(
                text = if (isVpnActive) "Статус: Подключено" else "Статус: Отключено",
                color = if (isVpnActive) androidx.compose.ui.graphics.Color(0xFF4CAF50) else TextSecondary,
                fontSize = 16.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.height(8.dp))

            val currentProxyName = selectedProxy?.let { 
                if (it.method == "VLESS") "VLESS Cloudflare" else "${it.ip}:${it.port}" 
            } ?: "Не выбран"

            androidx.compose.material3.Text(
                text = "Текущий сервер: $currentProxyName",
                color = TextSecondary,
                fontSize = 14.sp,
                modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.height(24.dp))

            if (isLoading) {
                androidx.compose.material3.LinearProgressIndicator(
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    color = TextPrimary
                )
            }

            androidx.compose.foundation.layout.Row(
                modifier = androidx.compose.ui.Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                androidx.compose.material3.Text(
                    text = "Список серверов (${displayedProxies.size})",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                androidx.compose.foundation.layout.Row {
                    androidx.compose.material3.Button(
                        onClick = { showFavorites = false },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = if (!showFavorites) androidx.compose.ui.graphics.Color(0xFF238636) else androidx.compose.ui.graphics.Color.Transparent),
                        modifier = androidx.compose.ui.Modifier.height(32.dp).padding(end = 4.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) { androidx.compose.material3.Text("Все", color = androidx.compose.ui.graphics.Color.White, fontSize = 12.sp) }
                    
                    androidx.compose.material3.Button(
                        onClick = { showFavorites = true },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = if (showFavorites) androidx.compose.ui.graphics.Color(0xFF238636) else androidx.compose.ui.graphics.Color.Transparent),
                        modifier = androidx.compose.ui.Modifier.height(32.dp).padding(end = 4.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) { androidx.compose.material3.Text("Избранное", color = androidx.compose.ui.graphics.Color.White, fontSize = 12.sp) }

                    androidx.compose.material3.Button(
                        onClick = { viewModel.fetchProxies(forceRefresh = true) },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF1F2937)),
                        modifier = androidx.compose.ui.Modifier.height(32.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) { androidx.compose.material3.Text("Обновить", color = androidx.compose.ui.graphics.Color.White, fontSize = 12.sp) }
                }
            }

            androidx.compose.foundation.lazy.LazyColumn(modifier = androidx.compose.ui.Modifier.weight(1f)) {
                items(displayedProxies.size) { index ->
                    val proxy = displayedProxies[index]
                    val isSelected = proxy == selectedProxy

                    androidx.compose.foundation.layout.Row(
                        modifier = androidx.compose.ui.Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                            .background(if (isSelected) androidx.compose.ui.graphics.Color(0xFF1F2937) else CardBg)
                            .clickable { selectProxyFromList(proxy) }
                            .padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 16.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        androidx.compose.foundation.layout.Column(modifier = androidx.compose.ui.Modifier.weight(1f)) {
                            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                androidx.compose.material3.Text(
                                    text = getFlagEmoji(proxy.country),
                                    fontSize = 18.sp,
                                    modifier = androidx.compose.ui.Modifier.padding(end = 8.dp)
                                )
                                androidx.compose.material3.Text(
                                    text = if (proxy.method == "VLESS") "VLESS Cloudflare" else "${proxy.ip}:${proxy.port}",
                                    color = if (isSelected) androidx.compose.ui.graphics.Color(0xFF66BB6A) else androidx.compose.ui.graphics.Color(0xFFF0F6FC),
                                    fontSize = 15.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Medium
                                )
                            }
                            androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.height(6.dp))
                            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                androidx.compose.material3.Text(
                                    text = if (proxy.method == "VLESS") "CF Worker" else "${proxy.country} | SOCKS5",
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                                androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.width(12.dp))
                                androidx.compose.material3.Icon(
                                    painter = androidx.compose.ui.res.painterResource(android.R.drawable.presence_online),
                                    contentDescription = "Ping",
                                    tint = if (proxy.ping in 1..200) androidx.compose.ui.graphics.Color(0xFF66BB6A) 
                                           else if (proxy.ping in 201..500) androidx.compose.ui.graphics.Color(0xFFFFCA28) 
                                           else androidx.compose.ui.graphics.Color(0xFFEF5350),
                                    modifier = androidx.compose.ui.Modifier.size(10.dp).padding(end = 4.dp)
                                )
                                androidx.compose.material3.Text(
                                    text = if (proxy.ping > 0) "${proxy.ping} ms" else "-- ms",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        androidx.compose.material3.IconButton(
                            onClick = {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("tg://proxy?server=${proxy.ip}&port=${proxy.port}&type=socks5"))
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "Telegram не установлен", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = androidx.compose.ui.Modifier.size(36.dp)
                        ) {
                            androidx.compose.material3.Icon(
                                painter = androidx.compose.ui.res.painterResource(id = com.proxyvpn.R.drawable.ic_telegram),
                                contentDescription = "Telegram",
                                tint = androidx.compose.ui.graphics.Color.Unspecified,
                                modifier = androidx.compose.ui.Modifier.padding(4.dp)
                            )
                        }

                        androidx.compose.material3.IconButton(
                            onClick = { viewModel.toggleFavorite(proxy) },
                            modifier = androidx.compose.ui.Modifier.size(36.dp)
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = if (proxy.isFavorite) androidx.compose.material.icons.Icons.Filled.Star else androidx.compose.material.icons.Icons.Outlined.Star,
                                contentDescription = "Favorite",
                                tint = if (proxy.isFavorite) androidx.compose.ui.graphics.Color(0xFFFFD700) else TextSecondary,
                                modifier = androidx.compose.ui.Modifier.padding(6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun ToolsScreen(viewModel: MainViewModel) {
    val currentIp by viewModel.currentIp.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ElevatedCard(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Детектор Утечек (Geo Check)", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Проверьте, какой IP-адрес видит интернет. При включенном туннеле здесь должен отображаться IP прокси-сервера.", color = Color.Gray, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(16.dp))
                
                if (currentIp != null) {
                    Text("Ваш IP: $currentIp", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                Button(onClick = { viewModel.checkMyIp() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Узнать мой IP")
                }
            }
        }
    }
}

private fun startVpnService(context: Context, ip: String, port: Int, method: String? = null, password: String? = null) {
    val intent = Intent(context, TunnelVpnService::class.java).apply {
        action = TunnelVpnService.ACTION_START
        putExtra(TunnelVpnService.EXTRA_PROXY_IP, ip)
        putExtra(TunnelVpnService.EXTRA_PROXY_PORT, port)
        putExtra("method", method)
        putExtra("password", password)
    }
    context.startService(intent)
}

private fun stopVpnService(context: Context) {
    val intent = Intent(context, TunnelVpnService::class.java).apply {
        action = TunnelVpnService.ACTION_STOP
    }
    context.startService(intent)
}

fun getFlagEmoji(country: String): String {
    if (country == "Global" || country.length < 2) return "🌍"
    if (country.length == 2) {
        val c = country.uppercase()
        val first = c[0].code - 0x41 + 0x1F1E6
        val second = c[1].code - 0x41 + 0x1F1E6
        return String(Character.toChars(first)) + String(Character.toChars(second))
    }
    return "🌍"
}

@Composable
fun AppExceptionsDialog(onDismiss: () -> Unit, context: android.content.Context) {
    val pm = context.packageManager
    val packages = androidx.compose.runtime.remember {
        pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .sortedBy { it.loadLabel(pm).toString() }
    }
    val prefs = context.getSharedPreferences("vpn_settings", android.content.Context.MODE_PRIVATE)
    val bypassed = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateListOf<String>().apply { addAll(prefs.getStringSet("bypassed_apps", setOf()) ?: setOf()) } }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { androidx.compose.material3.Text("Исключения (не через VPN)") },
        text = {
            androidx.compose.foundation.lazy.LazyColumn {
                items(packages.size) { index ->
                    val app = packages[index]
                    val isChecked = bypassed.contains(app.packageName)
                    androidx.compose.foundation.layout.Row(
                        modifier = androidx.compose.ui.Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isChecked) bypassed.remove(app.packageName) else bypassed.add(app.packageName)
                                prefs.edit().putStringSet("bypassed_apps", bypassed.toSet()).apply()
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.Checkbox(
                            checked = isChecked,
                            onCheckedChange = null
                        )
                        androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.width(8.dp))
                        androidx.compose.material3.Text(text = app.loadLabel(pm).toString(), fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { androidx.compose.material3.Text("Готово") }
        }
    )
}
