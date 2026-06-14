package com.southcollege.exam.config; // 声明当前类所在的包路径，用于组织项目结构

// 引入项目中的实体类 User，代表系统中的用户信息
import com.southcollege.exam.entity.User;
// 引入用户状态枚举，用于判断账户是否处于激活、禁用等状态
import com.southcollege.exam.enums.UserStatusEnum;
// 引入用户服务接口，用于根据用户ID查询用户信息
import com.southcollege.exam.service.UserService;
// 引入JWT工具类，封装了Token的生成、解析和校验逻辑
import com.southcollege.exam.utils.JwtUtil;
// 引入JWT库中的 Claims 接口，表示Token中包含的声明数据（负载部分）
import io.jsonwebtoken.Claims;
// 引入Jakarta Servlet 中的过滤器链接口，用于将请求传递给下一个过滤器或目标资源
import jakarta.servlet.FilterChain;
// 引入 Jakarta Servlet 异常类，表示过滤器处理过程中可能抛出的Servlet相关异常
import jakarta.servlet.ServletException;
// 引入 Jakarta Servlet 的 HTTP 请求对象，用于获取客户端请求信息
import jakarta.servlet.http.HttpServletRequest;
// 引入 Jakarta Servlet 的 HTTP 响应对象，用于向客户端返回响应
import jakarta.servlet.http.HttpServletResponse;
// Lombok 注解，自动为类中所有 final 字段生成包含全部参数的构造函数
import lombok.RequiredArgsConstructor;
// Spring Security 提供的认证令牌，封装了用户主体、凭证和权限信息
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
// 表示一个简单的授权权限对象，用于构建用户的角色或权限列表
import org.springframework.security.core.authority.SimpleGrantedAuthority;
// Spring Security 上下文持有者，用于在请求线程中存取当前认证信息
import org.springframework.security.core.context.SecurityContextHolder;
// 将当前类标识为 Spring 容器中的一个组件（Bean），会被自动扫描并注册
import org.springframework.stereotype.Component;
// Spring 提供的过滤器基类，保证在一次请求中仅执行一次过滤逻辑
import org.springframework.web.filter.OncePerRequestFilter;

// IOException 用于处理输入输出异常
import java.io.IOException;
// Collections 工具类，用于创建单元素集合，在这里快速构建权限列表
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT认证过滤器，继承 OncePerRequestFilter 确保每次请求只执行一次过滤。
 * 主要功能：
 * 1. 从请求头 Authorization 中提取 Bearer token。
 * 2. 校验 token 有效性，解析出用户信息。
 * 3. 查询用户状态，若账户未激活或禁用则直接拒绝。
 * 4. 将认证信息存入 Spring Security 上下文，同时把用户基本属性写入 request 供后续使用。
 */
@Component // 声明该类为 Spring 的组件，会被自动扫描并纳入容器管理
@RequiredArgsConstructor // 自动生成包含所有 final 字段的构造器，用于依赖注入
public class JwtAuthenticationFilter extends OncePerRequestFilter { // 定义类，继承一次请求执行一次的过滤器基类

    // JWT工具类实例，被 final 修饰，由 Lombok @RequiredArgsConstructor 通过构造器注入
    private final JwtUtil jwtUtil;
    // 用户服务接口实例，同样通过构造器注入
    private final UserService userService;

    /**
     * 核心过滤方法，在每个请求到达时被调用
     *
     * @param request     客户端HTTP请求对象，可从中获取请求头、参数等
     * @param response    服务端HTTP响应对象，可向客户端写入响应内容
     * @param filterChain 过滤器链对象，用于将请求和响应传递到下一个过滤器或最终目标资源
     * @throws ServletException 若发生Servlet相关异常
     * @throws IOException      若发生输入输出异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, // HTTP请求对象，由容器传入
                                    HttpServletResponse response, // HTTP响应对象，由容器传入
                                    FilterChain filterChain) // 过滤器链，用于继续传递请求
            throws ServletException, IOException { // 声明可能抛出的异常

        // 如果是 ，直接放行，不做任何拦截
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) { // 比较请求方法，忽略大小写
            // 将请求和响应直接交给下一个过滤器或目标资源处理，不执行后续认证逻辑
            filterChain.doFilter(request, response);
            // 方法结束，不再执行后续代码
            return;
        }

        // 从请求中提取 Token，若不存在则返回 null
        String token = extractToken(request); // 调用自定义方法解析 Authorization 头
        // 如果没有携带 Token，则视为无需认证的请求（可能是登录接口等），直接放行
        if (token == null) { // 判断提取结果是否为 null
            // 传递到后续过滤器或控制器
            filterChain.doFilter(request, response);
            return; // 结束方法
        }

        // 进入 Token 校验与认证流程，使用 try-catch 捕获所有异常，防止因解析失败导致请求阻塞
        try {
            // 校验 Token 是否合法、未过期
            if (!jwtUtil.validateToken(token)) { // validateToken 方法返回 boolean，true 表示有效
                // Token 无效或过期，向客户端返回 401 状态及 JSON 错误信息
                writeErrorResponse(response, 401, "token无效或已过期");
                return; // 终止请求，不再继续过滤链
            }

            // 解析 Token，获取其中的声明数据（负载）
            Claims claims = jwtUtil.parseToken(token); // parseToken 返回 JWT 的载荷对象
            // 从 Claims 中提取用户ID（Token 的 subject 字段）
            Long userId = Long.valueOf(claims.getSubject()); // getSubject() 返回字符串，转为 Long
            // 从自定义字段 "username" 中提取用户名
            String username = claims.get("username", String.class); // get 方法可指定字段名和返回类型
            // 从自定义字段 "role" 中提取用户角色
            String role = claims.get("role", String.class);

            // 根据解析出的用户ID查询数据库中的最新用户信息
            User user = userService.getById(userId); // getById 是从 Service 层获取用户详情的方法
            // 如果用户不存在（可能被删除），返回 401
            if (user == null) { // 判断查询结果是否为 null
                writeErrorResponse(response, 401, "用户不存在");
                return; // 终止请求
            }
            // 检查用户状态是否处于激活状态（ACTIVE）
            if (!UserStatusEnum.ACTIVE.equals(user.getStatus())) { // 用户状态不等于 ACTIVE 则拦截
                // 账户被禁用或锁定，返回 403 禁止访问
                writeErrorResponse(response, 403, "账户已被禁用或锁定，请联系管理员");
                return;
            }

            // 将解析出的用户基本信息放入 request 属性，方便后续的控制器或服务直接获取
            request.setAttribute("userId", userId); // 存入用户ID，键为 "userId"
            request.setAttribute("username", username); // 存入用户名
            request.setAttribute("role", role); // 存入用户角色

            // 构建 Spring Security 的认证对象，表示当前请求的用户身份和权限
            Map<String, String> details = new HashMap<>();
            details.put("username", username);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            claims.getSubject(), // 主体信息，使用 Token 的 subject（用户ID的字符串形式）
                            null,                // 凭证，已经通过 JWT 校验，可设为 null
                            // 构造权限列表：将角色字符串转换为 "ROLE_" 前缀的授权对象
                            Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role))
                    );
            authentication.setDetails(details);
            // 将认证对象存入 Spring Security 的安全上下文，表明当前线程中已通过认证
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (Exception e) { // 捕获任何解析、校验或数据库查询过程中出现的异常
            // 发生异常时，清空当前的安全上下文，避免遗留无效的认证状态
            SecurityContextHolder.clearContext();
            // 返回统一的 Token 无效响应
            writeErrorResponse(response, 401, "token无效或已过期");
            return; // 终止请求
        }

        // 所有校验通过，将请求和响应交给下一个过滤器或控制器处理
        filterChain.doFilter(request, response);
    }

    /**
     * 从 HTTP 请求的 Authorization 头中提取 Bearer Token
     * @param request HTTP请求对象
     * @return 提取到的 Token 字符串（不含 "Bearer " 前缀），若未找到或格式不对则返回 null
     */
    private String extractToken(HttpServletRequest request) { // 方法参数：当前请求对象
        // 获取请求头 "Authorization" 的值，例如 "Bearer eyJhbGciOi..."
        String bearerToken = request.getHeader("Authorization"); // getHeader 返回指定头部的字符串值
        // 判断是否以 "Bearer " 开头，注意后面有一个空格
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) { // 非空且匹配前缀
            // 截取第7位开始到末尾的子串，去除 "Bearer " 部分，得到纯净的 JWT 字符串
            return bearerToken.substring(7); // "Bearer " 长度为7
        }
        // 不符合 Bearer Token 格式，返回 null
        return null;
    }

    /**
     * 向客户端写入 JSON 格式的错误响应
     * @param response HTTP响应对象
     * @param status   HTTP状态码，如 401 或 403
     * @param message  错误提示消息
     * @throws IOException 可能由 response.getWriter() 或 write 操作抛出
     */
    private void writeErrorResponse(HttpServletResponse response, // 用于设置状态码和写入响应体
                                    int status, // 欲返回的状态码
                                    String message) // 错误信息文本
            throws IOException { // 声明可能抛出的 IOException
        // 设置 HTTP 响应的状态码，如 401 或 403
        response.setStatus(status);
        // 设置响应内容类型为 JSON，并指定 UTF-8 编码，防止中文乱码
        response.setContentType("application/json;charset=UTF-8");
        // 对消息字符串进行简单的转义，避免其中的反斜杠和双引号破坏 JSON 结构
        String escapedMessage = message.replace("\\", "\\\\") // 将单个反斜线替换为双反斜线
                .replace("\"", "\\\""); // 将双引号替换为 \" 转义序列
        // 通过响应对象的 Writer 输出拼接好的 JSON 字符串
        response.getWriter().write(
                // 手动构造 JSON 格式：{"code":状态码,"message":"转义后的消息","data":null}
                "{\"code\":" + status + ",\"message\":\"" + escapedMessage + "\",\"data\":null}"
        );
    }
}