::rem---------------------------------------------遍历目录和文件..^| find /i ".%2"
@echo off
for /f %%s in ('dir /b %1')do (
adb push %1/%%s %2
)
