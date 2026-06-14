package com.southcollege.exam.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.southcollege.exam.dto.request.ExamCreateRequest;
import com.southcollege.exam.dto.request.ExamUpdateRequest;
import com.southcollege.exam.dto.request.GradeSubjectiveRequest;
import com.southcollege.exam.dto.request.PageRequest;
import com.southcollege.exam.dto.response.ExamResponse;
import com.southcollege.exam.dto.response.ExamResultResponse;
import com.southcollege.exam.dto.response.ExamSessionResponse;
import com.southcollege.exam.dto.response.PageResult;
import com.southcollege.exam.dto.response.PaperResponse;
import com.southcollege.exam.dto.response.QuestionForExamResponse;
import com.southcollege.exam.dto.response.QuestionResponse;
import com.southcollege.exam.entity.*;
import com.southcollege.exam.enums.ExamSessionStatusEnum;
import com.southcollege.exam.enums.ExamStatusEnum;
import com.southcollege.exam.enums.GradingStatusEnum;
import com.southcollege.exam.enums.QuestionTypeEnum;
import com.southcollege.exam.enums.RoleEnum;
import com.southcollege.exam.exception.BusinessException;
import com.southcollege.exam.mapper.ExamMapper;
import com.southcollege.exam.mapstruct.ExamDtoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 考试服务层（ExamService）
 * ===========================================
 * 管理考试的全生命周期，包括：
 * 1. 考试创建、更新、发布、结束、删除（教师端）
 * 2. 考试过程管理：开始考试、自动保存、提交答卷（学生端）
 * 3. 评分管理：客观题自动评分、主观题人工评分、批量自动评分
 * 4. 结果查询：考试结果、题目回顾、分页列表
 * 5. 定时任务：自动提交过期考试会话
 *
 * 权限模型：
 * - 教师/管理员：只能操作自己创建的考试
 * - 学生：只能参加已发布且已加入课程的考试
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExamService extends ServiceImpl<ExamMapper, Exam> {

    // ==================== 依赖注入 ====================

    private final PaperService paperService;          // 试卷服务
    private final QuestionService questionService;    // 题目服务
    private final ExamSessionService examSessionService; // 考试会话服务（记录学生答题状态）
    private final CourseService courseService;        // 课程服务（校验课程成员关系）
    private final UserService userService;            // 用户服务（获取用户显示名称）
    private final ExamDtoMapper examDtoMapper;

    // ==================== 基础查询方法 ====================

    /**
     * 根据课程ID查询考试列表（无条件查询，仅返回原始数据）
     * @param courseId 课程ID
     * @return 该课程下的所有考试列表
     */
    public List<Exam> getByCourseId(Long courseId) {
        return lambdaQuery().eq(Exam::getCourseId, courseId).list();
    }

    /**
     * 根据教师ID查询考试列表
     * @param teacherId 教师ID
     * @return 该教师创建的考试列表（包含动态状态计算和展示字段填充）
     */
    public List<Exam> getByTeacherId(Long teacherId) {
        List<Exam> exams = lambdaQuery().eq(Exam::getTeacherId, teacherId).list();
        applyCurrentStatuses(exams);
        fillExamDisplayFields(exams);
        return exams;
    }

    /**
     * 统计指定教师创建的考试数量
     * @param teacherId 教师ID
     * @return 考试数量
     */
    public long countByTeacherId(Long teacherId) {
        return lambdaQuery().eq(Exam::getTeacherId, teacherId).count();
    }

    /**
     * 查询所有考试并填充展示字段
     * @return 所有考试列表（包含动态状态、课程名称、教师名称等展示字段）
     */
    public List<Exam> listWithDisplayFields() {
        List<Exam> exams = list();
        applyCurrentStatuses(exams);
        fillExamDisplayFields(exams);
        return exams;
    }

    /**
     * 根据ID查询考试并填充展示字段
     * @param id 考试ID
     * @return 考试详情（包含动态状态、课程名称、教师名称等展示字段），不存在则返回null
     */
    public Exam getByIdWithDisplayFields(Long id) {
        Exam exam = getById(id);
        if (exam == null) {
            return null;
        }
        applyCurrentStatus(exam);
        fillExamDisplayFields(List.of(exam));
        return exam;
    }

    /**
     * 根据ID和用户权限查询考试详情
     * 权限校验规则：
     * - 教师/管理员：只能查看自己创建的考试
     * - 学生：只能查看已发布/进行中/已结束状态的考试，且必须已加入对应课程；学生端会隐藏正确答案
     * @param id 考试ID
     * @param userId 当前用户ID
     * @param userRole 当前用户角色
     * @return 考试详情
     * @throws BusinessException 考试不存在、无权查看、未加入课程等情况
     */
    public Exam getByIdWithPermission(Long id, Long userId, String userRole) {
        Exam exam = getByIdWithDisplayFields(id);
        if (exam == null) {
            throw new BusinessException("考试不存在");
        }

        RoleEnum role = RoleEnum.fromCode(userRole);

        // 管理员和教师权限相同：只能查看/操作自己创建的考试
        if (role.hasPermission(RoleEnum.TEACHER)) {
            if (exam.getTeacherId() == null || !exam.getTeacherId().equals(userId)) {
                throw new BusinessException("无权查看该考试");
            }
            return exam;
        }

        if (RoleEnum.STUDENT.getCode().equals(userRole)) {
            if (!ExamStatusEnum.PUBLISHED.getCode().equals(exam.getStatus())
                    && !ExamStatusEnum.STARTED.getCode().equals(exam.getStatus())
                    && !ExamStatusEnum.ENDED.getCode().equals(exam.getStatus())) {
                throw new BusinessException("考试未发布");
            }
            if (!courseService.isCourseMember(exam.getCourseId(), userId)) {
                throw new BusinessException("请先加入课程");
            }
            if (exam.getExamPaper() != null && exam.getExamPaper().getItems() != null) {
                exam.getExamPaper().getItems().forEach(item -> item.setCorrectAnswer(null));
            }
            return exam;
        }

        throw new BusinessException("无权查看该考试");
    }

    // ==================== 考试创建与更新 ====================

    /**
     * 创建考试（教师端）
     * 核心流程：
     * 1. 校验试卷存在性及所有权
     * 2. 将试卷中的题目快照复制到 exam.examPaper，实现考试与试卷解耦（后续试卷修改不影响已创建考试）
     * 3. 根据题型分值配置计算总分（填空题按空数乘基础分值）
     * 4. 校验课程存在性及所有权
     * 5. 保存考试，初始状态为 DRAFT（草稿）
     *
     * @param examRequest 考试创建请求（包含标题、课程ID、试卷ID、时间、分值配置等）
     * @param teacherId 当前教师ID
     * @return 是否创建成功
     * @throws BusinessException 试卷不存在、无权使用、题目失效、课程不存在、及格分大于总分等
     */
    @Transactional
    public boolean createExam(ExamCreateRequest examRequest, Long teacherId) {
        if (examRequest.getPaperId() == null) {
            throw new BusinessException("请选择试卷");
        }

        Paper paper = paperService.getById(examRequest.getPaperId());
        if (paper == null) {
            throw new BusinessException("试卷不存在");
        }
        if (!paper.getTeacherId().equals(teacherId)) {
            throw new BusinessException("无权使用该试卷");
        }

        List<Long> questionIds = paper.getQuestionIds();
        if (questionIds == null || questionIds.isEmpty()) {
            throw new BusinessException("试卷中没有题目");
        }

        List<Question> questions = questionService.listByIds(questionIds);
        if (questions.isEmpty()) {
            throw new BusinessException("试卷中的题目均已失效，请重新组卷");
        }

        Map<Long, Question> questionMap = questions.stream()
                .collect(Collectors.toMap(Question::getId, q -> q));

        Map<String, BigDecimal> typeScoreMap = examRequest.getQuestionScores() != null
                ? examRequest.getQuestionScores()
                : Collections.emptyMap();

        BigDecimal totalScore = BigDecimal.ZERO;
        List<Exam.ExamQuestion> examQuestions = new ArrayList<>();
        for (Long questionId : questionIds) {
            Question question = questionMap.get(questionId);
            if (question == null) {
                continue;
            }

            BigDecimal score = computeQuestionTypeScore(question.getType(), question.getCorrectAnswer(), typeScoreMap);
            totalScore = totalScore.add(score);

            Exam.ExamQuestion examQuestion = new Exam.ExamQuestion();
            examQuestion.setQuestionId(question.getId());
            examQuestion.setContent(question.getContent());
            examQuestion.setType(question.getType());
            examQuestion.setDifficulty(question.getDifficulty());
            examQuestion.setCorrectAnswer(question.getCorrectAnswer());
            examQuestion.setExplanation(question.getExplanation());

            if ("FILL_BLANK".equals(question.getType())
                    && question.getCorrectAnswer() instanceof java.util.List<?> list) {
                examQuestion.setBlankCount(list.size());
            }

            if (question.getScoringCriteria() != null && !question.getScoringCriteria().isEmpty()) {
                examQuestion.setScoringCriteria(question.getScoringCriteria().stream().map(c -> {
                    Exam.ScoringCriterion sc = new Exam.ScoringCriterion();
                    sc.setPoint(c.getPoint());
                    sc.setScore(c.getScore());
                    return sc;
                }).toList());
            }

            if (question.getOptions() != null) {
                examQuestion.setOptions(question.getOptions().stream().map(opt -> {
                    Exam.Option examOpt = new Exam.Option();
                    examOpt.setId(opt.getId());
                    examOpt.setText(opt.getText());
                    return examOpt;
                }).toList());
            }

            examQuestions.add(examQuestion);
        }

        Course course = courseService.getById(examRequest.getCourseId());
        if (course == null) {
            throw new BusinessException("关联课程不存在");
        }
        if (!course.getTeacherId().equals(teacherId)) {
            throw new BusinessException("无权在该课程下创建考试");
        }

        Exam exam = new Exam();
        exam.setTitle(examRequest.getTitle());
        exam.setDescription(examRequest.getDescription());
        exam.setCourseId(examRequest.getCourseId());
        exam.setTeacherId(teacherId);
        exam.setStartedAt(examRequest.getStartedAt());
        exam.setEndedAt(examRequest.getEndedAt());
        exam.setDuration(examRequest.getDuration());
        exam.setPassScore(examRequest.getPassScore());
        if (examRequest.getPassScore() != null && totalScore.compareTo(examRequest.getPassScore()) < 0) {
            throw new BusinessException("及格分不能大于总分");
        }
        exam.setExamPaper(new Exam.ExamPaperData(examQuestions, typeScoreMap));
        exam.setTotalScore(totalScore);
        exam.setStatus(ExamStatusEnum.DRAFT.getCode());
        return save(exam);
    }

    /**
     * 更新考试（教师端）
     * 字段修改规则：
     * - 草稿状态(DRAFT)：可修改全部字段，包括标题、描述、课程、时间、时长、分值配置等
     * - 非草稿状态：锁定关键字段（课程ID、开始时间、结束时间、时长、总分），只能修改标题、描述等次要字段
     * 注意：考试发布前应仔细确认各项配置，发布后限制修改
     *
     * @param id 考试ID
     * @param examRequest 考试更新请求
     * @param userId 当前用户ID
     * @return 是否更新成功
     * @throws BusinessException 无权操作、考试不存在等
     */
    @Transactional
    public boolean updateExam(Long id, ExamUpdateRequest examRequest, Long userId) {
        Exam originalExam = checkOwnership(id, userId);

        Exam exam = new Exam();
        BeanUtils.copyProperties(examRequest, exam);
        exam.setId(id);
        exam.setTeacherId(originalExam.getTeacherId());
        exam.setStatus(originalExam.getStatus());

        boolean isDraft = ExamStatusEnum.DRAFT.getCode().equals(originalExam.getStatus());

        if (!isDraft) {
            exam.setCourseId(originalExam.getCourseId());
            exam.setStartedAt(originalExam.getStartedAt());
            exam.setEndedAt(originalExam.getEndedAt());
            exam.setDuration(originalExam.getDuration());
            exam.setTotalScore(originalExam.getTotalScore());
        }

        if (isDraft && examRequest.getQuestionScores() != null && !examRequest.getQuestionScores().isEmpty()) {
            List<Exam.ExamQuestion> questions = originalExam.getExamPaper().getItems();
            if (questions != null && !questions.isEmpty()) {
                BigDecimal newTotalScore = BigDecimal.ZERO;
                for (Exam.ExamQuestion eq : questions) {
                    newTotalScore = newTotalScore.add(computeQuestionScore(eq, examRequest.getQuestionScores()));
                }
                exam.setExamPaper(new Exam.ExamPaperData(questions, examRequest.getQuestionScores()));
                exam.setTotalScore(newTotalScore);
            }
        }

        return updateById(exam);
    }

    /**
     * 根据状态查询考试列表（精确匹配数据库中的 status 字段）
     * @param status 考试状态代码（如 DRAFT、PUBLISHED、STARTED、ENDED）
     * @return 对应状态的考试列表
     */
    public List<Exam> getByStatus(String status) {
        return lambdaQuery().eq(Exam::getStatus, status).list();
    }

    /**
     * 获取学生可见的已发布考试分页列表（学生端首页）
     * 筛选条件：
     * - 考试所属课程必须是学生已加入的课程
     * - 排除草稿和已结束状态的考试
     * - 结束时间必须晚于当前时间（或即将结束但仍在30秒缓冲期内）
     * - 只返回 PUBLISHED 或 STARTED 状态的考试
     * @param pageRequest 分页参数
     * @param studentId 学生ID
     * @return 分页结果（包含考试动态状态和展示字段）
     */
    public PageResult<Exam> getPublishedExams(PageRequest pageRequest, Long studentId) {
        List<Course> myCourses = courseService.getMyCourses(studentId);
        List<Long> courseIds = myCourses.stream()
                .map(Course::getId)
                .toList();
        if (courseIds.isEmpty()) {
            return PageResult.empty(pageRequest.getCurrent(), pageRequest.getSize());
        }

        Page<Exam> page = new Page<>(pageRequest.getCurrent(), pageRequest.getSize());
        LambdaQueryWrapper<Exam> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Exam::getCourseId, courseIds)
                .notIn(Exam::getStatus, ExamStatusEnum.DRAFT.getCode(), ExamStatusEnum.ENDED.getCode())
                .ge(Exam::getEndedAt, LocalDateTime.now());
        applyDbSorting(wrapper, pageRequest);

        Page<Exam> dbPage = page(page, wrapper);
        List<Exam> records = dbPage.getRecords();
        applyCurrentStatuses(records);
        records = records.stream()
                .filter(exam -> ExamStatusEnum.PUBLISHED.getCode().equals(exam.getStatus())
                        || ExamStatusEnum.STARTED.getCode().equals(exam.getStatus()))
                .toList();
        fillExamDisplayFields(records);

        long filteredTotal = records.size() + (long) (dbPage.getCurrent() - 1) * dbPage.getSize();
        long adjustedTotal = Math.min(filteredTotal, dbPage.getTotal());
        long adjustedPages = adjustedTotal == 0 ? 0 : (adjustedTotal + dbPage.getSize() - 1) / dbPage.getSize();

        PageResult<Exam> result = PageResult.from(dbPage);
        result.setRecords(records);
        result.setTotal(adjustedTotal);
        result.setPages(adjustedPages);
        result.setHasNext(dbPage.getCurrent() < adjustedPages);
        result.setHasPrevious(dbPage.getCurrent() > 1);
        return result;
    }

    // ==================== 考试过程管理（学生端） ====================

    /**
     * 获取考试题目列表（学生端，进入考试后获取题目）
     * 前置校验：
     * 1. 学生必须已加入对应课程
     * 2. 考试状态必须是 PUBLISHED 或 STARTED
     * 3. 当前时间必须在考试开始时间和结束时间(+30秒缓冲)之间
     * 4. 学生必须已开始该考试（存在 IN_PROGRESS 状态的考试会话）
     * 5. 考试时长限制：不能超过个人考试截止时间
     * 返回数据：隐藏正确答案，只返回题目内容、选项、分值等学生可见信息
     * @param examId 考试ID
     * @param studentId 学生ID
     * @return 题目列表（不含正确答案）
     */
    public List<QuestionForExamResponse> getExamQuestions(Long examId, Long studentId) {
        Exam exam = getByIdOrThrow(examId);

        if (!courseService.isCourseMember(exam.getCourseId(), studentId)) {
            throw new BusinessException("请先加入对应课程");
        }

        if (!ExamStatusEnum.PUBLISHED.getCode().equals(exam.getStatus()) &&
            !ExamStatusEnum.STARTED.getCode().equals(exam.getStatus())) {
            throw new BusinessException("考试未发布");
        }

        LocalDateTime now = LocalDateTime.now();
        if (exam.getStartedAt() == null || exam.getEndedAt() == null) {
            throw new BusinessException("考试时间未设置");
        }
        if (now.isBefore(exam.getStartedAt())) {
            throw new BusinessException("考试未开始");
        }
        if (now.isAfter(exam.getEndedAt().plusSeconds(30))) {
            throw new BusinessException("考试已结束");
        }

        ExamSession session = examSessionService.getByExamIdAndStudentId(examId, studentId);
        if (session == null) {
            throw new BusinessException("未开始该考试");
        }
        if (!ExamSessionStatusEnum.IN_PROGRESS.getCode().equals(session.getStatus())) {
            throw new BusinessException("当前考试状态不允许继续作答");
        }
        if (exam.getDuration() != null) {
            LocalDateTime durationDeadline = sessionDeadline(session, exam.getDuration());
            if (now.isAfter(durationDeadline)) {
                throw new BusinessException("考试已超时");
            }
        }

        if (exam.getExamPaper() == null || exam.getExamPaper().getItems() == null || exam.getExamPaper().getItems().isEmpty()) {
            throw new BusinessException("考试题目不存在");
        }

        return exam.getExamPaper().getItems().stream()
                .map(q -> convertExamQuestionToResponse(q, exam.getExamPaper().getTypeScores()))
                .toList();
    }

    /**
     * 将考试题目转换为学生端响应对象（隐藏正确答案）
     * 分值计算规则：
     * - 填空题：基础分值 × 空数
     * - 其他题型：基础分值
     * @param examQuestion 考试题目快照
     * @param typeScores 题型分值映射
     * @return 学生端题目响应（不含正确答案）
     */
    private QuestionForExamResponse convertExamQuestionToResponse(
            Exam.ExamQuestion examQuestion, Map<String, BigDecimal> typeScores) {
        QuestionForExamResponse response = new QuestionForExamResponse();
        response.setId(examQuestion.getQuestionId());
        response.setContent(examQuestion.getContent());
        response.setType(examQuestion.getType());
        response.setDifficulty(examQuestion.getDifficulty());

        BigDecimal baseScore = typeScores != null ? typeScores.getOrDefault(examQuestion.getType(), BigDecimal.ZERO) : BigDecimal.ZERO;
        if ("FILL_BLANK".equals(examQuestion.getType()) && examQuestion.getBlankCount() != null && examQuestion.getBlankCount() > 0) {
            response.setScore(baseScore.multiply(BigDecimal.valueOf(examQuestion.getBlankCount())));
        } else {
            response.setScore(baseScore);
        }

        if (examQuestion.getOptions() != null) {
            response.setOptions(examQuestion.getOptions().stream().map(opt -> {
                QuestionForExamResponse.Option option = new QuestionForExamResponse.Option();
                option.setId(opt.getId());
                option.setText(opt.getText());
                return option;
            }).toList());
        }

        if (examQuestion.getBlankCount() != null) {
            response.setBlankCount(examQuestion.getBlankCount());
        }

        return response;
    }

    /**
     * 获取考试回顾题目（含正确答案，仅在考试结束后可查）
     * 前置校验：学生只能查看自己已提交且已结束的考试；教师可查看自己创建的考试
     * @param examId 考试ID
     * @param userId 当前用户ID
     * @param userRole 当前用户角色
     * @return 题目列表（含正确答案和解析）
     */
    public List<Exam.ExamQuestion> getReviewQuestions(Long examId, Long userId, String userRole) {
        Exam exam = checkReviewPermission(examId, userId, userRole);
        return exam.getExamPaper() != null && exam.getExamPaper().getItems() != null ? exam.getExamPaper().getItems() : List.of();
    }

    /**
     * 校验考试回顾权限
     * 权限规则：
     * - 教师/管理员：只能查看自己创建的考试
     * - 学生：必须已加入课程、已参加该考试、考试已结束、且考试会话状态为 SUBMITTED 或 GRADED
     * @param examId 考试ID
     * @param userId 当前用户ID
     * @param userRole 当前用户角色
     * @return 通过权限校验的考试对象
     */
    private Exam checkReviewPermission(Long examId, Long userId, String userRole) {
        Exam exam = getById(examId);
        if (exam == null) {
            throw new BusinessException("考试不存在");
        }
        applyCurrentStatus(exam);

        RoleEnum role = RoleEnum.fromCode(userRole);

        // 管理员和教师权限相同：只能查看/操作自己创建的考试
        if (role.hasPermission(RoleEnum.TEACHER)) {
            if (exam.getTeacherId() == null || !exam.getTeacherId().equals(userId)) {
                throw new BusinessException("无权查看该考试");
            }
            return exam;
        }

        if (RoleEnum.STUDENT.getCode().equals(userRole)) {
            if (!courseService.isCourseMember(exam.getCourseId(), userId)) {
                throw new BusinessException("请先加入对应课程");
            }
            ExamSession session = examSessionService.getByExamIdAndStudentId(examId, userId);
            if (session == null) {
                throw new BusinessException("您尚未参加该考试");
            }
            boolean canView = ExamStatusEnum.ENDED.getCode().equals(exam.getStatus())
                    && (ExamSessionStatusEnum.SUBMITTED.getCode().equals(session.getStatus())
                        || ExamSessionStatusEnum.GRADED.getCode().equals(session.getStatus()));
            if (!canView) {
                throw new BusinessException("考试尚未结束，暂时无法查看答案");
            }
            return exam;
        }

        throw new BusinessException("无权查看该考试");
    }

    /**
     * 获取学生已加入课程的考试列表（含个人考试状态）
     * 用途：学生端"我的考试"页面
     * 个人状态说明：如果学生已开始考试，显示对应会话状态；否则显示 NOT_STARTED
     * @param pageRequest 分页参数
     * @param studentId 学生ID
     * @return 分页结果（包含考试动态状态和个人考试进度状态）
     */
    public PageResult<Exam> getMyExams(PageRequest pageRequest, Long studentId) {
        List<Course> myCourses = courseService.getMyCourses(studentId);
        List<Long> courseIds = myCourses.stream()
                .map(Course::getId)
                .toList();
        if (courseIds.isEmpty()) {
            return PageResult.empty(pageRequest.getCurrent(), pageRequest.getSize());
        }

        Page<Exam> page = new Page<>(pageRequest.getCurrent(), pageRequest.getSize());
        LambdaQueryWrapper<Exam> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Exam::getCourseId, courseIds)
                .ne(Exam::getStatus, ExamStatusEnum.DRAFT.getCode());
        applyDbSorting(wrapper, pageRequest);

        Page<Exam> dbPage = page(page, wrapper);
        List<Exam> records = dbPage.getRecords();
        applyCurrentStatuses(records);

        List<Long> examIds = records.stream().map(Exam::getId).toList();
        Map<Long, ExamSession> sessionMap = examSessionService.getByExamIdsAndStudentId(examIds, studentId);

        for (Exam exam : records) {
            ExamSession session = sessionMap.get(exam.getId());
            if (session != null) {
                exam.setStudentExamStatus(session.getStatus());
            } else {
                exam.setStudentExamStatus(ExamSessionStatusEnum.NOT_STARTED.getCode());
            }
        }

        fillExamDisplayFields(records);

        return PageResult.from(dbPage);
    }

    /**
     * 开始考试（学生端）
     * 核心流程：
     * 1. 校验考试存在且已发布（PUBLISHED 或 STARTED）
     * 2. 校验学生已加入对应课程
     * 3. 校验当前时间在考试开始时间和结束时间(+30秒缓冲)之间
     * 4. 幂等处理：如果已有进行中的会话，直接返回；如果已完成，报错
     * 5. 创建新的考试会话，状态为 IN_PROGRESS
     * 并发安全：使用数据库唯一键（examId + studentId）防重复开始，捕获 DuplicateKeyException 做幂等回查
     * @param examId 考试ID
     * @param studentId 学生ID
     * @return 考试会话对象（新创建或已存在的进行中会话）
     */
    @Transactional
    public ExamSession startExam(Long examId, Long studentId) {
        Exam exam = getByIdOrThrow(examId);
        assertExamPublished(exam);

        if (!courseService.isCourseMember(exam.getCourseId(), studentId)) {
            throw new BusinessException("请先加入对应课程");
        }

        LocalDateTime now = LocalDateTime.now();
        assertExamTimeSet(exam);
        if (now.isBefore(exam.getStartedAt())) {
            throw new BusinessException("考试未开始");
        }
        if (now.isAfter(exam.getEndedAt().plusSeconds(30))) {
            throw new BusinessException("考试已结束");
        }
        //创建考试回话

        ExamSession existing = examSessionService.getByExamIdAndStudentId(examId, studentId);
        if (existing != null) {
            if (ExamSessionStatusEnum.IN_PROGRESS.getCode().equals(existing.getStatus())) {
                return existing;
            }
            throw new BusinessException("已参加过该考试");
        }

        ExamSession session = new ExamSession();
        session.setExamId(examId);
        session.setStudentId(studentId);
        session.setStartedAt(now);
        session.setStatus(ExamSessionStatusEnum.IN_PROGRESS.getCode());
        session.setTotalScore(exam.getTotalScore());

        try {
            examSessionService.save(session);
        } catch (DuplicateKeyException e) {
            existing = examSessionService.getByExamIdAndStudentId(examId, studentId);
            if (existing != null && ExamSessionStatusEnum.IN_PROGRESS.getCode().equals(existing.getStatus())) {
                return existing;
            }
            throw new BusinessException("已参加过该考试");
        }

        return session;
    }

    /**
     * 自动保存考试答案（学生端，定时自动保存或手动保存）
     * 特点：
     * - 非阻塞：遇到乐观锁冲突或状态异常时静默处理，不抛异常影响学生答题
     * - 过滤：只保留属于当前考试的题目答案，过滤无效/篡改数据
     * - 时限校验：考试结束后或超时时长后拒绝保存
     * @param examId 考试ID
     * @param studentId 学生ID
     * @param answers 当前答题内容（可为null或空）
     */
    @Transactional
    public void autoSaveExam(Long examId, Long studentId, List<ExamSession.Answer> answers) {
        Exam exam = getByIdOrThrow(examId);
        assertExamPublished(exam);
        assertExamTimeSet(exam);
        if (LocalDateTime.now().isAfter(exam.getEndedAt().plusSeconds(30))) {
            throw new BusinessException("考试已结束，无法继续保存");
        }
        ExamSession session = examSessionService.getByExamIdAndStudentId(examId, studentId);
        if (session == null) {
            throw new BusinessException("未开始该考试");
        }
        if (!ExamSessionStatusEnum.IN_PROGRESS.getCode().equals(session.getStatus())) {
            return;
        }
        if (session.getSubmittedAt() != null) {
            return;
        }
        if (exam.getDuration() != null) {
            LocalDateTime durationDeadline = sessionDeadline(session, exam.getDuration());
            if (LocalDateTime.now().isAfter(durationDeadline)) {
                return;
            }
        }

        Set<Long> validQuestionIds = getQuestionIdsFromExam(exam);
        if (!validQuestionIds.isEmpty() && answers != null) {
            int beforeFilter = answers.size();
            answers = answers.stream()
                    .filter(answer -> answer != null && answer.getQuestionId() != null
                            && validQuestionIds.contains(answer.getQuestionId()))
                    .collect(Collectors.toList());
            if (answers.size() < beforeFilter) {
                log.warn("自动保存时过滤掉无效题目: examId={}, studentId={}, 原始答案数={}, 有效答案数={}",
                        examId, studentId, beforeFilter, answers.size());
            }
        }

        session.setAnswers(answers);

        try {
            examSessionService.updateById(session);
        } catch (OptimisticLockingFailureException e) {
            log.warn("自动保存遇到乐观锁冲突，忽略本次保存: examId={}, studentId={}", examId, studentId);
        }
    }

    /**
     * 提交考试答卷（学生端）
     * 核心流程：
     * 1. 校验考试状态、时间窗口、个人考试会话状态
     * 2. 规范化答案数据（过滤空答案）
     * 3. 校验答案题目ID合法性（必须属于当前考试，不能重复）
     * 4. 客观题自动评分（单选、多选、判断、填空）
     * 5. 主观题标记为待评分状态
     * 6. 更新会话状态：无主观题则直接 GRADED，有主观题则 SUBMITTED（待教师评分）
     * 并发安全：使用乐观锁防止重复提交
     * @param examId 考试ID
     * @param studentId 学生ID
     * @param answers 提交的答案列表
     */
    @Transactional
    public void submitExam(Long examId, Long studentId, List<ExamSession.Answer> answers) {
        Exam exam = getByIdOrThrow(examId);
        assertExamPublished(exam);

        ExamSession session = examSessionService.getByExamIdAndStudentId(examId, studentId);
        if (session == null) {
            throw new BusinessException("未开始该考试");
        }
        if (session.getSubmittedAt() != null) {
            throw new BusinessException("该考试已提交，请勿重复提交");
        }
        if (!ExamSessionStatusEnum.IN_PROGRESS.getCode().equals(session.getStatus())) {
            throw new BusinessException("当前考试状态不允许提交");
        }

        LocalDateTime now = LocalDateTime.now();
        if (exam.getEndedAt() == null) {
            throw new BusinessException("考试时间未设置");
        }
        if (now.isAfter(exam.getEndedAt().plusSeconds(30))) {
            throw new BusinessException("考试已结束");
        }
        if (exam.getDuration() != null) {
            LocalDateTime durationDeadline = sessionDeadline(session, exam.getDuration());
            if (now.isAfter(durationDeadline)) {
                throw new BusinessException("考试已超时");
            }
        }

        answers = normalizeSubmittedAnswers(answers);

        Set<Long> examQuestionIds = getQuestionIdsFromExam(exam);
        validateAnswerQuestionIds(answers, examQuestionIds);

        Map<Long, BigDecimal> scoreMap = getScoreMapFromExam(exam);

        GradingResult gr = gradeAnswersInPlace(answers, exam, scoreMap);

        validateAnswers(answers);

        session.setAnswers(answers);
        session.setScore(gr.objectiveScore());
        session.setSubmittedAt(now);
        session.setStatus(gr.hasSubjective() ? ExamSessionStatusEnum.SUBMITTED.getCode() : ExamSessionStatusEnum.GRADED.getCode());
        session.setGradingStatus(gr.hasSubjective() ? GradingStatusEnum.PENDING.getCode() : GradingStatusEnum.COMPLETED.getCode());

        try {
            examSessionService.updateById(session);
        } catch (OptimisticLockingFailureException e) {
            throw new BusinessException("该考试已提交，请勿重复提交");
        }
    }

    // ==================== 客观题自动评分引擎 ====================

    /**
     * 客观题答案校验核心方法
     * 支持的题型及校验逻辑：
     * - 多选题(MULTIPLE_CHOICE)：JSON数组比较，排序后精确匹配，异常时降级为字符串比较
     * - 判断题(TRUE_FALSE)：归一化处理（A/正确/true -> TRUE, B/错误/false -> FALSE）
     * - 填空题(FILL_BLANK)：支持多候选答案、数字等价、文本归一化
     * - 单选题(SINGLE_CHOICE)及其他：直接字符串忽略大小写比较
     * @param examQuestion 考试题目快照（含正确答案）
     * @param answer 学生提交的答案字符串
     * @return 是否正确
     */
    private boolean checkAnswerByExamQuestion(Exam.ExamQuestion examQuestion, String answer) {
        if (examQuestion.getCorrectAnswer() == null || answer == null) {
            return false;
        }

        if (QuestionTypeEnum.MULTIPLE_CHOICE.getCode().equals(examQuestion.getType())) {
            try {
                List<String> studentAnswers = JSONUtil.toList(answer, String.class);
                List<String> correctAnswers = JSONUtil.toList(examQuestion.getCorrectAnswer().toString(), String.class);

                studentAnswers = studentAnswers.stream().map(String::trim).sorted().collect(Collectors.toList());
                correctAnswers = correctAnswers.stream().map(String::trim).sorted().collect(Collectors.toList());

                return studentAnswers.equals(correctAnswers);
            } catch (Exception e) {
                return examQuestion.getCorrectAnswer().toString().equalsIgnoreCase(answer.trim());
            }
        }

        if (QuestionTypeEnum.TRUE_FALSE.getCode().equals(examQuestion.getType())) {
            String normalizedStudent = normalizeTrueFalseAnswer(answer);
            String normalizedCorrect = normalizeTrueFalseAnswer(examQuestion.getCorrectAnswer().toString());
            if (normalizedStudent != null && normalizedCorrect != null) {
                return normalizedStudent.equals(normalizedCorrect);
            }
        }

        if (QuestionTypeEnum.FILL_BLANK.getCode().equals(examQuestion.getType())) {
            return isFillBlankAnswerCorrect(answer, examQuestion.getCorrectAnswer());
        }

        return examQuestion.getCorrectAnswer().toString().trim().equalsIgnoreCase(answer.trim());
    }

    /**
     * 填空题答案校验
     * 支持多候选答案匹配，匹配逻辑：
     * 1. 文本归一化后精确相等
     * 2. 数字等价比较（如 "1.0" 和 "1.00"）
     * @param studentAnswer 学生答案
     * @param correctAnswer 正确答案（可能是JSON数组或分隔符分隔的字符串）
     * @return 是否与任一候选答案匹配
     */
    private boolean isFillBlankAnswerCorrect(String studentAnswer, Object correctAnswer) {
        String normalizedStudent = normalizeText(studentAnswer);
        if (normalizedStudent.isEmpty() || correctAnswer == null) {
            return false;
        }

        List<String> candidates = parseFillBlankCandidates(correctAnswer);
        if (candidates.isEmpty()) {
            return false;
        }

        for (String candidate : candidates) {
            String normalizedCandidate = normalizeText(candidate);
            if (normalizedCandidate.isEmpty()) {
                continue;
            }
            if (normalizedStudent.equals(normalizedCandidate)) {
                return true;
            }
            if (isNumericEquivalent(normalizedStudent, normalizedCandidate)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 解析填空题候选答案列表
     * 支持格式：
     * - JSON数组（如 ["答案1", "答案2"]）
     * - 分隔符分隔：换行、英文逗号、中文逗号、分号、中文分号、竖线
     * - 单值字符串
     * @param correctAnswer 原始正确答案对象
     * @return 候选答案列表
     */
    private List<String> parseFillBlankCandidates(Object correctAnswer) {
        if (correctAnswer == null) {
            return List.of();
        }
        String raw = correctAnswer.toString();
        if (raw == null) {
            return List.of();
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return List.of();
        }

        try {
            if (JSONUtil.isTypeJSONArray(value)) {
                List<String> arr = JSONUtil.toList(value, String.class);
                return arr == null ? List.of() : arr.stream().filter(StringUtils::isNotBlank).toList();
            }
        } catch (Exception ignored) {
            log.warn("JSON 解析候选答案失败，回退到字符串分割: {}", value);
        }

        if (value.contains("\n") || value.contains(",") || value.contains("，")
                || value.contains(";") || value.contains("；") || value.contains("|")) {
            return java.util.Arrays.stream(value.split("[\\n,，;；|]+"))
                    .map(String::trim)
                    .filter(StringUtils::isNotBlank)
                    .toList();
        }

        return List.of(value);
    }

    /**
     * 文本归一化处理
     * 处理内容：全角空格转半角、不间断空格转普通空格、去除首尾空白、合并连续空白、转小写
     * @param value 原始文本
     * @return 归一化后的文本
     */
    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace('\u00A0', ' ')
                .replace('\u3000', ' ')
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase();
    }

    /**
     * 判断两个字符串是否数字等价（用于填空题数字答案的容错）
     * 例如："1.0" 和 "1.00" 视为等价，"1e2" 和 "100" 视为等价
     * @param a 字符串a
     * @param b 字符串b
     * @return 是否数字等价
     */
    private boolean isNumericEquivalent(String a, String b) {
        try {
            return new BigDecimal(a).compareTo(new BigDecimal(b)) == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 判断题答案归一化
     * 支持的输入：A/正确/true -> TRUE；B/错误/false -> FALSE
     * @param answer 学生原始输入
     * @return 归一化后的 TRUE/FALSE，无法识别则返回null
     */
    private String normalizeTrueFalseAnswer(String answer) {
        if (answer == null) {
            return null;
        }
        String value = answer.trim();
        if ("A".equalsIgnoreCase(value) || "正确".equals(value) || "true".equalsIgnoreCase(value)) {
            return "TRUE";
        }
        if ("B".equalsIgnoreCase(value) || "错误".equals(value) || "false".equalsIgnoreCase(value)) {
            return "FALSE";
        }
        return null;
    }

    // ==================== 考试生命周期管理（教师端） ====================

    /**
     * 发布考试（教师端）
     * 发布前置校验（草稿状态才能发布）：
     * 1. 时间设置：开始时间 < 结束时间，且开始时间必须晚于当前时间
     * 2. 时长设置：必须大于0，且不能超过考试时间窗口
     * 3. 分值设置：必须有题目，且总分必须大于0
     * 4. 及格分：如有设置，不能大于总分
     * 5. 课程权限：必须有权在该课程下发布考试
     * 发布成功后状态变为 PUBLISHED
     * @param examId 考试ID
     * @param userId 当前用户ID
     */
    @Transactional
    public void publishExam(Long examId, Long userId) {
        Exam exam = checkOwnership(examId, userId);

        if (exam.getStatus() == null) {
            exam.setStatus(ExamStatusEnum.DRAFT.getCode());
        }

        if (!ExamStatusEnum.DRAFT.getCode().equals(exam.getStatus())) {
            throw new BusinessException("仅草稿状态的考试可以发布");
        }

        if (exam.getStartedAt() == null || exam.getEndedAt() == null) {
            throw new BusinessException("请先设置考试开始和结束时间");
        }
        if (!exam.getStartedAt().isBefore(exam.getEndedAt())) {
            throw new BusinessException("考试开始时间必须早于结束时间");
        }
        if (!exam.getStartedAt().isAfter(LocalDateTime.now())) {
            throw new BusinessException("考试开始时间已过，无法发布，请修改开始时间后重试");
        }
        if (exam.getDuration() == null || exam.getDuration() <= 0) {
            throw new BusinessException("请设置有效的考试时长（分钟）");
        }
        long windowMinutes = java.time.Duration.between(exam.getStartedAt(), exam.getEndedAt()).toMinutes();
        if (exam.getDuration() > windowMinutes) {
            throw new BusinessException("考试时长不能超过考试时间窗口");
        }
        if (exam.getPassScore() != null && exam.getTotalScore() != null
                && exam.getPassScore().compareTo(exam.getTotalScore()) > 0) {
            throw new BusinessException("及格分不能大于总分");
        }
        if (exam.getExamPaper() == null || exam.getExamPaper().getItems() == null || exam.getExamPaper().getItems().isEmpty()) {
            throw new BusinessException("考试中没有题目，请先设置题目和分值");
        }
        Map<String, BigDecimal> typeScores = exam.getExamPaper().getTypeScores();
        if (typeScores == null || typeScores.isEmpty()) {
            throw new BusinessException("请先设置各题分值，总分必须大于0");
        }
        BigDecimal totalScore = exam.getExamPaper().getItems().stream()
                .map(q -> computeQuestionScore(q, typeScores))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalScore.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("请先设置各题分值，总分必须大于0");
        }
        exam.setTotalScore(totalScore);
        if (exam.getCourseId() != null) {
            Course publishCourse = courseService.getById(exam.getCourseId());
            if (publishCourse == null) {
                throw new BusinessException("关联课程不存在");
            }
            if (!publishCourse.getTeacherId().equals(userId)) {
                throw new BusinessException("无权在该课程下发布考试");
            }
        }

        exam.setStatus(ExamStatusEnum.PUBLISHED.getCode());
        updateById(exam);
    }

    /**
     * 提前结束考试（教师端）
     * 适用场景：考试进行中，教师因特殊情况需要提前终止考试
     * 限制：只能结束 STARTED 状态的考试，已结束则幂等返回
     * @param examId 考试ID
     * @param userId 当前用户ID
     */
    @Transactional
    public void endExam(Long examId, Long userId) {
        Exam exam = checkOwnership(examId, userId);
        applyCurrentStatus(exam);

        if (ExamStatusEnum.ENDED.getCode().equals(exam.getStatus())) {
            return;
        }
        if (!ExamStatusEnum.STARTED.getCode().equals(exam.getStatus())) {
            throw new BusinessException("仅进行中的考试可以提前结束");
        }

        exam.setStatus(ExamStatusEnum.ENDED.getCode());
        exam.setEndedAt(LocalDateTime.now());
        updateById(exam);
        log.info("教师 [{}] 提前结束了考试 [{}]", userId, examId);
    }

    /**
     * 校验考试操作权限（管理员和教师权限相同，只能操作自己的考试）
     * @param examId 考试ID
     * @param userId 当前用户ID
     * @return 通过权限校验的考试对象
     */
    public Exam checkOwnership(Long examId, Long userId) {
        Exam exam = getById(examId);
        if (exam == null) {
            throw new BusinessException("考试不存在");
        }
        if (exam.getTeacherId() == null || !exam.getTeacherId().equals(userId)) {
            throw new BusinessException("无权操作该考试");
        }
        return exam;
    }

    /**
     * 校验考试是否可以删除
     * 删除限制：如果有学生已参加考试（存在考试会话记录），则不允许删除
     * @param examId 考试ID
     */
    public void checkCanDelete(Long examId) {
        List<ExamSession> sessions = examSessionService.getByExamId(examId);
        if (!sessions.isEmpty()) {
            throw new BusinessException("已有 " + sessions.size() + " 名学生参加该考试，无法删除");
        }
    }

    /**
     * 删除考试（逻辑删除关联会话 + 物理删除考试）
     * @param examId 考试ID
     */
    @Transactional
    public void deleteExam(Long examId) {
        examSessionService.lambdaUpdate()
                .eq(ExamSession::getExamId, examId)
                .set(ExamSession::getDeleted, 1)
                .update();
        removeById(examId);
    }

    // ==================== 评分管理（教师端） ====================

    /**
     * 主观题人工评分（教师端）
     * 核心流程：
     * 1. 校验评分请求参数完整性
     * 2. 校验考试记录存在性及教师评分权限（只能评自己创建的考试）
     * 3. 校验考试已提交且处于待评分状态
     * 4. 逐题评分：校验题目是否为主观题、得分不超过满分、得分不为负数
     * 5. 校验所有主观题是否已评分完毕
     * 6. 计算最终总分（客观题分数 + 主观题分数），更新会话状态为 GRADED
     * 并发安全：使用乐观锁防止并发评分冲突
     * @param operatorId 当前操作人ID
     * @param request 评分请求（含考试会话ID和各题得分）
     */
    @Transactional
    public void gradeSubjectiveAnswers(Long operatorId, GradeSubjectiveRequest request) {
        if (request == null || request.getExamSessionId() == null) {
            throw new BusinessException("评分请求参数不完整");
        }
        if (request.getGrades() == null || request.getGrades().isEmpty()) {
            throw new BusinessException("请至少提交一题评分结果");
        }

        ExamSession session = examSessionService.getById(request.getExamSessionId());
        if (session == null) {
            throw new BusinessException("考试记录不存在");
        }

        Exam exam = getById(session.getExamId());
        if (exam == null) {
            throw new BusinessException("无权评分该考试");
        }
        if (exam.getTeacherId() == null || !exam.getTeacherId().equals(operatorId)) {
            throw new BusinessException("无权评分该考试");
        }

        if (!ExamSessionStatusEnum.SUBMITTED.getCode().equals(session.getStatus())) {
            throw new BusinessException("考试尚未提交");
        }
        if (!GradingStatusEnum.PENDING.getCode().equals(session.getGradingStatus())) {
            throw new BusinessException("该考试无需评分");
        }

        Map<Long, BigDecimal> scoreMap = getScoreMapFromExam(exam);

        List<ExamSession.Answer> answers = session.getAnswers();
        if (answers == null || answers.isEmpty()) {
            throw new BusinessException("该考试没有可评分的答案");
        }
        BigDecimal subjectiveScore = BigDecimal.ZERO;

        for (GradeSubjectiveRequest.SubjectiveGrade grade : request.getGrades()) {
            if (grade == null || grade.getQuestionId() == null || grade.getScore() == null) {
                throw new BusinessException("评分数据不完整");
            }

            ExamSession.Answer answer = answers.stream()
                    .filter(a -> a.getQuestionId().equals(grade.getQuestionId()))
                    .findFirst()
                    .orElse(null);

            if (answer == null) {
                answer = new ExamSession.Answer();
                answer.setQuestionId(grade.getQuestionId());
                Exam.ExamQuestion examQuestion = findExamQuestion(exam, grade.getQuestionId());
                if (examQuestion == null) {
                    throw new BusinessException("题目 " + grade.getQuestionId() + " 不在本考试中");
                }
                answer.setQuestionType(examQuestion.getType());
                answer.setAnswer(null);
                answers.add(answer);
            }

            if (answer.getQuestionType() == null || !QuestionTypeEnum.fromCode(answer.getQuestionType()).isSubjective()) {
                throw new BusinessException("题目 " + grade.getQuestionId() + " 不是主观题，不能手动评分");
            }

            BigDecimal maxScore = scoreMap.getOrDefault(grade.getQuestionId(), BigDecimal.ZERO);
            if (grade.getScore().compareTo(maxScore) > 0) {
                throw new BusinessException("题目 " + grade.getQuestionId() + " 的得分不能超过满分 " + maxScore);
            }

            if (grade.getScore().compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException("题目 " + grade.getQuestionId() + " 的得分不能为负数");
            }

            answer.setScore(grade.getScore());
            answer.setTeacherComment(grade.getComment());
            answer.setGradingStatus(GradingStatusEnum.GRADED.getCode());
            answer.setIsCorrect(grade.getScore().compareTo(BigDecimal.ZERO) > 0);
        }

        boolean allGraded = answers.stream()
                .filter(a -> QuestionTypeEnum.fromCode(a.getQuestionType()).isSubjective())
                .allMatch(a -> GradingStatusEnum.GRADED.getCode().equals(a.getGradingStatus()));

        if (!allGraded) {
            throw new BusinessException("还有主观题未评分，请完成所有主观题的评分");
        }

        for (ExamSession.Answer answer : answers) {
            if (QuestionTypeEnum.fromCode(answer.getQuestionType()).isSubjective() && answer.getScore() != null) {
                subjectiveScore = subjectiveScore.add(answer.getScore());
            }
        }

        BigDecimal objectiveScore = session.getScore() != null ? session.getScore() : BigDecimal.ZERO;
        BigDecimal finalScore = objectiveScore.add(subjectiveScore);

        session.setScore(finalScore);
        session.setAnswers(answers);
        session.setStatus(ExamSessionStatusEnum.GRADED.getCode());
        session.setGradingStatus(GradingStatusEnum.COMPLETED.getCode());
        try {
            examSessionService.updateById(session);
        } catch (OptimisticLockingFailureException e) {
            throw new BusinessException("评分失败，该考试记录已被其他人修改，请刷新后重试");
        }
    }

    /**
     * 获取考试结果详情（学生/教师端）
     * 权限规则：学生只能查看自己的考试结果，教师可查看自己创建考试的所有学生结果
     * 返回内容：
     * - 考试基本信息（标题、时间、总分、及格分等）
     * - 学生答题详情（每题答案、得分、满分、是否正确、教师评语等）
     * - 客观题总分、主观题总分、最终总分
     * @param examSessionId 考试会话ID
     * @param userId 当前用户ID
     * @return 考试结果响应对象
     */
    public ExamResultResponse getExamResult(Long examSessionId, Long userId) {
        ExamSession session = examSessionService.getById(examSessionId);
        if (session == null) {
            throw new BusinessException("考试记录不存在");
        }

        Exam exam = getById(session.getExamId());
        if (exam == null) {
            throw new BusinessException("考试不存在");
        }

        boolean canViewAsTeacher = userId.equals(exam.getTeacherId());
        if (!session.getStudentId().equals(userId) && !canViewAsTeacher) {
            throw new BusinessException("无权查看该考试结果");
        }

        Map<Long, BigDecimal> scoreMap = getScoreMapFromExam(exam);

        BigDecimal objectiveScore = BigDecimal.ZERO;
        BigDecimal subjectiveScore = BigDecimal.ZERO;

        List<ExamResultResponse.AnswerDetail> answerDetails = new ArrayList<>();

        List<ExamSession.Answer> sessionAnswers = session.getAnswers();
        if (sessionAnswers == null || sessionAnswers.isEmpty()) {
            throw new BusinessException("暂无答题记录");
        }

        for (ExamSession.Answer answer : sessionAnswers) {
            Exam.ExamQuestion examQuestion = findExamQuestion(exam, answer.getQuestionId());
            BigDecimal maxScore = scoreMap.getOrDefault(answer.getQuestionId(), BigDecimal.ZERO);

            if (QuestionTypeEnum.fromCode(answer.getQuestionType()).isObjective()) {
                objectiveScore = objectiveScore.add(answer.getScore() != null ? answer.getScore() : BigDecimal.ZERO);
            } else if (QuestionTypeEnum.fromCode(answer.getQuestionType()).isSubjective()) {
                subjectiveScore = subjectiveScore.add(answer.getScore() != null ? answer.getScore() : BigDecimal.ZERO);
            }

            ExamResultResponse.AnswerDetail detail = new ExamResultResponse.AnswerDetail();
            detail.setQuestionId(answer.getQuestionId());
            detail.setQuestionContent(examQuestion != null ? examQuestion.getContent() : "");
            detail.setQuestionType(answer.getQuestionType());
            detail.setAnswer(answer.getAnswer());
            detail.setIsCorrect(answer.getIsCorrect());
            detail.setScore(answer.getScore());
            detail.setMaxScore(maxScore);
            detail.setGradingStatus(answer.getGradingStatus());
            detail.setTeacherComment(answer.getTeacherComment());
            answerDetails.add(detail);
        }

        ExamResultResponse result = new ExamResultResponse();
        result.setExamSessionId(session.getId());
        result.setExamId(exam.getId());
        result.setExamTitle(exam.getTitle());
        result.setStudentId(session.getStudentId());
        User student = userService.getById(session.getStudentId());
        result.setStudentName(userService.getDisplayName(student));
        result.setStartedAt(session.getStartedAt());
        result.setSubmittedAt(session.getSubmittedAt());
        result.setObjectiveScore(objectiveScore);
        result.setSubjectiveScore(subjectiveScore);
        result.setTotalScore(session.getScore());
        result.setMaxScore(exam.getTotalScore());
        result.setPassScore(exam.getPassScore());
        result.setGradingStatus(session.getGradingStatus());
        result.setAnswers(answerDetails);

        return result;
    }

    // ==================== 分页查询与数据填充 ====================

    /**
     * 考试分页查询（教师端列表）
     * 状态查询策略：
     * - DRAFT 和 ENDED 是数据库直接存储的状态，可直接按 status 字段查询
     * - PUBLISHED 和 STARTED 是动态计算状态，需通过时间范围查询后由 applyCurrentStatuses 计算
     * @param pageRequest 分页参数
     * @param courseId 课程ID（可选筛选条件）
     * @param teacherId 教师ID（可选筛选条件，只能查自己的）
     * @param status 状态筛选（可选）
     * @param currentUserId 当前用户ID
     * @return 分页结果（含展示字段）
     */
    public PageResult<Exam> page(PageRequest pageRequest, Long courseId, Long teacherId, String status,
                                  Long currentUserId) {
        if (teacherId != null && !teacherId.equals(currentUserId)) {
            return PageResult.empty(pageRequest.getCurrent(), pageRequest.getSize());
        }

        boolean isStoredStatus = StringUtils.isNotBlank(status)
                && (ExamStatusEnum.DRAFT.getCode().equals(status) || ExamStatusEnum.ENDED.getCode().equals(status));

        Page<Exam> page = new Page<>(pageRequest.getCurrent(), pageRequest.getSize());
        LambdaQueryWrapper<Exam> wrapper = new LambdaQueryWrapper<>();
        applySharedFilters(wrapper, courseId, teacherId, currentUserId);

        if (isStoredStatus) {
            wrapper.eq(Exam::getStatus, status);
        } else {
            applyTimeFilter(wrapper, status);
        }

        applyDbSorting(wrapper, pageRequest);
        Page<Exam> dbPage = page(page, wrapper);
        List<Exam> records = dbPage.getRecords();

        if (!isStoredStatus) {
            applyCurrentStatuses(records);
        }
        fillExamDisplayFields(records);

        return PageResult.from(dbPage);
    }

    /**
     * 应用共享查询条件（教师ID、课程ID）
     * @param wrapper 查询包装器
     * @param courseId 课程ID
     * @param teacherId 教师ID（如为null则使用currentUserId）
     * @param currentUserId 当前用户ID
     */
    private void applySharedFilters(LambdaQueryWrapper<Exam> wrapper, Long courseId, Long teacherId,
                                      Long currentUserId) {
        if (teacherId != null) {
            wrapper.eq(Exam::getTeacherId, teacherId);
        } else {
            wrapper.eq(Exam::getTeacherId, currentUserId);
        }
        if (courseId != null) {
            wrapper.eq(Exam::getCourseId, courseId);
        }
    }

    /**
     * 根据动态状态应用时间范围过滤
     * 动态状态映射：
     * - PUBLISHED：开始时间 > 当前时间
     * - STARTED：开始时间 <= 当前时间 < 结束时间
     * - ENDED：结束时间 <= 当前时间
     * @param wrapper 查询包装器
     * @param status 目标状态代码
     */
    private void applyTimeFilter(LambdaQueryWrapper<Exam> wrapper, String status) {
        if (status == null) {
            return;
        }
        boolean isDynamic = !ExamStatusEnum.DRAFT.getCode().equals(status)
                && !ExamStatusEnum.ENDED.getCode().equals(status);
        if (!isDynamic) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        if (ExamStatusEnum.PUBLISHED.getCode().equals(status)) {
            wrapper.gt(Exam::getStartedAt, now);
        } else if (ExamStatusEnum.STARTED.getCode().equals(status)) {
            wrapper.le(Exam::getStartedAt, now).gt(Exam::getEndedAt, now);
        } else if (ExamStatusEnum.ENDED.getCode().equals(status)) {
            wrapper.le(Exam::getEndedAt, now);
        }
    }

    /**
     * 应用数据库排序
     * 支持的排序字段：createtime/created_at, startedat/started_at/starttime, endedat/ended_at/endtime, totalscore, duration
     * @param wrapper 查询包装器
     * @param pageRequest 分页请求（含排序字段和方向）
     */
    private void applyDbSorting(LambdaQueryWrapper<Exam> wrapper, PageRequest pageRequest) {
        String orderBy = StringUtils.isBlank(pageRequest.getOrderBy()) ? "id" : pageRequest.getOrderBy().toLowerCase();
        boolean asc = Boolean.TRUE.equals(pageRequest.getAsc());
        switch (orderBy) {
            case "createtime", "created_at" -> {
                if (asc) wrapper.orderByAsc(Exam::getCreatedAt); else wrapper.orderByDesc(Exam::getCreatedAt);
            }
            case "startedat", "started_at", "starttime" -> {
                if (asc) wrapper.orderByAsc(Exam::getStartedAt); else wrapper.orderByDesc(Exam::getStartedAt);
            }
            case "endedat", "ended_at", "endtime" -> {
                if (asc) wrapper.orderByAsc(Exam::getEndedAt); else wrapper.orderByDesc(Exam::getEndedAt);
            }
            case "totalscore" -> {
                if (asc) wrapper.orderByAsc(Exam::getTotalScore); else wrapper.orderByDesc(Exam::getTotalScore);
            }
            case "duration" -> {
                if (asc) wrapper.orderByAsc(Exam::getDuration); else wrapper.orderByDesc(Exam::getDuration);
            }
            default -> {
                if (asc) wrapper.orderByAsc(Exam::getId); else wrapper.orderByDesc(Exam::getId);
            }
        }
    }

    /**
     * 填充考试展示字段（课程名称、教师名称）
     * 批量查询优化：一次性查询所有相关课程和教师信息，避免N+1查询问题
     * @param exams 考试列表
     */
    private void fillExamDisplayFields(List<Exam> exams) {
        if (exams == null || exams.isEmpty()) {
            return;
        }

        List<Long> courseIds = exams.stream()
                .map(Exam::getCourseId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        Map<Long, String> courseNameMap = courseService.listByIds(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, Course::getName, (a, _b) -> a));

        List<Long> teacherIds = exams.stream()
                .map(Exam::getTeacherId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        Map<Long, String> teacherNameMap = userService.getDisplayNameMap(teacherIds);

        for (Exam exam : exams) {
            if (exam.getCourseId() != null) {
                exam.setCourseName(courseNameMap.get(exam.getCourseId()));
            }
            if (exam.getTeacherId() != null) {
                exam.setTeacherName(teacherNameMap.get(exam.getTeacherId()));
            }
        }
    }

    // ==================== 答案验证与规范化 ====================

    /**
     * 校验提交答案的格式合法性
     * 校验规则：
     * - 答案列表不能为null
     * - 每道题目必须有题目ID
     * - 多选题答案必须是有效的JSON非空数组
     * @param answers 答案列表
     */
    private void validateAnswers(List<ExamSession.Answer> answers) {
        if (answers == null) {
            throw new BusinessException("答案数据不能为空");
        }

        if (answers.isEmpty()) {
            return;
        }

        for (ExamSession.Answer answer : answers) {
            if (answer.getQuestionId() == null) {
                throw new BusinessException("题目ID不能为空");
            }

            if (!hasSubstantiveAnswer(answer)) {
                continue;
            }

            if (QuestionTypeEnum.MULTIPLE_CHOICE.getCode().equals(answer.getQuestionType())) {
                try {
                    List<String> options = JSONUtil.toList(answer.getAnswer(), String.class);
                    if (options.isEmpty()) {
                        throw new BusinessException("多选题答案不能为空数组");
                    }
                } catch (Exception e) {
                    throw new BusinessException("多选题答案格式错误，应为JSON数组格式，如：[\"A\", \"B\"]");
                }
            }
        }
    }

    /**
     * 规范化提交的答案列表（过滤无实质内容的答案）
     * @param answers 原始答案列表
     * @return 过滤后的答案列表（空答案被移除）
     */
    private List<ExamSession.Answer> normalizeSubmittedAnswers(List<ExamSession.Answer> answers) {
        if (answers == null || answers.isEmpty()) {
            return List.of();
        }
        return answers.stream()
                .filter(this::hasSubstantiveAnswer)
                .collect(Collectors.toList());
    }

    /**
     * 判断答案是否有实质内容
     * 空值判定：null、空字符串、空白字符串、多选题空数组均视为无实质内容
     * @param answer 答案对象
     * @return 是否有实质内容
     */
    private boolean hasSubstantiveAnswer(ExamSession.Answer answer) {
        if (answer == null || answer.getAnswer() == null) {
            return false;
        }

        String value = answer.getAnswer().trim();
        if (value.isEmpty()) {
            return false;
        }

        if (QuestionTypeEnum.MULTIPLE_CHOICE.getCode().equals(answer.getQuestionType()) && JSONUtil.isTypeJSONArray(value)) {
            try {
                return !JSONUtil.toList(value, String.class).isEmpty();
            } catch (Exception e) {
                return false;
            }
        }

        return true;
    }

    /**
     * 计算单个考试会话的截止时间（开始时间 + 考试时长 + 30秒缓冲）
     * @param session 考试会话
     * @param durationMinutes 考试时长（分钟）
     * @return 截止时间
     */
    private LocalDateTime sessionDeadline(ExamSession session, Integer durationMinutes) {
        if (session.getStartedAt() == null) {
            throw new BusinessException("考试开始时间异常");
        }
        return session.getStartedAt().plusMinutes(durationMinutes).plusSeconds(30);
    }

    /**
     * 校验答案题目ID的合法性（去重检查、归属检查）
     * @param answers 提交的答案列表
     * @param examQuestionIds 当前考试的有效题目ID集合
     */
    private void validateAnswerQuestionIds(List<ExamSession.Answer> answers, Set<Long> examQuestionIds) {
        if (answers == null || answers.isEmpty()) {
            return;
        }
        if (examQuestionIds.isEmpty()) {
            throw new BusinessException("考试题目不存在，无法提交");
        }

        Set<Long> submittedQuestionIds = new HashSet<>();
        for (ExamSession.Answer answer : answers) {
            Long questionId = answer.getQuestionId();
            if (!submittedQuestionIds.add(questionId)) {
                throw new BusinessException("题目 " + questionId + " 重复提交");
            }
            if (!examQuestionIds.contains(questionId)) {
                throw new BusinessException("题目 " + questionId + " 不属于当前考试");
            }
        }
    }

    // ==================== exam.examPaper 数据访问辅助方法 ====================

    /**
     * 根据题型和正确答案计算单题分值
     * 特殊处理：填空题按空数乘以基础分值（每空单独计分）
     * @param q 考试题目快照
     * @param typeScores 题型基础分值映射
     * @return 该题总分
     */
    private BigDecimal computeQuestionScore(Exam.ExamQuestion q, Map<String, BigDecimal> typeScores) {
        return computeQuestionTypeScore(q.getType(), q.getCorrectAnswer(), typeScores);
    }

    /**
     * 根据题型代码和正确答案（原始 Object）计算单题分值
     * 与 computeQuestionScore 等价，用于题目对象尚未构建为 ExamQuestion 的场景
     */
    private BigDecimal computeQuestionTypeScore(String type, Object correctAnswer, Map<String, BigDecimal> typeScores) {
        BigDecimal base = typeScores != null ? typeScores.getOrDefault(type, BigDecimal.ZERO) : BigDecimal.ZERO;
        if ("FILL_BLANK".equals(type) && correctAnswer instanceof java.util.List<?> list && !list.isEmpty()) {
            return base.multiply(BigDecimal.valueOf(list.size()));
        }
        return base;
    }

    /**
     * 从 exam.examPaper 构建题目ID到分值的映射Map
     * @param exam 考试对象
     * @return 题目ID -> 分值 的映射
     */
    private Map<Long, BigDecimal> getScoreMapFromExam(Exam exam) {
        if (exam == null || exam.getExamPaper() == null || exam.getExamPaper().getItems() == null) {
            return Map.of();
        }
        Map<String, BigDecimal> typeScores = exam.getExamPaper().getTypeScores();
        if (typeScores == null || typeScores.isEmpty()) {
            return Map.of();
        }
        return exam.getExamPaper().getItems().stream()
                .filter(q -> q.getQuestionId() != null)
                .collect(Collectors.toMap(
                        Exam.ExamQuestion::getQuestionId,
                        q -> computeQuestionScore(q, typeScores),
                        (a, b) -> a));
    }

    /**
     * 从 exam.examPaper 获取所有题目ID集合
     * @param exam 考试对象
     * @return 题目ID集合
     */
    private Set<Long> getQuestionIdsFromExam(Exam exam) {
        if (exam == null || exam.getExamPaper() == null || exam.getExamPaper().getItems() == null) {
            return Set.of();
        }
        return exam.getExamPaper().getItems().stream()
                .map(Exam.ExamQuestion::getQuestionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * 在 exam.examPaper 中根据题目ID查找题目快照
     * @param exam 考试对象
     * @param questionId 题目ID
     * @return 题目快照，未找到则返回null
     */
    private Exam.ExamQuestion findExamQuestion(Exam exam, Long questionId) {
        if (exam == null || exam.getExamPaper() == null || exam.getExamPaper().getItems() == null || questionId == null) {
            return null;
        }
        return exam.getExamPaper().getItems().stream()
                .filter(q -> q.getQuestionId() != null && q.getQuestionId().equals(questionId))
                .findFirst()
                .orElse(null);
    }

    /**
     * 对答案列表中的客观题进行自动评分（原地修改 answer 的 isCorrect、score、gradingStatus），
     * 主观题标记为待评分。返回客观题总分和是否存在主观题的汇总结果。
     *
     * @param answers  考生答案列表（会原地修改）
     * @param exam     考试对象
     * @param scoreMap 题目ID -> 分值 映射
     * @return 评分汇总结果
     */
    private GradingResult gradeAnswersInPlace(List<ExamSession.Answer> answers, Exam exam,
                                               Map<Long, BigDecimal> scoreMap) {
        BigDecimal objectiveScore = BigDecimal.ZERO;
        boolean hasSubjective = false;

        for (ExamSession.Answer answer : answers) {
            if (answer == null || answer.getQuestionId() == null) {
                continue;
            }
            Exam.ExamQuestion eq = findExamQuestion(exam, answer.getQuestionId());
            if (eq == null) {
                continue;
            }
            answer.setQuestionType(eq.getType());
            QuestionTypeEnum type = QuestionTypeEnum.fromCode(eq.getType());

            if (type.isObjective()) {
                boolean isCorrect = checkAnswerByExamQuestion(eq, answer.getAnswer());
                answer.setIsCorrect(isCorrect);
                answer.setGradingStatus(GradingStatusEnum.GRADED.getCode());
                BigDecimal score = isCorrect ? scoreMap.getOrDefault(answer.getQuestionId(), BigDecimal.ZERO) : BigDecimal.ZERO;
                answer.setScore(score);
                objectiveScore = objectiveScore.add(score);
            } else if (type.isSubjective()) {
                hasSubjective = true;
                answer.setIsCorrect(null);
                answer.setScore(null);
                answer.setGradingStatus(GradingStatusEnum.PENDING.getCode());
            }
        }
        return new GradingResult(objectiveScore, hasSubjective);
    }

    /**
     * 客观题自动评分结果
     */
    private record GradingResult(BigDecimal objectiveScore, boolean hasSubjective) {}

    // ==================== 考试状态动态计算 ====================

    /**
     * 批量计算并应用考试的当前动态状态
     * 动态状态规则（仅对非 DRAFT/ENDED 状态生效）：
     * - 当前时间 > 结束时间：ENDED
     * - 开始时间 <= 当前时间 <= 结束时间：STARTED
     * - 当前时间 < 开始时间：PUBLISHED
     * @param exams 考试列表
     */
    private void applyCurrentStatuses(List<Exam> exams) {
        if (exams == null || exams.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        exams.forEach(exam -> applyCurrentStatus(exam, now));
    }

    /**
     * 计算并应用单个考试的当前动态状态（使用当前系统时间）
     * @param exam 考试对象
     */
    private void applyCurrentStatus(Exam exam) {
        applyCurrentStatus(exam, LocalDateTime.now());
    }

    /**
     * 计算并应用单个考试的当前动态状态（使用指定时间）
     * @param exam 考试对象
     * @param now 用于判断的当前时间
     */
    private void applyCurrentStatus(Exam exam, LocalDateTime now) {
        if (exam == null) {
            return;
        }
        if (ExamStatusEnum.DRAFT.getCode().equals(exam.getStatus())
                || ExamStatusEnum.ENDED.getCode().equals(exam.getStatus())) {
            return;
        }
        if (exam.getStartedAt() == null || exam.getEndedAt() == null) {
            return;
        }

        String newStatus;
        if (now.isAfter(exam.getEndedAt())) {
            newStatus = ExamStatusEnum.ENDED.getCode();
        } else if (!now.isBefore(exam.getStartedAt())) {
            newStatus = ExamStatusEnum.STARTED.getCode();
        } else {
            newStatus = ExamStatusEnum.PUBLISHED.getCode();
        }

        if (!newStatus.equals(exam.getStatus())) {
            exam.setStatus(newStatus);
        }
    }

    // ==================== 定时任务 ====================

    /**
     * 定时任务：每分钟执行一次，自动处理过期考试
     * 处理内容：
     * 1. 批量更新已到期考试的状态为 ENDED
     * 2. 批量更新已到开始时间的考试状态为 STARTED
     * 3. 强制提交所有已过期的进行中的考试会话（超时自动交卷）
     * 异常处理：单个会话处理失败不影响其他会话，整体异常被捕获记录
     */
    @Scheduled(fixedRate = 60000)
    public void autoSubmitExpiredSessions() {
        try {
            LocalDateTime now = LocalDateTime.now();

            // 批量更新已结束的考试状态
            lambdaUpdate()
                    .ne(Exam::getStatus, ExamStatusEnum.ENDED.getCode())
                    .ne(Exam::getStatus, ExamStatusEnum.DRAFT.getCode())
                    .le(Exam::getEndedAt, now)
                    .set(Exam::getStatus, ExamStatusEnum.ENDED.getCode())
                    .update();

            // 批量更新进行中的考试状态
            lambdaUpdate()
                    .eq(Exam::getStatus, ExamStatusEnum.PUBLISHED.getCode())
                    .le(Exam::getStartedAt, now)
                    .gt(Exam::getEndedAt, now)
                    .set(Exam::getStatus, ExamStatusEnum.STARTED.getCode())
                    .update();

            List<ExamSession> expiredSessions = examSessionService.getExpiredInProgress();
            if (expiredSessions == null || expiredSessions.isEmpty()) {
                return;
            }
            log.info("开始处理过期考试会话，共 {} 个", expiredSessions.size());
            int processed = 0;
            for (ExamSession session : expiredSessions) {
                try {
                    submitExpiredSession(session);
                    processed++;
                } catch (Exception e) {
                    log.error("强制交卷失败: sessionId={}", session.getId(), e);
                }
            }
            log.info("过期考试处理完成，成功处理 {} 个", processed);
        } catch (Exception e) {
            log.error("autoSubmitExpiredSessions 定时任务执行异常", e);
        }
    }

    /**
     * 强制提交过期考试会话（定时任务调用）
     * 事务传播：REQUIRES_NEW，确保每个会话独立提交，失败不影响其他会话
     * 处理逻辑与学生正常提交类似，但不校验时间限制（已经超时）
     * @param session 过期的考试会话
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void submitExpiredSession(ExamSession session) {
        Exam exam = getById(session.getExamId());
        if (exam == null) {
            return;
        }
        List<ExamSession.Answer> answers = session.getAnswers();
        if (answers == null) {
            answers = List.of();
        }
        answers = normalizeSubmittedAnswers(answers);

        GradingResult gr = gradeAnswersInPlace(answers, exam, getScoreMapFromExam(exam));

        session.setAnswers(answers.isEmpty() ? List.of() : answers);
        session.setScore(gr.objectiveScore());
        session.setSubmittedAt(LocalDateTime.now());
        session.setStatus(gr.hasSubjective() ? ExamSessionStatusEnum.SUBMITTED.getCode() : ExamSessionStatusEnum.GRADED.getCode());
        session.setGradingStatus(gr.hasSubjective() ? GradingStatusEnum.PENDING.getCode() : GradingStatusEnum.COMPLETED.getCode());
        examSessionService.updateById(session);
        log.info("强制交卷成功: sessionId={}, examId={}, studentId={}, hasSubjective={}",
                session.getId(), session.getExamId(), session.getStudentId(), gr.hasSubjective());
    }

    // ==================== 数据转换方法 ====================

    /**
     * 将 Exam 实体转换为 ExamResponse DTO
     * @param exam 考试实体
     * @return 考试响应对象，null输入返回null
     */
    public ExamResponse convertToResponse(Exam exam) {
        if (exam == null) return null;
        return examDtoMapper.toResponse(exam);
    }

    /**
     * 批量转换 Exam 实体列表为 ExamResponse DTO 列表
     * @param exams 考试实体列表
     * @return 考试响应对象列表
     */
    public List<ExamResponse> convertToResponses(List<Exam> exams) {
        if (exams == null || exams.isEmpty()) return List.of();
        return examDtoMapper.toResponseList(exams);
    }

    /**
     * 将 Exam 分页结果转换为 ExamResponse 分页结果
     * 额外注入考试统计信息（参与人数、提交人数、待评分人数）
     * @param pageResult 原始分页结果
     * @return 转换后的分页结果（含统计信息）
     */
    public PageResult<ExamResponse> convertToPageResult(PageResult<Exam> pageResult) {
        if (pageResult == null) {
            return PageResult.empty(1, 10);
        }
        PageResult<ExamResponse> result = PageResult.map(pageResult, this::convertToResponses);
        injectExamStats(result.getRecords());
        return result;
    }

    /**
     * 为考试响应列表注入统计信息
     * 统计维度：
     * - participantCount：参与考试的学生总数
     * - submittedCount：已提交（含已评分）的学生数
     * - pendingGradingCount：已提交但待评分的学生数（含主观题待评）
     * @param responses 考试响应列表
     */
    private void injectExamStats(List<ExamResponse> responses) {
        if (responses == null || responses.isEmpty()) return;
        List<Long> examIds = responses.stream().map(ExamResponse::getId).toList();
        Map<Long, Integer> participantMap = new HashMap<>();
        Map<Long, Integer> submittedMap = new HashMap<>();
        Map<Long, Integer> pendingMap = new HashMap<>();

        List<ExamSession> sessions = examSessionService.lambdaQuery()
                .in(ExamSession::getExamId, examIds)
                .list();
        for (ExamSession s : sessions) {
            Long eid = s.getExamId();
            participantMap.merge(eid, 1, Integer::sum);
            if (ExamSessionStatusEnum.SUBMITTED.getCode().equals(s.getStatus())
                    || ExamSessionStatusEnum.GRADED.getCode().equals(s.getStatus())) {
                submittedMap.merge(eid, 1, Integer::sum);
                String gs = s.getGradingStatus();
                if (GradingStatusEnum.PENDING.getCode().equals(gs)
                        || GradingStatusEnum.GRADING.getCode().equals(gs)) {
                    pendingMap.merge(eid, 1, Integer::sum);
                }
            }
        }
        for (ExamResponse r : responses) {
            r.setParticipantCount(participantMap.getOrDefault(r.getId(), 0).longValue());
            r.setSubmittedCount(submittedMap.getOrDefault(r.getId(), 0).longValue());
            r.setPendingGradingCount(pendingMap.getOrDefault(r.getId(), 0).longValue());
        }
    }

    /**
     * 将考试会话转换为响应对象（委托给 ExamSessionService）
     * @param session 考试会话
     * @return 会话响应对象
     */
    public ExamSessionResponse convertSessionToResponse(ExamSession session) {
        return examSessionService.convertToResponse(session);
    }

    /**
     * 将试卷转换为响应对象（委托给 PaperService）
     * @param paper 试卷实体
     * @return 试卷响应对象
     */
    public PaperResponse convertPaperToResponse(Paper paper) {
        return paperService.convertToResponse(paper);
    }

    /**
     * 将题目列表转换为响应对象列表（委托给 QuestionService）
     * @param questions 题目实体列表
     * @param userRole 当前用户角色（决定返回内容的可见性）
     * @return 题目响应对象列表
     */
    public List<QuestionResponse> convertQuestionsToResponses(List<Question> questions, String userRole) {
        return questionService.convertToResponses(questions, userRole);
    }

    // ==================== 通用工具方法 ====================

    /**
     * 根据ID获取考试，不存在则抛业务异常
     * 获取后自动应用动态状态计算
     * @param examId 考试ID
     * @return 考试实体
     */
    private Exam getByIdOrThrow(Long examId) {
        Exam exam = getById(examId);
        if (exam == null) {
            throw new BusinessException("考试不存在");
        }
        applyCurrentStatus(exam);
        return exam;
    }

    /**
     * 断言考试已发布（PUBLISHED 或 STARTED 状态）
     * @param exam 考试对象
     */
    private void assertExamPublished(Exam exam) {
        if (!ExamStatusEnum.PUBLISHED.getCode().equals(exam.getStatus())
                && !ExamStatusEnum.STARTED.getCode().equals(exam.getStatus())) {
            throw new BusinessException("考试未发布");
        }
    }

    /**
     * 断言考试时间已设置（开始时间和结束时间均不为null）
     * @param exam 考试对象
     */
    private void assertExamTimeSet(Exam exam) {
        if (exam.getStartedAt() == null || exam.getEndedAt() == null) {
            throw new BusinessException("考试时间未设置");
        }
    }
}