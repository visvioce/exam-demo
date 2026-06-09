#!/bin/bash
# =====================================================
# 南方职业学院在线考试系统 - 一键停止脚本
# 使用方法: bash stop.sh
# =====================================================
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_ok()   { echo -e "${GREEN}[OK]${NC}    $*"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }

# 辅助函数：端口是否被占用
port_in_use() {
    local port="$1"
    ss -tlnp 2>/dev/null | grep -q ":${port} " || return 1
}

# 辅助函数：优雅停止进程（先 SIGTERM，超时后 SIGKILL）
kill_graceful() {
    local pid="$1"
    local name="${2:-进程}"
    if ! kill -0 "$pid" 2>/dev/null; then
        return 1
    fi
    # 先尝试 SIGTERM
    kill "$pid" 2>/dev/null || true
    # 等待最多 3 秒
    for i in $(seq 1 30); do
        if ! kill -0 "$pid" 2>/dev/null; then
            return 0
        fi
        sleep 0.1
    done
    # 还没退出，上 SIGKILL
    kill -9 "$pid" 2>/dev/null && return 0 || true
    return 1
}

echo -e "${YELLOW}正在停止所有服务...${NC}"
echo ""

# ---------- 1. 停止前端 ----------
echo "停止前端服务..."
FRONTEND_STOPPED=false

for pid in $(pgrep -f "node.*vite" 2>/dev/null || true); do
    if kill_graceful "$pid" "前端 Vite"; then
        log_ok "前端 (PID $pid) 已停止"
        FRONTEND_STOPPED=true
    fi
done

if [ "$FRONTEND_STOPPED" = false ]; then
    log_warn "前端未运行或已停止"
fi

# ---------- 2. 停止后端 ----------
echo ""
echo "停止后端服务..."

BACKEND_STOPPED=false

# 停止 Spring Boot 应用进程
for pid in $(pgrep -f "ExamApplication" 2>/dev/null || true); do
    if kill_graceful "$pid" "后端应用"; then
        log_ok "后端应用 (PID $pid) 已停止"
        BACKEND_STOPPED=true
    fi
done

# 停止 Maven spring-boot:run 启动进程
for pid in $(pgrep -f "spring-boot:run" 2>/dev/null || true); do
    if kill_graceful "$pid" "Maven 启动进程"; then
        log_ok "Maven 启动进程 (PID $pid) 已停止"
        BACKEND_STOPPED=true
    fi
done

if [ "$BACKEND_STOPPED" = false ]; then
    log_warn "后端未运行或已停止"
fi

# 等待端口释放
sleep 2

# ---------- 3. 停止 MySQL（可选）----------
echo ""
echo "检查 MySQL..."

# 默认不停止 MySQL（因为配置了开机自启）
# 如果需要停止，取消下面的注释
# if systemctl is-active --quiet exam-mysql 2>/dev/null; then
#     sudo systemctl stop exam-mysql
#     log_ok "MySQL 已停止"
# elif systemctl is-active --quiet mysql 2>/dev/null || systemctl is-active --quiet mysqld 2>/dev/null; then
#     sudo systemctl stop mysql 2>/dev/null || sudo systemctl stop mysqld 2>/dev/null
#     log_ok "MySQL 已停止"
# else
#     log_warn "MySQL 未运行"
# fi

log_warn "MySQL 保持运行（开机自启服务，如需停止请手动执行: sudo systemctl stop exam-mysql）"

# ---------- 4. 验证 ----------
echo ""
echo "========================================="
echo "  停止完成"
echo "========================================="

# 检查端口是否已释放
PORTS_FREE=true
for port in 8080 5173 5174 5175 5176; do
    if port_in_use "$port"; then
        echo -e "  端口 $port : ${YELLOW}⚠️ 仍被占用${NC}"
        PORTS_FREE=false
    else
        echo -e "  端口 $port : ${GREEN}✅ 已释放${NC}"
    fi
done

echo ""
if [ "$PORTS_FREE" = true ]; then
    echo -e "${GREEN}所有服务已停止${NC}"
else
    echo -e "${YELLOW}部分端口仍被占用，可能需要手动检查${NC}"
fi