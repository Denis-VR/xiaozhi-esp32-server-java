# Руководство по развёртыванию сервера Xiaozhi ESP32 на CentOS

## Системные требования

- CentOS 7/8 (рекомендуется CentOS 8)
- Минимальная установка + инструменты разработки (gcc, make и др.)
- Не менее 2 ГБ ОЗУ (рекомендуется 4 ГБ)
- Не менее 10 ГБ дискового пространства

## 1. Подготовка окружения

### 1.1 Установка базовых инструментов

```bash
sudo yum install -y epel-release
sudo yum install -y wget curl git vim unzip
```

### 1.2 Настройка файрвола

```bash
sudo firewall-cmd --permanent --add-port=8084/tcp
sudo firewall-cmd --permanent --add-port=8091/tcp
sudo firewall-cmd --permanent --add-port=3306/tcp
sudo firewall-cmd --reload
```

## 2. Установка Java JDK 21

```bash
sudo yum install -y java-21-openjdk java-21-openjdk-devel
```

Проверка установки:

```bash
java -version
```

## 3. Установка MySQL 8.0

```bash
# Установка репозитория MySQL 8.0
sudo yum localinstall -y https://dev.mysql.com/get/mysql80-community-release-el7-7.noarch.rpm
sudo yum install -y mysql-community-server
```

Запуск MySQL:

```bash
sudo systemctl start mysqld
sudo systemctl enable mysqld
```

Получение временного пароля:

```bash
sudo grep 'temporary password' /var/log/mysqld.log
```

Первичная настройка безопасности:

```bash
sudo mysql_secure_installation
```

## 4. Установка Maven

```bash
sudo yum install -y maven
```

Проверка установки:

```bash
mvn -v
```

## 5. Установка Node.js 16

```bash
curl -sL https://rpm.nodesource.com/setup_16.x | sudo bash -
sudo yum install -y nodejs
```

Проверка установки:

```bash
node -v
npm -v
```

## 6. Установка FFmpeg

```bash
sudo yum install -y ffmpeg ffmpeg-devel
```

Проверка установки:

```bash
ffmpeg -version
```

## 7. Настройка базы данных

Создание базы данных и пользователя:

```sql
mysql -u root -p
CREATE DATABASE xiaozhi CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'xiaozhi'@'localhost' IDENTIFIED BY '123456';
GRANT ALL PRIVILEGES ON xiaozhi.* TO 'xiaozhi'@'localhost';
FLUSH PRIVILEGES;
exit
```

Импорт стартового SQL‑скрипта:

```bash
mysql -u root -p xiaozhi < db/init.sql
```

## 8. Загрузка модели распознавания речи Vosk

```bash
wget https://alphacephei.com/vosk/models/vosk-model-cn-0.22.zip
unzip vosk-model-cn-0.22.zip
mkdir -p models
mv vosk-model-cn-0.22 models/vosk-model
```

## 9. Развёртывание проекта

### 9.1 Клонирование репозитория

```bash
git clone https://github.com/joey-zhou/xiaozhi-esp32-server-java
cd xiaozhi-esp32-server-java
```

### 9.2 Развёртывание бэкенда

```bash
mvn clean package -DskipTests
java -jar target/xiaozhi.server-*.jar &（版本号可能不同）
```

### 9.3 Сборка фронтенда

```bash
cd web
npm install
npm run build
```

## 10. Настройка системной службы (опционально)

### 10.1 Создание systemd‑сервиса для бэкенда

Откройте файл службы:

```bash
sudo vim /etc/systemd/system/xiaozhi.service
```

Добавьте содержимое:

```
[Unit]
Description=Xiaozhi ESP32 Server
After=syslog.target network.target

[Service]
User=root
WorkingDirectory=/path/to/xiaozhi-esp32-server-java
ExecStart=/usr/bin/java -jar target/xiaozhi.server-*.jar（版本号可能不同）
SuccessExitStatus=143
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

Запуск службы:

```bash
sudo systemctl daemon-reload
sudo systemctl start xiaozhi
sudo systemctl enable xiaozhi
```

### 10.2 Настройка Nginx (опционально)

```bash
sudo yum install -y nginx
sudo vim /etc/nginx/conf.d/xiaozhi.conf
```

Добавьте конфигурацию:

```
server {
    listen 80;
    server_name your_domain_or_ip;

    location / {
        root /path/to/xiaozhi-esp32-server-java/web/dist;
        try_files $uri $uri/ /index.html;
    }

    location /api {
        proxy_pass http://localhost:8091;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

Запуск Nginx:

```bash
sudo systemctl start nginx
sudo systemctl enable nginx
```

## 11. Доступ к системе

- Прямой доступ: `http://your_server_ip:8084`
- Если настроен Nginx: `http://your_domain_or_ip`
- Дефолтный админ‑аккаунт: admin/123456

## Частые проблемы и их решение

1. **Сбой инициализации MySQL**

   ```bash
   sudo systemctl restart mysqld
   mysql_upgrade -u root -p
   ```

2. **Конфликт портов**

   ```bash
   netstat -tulnp | grep 8084
   kill -9 <PID>
   ```

3. **Проблемы с версией Node.js**

   ```bash
   sudo yum remove -y nodejs npm
   curl -sL https://deb.nodesource.com/setup_16.x | sudo -E bash -
   sudo yum install -y nodejs
   ```

4. **Недостаточно памяти**

   Увеличьте swap:

   ```bash
   sudo dd if=/dev/zero of=/swapfile bs=1M count=2048
   sudo mkswap /swapfile
   sudo swapon /swapfile
   echo '/swapfile swap swap defaults 0 0' | sudo tee -a /etc/fstab
   ```

5. **Модель Vosk не загружается**

   ```bash
   chmod -R 755 models
   ```

## Команды обслуживания

- Просмотр логов бэкенда:

  ```bash
  journalctl -u xiaozhi -f
  ```

- Обновление кода:

  ```bash
  git pull origin master
  mvn clean package -DskipTests
  sudo systemctl restart xiaozhi
  ```

- Резервная копия БД:

  ```bash
  mysqldump -u root -p xiaozhi > xiaozhi_backup_$(date +%Y%m%d).sql
  ```

## Примечания

1. **Рекомендации для продакшена:**
   - Измените пароли по умолчанию
   - Настройте HTTPS
   - Регулярно делайте бэкапы БД

2. **Оптимизация производительности:**

   Увеличение памяти JVM:

   ```bash
   java -Xms512m -Xmx1024m -jar target/xiaozhi.server-*.jar（版本号可能不同）
   ```
