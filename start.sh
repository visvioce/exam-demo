#!/bin/bash
set -euo pipefail

cd "$(dirname "$0")"
BASE_DIR="$(pwd)"
PID_DIR="$BASE_DIR/.pids"
LOG_DIR="$BASE_DIR/logs"

mkdir -p "$PID_DIR" "$LOG_DIR"

# ── 配置 ──────────────────────────────
BACKEND_JAR="$BASE_DIR/backend/target/exam-system-1.0.0.jar"
BACKEND_URL="http://localhost:8080/api/carousels/active"
FRONTEND_URL="http://localhost:5173"
BACKEND_PORT=8080
FRONTEND_PORT=5173
JWT_SECRET="exam_system_jwt_secret_key_256_bits_for_graduation_project_2024"
JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk}"

# ── 颜色 ──────────────────────────────
R='\033[0;31m'
G='\033[0;32m'
Y='\033[1;33m'
C='\033[0;36m'
NC='\033[0m'

log_info()  { echo -e "${C}[INFO]${NC}  $*"; }
log_ok()    { echo -e "${G}[OK]${NC}    $*"; }
log_warn()  { echo -e "${Y}[WARN]${NC}  $*"; }
log_error() { echo -e "${R}[ERROR]${NC} $*"; }

# ── 工具函数 ──────────────────────────

usage() {
    cat <<EOF
用法: $0 {start|stop|restart|status|logs}

  start    编译并启动前后端服务
  stop     停止所有服务
  restart  重启所有服务
  status   查看服务运行状态
  logs     查看实时日志 (后端+前端)
EOF
    exit 1
}

check_command() {
    if ! command -v "$1" &>/dev/null; then
        log_error "未找到命令: $1，请确保已安装"
        exit 1
    fi
}

wait_for_port() {
    local port=$1 name=$2 max_wait=${3:-30}
    log_info "等待 $name 端口 $port 就绪..."
    for ((i=0; i<max_wait; i++)); do
        if nc -z localhost "$port" 2>/dev/null; then
            log_ok "$name 已就绪"
            return 0
        fi
        sleep 1
    done
    log_error "$name 在 ${max_wait}s 内未就绪"
    return 1
}

kill_by_pidfile() {
    local pidfile=$1 name=$2
    if [[ -f "$pidfile" ]]; then
        local pid
        pid=$(cat "$pidfile")
        if kill -0 "$pid" 2>/dev/null; then
            log_info "停止 $name (PID: $pid)..."
            kill "$pid" 2>/dev/null || true
            local count=0
            while kill -0 "$pid" 2>/dev/null && [[ $count -lt 10 ]]; do
                sleep 1
                ((count++))
            done
            if kill -0 "$pid" 2>/dev/null; then
                log_warn "$name 未响应，强制终止..."
                kill -9 "$pid" 2>/dev/null || true
            fi
            log_ok "$name 已停止"
        fi
        rm -f "$pidfile"
    fi
}

# ── 子命令: stop ──────────────────────

cmd_stop() {
    log_info "正在停止服务..."
    kill_by_pidfile "$PID_DIR/backend.pid" "后端"
    kill_by_pidfile "$PID_DIR/frontend.pid" "前端"
    log_ok "所有服务已停止"
}

# ── 子命令: status ────────────────────

cmd_status() {
    local backend_ok=false frontend_ok=false

    echo ""
    echo "========== 服务状态 =========="

    # 后端状态
    if [[ -f "$PID_DIR/backend.pid" ]]; then
        local pid
        pid=$(cat "$PID_DIR/backend.pid")
        if kill -0 "$pid" 2>/dev/null; then
            if curl -s "$BACKEND_URL" &>/dev/null; then
                log_ok "后端运行正常  (PID: $pid, http://localhost:$BACKEND_PORT)"
                backend_ok=true
            else
                log_warn "后端进程存活但接口未响应 (PID: $pid)"
            fi
        else
            log_error "后端进程已退出 (PID: $pid)"
        fi
    else
        log_error "后端未启动"
    fi

    # 前端状态
    if [[ -f "$PID_DIR/frontend.pid" ]]; then
        local pid
        pid=$(cat "$PID_DIR/frontend.pid")
        if kill -0 "$pid" 2>/dev/null; then
            if curl -s -o /dev/null "$FRONTEND_URL" 2>/dev/null; then
                log_ok "前端运行正常  (PID: $pid, http://localhost:$FRONTEND_PORT)"
                frontend_ok=true
            else
                log_warn "前端进程存活但未响应 (PID: $pid)"
            fi
        else
            log_error "前端进程已退出 (PID: $pid)"
        fi
    else
        log_error "前端未启动"
    fi

    echo "=============================="
    echo ""

    if $backend_ok && $frontend_ok; then
        echo "账号: admin / 123456"
        echo ""
        return 0
    else
        return 1
    fi
}

# ── 子命令: logs ──────────────────────

cmd_logs() {
    local backend_log="$LOG_DIR/backend.log"
    local frontend_log="$LOG_DIR/frontend.log"

    if [[ ! -f "$backend_log" && ! -f "$frontend_log" ]]; then
        log_warn "暂无日志文件"
        exit 0
    fi

    log_info "按 Ctrl+C 退出日志查看"
    tail -f "$backend_log" "$frontend_log" 2>/dev/null || true
}

# ── 子命令: start ─────────────────────

cmd_start() {
    # 检查依赖
    check_command java
    check_command mvn
    check_command npm
    check_command nc

    # 检查 Java 版本
    local java_version
    java_version=$($JAVA_HOME/bin/java -version 2>&1 | head -n1 | grep -oP '"\K[^"]+' || true)
    if [[ -z "$java_version" ]]; then
        log_error "无法检测 Java 版本，请检查 JAVA_HOME"
        exit 1
    fi
    log_info "Java 版本: $java_version"

    # 检查 MySQL
    if ! pgrep -x mysqld &>/dev/null; then
        log_warn "MySQL 未运行"
        read -rp "是否继续? (y/n) " reply
        if [[ ! "$reply" =~ ^[Yy]$ ]]; then
            exit 1
        fi
    else
        log_ok "MySQL 运行中"
    fi

    # 先停止旧进程
    cmd_stop
    sleep 1

    # 加载 .env（如果存在）
    if [[ -f "$BASE_DIR/backend/.env" ]]; then
        log_info "加载 backend/.env..."
        set -a
        # shellcheck source=/dev/null
        source "$BASE_DIR/backend/.env"
        set +a
    fi
    export JWT_SECRET="$JWT_SECRET"

    # ── 编译后端 ──
    log_info "编译后端..."
    cd "$BASE_DIR/backend"
    export JAVA_HOME PATH="$JAVA_HOME/bin:$PATH"
    if ! mvn clean package -DskipTests -q; then
        log_error "后端编译失败"
        exit 1
    fi
    log_ok "后端编译完成"

    # ── 启动后端 ──
    log_info "启动后端 (Spring Boot)..."
    cd "$BASE_DIR"
    nohup "$JAVA_HOME/bin/java" -jar "$BACKEND_JAR" \
        --spring.profiles.active=dev \
        > "$LOG_DIR/backend.log" 2>&1 &
    BACKEND_PID=$!
    echo $BACKEND_PID > "$PID_DIR/backend.pid"
    disown $BACKEND_PID 2>/dev/null || true

    if ! wait_for_port "$BACKEND_PORT" "后端" 30; then
        log_error "后端启动失败，查看日志: $LOG_DIR/backend.log"
        cmd_stop
        exit 1
    fi

    # 验证接口
    if curl -s "$BACKEND_URL" &>/dev/null; then
        log_ok "后端接口响应正常"
    else
        log_warn "后端端口已开放，但 /api/carousels/active 未响应（可能无此接口）"
    fi

    # ── 启动前端 ──
    log_info "启动前端 (Vue + Vite)..."
    cd "$BASE_DIR/frontend"
    nohup npm run dev > "$LOG_DIR/frontend.log" 2>&1 &
    FRONTEND_PID=$!
    echo $FRONTEND_PID > "$PID_DIR/frontend.pid"
    disown $FRONTEND_PID 2>/dev/null || true

    if ! wait_for_port "$FRONTEND_PORT" "前端" 30; then
        log_error "前端启动失败，查看日志: $LOG_DIR/frontend.log"
        cmd_stop
        exit 1
    fi

    # ── 完成 ──
    echo ""
    echo "=========================================="
    log_ok " 服务启动完成"
    echo " 前端: $FRONTEND_URL"
    echo " 后端: http://localhost:$BACKEND_PORT"
    echo " 账号: admin / 123456"
    echo "=========================================="
    echo ""
    log_info "管理命令:"
    echo "  停止:   $0 stop"
    echo "  状态:   $0 status"
    echo "  日志:   $0 logs"
    echo "  重启:   $0 restart"
    echo ""
}

# ── 子命令: restart ───────────────────

cmd_restart() {
    cmd_stop
    sleep 2
    cmd_start
}

# ── 主入口 ────────────────────────────

[[ $# -eq 0 ]] && usage

case "$1" in
    start)   cmd_start ;;
    stop)    cmd_stop ;;
    restart) cmd_restart ;;
    status)  cmd_status ;;
    logs)    cmd_logs ;;
    *)       usage ;;
esac
