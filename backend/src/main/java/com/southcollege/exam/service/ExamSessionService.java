package com.southcollege.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.southcollege.exam.dto.request.PageRequest;
import com.southcollege.exam.dto.response.PageResult;
import com.southcollege.exam.entity.Exam;
import com.southcollege.exam.entity.ExamSession;
import com.southcollege.exam.enums.RoleEnum;
import com.southcollege.exam.mapper.ExamSessionMapper;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        return baseMapper.selectByExamId(examId);
    }

    /**
     * 根据学生ID查询该学生的所有考试记录
     */
    public List<ExamSession> getByStudentId(Long studentId) {
        return baseMapper.selectByStudentId(studentId);
    }

    /**
     * 根据考试ID和学生ID查询唯一考试记录
     */
    public ExamSession getByExamIdAndStudentId(Long examId, Long studentId) {
        return baseMapper.selectByExamIdAndStudentId(examId, studentId);
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

        List<ExamSession> sessions = baseMapper.selectByExamIdsAndStudentId(examIds, studentId);
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
        return baseMapper.selectByTeacherId(teacherId);
    }

    /**
     * 查询教师待评分的考试记录
     */
    public List<ExamSession> getPendingGradingSessions(Long teacherId) {
        return baseMapper.selectPendingGradingByTeacherId(teacherId);
    }

    /**
     * 查询指定考试中待评分主观题的记录
     */
    public List<ExamSession> getPendingGradingByExamId(Long examId) {
        return lambdaQuery()
                .eq(ExamSession::getExamId, examId)
                .eq(ExamSession::getStatus, "SUBMITTED")
                .eq(ExamSession::getGradingStatus, "PENDING")
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
        boolean isAdmin = RoleEnum.ADMIN.getCode().equals(currentUserRole);

        Page<ExamSession> page = new Page<>(pageRequest.getCurrent(), pageRequest.getSize());
        LambdaQueryWrapper<ExamSession> wrapper = new LambdaQueryWrapper<>();

        if (!isAdmin) {
            List<Long> teacherExamIds = examService.getByTeacherId(currentUserId).stream()
                    .map(Exam::getId)
                    .toList();
            if (teacherExamIds.isEmpty()) {
                return PageResult.empty(pageRequest.getCurrent(), pageRequest.getSize());
            }
            wrapper.in(ExamSession::getExamId, teacherExamIds);
        }

        if (examId != null) {
            Exam exam = examService.getById(examId);
            if (exam == null || (!isAdmin && !exam.getTeacherId().equals(currentUserId))) {
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
}