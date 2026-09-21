$url_arm64 = "https://github.com/XTLS/Xray-core/releases/download/v26.3.27/Xray-linux-arm64-v8a.zip"
$url_x86_64 = "https://github.com/XTLS/Xray-core/releases/download/v26.3.27/Xray-linux-64.zip"

Invoke-WebRequest -Uri $url_arm64 -OutFile "xray_arm64.zip"
tar -xf xray_arm64.zip xray
Move-Item -Path "xray" -Destination "app\src\main\jniLibs\arm64-v8a\libxray.so" -Force

Invoke-WebRequest -Uri $url_x86_64 -OutFile "xray_x64.zip"
tar -xf xray_x64.zip xray
Move-Item -Path "xray" -Destination "app\src\main\jniLibs\x86_64\libxray.so" -Force

Remove-Item "xray_arm64.zip", "xray_x64.zip" -Force
