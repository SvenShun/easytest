#!/usr/bin/env bash
set -uo pipefail
printf 'MeterSphere UI  : %s\n' "$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:5100/)"
printf 'MeterSphere API : %s\n' "$(curl -s -o /dev/null -w '%{http_code}' -X POST http://127.0.0.1:8082/login -H 'Content-Type: application/json' -d '{"username":"admin","password":"metersphere"}')"
printf 'Sonic UI        : %s\n' "$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:3001/)"
printf 'Sonic API       : %s\n' "$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:3001/server/api/controller/users/loginConfig)"
