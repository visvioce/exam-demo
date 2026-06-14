package com.southcollege.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.southcollege.exam.dto.request.PageRequest;
import com.southcollege.exam.dto.response.PageResult;
import com.southcollege.exam.dto.response.ExamSessionResponse;
import com.southcollege.exam.entity.Exam;
import com.southcollege.exam.entity.ExamSession;
import com.southcollege.exam.enums.ExamSessionStatusEnum;
import com.southcollege.exam.enums.GradingStatusEnum;
import com.southcollege.exam.exception.BusinessException;
import com.southcollege.exam.mapper.ExamSessionMapper;
import com.southcollege.exam.mapstruct.ExamSessionDtoMapper;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 考试会话服务
 * <p>
 * 负责管理学生考试会话（ExamSession）的全生命周期操作，包括：
 * <ul>
 *     <li>考试会话的查询（按考试、学生、教师等维度）</li>
 *     <li>待评分会话查询</li>
 *     <li>分页查询（支持多条件筛选、教师数据隔离）</li>
 *     <li>过期会话检测（用于定时任务强制提交）</li>
 *     <li>实体与 DTO 之间的转换</li>
 * </ul>
 * </p>
 * <p>
 * 注意：本服务不直接操作考试（Exam）的创建/发布等流程，相关能力依赖 {@link ExamService}。
 * </p>
 *
 * @author SouthCollege
 * @since 1.0
 */
@Service
public class ExamSessionService extends ServiceImpl<ExamSessionMapper, ExamSession> {

    private final ExamService examService;
    private final UserService userService;
    private final ExamSessionDtoMapper examSessionDtoMapper;

    /**
     * 构造函数，注入所需的服务
     *
     * @param examService 考试服务，用于查询考试信息（需使用 @Lazy 避免循环依赖）
     * @param userService 用户服务，用于获取学生姓名等用户信息
     * @param examSessionDtoMapper MapStruct 映射器
     */
    public ExamSessionService(@Lazy ExamService examService, UserService userService,
                              ExamSessionDtoMapper examSessionDtoMapper) {
        this.examService = examService;
        this.userService = userService;
        this.examSessionDtoMapper = examSessionDtoMapper;
    }

    /**
     * 根据考试 ID 查询所有考试会话记录
     *
     * @param examId 考试 ID
     * @return 考试会话列表，可能为空列表
     */
    public List<ExamSession> getByExamId(Long examId) {
        return lambdaQuery().eq(ExamSession::getExamId, examId).list();
    }

    /**
     * 获取考试会话详情（带权限校验）
     * <p>
     * 教师/管理员：只能查看自己创建的考试的会话记录
     * 学生：只能查看自己的会话记录
     * </p>
     *
     * @param sessionId 会话ID
     * @param userId    当前用户ID
     * @return 考试会话
     * @throws BusinessException 会话不存在、考试不存在或无权限
     */
    public ExamSession getByIdWithPermission(Long sessionId, Long userId) {
        ExamSession session = getById(sessionId);
        if (session == null) {
            throw new BusinessException("考试记录不存在");
        }

        Exam exam = examService.getById(session.getExamId());
        if (exam == null) {
            throw new BusinessException("考试不存在");
        }

        boolean isTeacher = userId.equals(exam.getTeacherId());
        boolean isOwner = session.getStudentId().equals(userId);

        if (!isTeacher && !isOwner) {
            throw new BusinessException("无权查看该考试记录");
        }

        return session;
    }

    /**
     * 根据学生 ID 查询该学生的所有考试会话记录
     *
     * @param studentId 学生 ID
     * @return 考试会话列表，可能为空列表
     */
    public List<ExamSession> getByStudentId(Long studentId) {
        return lambdaQuery().eq(ExamSession::getStudentId, studentId).list();
    }

    /**
     * 根据考试 ID 和学生 ID 查询唯一的考试会话记录
     * <p>每个学生对同一场考试只会有一条会话记录</p>
     *
     * @param examId    考试 ID
     * @param studentId 学生 ID
     * @return 考试会话实体，如果不存在则返回 null
     */
    public ExamSession getByExamIdAndStudentId(Long examId, Long studentId) {
        return lambdaQuery()
                .eq(ExamSession::getExamId, examId)
                .eq(ExamSession::getStudentId, studentId)
                .one();
    }

    /**
     * 批量查询某个学生在多个考试中的会话记录，并按考试 ID 分组返回
     * <p>
     * 此方法用于优化“我的考试列表”等场景，避免循环调用数据库。
     * </p>
     *
     * @param examIds   考试 ID 列表（不能为空）
     * @param studentId 学生 ID
     * @return 映射：考试 ID -> 该学生的考试会话（若某考试无记录，则映射中不包含该键）
     */
    public Map<Long, ExamSession> getByExamIdsAndStudentId(List<Long> examIds, Long studentId) {
        if (examIds == null || examIds.isEmpty()) {
            return new HashMap<>();
        }

        List<ExamSession> sessions = lambdaQuery()
                .in(ExamSession::getExamId, examIds)
                .eq(ExamSession::getStudentId, studentId)
                .list();
        Map<Long, ExamSession> sessionMap = new HashMap<>();
        for (ExamSession session : sessions) {
            sessionMap.put(session.getExamId(), session);
        }
        return sessionMap;
    }

    /**
     * 根据教师 ID 查询所有关联的考试会话记录
     * <p>通过教师创建的考试间接关联：先查询该教师的所有考试，再查询这些考试下的会话。</p>
     *
     * @param teacherId 教师 ID
     * @return 考试会话列表，可能为空列表
     */
    public List<ExamSession> getByTeacherId(Long teacherId) {
        List<Long> examIds = examService.getByTeacherId(teacherId).stream()
                .map(Exam::getId)
                .toList();
        if (examIds.isEmpty()) return List.of();
        return lambdaQuery().in(ExamSession::getExamId, examIds).list();
    }

    /**
     * 查询某个教师所有待评分的考试会话
     * <p>条件：考试状态为“已提交”，且评分状态为“待评分”。</p>
     *
     * @param teacherId 教师 ID
     * @return 待评分会话列表
     */
    public List<ExamSession> getPendingGradingSessions(Long teacherId) {
        List<Long> examIds = examService.getByTeacherId(teacherId).stream()
                .map(Exam::getId)
                .toList();
        if (examIds.isEmpty()) return List.of();
        return lambdaQuery()
                .in(ExamSession::getExamId, examIds)
                .eq(ExamSession::getStatus, ExamSessionStatusEnum.SUBMITTED.getCode())
                .eq(ExamSession::getGradingStatus, GradingStatusEnum.PENDING.getCode())
                .list();
    }

    /**
     * 查询指定考试中所有待评分主观题的会话记录
     * <p>教师阅卷时，用于获取某场考试下所有需要主观题评分的考生记录。</p>
     *
     * @param examId 考试 ID
     * @return 待评分会话列表
     */
    public List<ExamSession> getPendingGradingByExamId(Long examId) {
        return lambdaQuery()
                .eq(ExamSession::getExamId, examId)
                .eq(ExamSession::getStatus, ExamSessionStatusEnum.SUBMITTED.getCode())
                .eq(ExamSession::getGradingStatus, GradingStatusEnum.PENDING.getCode())
                .list();
    }

    /**
     * 分页查询考试会话记录（教师端使用）
     * <p>支持按考试 ID、学生 ID、会话状态、评分状态筛选，并自动进行教师数据隔离：
     * 当前登录用户只能看到自己创建的考试所对应的会话记录。</p>
     *
     * @param pageRequest     分页参数（当前页、每页大小、排序规则等）
     * @param examId          考试 ID（可选，若提供则只查询该考试下的会话）
     * @param studentId       学生 ID（可选，若提供则只查询该学生的会话）
     * @param status          会话状态筛选（可选，对应 ExamSessionStatusEnum）
     * @param gradingStatus   评分状态筛选（可选，对应 GradingStatusEnum）
     * @param currentUserId   当前登录用户 ID（用于数据隔离）
     * @param currentUserRole 当前登录用户角色（本实现中仅教师可用，但保留参数以便扩展）
     * @return 分页结果，其中每个会话会额外填充学生姓名（通过 fillStudentNames 完成）
     */
    public PageResult<ExamSession> page(PageRequest pageRequest, Long examId, Long studentId,
                                         String status, String gradingStatus,
                                         Long currentUserId, String currentUserRole) {

        Page<ExamSession> page = new Page<>(pageRequest.getCurrent(), pageRequest.getSize());
        LambdaQueryWrapper<ExamSession> wrapper = new LambdaQueryWrapper<>();

        // 数据隔离：只查询当前教师创建的考试所关联的会话
        List<Long> teacherExamIds = examService.getByTeacherId(currentUserId).stream()
                .map(Exam::getId)
                .toList();
        if (teacherExamIds.isEmpty()) {
            return PageResult.empty(pageRequest.getCurrent(), pageRequest.getSize());
        }
        wrapper.in(ExamSession::getExamId, teacherExamIds);

        // 可选条件：考试 ID 筛选（额外校验该考试是否确实属于当前教师）
        if (examId != null) {
            Exam exam = examService.getById(examId);
            if (exam == null || (exam.getTeacherId() == null || !exam.getTeacherId().equals(currentUserId))) {
                return PageResult.empty(pageRequest.getCurrent(), pageRequest.getSize());
            }
            wrapper.eq(ExamSession::getExamId, examId);
        }

        if (studentId != null) {
            wrapper.eq(ExamSession::getStudentId, studentId);
        }

        if (StringUtils.isNotBlank(status)) {
            wrapper.eq(ExamSession::getStatus, status);
        }

        if (StringUtils.isNotBlank(gradingStatus)) {
            wrapper.eq(ExamSession::getGradingStatus, gradingStatus);
        }

        // 默认按开始时间倒序
        wrapper.orderByDesc(ExamSession::getStartedAt);
        Page<ExamSession> result = page(page, wrapper);
        fillStudentNames(result.getRecords());
        return PageResult.from(result);
    }

    /**
     * 批量填充考试会话记录中的学生显示名称
     * <p>通过 userService 批量查询学生姓名，然后设置到每个会话的 studentName 字段中。</p>
     *
     * @param sessions 考试会话列表，可能为 null 或空
     */
    public void fillStudentNames(List<ExamSession> sessions) {
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        List<Long> studentIds = sessions.stream()
                .map(ExamSession::getStudentId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        Map<Long, String> nameMap = userService.getDisplayNameMap(studentIds);
        for (ExamSession session : sessions) {
            if (session.getStudentId() == null) {
                continue;
            }
            session.setStudentName(nameMap.get(session.getStudentId()));
        }
    }

    /**
     * 获取所有已超时但仍处于“进行中”状态的考试会话
     * <p>用于定时任务自动强制提交。超时规则：
     * <ul>
     *     <li>如果考试设置了 duration（时长），则截止时间为 开始时间 + duration 分钟 + 30秒缓冲</li>
     *     <li>如果考试没有 duration，则使用考试的 endedAt + 30秒缓冲</li>
     * </ul>
     * </p>
     *
     * @return 过期的考试会话列表
     */
    public List<ExamSession> getExpiredInProgress() {
        // 1. 查询所有进行中的会话
        List<ExamSession> inProgressSessions = lambdaQuery()
                .eq(ExamSession::getStatus, ExamSessionStatusEnum.IN_PROGRESS.getCode())
                .isNotNull(ExamSession::getStartedAt)
                .list();
        if (inProgressSessions.isEmpty()) return List.of();

        // 2. 批量获取这些会话对应的考试信息
        List<Long> examIds = inProgressSessions.stream()
                .map(ExamSession::getExamId)
                .distinct()
                .toList();
        Map<Long, Exam> examMap = examService.listByIds(examIds).stream()
                .collect(Collectors.toMap(Exam::getId, e -> e));

        // 3. 判断每个会话是否已超时
        LocalDateTime now = LocalDateTime.now();
        return inProgressSessions.stream()
                .filter(session -> {
                    Exam exam = examMap.get(session.getExamId());
                    if (exam == null) return false;
                    LocalDateTime deadline;
                    if (exam.getDuration() != null) {
                        deadline = session.getStartedAt().plusMinutes(exam.getDuration()).plusSeconds(30);
                    } else {
                        deadline = exam.getEndedAt() != null
                                ? exam.getEndedAt().plusSeconds(30) : null;
                    }
                    return deadline != null && now.isAfter(deadline);
                })
                .toList();
    }

    /**
     * 将考试会话实体转换为响应 DTO（单条记录）
     * <p>会递归转换 answers 列表中的每个 Answer 实体。</p>
     *
     * @param session 考试会话实体，可能为 null
     * @return 转换后的响应对象，若入参为 null 则返回 null
     */
    public ExamSessionResponse convertToResponse(ExamSession session) {
        if (session == null) return null;
        return examSessionDtoMapper.toResponse(session);
    }

    /**
     * 批量将考试会话实体列表转换为响应 DTO 列表
     *
     * @param sessions 考试会话实体列表，可能为 null 或空
     * @return 转换后的响应列表，若入参为空则返回空列表
     */
    public List<ExamSessionResponse> convertToResponses(List<ExamSession> sessions) {
        if (sessions == null || sessions.isEmpty()) return List.of();
        return examSessionDtoMapper.toResponseList(sessions);
    }

    /**
     * 将分页结果中的实体转换为响应 DTO 的分页结果
     *
     * @param pageResult 原始分页结果（包含 ExamSession 实体）
     * @return 转换后的分页结果（包含 ExamSessionResponse），若入参为 null 则返回空分页
     */
    public PageResult<ExamSessionResponse> convertToPageResult(PageResult<ExamSession> pageResult) {
        return PageResult.map(pageResult, this::convertToResponses);
    }
}