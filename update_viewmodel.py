import re

with open(r'app\src\main\java\com\proxyvpn\MainViewModel.kt', 'r', encoding='utf-8') as f:
    text = f.read()

if "encryptedDns" not in text:
    replacement = """    private val _forceDns = MutableStateFlow(prefs.getBoolean("force_dns", false))
    val forceDns = _forceDns.asStateFlow()
    
    private val _encryptedDns = MutableStateFlow(prefs.getBoolean("encrypted_dns", false))
    val encryptedDns = _encryptedDns.asStateFlow()
    
    fun toggleEncryptedDns() {
        val newVal = !_encryptedDns.value
        _encryptedDns.value = newVal
        prefs.edit().putBoolean("encrypted_dns", newVal).apply()
    }"""
    text = text.replace("""    private val _forceDns = MutableStateFlow(prefs.getBoolean("force_dns", false))
    val forceDns = _forceDns.asStateFlow()""", replacement)

with open(r'app\src\main\java\com\proxyvpn\MainViewModel.kt', 'w', encoding='utf-8') as f:
    f.write(text)
