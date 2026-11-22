@echo off
chcp 65001 > nul
rem KTS Crimea A: fake+multidisorder, md5sig+badseq, autottl
set "ROOT=%~dp0..\"
set "BIN=%ROOT%zapret-winws\"
set "FILES=%ROOT%zapret-winws\files\"
set "LISTS=%ROOT%lists\"
if not defined GAMEFILTERTCP set "GAMEFILTERTCP=12"
if not defined GAMEFILTERUDP set "GAMEFILTERUDP=12"
cd /d "%BIN%"
start "zapret2: %~n0" /min "%BIN%winws2.exe" ^
--wf-tcp-out=80,443,2053,2083,2087,2096,8443,%GAMEFILTERTCP% ^
--wf-udp-out=443,19294-19344,50000-50100,%GAMEFILTERUDP% ^
--lua-init=@"%BIN%lua\zapret-lib.lua" ^
--lua-init=@"%BIN%lua\zapret-antidpi.lua" ^
--blob=quic_google:@"%FILES%quic_initial_www_google_com.bin" ^
--blob=tls_google:@"%FILES%tls_clienthello_www_google_com.bin" ^
--blob=dc_udp:@"%FILES%ACTIVE_DISCORD_UDP.bin" ^
--blob=game_udp:@"%FILES%ACTIVE_GAME_UDP.bin" ^
--filter-tcp=443 --filter-l7=tls --hostlist="%LISTS%list-youtube.txt" --out-range=-d10 --payload=tls_client_hello ^
--lua-desync=fake:blob=fake_default_tls:tcp_md5:tcp_seq=-10000:ip_autottl=-2,3-20:repeats=8 --lua-desync=multidisorder:pos=1,midsld --new ^
--filter-tcp=443,2053,2083,2087,2096,8443 --filter-l7=tls --hostlist="%LISTS%list-discord.txt" --out-range=-d10 --payload=tls_client_hello ^
--lua-desync=fake:blob=fake_default_tls:tcp_md5:tcp_seq=-10000:ip_autottl=-2,3-20:repeats=8 --lua-desync=multidisorder:pos=1,midsld --new ^
--filter-tcp=443 --filter-l7=tls --hostlist="%LISTS%list-custom.txt" --out-range=-d10 --payload=tls_client_hello ^
--lua-desync=fake:blob=fake_default_tls:tcp_md5:tcp_seq=-10000:ip_autottl=-2,3-20:repeats=8 --lua-desync=multidisorder:pos=1,midsld --new ^
--filter-tcp=80 --filter-l7=http --hostlist="%LISTS%list-custom.txt" --out-range=-d10 --payload=http_req ^
--lua-desync=fake:blob=fake_default_http:ip_autottl=-2,3-20:tcp_md5 --lua-desync=fakedsplit:ip_autottl=-2,3-20:tcp_md5 --new ^
--filter-udp=443 --filter-l7=quic --hostlist="%LISTS%list-youtube.txt" --payload=quic_initial ^
--lua-desync=fake:blob=quic_google:repeats=11 --new ^
--filter-udp=443 --filter-l7=quic --hostlist="%LISTS%list-custom.txt" --payload=quic_initial ^
--lua-desync=fake:blob=quic_google:repeats=11 --new ^
--filter-l7=stun,discord --out-range=-n4 --payload=stun,discord_ip_discovery ^
--lua-desync=fake:blob=dc_udp:repeats=6 --new ^
--filter-tcp=%GAMEFILTERTCP% --filter-l7=tls --ipset="%LISTS%ipset-all.txt" --out-range=-d10 --payload=tls_client_hello ^
--lua-desync=fake:blob=fake_default_tls:tcp_md5:repeats=6 --lua-desync=multidisorder:pos=1,midsld --new ^
--filter-udp=%GAMEFILTERUDP% --ipset="%LISTS%ipset-all.txt" --out-range=-n2 --payload=all ^
--lua-desync=fake:blob=game_udp:repeats=10
