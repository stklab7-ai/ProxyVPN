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
    val context = LocalContext.current

    val vpnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            selectedProxy?.let { proxy ->
                startVpnService(context, proxy.ip, proxy.port, proxy.method, proxy.password)
                viewModel.setVpnActive(true)
            }
        }
    }

    var showFavorites by remember { mutableStateOf(false) }
    val displayedProxies = proxies.filter { !showFavorites || it.isFavorite }

    val toggleVpn: (ProxyItem) -> Unit = { proxy ->
        if (isVpnActive && proxy == selectedProxy) {
            stopVpnService(context)
            viewModel.setVpnActive(false)
        } else {
            viewModel.selectProxy(proxy)
            val intent = VpnService.prepare(context)
            if (intent != null) {
                vpnLauncher.launch(intent)
            } else {
                startVpnService(context, proxy.ip, proxy.port, proxy.method, proxy.password)
                viewModel.setVpnActive(true)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.fetchProxies(forceRefresh = false)
    }


    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

        Text(
            text = "FreeProxy",
            fontSize = 24.sp,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 16.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardBg)
                .padding(12.dp)
                .padding(bottom = 12.dp)
        ) {
            Text(
                text = if (isVpnActive) "VPN Активен: ${selectedProxy?.ip}:${selectedProxy?.port}" else if (isLoading) "Поиск прокси..." else "Готов к работе",
                color = if (isVpnActive) Color(0xFF4CAF50) else TextSecondary,
                fontSize = 14.sp
            )
        }

        if (isLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                color = TextPrimary
            )
        } else {
            Spacer(modifier = Modifier.height(15.dp))
        }

        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Button(
                onClick = { viewModel.fetchProxies(forceRefresh = true) },
                modifier = Modifier.weight(1f).height(44.dp).padding(end = 4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("🔍 SOCKS5", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Прокси (${displayedProxies.size})",
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Row {
                Button(
                    onClick = { showFavorites = false },
                    colors = ButtonDefaults.buttonColors(containerColor = if (!showFavorites) Color(0xFF238636) else Color.Transparent),
                    modifier = Modifier.height(32.dp)
                ) { Text("Все", color = Color.White, fontSize = 12.sp) }
                Button(
                    onClick = { showFavorites = true },
                    colors = ButtonDefaults.buttonColors(containerColor = if (showFavorites) Color(0xFF238636) else Color.Transparent),
                    modifier = Modifier.height(32.dp)
                ) { Text("Избранные", color = Color.White, fontSize = 12.sp) }
            }
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(displayedProxies.size) { index ->
                val proxy = displayedProxies[index]
                val isRunningThisProxy = isVpnActive && proxy == selectedProxy

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isRunningThisProxy) Color(0xFF1F2937) else Color.Transparent)
                        .clickable { toggleVpn(proxy) }
                        .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (proxy.method == "VLESS") "VLESS Cloudflare" else "${proxy.ip}:${proxy.port}",
                            color = if (isRunningThisProxy) Color(0xFF4CAF50) else Color(0xFFF0F6FC),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (proxy.method == "VLESS") "CF Worker" else "${proxy.country} | SOCKS5",
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
                    }

                    IconButton(
                        onClick = { viewModel.toggleFavorite(proxy) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (proxy.isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                            contentDescription = "Favorite",
                            tint = if (proxy.isFavorite) Color(0xFFFFD700) else TextSecondary,
                            modifier = Modifier.padding(6.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("tg://proxy?server=${proxy.ip}&port=${proxy.port}&type=socks5"))
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Telegram не установлен", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_telegram),
                            contentDescription = "Telegram",
                            tint = Color.Unspecified,
                            modifier = Modifier.padding(6.dp)
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
