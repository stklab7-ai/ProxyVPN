$url_arm64 = "https://github.com/shadowsocks/shadowsocks-rust/releases/download/v1.25.0/shadowsocks-v1.25.0.aarch64-linux-android.tar.xz"
$url_x86_64 = "https://github.com/shadowsocks/shadowsocks-rust/releases/download/v1.25.0/shadowsocks-v1.25.0.x86_64-linux-android.tar.xz"

New-Item -ItemType Directory -Force -Path "app\src\main\jniLibs\arm64-v8a"
New-Item -ItemType Directory -Force -Path "app\src\main\jniLibs\x86_64"

Invoke-WebRequest -Uri $url_arm64 -OutFile "ss_arm64.tar.xz"
tar -xf ss_arm64.tar.xz sslocal
Move-Item -Path "sslocal" -Destination "app\src\main\jniLibs\arm64-v8a\libsslocal.so" -Force

Invoke-WebRequest -Uri $url_x86_64 -OutFile "ss_x86_64.tar.xz"
tar -xf ss_x86_64.tar.xz sslocal
Move-Item -Path "sslocal" -Destination "app\src\main\jniLibs\x86_64\libsslocal.so" -Force

Remove-Item "ss_arm64.tar.xz", "ss_x86_64.tar.xz" -Force
