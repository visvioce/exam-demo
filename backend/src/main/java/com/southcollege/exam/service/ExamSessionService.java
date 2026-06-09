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
import com.southcollege.exam.mapper.ExamSessionMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 考试会话服务
 * 管理学生考试记录，包括查询、分页、数据隔离和显示名称填充
 */
@Service
public class ExamSessionService extends ServiceImpl<ExamSessionMapper, ExamSession> {

    private final ExamService examService;
    private final UserService userService;

    public ExamSessionService(@Lazy ExamService examService, UserService userService) {
        this.examService = examService;
        this.userService = userService;
    }

    /**
     * 根据考试ID查询所有考试记录
     */
    public List<ExamSession> getByExamId(Long examId) {
        return lambdaQuery().eq(ExamSession::getExamId, examId).list();
    }

    /**
     * 根据学生ID查询该学生的所有考试记录
     */
    public List<ExamSession> getByStudentId(Long studentId) {
        return lambdaQuery().eq(ExamSession::getStudentId, studentId).list();
    }

    /**
     * 根据考试ID和学生ID查询唯一考试记录
     */
    public ExamSession getByExamIdAndStudentId(Long examId, Long studentId) {
        return lambdaQuery()
                .eq(ExamSession::getExamId, examId)
                .eq(ExamSession::getStudentId, studentId)
                .one();
    }

    /**
     * 批量查询学生在多个考试中的记录，按考试ID分组返回
     *
     * @param examIds   考试ID列表
     * @param studentId 学生ID
     * @return 考试ID -> 考试记录的映射
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
     * 根据教师ID查询关联的考试记录（通过教师创建的考试关联）
     */
    public List<ExamSession> getByTeacherId(Long teacherId) {
        List<Long> examIds = examService.getByTeacherId(teacherId).stream()
                .map(Exam::getId)
                .toList();
        if (examIds.isEmpty()) return List.of();
        return lambdaQuery().in(ExamSession::getExamId, examIds).list();
    }

    /**
     * 查询教师待评分的考试记录
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
     * 查询指定考试中待评分主观题的记录
     */
    public List<ExamSession> getPendingGradingByExamId(Long examId) {
        return lambdaQuery()
                .eq(ExamSession::getExamId, examId)
                .eq(ExamSession::getStatus, ExamSessionStatusEnum.SUBMITTED.getCode())
                .eq(ExamSession::getGradingStatus, GradingStatusEnum.PENDING.getCode())
                .list();
    }

    /**
     * 分页查询考试记录，支持多维度筛选和角色数据隔离
     *
     * @param pageRequest     分页参数
     * @param examId          考试ID（可选）
     * @param studentId       学生ID（可选）
     * @param status          考试状态筛选（可选）
     * @param gradingStatus   评分状态筛选（可选）
     * @param currentUserId   当前登录用户ID
     * @param currentUserRole 当前登录用户角色
     * @return 分页结果，包含学生姓名
     */
    public PageResult<ExamSession> page(PageRequest pageRequest, Long examId, Long studentId,
                                         String status, String gradingStatus,
                                         Long currentUserId, String currentUserRole) {

        Page<ExamSession> page = new Page<>(pageRequest.getCurrent(), pageRequest.getSize());
        LambdaQueryWrapper<ExamSession> wrapper = new LambdaQueryWrapper<>();

        List<Long> teacherExamIds = examService.getByTeacherId(currentUserId).stream()
                .map(Exam::getId)
                .toList();
        if (teacherExamIds.isEmpty()) {
            return PageResult.empty(pageRequest.getCurrent(), pageRequest.getSize());
        }
        wrapper.in(ExamSession::getExamId, teacherExamIds);

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

        wrapper.orderByDesc(ExamSession::getStartedAt);
        Page<ExamSession> result = page(page, wrapper);
        fillStudentNames(result.getRecords());
        return PageResult.from(result);
    }

    /**
     * 批量填充考试记录中的学生显示名称
     *
     * @param sessions 考试记录列表
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

    public List<ExamSession> getExpiredInProgress() {
        List<ExamSession> inProgressSessions = lambdaQuery()
                .eq(ExamSession::getStatus, ExamSessionStatusEnum.IN_PROGRESS.getCode())
                .isNotNull(ExamSession::getStartedAt)
                .list();
        if (inProgressSessions.isEmpty()) return List.of();

        List<Long> examIds = inProgressSessions.stream()
                .map(ExamSession::getExamId)
                .distinct()
                .toList();
        Map<Long, Exam> examMap = examService.listByIds(examIds).stream()
                .collect(Collectors.toMap(Exam::getId, e -> e));

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

    public ExamSessionResponse convertToResponse(ExamSession session) {
        if (session == null) {
            return null;
        }
        ExamSessionResponse response = new ExamSessionResponse();
        BeanUtils.copyProperties(session, response);
        if (session.getAnswers() != null && !session.getAnswers().isEmpty()) {
            response.setAnswers(session.getAnswers().stream()
                    .map(this::convertAnswerToResponse)
                    .toList());
        } else {
            response.setAnswers(List.of());
        }
        return response;
    }

    private ExamSessionResponse.AnswerResponse convertAnswerToResponse(ExamSession.Answer answer) {
        if (answer == null) {
            return null;
        }
        ExamSessionResponse.AnswerResponse answerResponse = new ExamSessionResponse.AnswerResponse();
        BeanUtils.copyProperties(answer, answerResponse);
        return answerResponse;
    }

    public List<ExamSessionResponse> convertToResponses(List<ExamSession> sessions) {
        if (sessions == null || sessions.isEmpty()) {
            return List.of();
        }
        return sessions.stream()
                .map(this::convertToResponse)
                .toList();
    }

    public PageResult<ExamSessionResponse> convertToPageResult(PageResult<ExamSession> pageResult) {
        if (pageResult == null) {
            return PageResult.empty(1, 10);
        }
        PageResult<ExamSessionResponse> response = new PageResult<>();
        response.setRecords(convertToResponses(pageResult.getRecords()));
        response.setTotal(pageResult.getTotal());
        response.setSize(pageResult.getSize());
        response.setCurrent(pageResult.getCurrent());
        response.setPages(pageResult.getPages());
        response.setHasNext(pageResult.getHasNext());
        response.setHasPrevious(pageResult.getHasPrevious());
        return response;
    }
}