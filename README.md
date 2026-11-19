# Sueca no Café

Sueca de quatro, em duas equipas, com as regras de Portugal. Cada jogador usa o
seu próprio telemóvel e **o servidor do jogo corre dentro da app de quem abre a
mesa**: sem internet, sem contas, sem browser. Só a rede Wi-Fi local.

## Como se joga

1. Um jogador abre a mesa (`Abrir mesa de 4`). A app liga o servidor interno na
   porta 8743 e mostra o endereço, o código da mesa e um QR.
2. Os outros apontam a câmara para o QR e entram direto na mesa
   (`sueca://entrar?host=<ip:porta>&mesa=<código>`), ou escrevem o endereço à
   mão. Só entra quem tiver a app.
3. Quem abriu a mesa dá a primeira carta. A partida é o primeiro a ganhar
   4 jogos — a bandeira fecha a mesa.

Os lugares que ficam vazios são preenchidos por bots, por isso também se joga
com dois ou três telemóveis.

## Construir

Precisa de JDK 17 e do Android SDK 36.

```
./gradlew testDebugUnitTest   # testes do motor, das mesas e da vista
./gradlew assembleDebug       # APK de teste em app/build/outputs/apk/debug/
./gradlew assembleRelease     # APK assinado em app/build/outputs/apk/release/
```

`minSdk 26`. Os QR são gerados dentro do APK (`com.google.zxing:core`) e os
tipos estão em `app/src/main/res/font`, por isso a app funciona sem nunca sair
da rede local.

Para o `assembleRelease` sair assinado é preciso um `keystore.properties` na
raiz do projecto, com `storeFile`, `storePassword`, `keyAlias` e `keyPassword`
a apontar para uma chave privada. Esse ficheiro e os keystore estão no
`.gitignore`: a chave de lançamento nunca entra no repositório. Sem ele o
release compila, mas fica sem assinatura.

## Estrutura

| Pasta | O que é |
| --- | --- |
| `app/src/main/java/com/sueca/cafe/Engine.kt`, `Bots.kt` | regras da sueca e jogadores automáticos |
| `app/src/main/java/com/sueca/cafe/net/` | servidor Ktor interno, salas e o cliente HTTP que anfitrião e convidado partilham |
| `app/src/main/java/com/sueca/cafe/svc/` | serviço em primeiro plano que mantém a mesa viva |
| `app/src/main/java/com/sueca/cafe/ui/` | interface Compose: mesa, cartas, animações, QR, ecrãs |
| `app/src/test/` | testes unitários (motor, HTTP, salas, vista) |
| `webapp_old/` | a versão web (PWA) que deu origem ao projecto — já não é o produto |
| `tools/` | scripts de verificação em emulador |

## Estado

Versão 0.1: jogo completo a quatro dispositivos na mesma rede, com vaza
animada, trunfo virado junto ao dador, registo de vazas e pontuação por equipa.
O APK publicado na release está assinado com a chave de lançamento, por isso as
actualizações passam a instalar-se por cima sem desinstalar primeiro.
