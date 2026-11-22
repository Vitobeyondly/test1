@echo off
chcp 65001 > nul

set "ROOT=%~dp0..\"
set "BIN=%ROOT%zapret-winws\"
set "FILES=%ROOT%zapret-winws\files\"
set "LISTS=%ROOT%lists\"
if not defined GAMEFILTERTCP set "GAMEFILTERTCP=12"
if not defined GAMEFILTERUDP set "GAMEFILTERUDP=12"
cd /d "%BIN%"
start "zapret2: %~n0" /min "%BIN%winws2.exe" ^
--wf-tcp-out=80,443,2053,2083,2087,2096,8443,%GAMEFILTERTCP% ^
--wf-tcp-in=443,2053,2083,2087,2096,8443 ^
--wf-udp-out=443,19294-19344,50000-50100,%GAMEFILTERUDP% ^
--lua-init=@"%BIN%lua\zapret-lib.lua" ^
--lua-init=@"%BIN%lua\zapret-antidpi.lua" ^
--lua-init=@"%BIN%lua\zapret-auto.lua" ^
--blob=quic_google:@"%FILES%quic_initial_www_google_com.bin" ^
--blob=tls_google:@"%FILES%tls_clienthello_www_google_com.bin" ^
--blob=dc_udp:@"%FILES%ACTIVE_DISCORD_UDP.bin" ^
--blob=game_udp:@"%FILES%ACTIVE_GAME_UDP.bin" ^
--filter-tcp=443 --filter-l7=tls --hostlist="%LISTS%list-youtube.txt" --in-range=-s5556 --out-range=-s34228 ^
--payload=all --lua-desync=circular:fails=3:time=60 ^
--payload=tls_client_hello ^
--lua-desync=fake:blob=tls_google:tcp_ts=-1000000:repeats=6:strategy=1 --lua-desync=multisplit:pos=1:seqovl=681:seqovl_pattern=tls_google:strategy=1 ^
--lua-desync=fake:blob=fake_default_tls:tcp_md5:repeats=6:strategy=2 --lua-desync=multidisorder:pos=1,midsld:strategy=2 ^
--lua-desync=fake:blob=fake_default_tls:tcp_md5:repeats=6:strategy=3 --lua-desync=hostfakesplit:host=ya.ru:tcp_md5:repeats=2:strategy=3 ^
--lua-desync=fakeddisorder:pos=1,midsld:tcp_md5:strategy=4 --new ^
--filter-tcp=443,2053,2083,2087,2096,8443 --filter-l7=tls --hostlist="%LISTS%list-discord.txt" --in-range=-s5556 --out-range=-s34228 ^
--payload=all --lua-desync=circular:fails=3:time=60 ^
--payload=tls_client_hello ^
--lua-desync=fake:blob=tls_google:tcp_ts=-1000000:repeats=6:strategy=1 --lua-desync=multisplit:pos=1:seqovl=681:seqovl_pattern=tls_google:strategy=1 ^
--lua-desync=fake:blob=fake_default_tls:tcp_md5:repeats=6:strategy=2 --lua-desync=multidisorder:pos=1,midsld:strategy=2 ^
--lua-desync=fake:blob=fake_default_tls:tcp_md5:repeats=6:strategy=3 --lua-desync=hostfakesplit:host=ya.ru:tcp_md5:repeats=2:strategy=3 ^
--lua-desync=fakeddisorder:pos=1,midsld:tcp_md5:strategy=4 --new ^
--filter-tcp=443 --filter-l7=tls --hostlist="%LISTS%list-custom.txt" --out-range=-d10 --payload=tls_client_hello ^
--lua-desync=fake:blob=fake_default_tls:tcp_md5:repeats=6 --lua-desync=multidisorder:pos=1,midsld --new ^
--filter-tcp=80 --filter-l7=http --hostlist="%LISTS%list-custom.txt" --out-range=-d10 --payload=http_req ^
--lua-desync=fake:blob=fake_default_http:tcp_md5 --lua-desync=fakedsplit:tcp_md5 --new ^
--filter-udp=443 --filter-l7=quic --hostlist="%LISTS%list-youtube.txt" --payload=quic_initial ^
--lua-desync=fake:blob=quic_google:repeats=6 --new ^
--filter-l7=stun,discord --out-range=-n4 --payload=stun,discord_ip_discovery ^
--lua-desync=fake:blob=dc_udp:repeats=6 --new ^
--filter-tcp=%GAMEFILTERTCP% --filter-l7=tls --ipset="%LISTS%ipset-all.txt" --out-range=-d10 --payload=tls_client_hello ^
--lua-desync=fake:blob=fake_default_tls:tcp_md5:repeats=6 --lua-desync=multidisorder:pos=1,midsld --new ^
--filter-udp=%GAMEFILTERUDP% --ipset="%LISTS%ipset-all.txt" --out-range=-n2 --payload=all ^
--lua-desync=fake:blob=game_udp:repeats=10
