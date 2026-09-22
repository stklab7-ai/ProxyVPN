## Окружение и shell

**Платформа:** Windows 10/11.
**Shell по умолчанию:** PowerShell (не cmd, не bash, не Git Bash).
**Кодировка консоли:** русский текст может отображаться как кракозябры (CP866).

### Жёсткие правила

1. **Запрещено использовать cmd-команды.** `call`, `cat` (как cmd-команда), `type`, `dir` без параметров, `copy`, `del`, `set VAR=value`, `%VAR%` — работают иначе или не существуют в PowerShell. Используй эквиваленты:

   | Нельзя (cmd) | Нужно (PowerShell) |
   |---|---|
   | `call program.exe` | `& "C:\path\program.exe"` или `Start-Process` |
   | `type file.txt` | `Get-Content file.txt` |
   | `dir` | `Get-ChildItem` |
   | `copy a b` | `Copy-Item a b` |
   | `del file` | `Remove-Item file` |
   | `set VAR=value` | `$env:VAR = "value"` |
   | `%VAR%` | `$env:VAR` |

2. **Запрещено использовать bash-команды.** `grep`, `sed`, `awk`, `find`, `cat file | grep` — не работают. Используй:
   - `Select-String` вместо `grep`
   - `-replace` вместо `sed`
   - `Where-Object` вместо `awk`
   - `Get-ChildItem -Recurse` вместо `find`

3. **Пути с пробелами и кириллицей — только в кавычках.** Пример:
   ```powershell
   & "C:\Program Files\Java\bin\kotlinc.bat" ChecksumTest.kt -include-runtime -d ChecksumTest.jar
   ```
