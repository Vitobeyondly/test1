@echo off
chcp 65001 >nul
set "ROOT=%~dp0.."
set "BIN=%ROOT%\zapret-winws"
set "FILES=%ROOT%zapret-winws\files\"
set "LISTS=%ROOT%\lists"
if not defined GAMEFILTERTCP set "GAMEFILTERTCP=12"
if not defined GAMEFILTERUDP set "GAMEFILTERUDP=12"
cd /d "%BIN%"

start "zapret1: %~n0" /min "%BIN%\winws.exe" ^
--wf-tcp=80,443,2053,2083,2087,2096,8443,%GAMEFILTERTCP% ^
--wf-udp=443,19294-19344,50000-50100,%GAMEFILTERUDP% ^
--filter-udp=443 --hostlist="%LISTS%\list-youtube.txt" --dpi-desync=fake --dpi-desync-repeats=11 --dpi-desync-fake-quic="%FILES%\quic_initial_www_google_com.bin" --new ^
--filter-udp=19294-19344,50000-50100 --filter-l7=discord,stun --dpi-desync=fake --dpi-desync-fake-discord="%FILES%\ACTIVE_DISCORD_UDP.bin" --dpi-desync-fake-stun="%FILES%\ACTIVE_DISCORD_UDP.bin" --dpi-desync-repeats=6 --new ^
--filter-tcp=443 --hostlist="%LISTS%\list-youtube.txt" --dpi-desync=hostfakesplit --dpi-desync-fooling=ts --dpi-desync-hostfakesplit-mod=host=www.google.com --new ^
--filter-tcp=443,2053,2083,2087,2096,8443 --hostlist="%LISTS%\list-discord.txt" --dpi-desync=fake,multisplit --dpi-desync-split-seqovl=681 --dpi-desync-split-pos=1 --dpi-desync-fooling=ts --dpi-desync-repeats=8 --dpi-desync-split-seqovl-pattern="%FILES%\tls_clienthello_www_google_com.bin" --dpi-desync-fake-tls="%FILES%\tls_clienthello_www_google_com.bin" --dpi-desync-fake-http="%FILES%\tls_clienthello_www_google_com.bin" --new ^
--filter-tcp=80,443 --hostlist="%LISTS%\list-custom.txt" --dpi-desync=fake,multisplit --dpi-desync-split-seqovl=681 --dpi-desync-split-pos=1 --dpi-desync-fooling=ts --dpi-desync-repeats=8 --dpi-desync-split-seqovl-pattern="%FILES%\tls_clienthello_www_google_com.bin" --dpi-desync-fake-tls="%FILES%\tls_clienthello_www_google_com.bin" --dpi-desync-fake-http="%FILES%\tls_clienthello_www_google_com.bin" --new ^
--filter-tcp=%GAMEFILTERTCP% --ipset="%LISTS%ipset-all.txt" --dpi-desync=fake,multidisorder --dpi-desync-split-pos=1,midsld --dpi-desync-fooling=md5sig --dpi-desync-repeats=6 --new ^
--filter-udp=%GAMEFILTERUDP% --ipset="%LISTS%ipset-all.txt" --dpi-desync=fake --dpi-desync-any-protocol=1 --dpi-desync-cutoff=n2 --dpi-desync-repeats=10 --dpi-desync-fake-unknown-udp="%FILES%ACTIVE_GAME_UDP.bin"
