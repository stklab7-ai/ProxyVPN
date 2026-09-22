import re

with open(r'app\src\main\java\com\proxyvpn\MainActivity.kt', 'r', encoding='utf-8') as f:
    text = f.read()

target = """                Switch(checked = forceDns, onCheckedChange = { viewModel.toggleForceDns() })
            }
        }"""
        
replacement = """                Switch(checked = forceDns, onCheckedChange = { viewModel.toggleForceDns() })
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
        }"""
        
if "Шифрованный DNS" not in text:
    text = text.replace(target, replacement)

with open(r'app\src\main\java\com\proxyvpn\MainActivity.kt', 'w', encoding='utf-8') as f:
    f.write(text)
