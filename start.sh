#!/bin/bash
# =====================================================
# 南方职业学院在线考试系统 - 一键启动脚本
# MySQL 由 systemd 系统服务管理
# 使用方法: bash start.sh
# =====================================================
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
MYSQL_PORT=3306
MYSQL_PASSWORD="${MYSQL_PASSWORD:-123456}"
BACKEND_PORT=8080
FRONTEND_PORT=5173

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_step()  { echo -e "${BLUE}[STEP]${NC} $1"; }

# 辅助函数：检查端口是否有进程在监听
port_listening() {
    local port="$1"
    ss -tlnp 2>/dev/null | grep -q ":${port} " || return 1
}

# 辅助函数：通过进程名获取 PID（返回所有匹配 PID，用空格分隔）
find_pids_by_pattern() {
    local pattern="$1"
    pgrep -f "$pattern" 2>/dev/null | tr '\n' ' ' || true
}

echo "========================================="
echo "  南方职业学院在线考试系统 - 启动中..."
echo "========================================="

# ---------- 1. MySQL ----------
echo ""
log_step "1/3 检查 MySQL..."

if systemctl is-active --quiet exam-mysql 2>/dev/null; then
    log_info "MySQL 已在运行 (systemd 管理)"
elif systemctl is-active --quiet mysql 2>/dev/null || systemctl is-active --quiet mysqld 2>/dev/null; then
    log_info "MySQL 已在运行 (系统默认服务)"
else
    log_warn "MySQL 服务未运行，尝试启动..."
    sudo systemctl start exam-mysql 2>/dev/null || sudo systemctl start mysql 2>/dev/null || sudo systemctl start mysqld 2>/dev/null || {
        log_error "MySQL 启动失败，请检查服务状态"
        exit 1
    }
    for i in $(seq 1 15); do
        if mysqladmin ping -h 127.0.0.1 -P $MYSQL_PORT -u root -p"$MYSQL_PASSWORD" --silent 2>/dev/null; then
            break
        fi
        sleep 1
    done
    log_info "MySQL 启动成功"
fi

# ---------- 2. Backend ----------
echo ""
log_step "2/3 检查后端服务..."

BACKEND_RUNNING=false
if port_listening "$BACKEND_PORT"; then
    BACKEND_RUNNING=true
fi

if [ "$BACKEND_RUNNING" = true ]; then
    log_info "后端已在运行 (端口 $BACKEND_PORT)"
else
    log_info "启动后端服务 (Spring Boot)..."

    # 清理可能占用端口的僵尸进程（使用 pgrep 精确查找）
    for pid in $(pgrep -f "ExamApplication" 2>/dev/null || true); do
        kill "$pid" 2>/dev/null || true
    done
    sleep 1

    cd "$PROJECT_DIR/backend"
    nohup mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev" \
        > "$PROJECT_DIR/backend/backend.log" 2>&1 &

    # 等待后端启动（最多 60 秒）
    for i in $(seq 1 30); do
        if port_listening "$BACKEND_PORT"; then
            break
        fi
        sleep 2
    done

    if port_listening "$BACKEND_PORT"; then
        log_info "后端启动成功 (端口 $BACKEND_PORT)"
    else
        log_error "后端启动超时，请查看日志: backend/backend.log"
        exit 1
    fi
fi

# ---------- 3. Frontend ----------
echo ""
log_step "3/3 检查前端服务..."

cd "$PROJECT_DIR/frontend"

# 在 Vite 启动前先扫描已运行的 Vite 进程，检查其监听端口
FRONTEND_ACTUAL_PORT=""
for pid in $(pgrep -f "node.*vite" 2>/dev/null || true); do
    port=$(ss -tlnp 2>/dev/null | grep "pid=$pid" | awk '{print $4}' | grep -oP ':\K\d+' | head -1 || true)
    if [ -n "$port" ]; then
        FRONTEND_ACTUAL_PORT="$port"
        break
    fi
done

if [ -n "$FRONTEND_ACTUAL_PORT" ] && curl -s -o /dev/null -w "%{http_code}" "http://localhost:$FRONTEND_ACTUAL_PORT/" 2>/dev/null | grep -q "200"; then
    log_info "前端已在运行 (端口 $FRONTEND_ACTUAL_PORT)"
else
    log_info "启动前端服务 (Vite)..."
    nohup npm run dev > "$PROJECT_DIR/frontend/frontend.log" 2>&1 &

    # 等待 Vite 启动，自动检测实际端口
    FRONTEND_ACTUAL_PORT=""
    for i in $(seq 1 15); do
        for pid in $(pgrep -f "node.*vite" 2>/dev/null || true); do
            port=$(ss -tlnp 2>/dev/null | grep "pid=$pid" | awk '{print $4}' | grep -oP ':\K\d+' | head -1 || true)
            if [ -n "$port" ] && curl -s -o /dev/null -w "%{http_code}" "http://localhost:$port/" 2>/dev/null | grep -q "200"; then
                FRONTEND_ACTUAL_PORT="$port"
                break 2
            fi
        done
        sleep 1
    done

    if [ -n "$FRONTEND_ACTUAL_PORT" ]; then
        log_info "前端启动成功 (端口 $FRONTEND_ACTUAL_PORT)"
    else
        log_error "前端启动超时，请查看日志: frontend/frontend.log"
        exit 1
    fi
fi

# ---------- 4. 验证 ----------
echo ""
echo "========================================="
echo "  服务状态检查"
echo "========================================="

if mysqladmin ping -h 127.0.0.1 -P $MYSQL_PORT -u root -p"$MYSQL_PASSWORD" --silent 2>/dev/null; then
    echo -e "  MySQL   : ${GREEN}✅ 运行中${NC} (127.0.0.1:$MYSQL_PORT)"
else
    echo -e "  MySQL   : ${RED}❌ 未启动${NC}"
fi

if port_listening "$BACKEND_PORT"; then
    echo -e "  Backend : ${GREEN}✅ 运行中${NC} (http://localhost:$BACKEND_PORT)"
    echo    "            Swagger: http://localhost:$BACKEND_PORT/swagger-ui/index.html"
else
    echo -e "  Backend : ${RED}❌ 未启动${NC}"
fi

if [ -n "$FRONTEND_ACTUAL_PORT" ] && curl -s -o /dev/null -w "%{http_code}" "http://localhost:$FRONTEND_ACTUAL_PORT/" 2>/dev/null | grep -q "200"; then
    echo -e "  Frontend: ${GREEN}✅ 运行中${NC} (http://localhost:$FRONTEND_ACTUAL_PORT)"
else
    echo -e "  Frontend: ${RED}❌ 未启动${NC}"
fi

echo ""
echo "========================================="
echo "  启动完成！浏览器访问:"
echo -e "  ${GREEN}http://localhost:${FRONTEND_ACTUAL_PORT:-$FRONTEND_PORT}${NC}"
echo "========================================="