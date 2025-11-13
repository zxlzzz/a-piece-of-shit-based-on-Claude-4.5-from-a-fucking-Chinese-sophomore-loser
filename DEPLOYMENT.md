# 部署文档

## 📋 目录

1. [环境要求](#环境要求)
2. [开发环境启动](#开发环境启动)
3. [生产环境部署](#生产环境部署)
4. [环境变量配置](#环境变量配置)
5. [常见问题](#常见问题)

---

## 🔧 环境要求

### 后端
- Java 17+
- MySQL 8.0+
- Redis 6.0+
- Maven 3.6+

### 前端
- Node.js 16+
- npm 8+

---

## 🚀 开发环境启动

### 1. 后端启动

```bash
# 进入后端目录
cd backend

# 复制环境变量配置（首次）
cp .env.example .env

# 修改 .env 文件，设置数据库和 Redis 配置
# SPRING_PROFILES_ACTIVE=dev
# DB_URL=jdbc:mysql://localhost:3306/game_db...
# DB_USERNAME=gameuser
# DB_PASSWORD=123456

# 方式1：使用 Maven 启动
mvn spring-boot:run

# 方式2：打包后启动
mvn clean package -DskipTests
java -jar target/game-application-0.0.1-SNAPSHOT.jar
```

后端将在 `http://localhost:8080` 启动

### 2. 前端启动

```bash
# 进入前端目录
cd frontend

# 安装依赖（首次）
npm install

# 启动开发服务器
npm run dev
```

前端将在 `http://localhost:5173` 启动

### 3. 访问应用

打开浏览器访问：`http://localhost:5173`

---

## 🏭 生产环境部署

### 1. 后端部署

#### 方式 A：直接部署 JAR

```bash
# 1. 构建
cd backend
mvn clean package -DskipTests

# 2. 设置环境变量
export SPRING_PROFILES_ACTIVE=prod
export DB_URL="jdbc:mysql://your-db-host:3306/game_db?useSSL=true"
export DB_USERNAME="prod_user"
export DB_PASSWORD="your_strong_password"
export REDIS_HOST="your-redis-host"
export REDIS_PASSWORD="your_redis_password"
export JWT_SECRET="your-256-bit-secret-generated-by-openssl"
export CORS_ALLOWED_ORIGINS="https://yourdomain.com"

# 3. 启动（后台运行）
nohup java -jar target/game-application-0.0.1-SNAPSHOT.jar > logs/app.log 2>&1 &
```

#### 方式 B：使用 systemd 服务

创建 `/etc/systemd/system/game-backend.service`：

```ini
[Unit]
Description=Game Backend Service
After=network.target

[Service]
Type=simple
User=gameuser
WorkingDirectory=/opt/game-backend
Environment="SPRING_PROFILES_ACTIVE=prod"
Environment="DB_URL=jdbc:mysql://localhost:3306/game_db"
Environment="DB_USERNAME=gameuser"
Environment="DB_PASSWORD=your_password"
Environment="REDIS_HOST=localhost"
Environment="REDIS_PASSWORD=your_redis_password"
Environment="JWT_SECRET=your_jwt_secret"
Environment="CORS_ALLOWED_ORIGINS=https://yourdomain.com"
ExecStart=/usr/bin/java -jar /opt/game-backend/game-application.jar
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
```

启动服务：

```bash
sudo systemctl daemon-reload
sudo systemctl enable game-backend
sudo systemctl start game-backend
sudo systemctl status game-backend
```

### 2. 前端部署

```bash
# 1. 构建
cd frontend
npm install
npm run build

# 2. 部署到 Nginx
# dist/ 目录的内容复制到 Nginx root 目录
sudo cp -r dist/* /var/www/html/
```

### 3. Nginx 配置

创建或编辑 `/etc/nginx/sites-available/game`：

```nginx
server {
    listen 80;
    server_name yourdomain.com;

    # 前端静态文件
    root /var/www/html;
    index index.html;

    # 前端路由（SPA）
    location / {
        try_files $uri $uri/ /index.html;
    }

    # API 反向代理
    location /api {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # WebSocket 反向代理
    location /ws {
        proxy_pass http://localhost:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 86400;  # WebSocket 超时 24 小时
    }

    # 静态资源缓存
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }
}
```

启用配置并重启 Nginx：

```bash
sudo ln -s /etc/nginx/sites-available/game /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl restart nginx
```

### 4. HTTPS 配置（推荐使用 Let's Encrypt）

```bash
# 安装 certbot
sudo apt install certbot python3-certbot-nginx

# 获取证书并自动配置
sudo certbot --nginx -d yourdomain.com

# 自动续期
sudo certbot renew --dry-run
```

---

## 🔐 环境变量配置

### 后端必需环境变量（生产环境）

| 变量名 | 说明 | 示例 |
|--------|------|------|
| `SPRING_PROFILES_ACTIVE` | 运行环境 | `prod` |
| `DB_URL` | 数据库连接 | `jdbc:mysql://db:3306/game_db` |
| `DB_USERNAME` | 数据库用户 | `gameuser` |
| `DB_PASSWORD` | 数据库密码 | `Strong_P@ssw0rd` |
| `REDIS_HOST` | Redis 主机 | `redis-server` |
| `REDIS_PASSWORD` | Redis 密码 | `redis_p@ss` |
| `JWT_SECRET` | JWT 密钥 | 使用 `openssl rand -base64 32` 生成 |
| `CORS_ALLOWED_ORIGINS` | 允许的域名 | `https://yourdomain.com` |

### 前端环境变量（构建时）

前端环境变量在 `.env.production` 中配置，构建时会被打包进静态文件。

---

## ❓ 常见问题

### 1. 后端启动失败：数据库连接错误

**原因**：数据库配置错误或数据库未启动

**解决**：
- 检查 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 是否正确
- 确认 MySQL 服务已启动：`sudo systemctl status mysql`
- 检查数据库是否存在：`mysql -u root -p -e "SHOW DATABASES;"`

### 2. 后端启动失败：Redis 连接错误

**原因**：Redis 配置错误或 Redis 未启动

**解决**：
- 检查 `REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD` 是否正确
- 确认 Redis 服务已启动：`sudo systemctl status redis`
- 测试连接：`redis-cli -h localhost -p 6379 -a your_password ping`

### 3. 前端无法连接后端

**原因**：CORS 配置错误或 Nginx 配置错误

**解决**：
- 检查后端 `CORS_ALLOWED_ORIGINS` 是否包含前端域名
- 检查 Nginx 配置中的 `proxy_pass` 是否正确
- 查看浏览器控制台和网络请求

### 4. WebSocket 连接失败

**原因**：Nginx WebSocket 配置错误

**解决**：
- 确认 Nginx 配置中有 `proxy_http_version 1.1`
- 确认有 `proxy_set_header Upgrade $http_upgrade`
- 确认有 `proxy_set_header Connection "upgrade"`

### 5. JWT Token 验证失败

**原因**：JWT_SECRET 不一致或未设置

**解决**：
- 确认生产环境设置了 `JWT_SECRET` 环境变量
- 确认 JWT_SECRET 至少 256 位
- 重启后端服务

### 6. 数据库表不存在

**原因**：首次启动时需要创建表结构

**解决**：
- 开发环境：`ddl-auto: update` 会自动创建
- 生产环境：`ddl-auto: validate` 需要手动创建表
  - 使用数据库迁移工具（如 Flyway、Liquibase）
  - 或手动执行 SQL 脚本

---

## 📝 启动检查清单

### 开发环境
- [ ] MySQL 已启动
- [ ] Redis 已启动
- [ ] 后端配置文件正确（application-dev.yml）
- [ ] 前端依赖已安装（npm install）
- [ ] 后端运行在 8080 端口
- [ ] 前端运行在 5173 端口

### 生产环境
- [ ] 所有必需环境变量已设置
- [ ] JWT_SECRET 使用强密钥
- [ ] CORS_ALLOWED_ORIGINS 设置为实际域名
- [ ] 数据库密码已修改
- [ ] Redis 密码已设置
- [ ] Nginx 配置正确
- [ ] HTTPS 证书已配置
- [ ] 日志目录已创建
- [ ] 防火墙规则已配置
- [ ] 数据库定期备份已设置

---

## 🔄 更新部署

### 后端更新

```bash
# 1. 拉取最新代码
git pull origin main

# 2. 重新构建
cd backend
mvn clean package -DskipTests

# 3. 停止旧服务
sudo systemctl stop game-backend

# 4. 替换 JAR 文件
sudo cp target/game-application-0.0.1-SNAPSHOT.jar /opt/game-backend/game-application.jar

# 5. 启动新服务
sudo systemctl start game-backend
sudo systemctl status game-backend
```

### 前端更新

```bash
# 1. 拉取最新代码
git pull origin main

# 2. 重新构建
cd frontend
npm install
npm run build

# 3. 备份旧版本
sudo mv /var/www/html /var/www/html.backup.$(date +%Y%m%d%H%M%S)

# 4. 部署新版本
sudo cp -r dist/* /var/www/html/

# 5. 验证
curl http://localhost/
```

---

## 📊 监控和日志

### 查看后端日志

```bash
# systemd 服务日志
sudo journalctl -u game-backend -f

# 应用日志文件
tail -f logs/application.log
```

### 查看 Nginx 日志

```bash
# 访问日志
sudo tail -f /var/log/nginx/access.log

# 错误日志
sudo tail -f /var/log/nginx/error.log
```

---

## 🆘 紧急回滚

如果部署出现问题，快速回滚到上一个版本：

```bash
# 后端回滚
sudo systemctl stop game-backend
sudo cp /opt/game-backend/game-application.jar.backup /opt/game-backend/game-application.jar
sudo systemctl start game-backend

# 前端回滚
sudo rm -rf /var/www/html
sudo mv /var/www/html.backup.YYYYMMDDHHMMSS /var/www/html
```

---

## 📞 支持

如有问题，请查看：
- [项目 README](./README.md)
- [配置文档](./backend/CONFIG.md)
- [GitHub Issues](https://github.com/your-repo/issues)
