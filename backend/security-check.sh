#!/bin/bash

# ==============================================
# 生产环境安全检查脚本
# ==============================================

echo "🔍 开始安全检查..."
echo ""

ERRORS=0
WARNINGS=0

# 颜色定义
RED='\033[0;31m'
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
NC='\033[0m' # No Color

# ==============================================
# 1. 检查 JWT_SECRET
# ==============================================
echo "📋 检查 JWT_SECRET..."

if [ -z "$JWT_SECRET" ]; then
    echo -e "${RED}❌ JWT_SECRET 未设置！${NC}"
    echo "   请设置环境变量: export JWT_SECRET=\$(openssl rand -base64 64)"
    ERRORS=$((ERRORS + 1))
elif [ ${#JWT_SECRET} -lt 32 ]; then
    echo -e "${RED}❌ JWT_SECRET 太短（当前长度: ${#JWT_SECRET}）${NC}"
    echo "   建议至少 64 个字符"
    ERRORS=$((ERRORS + 1))
elif [ "$JWT_SECRET" == "my-super-secret-jwt-key-for-game-application-2024" ]; then
    echo -e "${RED}❌ JWT_SECRET 使用了默认值！${NC}"
    echo "   这是严重的安全漏洞，必须修改！"
    ERRORS=$((ERRORS + 1))
else
    echo -e "${GREEN}✅ JWT_SECRET 已正确设置（长度: ${#JWT_SECRET}）${NC}"
fi

echo ""

# ==============================================
# 2. 检查数据库密码
# ==============================================
echo "📋 检查数据库密码..."

if [ -z "$DB_PASSWORD" ]; then
    echo -e "${RED}❌ DB_PASSWORD 未设置！${NC}"
    ERRORS=$((ERRORS + 1))
elif [ "$DB_PASSWORD" == "123456" ] || [ "$DB_PASSWORD" == "change_this_in_production_12345!" ]; then
    echo -e "${RED}❌ DB_PASSWORD 使用了默认值！${NC}"
    echo "   当前值: $DB_PASSWORD"
    ERRORS=$((ERRORS + 1))
elif [ ${#DB_PASSWORD} -lt 8 ]; then
    echo -e "${YELLOW}⚠️  DB_PASSWORD 太短（当前长度: ${#DB_PASSWORD}）${NC}"
    echo "   建议至少 12 个字符"
    WARNINGS=$((WARNINGS + 1))
else
    echo -e "${GREEN}✅ DB_PASSWORD 已设置（长度: ${#DB_PASSWORD}）${NC}"
fi

echo ""

# ==============================================
# 3. 检查 Redis 密码
# ==============================================
echo "📋 检查 Redis 密码..."

if [ -z "$REDIS_PASSWORD" ]; then
    echo -e "${YELLOW}⚠️  REDIS_PASSWORD 未设置${NC}"
    echo "   生产环境强烈建议设置 Redis 密码"
    WARNINGS=$((WARNINGS + 1))
elif [ "$REDIS_PASSWORD" == "change_this_redis_password_123!" ]; then
    echo -e "${RED}❌ REDIS_PASSWORD 使用了默认值！${NC}"
    ERRORS=$((ERRORS + 1))
else
    echo -e "${GREEN}✅ REDIS_PASSWORD 已设置${NC}"
fi

echo ""

# ==============================================
# 4. 检查 CORS 配置
# ==============================================
echo "📋 检查 CORS 配置..."

if [ -z "$CORS_ALLOWED_ORIGINS" ]; then
    echo -e "${YELLOW}⚠️  CORS_ALLOWED_ORIGINS 未设置${NC}"
    WARNINGS=$((WARNINGS + 1))
elif [[ "$CORS_ALLOWED_ORIGINS" == *"localhost"* ]]; then
    echo -e "${YELLOW}⚠️  CORS 配置包含 localhost${NC}"
    echo "   当前值: $CORS_ALLOWED_ORIGINS"
    echo "   生产环境应该使用实际域名（HTTPS）"
    WARNINGS=$((WARNINGS + 1))
elif [[ "$CORS_ALLOWED_ORIGINS" != *"https://"* ]]; then
    echo -e "${YELLOW}⚠️  CORS 配置未使用 HTTPS${NC}"
    echo "   当前值: $CORS_ALLOWED_ORIGINS"
    WARNINGS=$((WARNINGS + 1))
else
    echo -e "${GREEN}✅ CORS_ALLOWED_ORIGINS 已正确配置${NC}"
    echo "   值: $CORS_ALLOWED_ORIGINS"
fi

echo ""

# ==============================================
# 5. 检查 application.yml
# ==============================================
echo "📋 检查 application.yml..."

if [ -f "src/main/resources/application.yml" ]; then
    if grep -q "ddl-auto: create" src/main/resources/application.yml; then
        echo -e "${RED}❌ application.yml 中 ddl-auto 设置为 create！${NC}"
        echo "   这会在每次重启时删除所有数据！"
        echo "   请改为: ddl-auto: validate"
        ERRORS=$((ERRORS + 1))
    else
        echo -e "${GREEN}✅ application.yml 配置安全${NC}"
    fi
else
    echo -e "${YELLOW}⚠️  找不到 application.yml 文件${NC}"
    WARNINGS=$((WARNINGS + 1))
fi

echo ""

# ==============================================
# 6. 检查 .env 文件是否被 Git 跟踪
# ==============================================
echo "📋 检查 .env 文件..."

if [ -f ".env" ]; then
    if git ls-files --error-unmatch .env 2>/dev/null; then
        echo -e "${RED}❌ .env 文件被 Git 跟踪！${NC}"
        echo "   这可能会泄露敏感信息"
        echo "   请运行: git rm --cached .env"
        ERRORS=$((ERRORS + 1))
    else
        echo -e "${GREEN}✅ .env 文件未被 Git 跟踪${NC}"
    fi
else
    echo -e "${YELLOW}⚠️  .env 文件不存在${NC}"
    echo "   建议: cp .env.example .env"
    WARNINGS=$((WARNINGS + 1))
fi

echo ""

# ==============================================
# 7. 检查默认端口
# ==============================================
echo "📋 检查服务端口..."

if [ ! -z "$SERVER_PORT" ] && [ "$SERVER_PORT" != "8080" ]; then
    echo -e "${GREEN}✅ 使用自定义端口: $SERVER_PORT${NC}"
else
    echo -e "${YELLOW}⚠️  使用默认端口 8080${NC}"
    echo "   建议在生产环境使用非标准端口或通过 Nginx 代理"
    WARNINGS=$((WARNINGS + 1))
fi

echo ""

# ==============================================
# 8. 检查 Redis 和数据库连接
# ==============================================
echo "📋 检查服务连接..."

# 检查 Redis
if command -v redis-cli &> /dev/null; then
    if [ -z "$REDIS_PASSWORD" ]; then
        if redis-cli -h ${REDIS_HOST:-localhost} -p ${REDIS_PORT:-6379} ping &> /dev/null; then
            echo -e "${GREEN}✅ Redis 连接正常${NC}"
        else
            echo -e "${RED}❌ Redis 连接失败${NC}"
            ERRORS=$((ERRORS + 1))
        fi
    else
        if redis-cli -h ${REDIS_HOST:-localhost} -p ${REDIS_PORT:-6379} -a "$REDIS_PASSWORD" ping &> /dev/null; then
            echo -e "${GREEN}✅ Redis 连接正常（已认证）${NC}"
        else
            echo -e "${RED}❌ Redis 连接失败${NC}"
            ERRORS=$((ERRORS + 1))
        fi
    fi
else
    echo -e "${YELLOW}⚠️  redis-cli 未安装，跳过 Redis 连接测试${NC}"
fi

echo ""

# ==============================================
# 总结
# ==============================================
echo "======================================"
echo "📊 检查结果汇总"
echo "======================================"

if [ $ERRORS -eq 0 ] && [ $WARNINGS -eq 0 ]; then
    echo -e "${GREEN}🎉 所有检查通过！可以安全上线。${NC}"
    exit 0
elif [ $ERRORS -eq 0 ]; then
    echo -e "${YELLOW}⚠️  发现 $WARNINGS 个警告${NC}"
    echo "建议修复后再上线，但不影响基本功能。"
    exit 1
else
    echo -e "${RED}❌ 发现 $ERRORS 个错误和 $WARNINGS 个警告${NC}"
    echo ""
    echo "请在上线前修复所有错误！"
    echo ""
    echo "快速修复建议："
    echo "  1. 生成新密钥: export JWT_SECRET=\$(openssl rand -base64 64)"
    echo "  2. 修改密码: export DB_PASSWORD='你的强密码'"
    echo "  3. 设置 Redis 密码: export REDIS_PASSWORD='你的Redis密码'"
    echo "  4. 修改 CORS: export CORS_ALLOWED_ORIGINS='https://yourdomain.com'"
    exit 2
fi
