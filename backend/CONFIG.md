# 环境配置说明

本项目支持通过环境变量配置敏感信息，提供多种配置方式。

## 📋 配置项说明

### 数据库配置
| 环境变量 | 默认值 | 说明 |
|---------|--------|------|
| `DB_URL` | `jdbc:mysql://localhost:3306/game_db?...` | 数据库连接地址 |
| `DB_USERNAME` | `gameuser` | 数据库用户名 |
| `DB_PASSWORD` | `123456` | 数据库密码 |

### Redis 配置
| 环境变量 | 默认值 | 说明 |
|---------|--------|------|
| `REDIS_HOST` | `localhost` | Redis 主机地址 |
| `REDIS_PORT` | `6379` | Redis 端口 |
| `REDIS_PASSWORD` | （空） | Redis 密码（可选） |

### JWT 配置
| 环境变量 | 默认值 | 说明 |
|---------|--------|------|
| `JWT_SECRET` | `my-super-secret-jwt-key-...` | JWT 签名密钥（⚠️ 生产环境必须修改） |
| `JWT_EXPIRATION` | `86400000` | Token 过期时间（毫秒，默认24小时） |

### CORS 配置
| 环境变量 | 默认值 | 说明 |
|---------|--------|------|
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://localhost:3000` | 允许的跨域来源 |
| `CORS_ALLOWED_METHODS` | `GET,POST,PUT,DELETE,OPTIONS` | 允许的 HTTP 方法 |
| `CORS_MAX_AGE` | `3600` | 预检请求缓存时间（秒） |

---

## 🚀 配置方式

### 方式一：使用 `.env` 文件（推荐用于本地开发）

1. 复制示例文件：
```bash
cp .env.example .env
```

2. 编辑 `.env` 文件，修改敏感信息：
```env
DB_PASSWORD=your_secure_password
JWT_SECRET=your_very_long_and_random_secret_key_here
```

3. 在 IDEA 中配置 EnvFile 插件：
   - 安装插件：`File → Settings → Plugins → 搜索 "EnvFile"`
   - 配置运行：`Run → Edit Configurations → EnvFile → 启用并选择 .env 文件`

### 方式二：IDEA 环境变量配置

1. 打开运行配置：`Run → Edit Configurations`
2. 选择你的 Spring Boot 应用
3. 在 `Environment variables` 中添加：
```
DB_PASSWORD=your_password;JWT_SECRET=your_secret;REDIS_PASSWORD=your_redis_password
```

### 方式三：命令行启动（适用于生产环境）

```bash
# Maven 启动
mvn spring-boot:run -Dspring-boot.run.arguments="--jwt.secret=your_secret"

# 或使用环境变量
export JWT_SECRET=your_very_long_and_random_secret_key
export DB_PASSWORD=your_secure_password
java -jar target/test-1.0-SNAPSHOT.jar
```

### 方式四：Docker 环境变量

在 `docker-compose.yml` 中添加：
```yaml
services:
  backend:
    image: your-backend-image
    environment:
      - DB_URL=jdbc:mysql://mysql:3306/game_db?...
      - DB_USERNAME=gameuser
      - DB_PASSWORD=secure_password
      - JWT_SECRET=your_very_long_secret_key
      - REDIS_HOST=redis
```

### 方式五：系统环境变量

**Linux/macOS:**
```bash
# 临时设置（当前会话）
export JWT_SECRET=your_secret_key
export DB_PASSWORD=your_password

# 永久设置（添加到 ~/.bashrc 或 ~/.zshrc）
echo 'export JWT_SECRET=your_secret_key' >> ~/.bashrc
source ~/.bashrc
```

**Windows:**
```cmd
# 临时设置
set JWT_SECRET=your_secret_key
set DB_PASSWORD=your_password

# 永久设置（系统环境变量）
# 控制面板 → 系统 → 高级系统设置 → 环境变量
```

---

## 🔒 安全建议

### 开发环境
- ✅ 使用 `.env` 文件（已在 `.gitignore` 中排除）
- ✅ 使用默认值即可，无需修改

### 生产环境
- ⚠️ **必须修改 JWT_SECRET**，使用至少 32 位随机字符串
- ⚠️ 修改所有默认密码
- ⚠️ 使用容器编排工具的 Secret 管理（如 Kubernetes Secrets）
- ⚠️ 限制 CORS 允许的来源为实际域名

### 生成安全密钥
```bash
# Linux/macOS - 生成随机 JWT Secret
openssl rand -base64 64

# 或使用 Python
python3 -c "import secrets; print(secrets.token_urlsafe(64))"
```

---

## 📝 注意事项

1. **不要提交敏感信息到 Git**
   - `.env` 文件已被 `.gitignore` 排除
   - 只提交 `.env.example` 作为模板

2. **默认值的作用**
   - `${VAR_NAME:default}` 语法会在环境变量未设置时使用默认值
   - 这样可以确保本地开发无需配置即可运行

3. **优先级**
   - 环境变量 > application.yml 中的默认值
   - 本地开发建议使用默认值
   - 生产环境通过环境变量覆盖

4. **验证配置是否生效**
   ```bash
   # 启动应用后查看日志
   # 如果看到 "Using default value" 相关警告，说明某些环境变量未设置
   ```

---

## 🐛 常见问题

**Q: 修改了 .env 文件，但配置没生效？**
A: 确保 IDEA 安装了 EnvFile 插件，并在运行配置中启用了 .env 文件。

**Q: 生产环境如何管理密钥？**
A: 推荐使用 Docker Secrets、Kubernetes Secrets 或云服务商的密钥管理服务（如 AWS Secrets Manager）。

**Q: JWT_SECRET 设置多长合适？**
A: 建议至少 256 位（32 字节），使用 Base64 编码后约 44 个字符。

**Q: 能否针对不同环境使用不同配置文件？**
A: 可以！创建 `application-dev.yml`、`application-prod.yml`，然后通过 `SPRING_PROFILES_ACTIVE=prod` 切换。

---

## 🚀 生产环境部署检查清单

### ⚠️ 安全配置（必须修改）

#### 1. 生成强随机 JWT 密钥
```bash
# 生成新的 JWT 密钥
openssl rand -base64 64

# 设置环境变量
export JWT_SECRET="生成的随机字符串"
```

**重要：** 绝对不要使用示例密钥！

#### 2. 修改所有默认密码
```bash
# 数据库密码（推荐12位以上，包含大小写字母、数字、特殊字符）
export DB_PASSWORD="YourStrong@DatabasePass123!"

# Redis 密码
export REDIS_PASSWORD="YourStrong@RedisPass456!"
```

**建议密码强度：**
- 长度 ≥ 12 位
- 包含大写字母（A-Z）
- 包含小写字母（a-z）
- 包含数字（0-9）
- 包含特殊字符（!@#$%^&*）

#### 3. 配置 CORS 允许的域名
```bash
# 生产环境 CORS 配置（替换为你的实际域名）
export CORS_ALLOWED_ORIGINS="https://yourgame.com,https://www.yourgame.com"
```

**注意：**
- 使用 HTTPS（不是 HTTP）
- 不要包含尾部斜杠
- 多个域名用逗号分隔

#### 4. 修改 JPA 配置
```yaml
# 生产环境禁用自动建表！
spring:
  jpa:
    hibernate:
      ddl-auto: validate  # 改为 validate 或 none
```

**风险：** `ddl-auto: create` 会在每次重启时删除所有数据！

---

### ✅ 上线前检查表

**环境变量检查：**
- [ ] `JWT_SECRET` 已修改为强随机密钥（至少64字符）
- [ ] `DB_PASSWORD` 已修改为强密码
- [ ] `REDIS_PASSWORD` 已设置
- [ ] `CORS_ALLOWED_ORIGINS` 配置为实际域名（HTTPS）
- [ ] `DB_URL` 指向生产数据库

**配置文件检查：**
- [ ] `application.yml` 中 `ddl-auto` 改为 `validate`
- [ ] 日志级别设置合理（INFO 或 WARN）
- [ ] 删除了所有测试/调试代码

**安全检查：**
- [ ] 关闭了不必要的端口
- [ ] 数据库不对外网开放
- [ ] Redis 不对外网开放
- [ ] 启用了 HTTPS

**功能测试：**
- [ ] 创建房间
- [ ] 加入房间
- [ ] 开始游戏
- [ ] 答题提交
- [ ] 重启服务器后 Redis 恢复
- [ ] 断线重连

---

### 🔒 额外安全建议

#### 使用 Docker Secrets（推荐）
```yaml
# docker-compose.prod.yml
services:
  backend:
    secrets:
      - db_password
      - jwt_secret
    environment:
      DB_PASSWORD_FILE: /run/secrets/db_password
      JWT_SECRET_FILE: /run/secrets/jwt_secret

secrets:
  db_password:
    file: ./secrets/db_password.txt
  jwt_secret:
    file: ./secrets/jwt_secret.txt
```

#### 限制 Redis 访问
```yaml
# docker-compose.prod.yml
redis:
  command: redis-server --requirepass ${REDIS_PASSWORD}
  networks:
    - backend  # 不暴露到外网
```

#### 定期更换密钥
- JWT_SECRET：每 3-6 个月更换
- 数据库密码：每 6-12 个月更换
- Redis 密码：每 6-12 个月更换

---

### 📊 生产环境配置模板

```bash
# .env.production（不要提交到 Git！）
# 数据库
DB_URL=jdbc:mysql://prod-db-host:3306/game_db?useSSL=true
DB_USERNAME=gameuser
DB_PASSWORD=你的超强数据库密码

# Redis
REDIS_HOST=prod-redis-host
REDIS_PORT=6379
REDIS_PASSWORD=你的超强Redis密码

# JWT
JWT_SECRET=你生成的64位随机密钥
JWT_EXPIRATION=86400000

# CORS
CORS_ALLOWED_ORIGINS=https://yourgame.com,https://www.yourgame.com

# 其他
SERVER_PORT=8080
```

---

### 🆘 常见上线问题排查

**问题 1：前端无法访问后端 API**
- 检查 CORS 配置
- 检查 Nginx 反向代理配置
- 检查防火墙规则

**问题 2：JWT 认证失败**
- 确认 JWT_SECRET 在所有实例上一致
- 检查 Token 过期时间
- 查看后端日志错误

**问题 3：Redis 连接失败**
- 检查 Redis 服务是否运行
- 检查 REDIS_HOST 和 REDIS_PORT
- 检查 REDIS_PASSWORD 是否正确
- 检查网络连通性

**问题 4：数据库连接失败**
- 检查数据库服务是否运行
- 检查 DB_URL、DB_USERNAME、DB_PASSWORD
- 检查数据库防火墙规则
- 查看数据库日志

---

### 📞 紧急回滚方案

如果上线后出现严重问题：

1. **立即回滚代码**
```bash
git revert <commit-hash>
git push
```

2. **重启服务**
```bash
docker-compose restart backend
```

3. **恢复数据库备份**（如果需要）
```bash
mysql -u gameuser -p game_db < backup_YYYYMMDD.sql
```

4. **清空 Redis 缓存**（如果需要）
```bash
redis-cli -a <password> FLUSHALL
```
