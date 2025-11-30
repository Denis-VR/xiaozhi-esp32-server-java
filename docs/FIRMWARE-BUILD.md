# Сборка прошивки для ESP32

1. Скачайте проект `xiaozhi-esp32` и настройте окружение по руководству:
   [«Настройка ESP IDF 5.3.2 на Windows и сборка Xiaozhi»](https://icnynnzcwou8.feishu.cn/wiki/JEYDwTTALi5s2zkGlFGcDiRknXf)

# Прошивка до версии 1.6.2 включительно

2. Откройте файл `xiaozhi-esp32/main/Kconfig.projbuild`, найдите параметр `WEBSOCKET_URL` и его значение по умолчанию. Замените `wss://api.tenclass.net` на свой адрес. Например, если ваш адрес — `ws://192.168.1.25:8091`, укажите его.

До изменения:

```
config WEBSOCKET_URL
    depends on CONNECTION_TYPE_WEBSOCKET
    string "Websocket URL"
    default "wss://api.tenclass.net/xiaozhi/v1/"
    help
        Communication with the server through websocket after wake up.
```

После изменения (пример):

```
config WEBSOCKET_URL
    depends on CONNECTION_TYPE_WEBSOCKET
    string "Websocket URL"
    default "ws://192.168.5.167:8091/ws/xiaozhi/v1/"
    help
        Communication with the server through websocket after wake up.
```

Внимание: адрес должен начинаться с `ws://`, а не `wss://`. Не перепутайте!

Внимание: адрес должен начинаться с `ws://`, а не `wss://`. Не перепутайте!

Внимание: адрес должен начинаться с `ws://`, а не `wss://`. Не перепутайте!

# Прошивка после версии 1.6.2

Найдите значение по умолчанию параметра `OTA_URL` и замените `https://api.tenclass.net/xiaozhi/ota/` на свой адрес. Например, если ваш адрес: `http://192.168.5.165:8091/api/device/ota/`, укажите его.

До изменения:
```
config OTA_VERSION_URL
    string "OTA Version URL"
    default "https://api.tenclass.net/xiaozhi/ota/"
    help
        The application will access this URL to check for updates.
```

После изменения (пример):
```
config OTA_VERSION_URL
    string "OTA Version URL"
    default "http://192.168.5.167:8091/api/device/ota"
    help
        The application will access this URL to check for updates.
```

Внимание: адрес должен начинаться с `http://`, а не `https://`. Не перепутайте!

Внимание: адрес должен начинаться с `http://`, а не `https://`. Не перепутайте!

Внимание: адрес должен начинаться с `http://`, а не `https://`. Не перепутайте!


3. Настройте параметры сборки

```
# В терминале перейдите в корень проекта xiaozhi-esp32
cd xiaozhi-esp32
# Например, если у вас плата esp32s3, установите целевую платформу esp32s3. Если другая — укажите соответствующую.
idf.py set-target esp32s3
# Откройте меню конфигурации
idf.py menuconfig
```

В меню конфигурации откройте раздел `Xiaozhi Assistant` и установите `BOARD_TYPE` на конкретную модель вашей платы.
Сохраните, выйдите и вернитесь в терминал.

4. Соберите прошивку

```
idf.py build
```

Если вы используете VS Code с установленным IDF — нажмите `F1` или `Ctrl+Shift+P`, введите «idf» и выберите задачу сборки.

Можно сразу перейти к прошивке без выполнения следующих шагов.

<img src="./images/vscode_idf.png" width="500px"/>

5. Упаковка прошивки (bin)

```
cd scripts
python release.py
```

После успешной сборки в каталоге `build` (в корне проекта) появится файл прошивки `merged-binary.bin`.
Именно его нужно прошивать на устройство.

Примечание: если после запуска второй команды появится ошибка, связанная с «zip», её можно игнорировать — если файл `merged-binary.bin` в каталоге `build` создан, продолжайте.

6. Прошивка устройства
   Подключите ESP32 к компьютеру, откройте браузер Chrome и перейдите по адресу

```
https://espressif.github.io/esp-launchpad/
```

Откройте руководство: [Инструмент прошивки/Веб‑прошивка (без локального IDF)](https://ccnphfhqs21z.feishu.cn/wiki/Zpz4wXBtdimBrLk25WdcXzxcnNS).
Найдите раздел: «Способ 2: ESP‑Launchpad — прошивка в браузере» и следуйте пунктам, начиная с «3. Прошивка/Загрузка на плату».

После успешной прошивки и подключения к сети разбудите ассистента ключевой фразой и следите за сообщениями в консоли сервера.
