#!/bin/bash
# Caminho até à mesa no emulador, tolerante a outros apps que ganhem o foco:
# antes de cada toque garante que a Sueca está em primeiro plano.
# Uso: ./tools/toque-mesa.sh <ficheiro.png> [jogadas] [serial]
set -e
ADB="$HOME/Library/Android/sdk/platform-tools/adb"
OUT="${1:-/tmp/mesa.png}"
JOGADAS="${2:-0}"
SERIAL="${3:-emulator-5556}"

dev() { $ADB -s "$SERIAL" "$@"; }

front() {
    for _ in 1 2 3 4 5; do
        local f=$(dev shell dumpsys window | grep -o 'mCurrentFocus=Window{[^ ]* [^/]*' | tail -1)
        case "$f" in *com.sueca.cafe*) return 0 ;; esac
        dev shell am start -n com.sueca.cafe/.MainActivity >/dev/null
        sleep 2
    done
    echo "AVISO: a Sueca não consegue ficar em foco" >&2
    return 1
}

tap() { front; dev shell input tap "$1" "$2"; sleep "${3:-2}"; }

dev shell am force-stop com.sueca.cafe
sleep 1
dev shell am start -n com.sueca.cafe/.MainActivity >/dev/null
sleep 5
tap 540 1132 5                       # Abrir mesa de 4
for _ in 1 2 3; do dev shell input swipe 540 1600 540 500 350; done
sleep 1
tap 540 2060 7                       # Começar a jogar

# a primeira carta clicável da mão (as ilegais não têm onClick)
carta_legal() {
    front
    dev shell uiautomator dump /sdcard/h.xml >/dev/null 2>&1
    dev shell cat /sdcard/h.xml | python3 "$(dirname "$0")/cartas.py"
}

for i in $(seq 1 "$JOGADAS"); do
    alvo=""
    for _ in 1 2 3 4 5 6; do
        alvo=$(carta_legal || true)
        [ -n "$alvo" ] && break
        sleep 2
    done
    [ -z "$alvo" ] && { echo "sem jogada legal depois de $i"; break; }
    tap $alvo 4
done

front || true
dev shell uiautomator dump /sdcard/mesa.xml >/dev/null 2>&1
dev exec-out screencap -p > "$OUT"
echo "foco: $(dev shell dumpsys window | grep -o 'mCurrentFocus=Window{[^ ]* [^/]*' | tail -1)"
echo "gravado: $OUT"
