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
 * 考试服务
 * 管理考试的全生命周期：创建、发布、取消、删除，以及考试过程管理：开始、保存、提交、评分和结果查询
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExamService extends ServiceImpl<ExamMapper, Exam> {

    private static final List<String> OBJECTIVE_TYPES = List.of(
            QuestionTypeEnum.SINGLE_CHOICE.getCode(),
            QuestionTypeEnum.MULTIPLE_CHOICE.getCode(),
            QuestionTypeEnum.TRUE_FALSE.getCode(),
            QuestionTypeEnum.FILL_BLANK.getCode()
    );
    private static final List<String> SUBJECTIVE_TYPES = List.of(
            QuestionTypeEnum.ESSAY.getCode()
    );

    private final PaperService paperService;
    private final QuestionService questionService;
    private final ExamSessionService examSessionService;
    private final CourseService courseService;
    private final UserService userService;

    public List<Exam> getByCourseId(Long courseId) {
        return lambdaQuery().eq(Exam::getCourseId, courseId).list();
    }

    public List<Exam> getByTeacherId(Long teacherId) {
        List<Exam> exams = lambdaQuery().eq(Exam::getTeacherId, teacherId).list();
        applyCurrentStatuses(exams);
        fillExamDisplayFields(exams);
        return exams;
    }

    public List<Exam> listWithDisplayFields() {
        List<Exam> exams = list();
        applyCurrentStatuses(exams);
        fillExamDisplayFields(exams);
        return exams;
    }

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
     */
    public Exam getByIdWithPermission(Long id, Long userId, String userRole) {
        Exam exam = getByIdWithDisplayFields(id);
        if (exam == null) {
            throw new BusinessException("考试不存在");
        }

        if (RoleEnum.TEACHER.getCode().equals(userRole) || RoleEnum.ADMIN.getCode().equals(userRole)) {
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

    /**
     * 创建考试：选定试卷，将试卷中所有题目快照复制到 exam.examPaper，之后与试卷完全解耦
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

            BigDecimal score = typeScoreMap.getOrDefault(question.getType(), BigDecimal.ZERO);
            if ("FILL_BLANK".equals(question.getType())
                    && question.getCorrectAnswer() instanceof java.util.List<?> list && !list.isEmpty()) {
                score = score.multiply(BigDecimal.valueOf(list.size()));
            }
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
     * 更新考试：草稿状态可修改全部字段，非草稿状态锁定关键字段
     */
    @Transactional
    public boolean updateExam(Long id, ExamUpdateRequest examRequest, Long userId, String userRole) {
        Exam originalExam = checkOwnership(id, userId, userRole);

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

    public List<Exam> getByStatus(String status) {
        return lambdaQuery().eq(Exam::getStatus, status).list();
    }

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

        PageResult<Exam> result = new PageResult<>();
        result.setCurrent((int) dbPage.getCurrent());
        result.setSize((int) dbPage.getSize());
        result.setTotal(adjustedTotal);
        result.setPages(adjustedPages);
        result.setRecords(records);
        result.setHasNext(dbPage.getCurrent() < adjustedPages);
        result.setHasPrevious(dbPage.getCurrent() > 1);
        return result;
    }

    public List<QuestionForExamResponse> getExamQuestions(Long examId, Long studentId) {
        Exam exam = getById(examId);
        if (exam == null) {
            throw new BusinessException("考试不存在");
        }
        applyCurrentStatus(exam);

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
     * 将考试题目转换为响应对象（学生端，隐藏正确答案）
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
     * 获取考试回顾题目（含正确答案，考试结束后）
     */
    public List<Exam.ExamQuestion> getReviewQuestions(Long examId, Long userId, String userRole) {
        Exam exam = checkReviewPermission(examId, userId, userRole);
        return exam.getExamPaper() != null && exam.getExamPaper().getItems() != null ? exam.getExamPaper().getItems() : List.of();
    }

    private Exam checkReviewPermission(Long examId, Long userId, String userRole) {
        Exam exam = getById(examId);
        if (exam == null) {
            throw new BusinessException("考试不存在");
        }
        applyCurrentStatus(exam);

        if (RoleEnum.TEACHER.getCode().equals(userRole) || RoleEnum.ADMIN.getCode().equals(userRole)) {
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

        PageResult<Exam> result = new PageResult<>();
        result.setCurrent((int) dbPage.getCurrent());
        result.setSize((int) dbPage.getSize());
        result.setTotal(dbPage.getTotal());
        result.setPages(dbPage.getPages());
        result.setRecords(records);
        result.setHasNext(dbPage.hasNext());
        result.setHasPrevious(dbPage.hasPrevious());
        return result;
    }

    @Transactional
    public ExamSession startExam(Long examId, Long studentId) {
        Exam exam = getById(examId);
        if (exam == null) {
            throw new BusinessException("考试不存在");
        }
        applyCurrentStatus(exam);
        if (!ExamStatusEnum.PUBLISHED.getCode().equals(exam.getStatus())
                && !ExamStatusEnum.STARTED.getCode().equals(exam.getStatus())) {
            throw new BusinessException("考试未发布");
        }

        if (!courseService.isCourseMember(exam.getCourseId(), studentId)) {
            throw new BusinessException("请先加入对应课程");
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
            if (existing != null) {
                if (ExamSessionStatusEnum.IN_PROGRESS.getCode().equals(existing.getStatus())) {
                    return existing;
                }
                throw new BusinessException("已参加过该考试");
            }
            throw new BusinessException("创建考试记录失败");
        }

        return session;
    }

    @Transactional
    public void autoSaveExam(Long examId, Long studentId, List<ExamSession.Answer> answers) {
        Exam exam = getById(examId);
        if (exam == null) {
            throw new BusinessException("考试不存在");
        }
        applyCurrentStatus(exam);
        if (!ExamStatusEnum.PUBLISHED.getCode().equals(exam.getStatus())
                && !ExamStatusEnum.STARTED.getCode().equals(exam.getStatus())) {
            throw new BusinessException("考试未发布");
        }
        if (exam.getEndedAt() == null) {
            throw new BusinessException("考试时间未设置");
        }
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

    @Transactional
    public void submitExam(Long examId, Long studentId, List<ExamSession.Answer> answers) {
        Exam exam = getById(examId);
        if (exam == null) {
            throw new BusinessException("考试不存在");
        }
        applyCurrentStatus(exam);
        if (!ExamStatusEnum.PUBLISHED.getCode().equals(exam.getStatus())
                && !ExamStatusEnum.STARTED.getCode().equals(exam.getStatus())) {
            throw new BusinessException("考试未发布");
        }

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

        BigDecimal objectiveScore = BigDecimal.ZERO;
        boolean hasSubjective = false;

        for (ExamSession.Answer answer : answers) {
            Exam.ExamQuestion examQuestion = findExamQuestion(exam, answer.getQuestionId());
            if (examQuestion != null) {
                answer.setQuestionType(examQuestion.getType());

                if (OBJECTIVE_TYPES.contains(examQuestion.getType())) {
                    boolean isCorrect = checkAnswerByExamQuestion(examQuestion, answer.getAnswer());
                    answer.setIsCorrect(isCorrect);
                    answer.setGradingStatus(GradingStatusEnum.GRADED.getCode());

                    if (isCorrect) {
                        BigDecimal score = scoreMap.getOrDefault(answer.getQuestionId(), BigDecimal.ZERO);
                        answer.setScore(score);
                        objectiveScore = objectiveScore.add(score);
                    } else {
                        answer.setScore(BigDecimal.ZERO);
                    }
                } else if (SUBJECTIVE_TYPES.contains(examQuestion.getType())) {
                    hasSubjective = true;
                    answer.setIsCorrect(null);
                    answer.setScore(null);
                    answer.setGradingStatus(GradingStatusEnum.PENDING.getCode());
                }
            }
        }

        validateAnswers(answers);

        session.setAnswers(answers);
        session.setScore(objectiveScore);
        session.setSubmittedAt(now);
        session.setStatus(hasSubjective ? ExamSessionStatusEnum.SUBMITTED.getCode() : ExamSessionStatusEnum.GRADED.getCode());
        session.setGradingStatus(hasSubjective ? GradingStatusEnum.PENDING.getCode() : GradingStatusEnum.COMPLETED.getCode());

        try {
            examSessionService.updateById(session);
        } catch (OptimisticLockingFailureException e) {
            throw new BusinessException("该考试已提交，请勿重复提交");
        }
    }

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

    private boolean isNumericEquivalent(String a, String b) {
        try {
            return new BigDecimal(a).compareTo(new BigDecimal(b)) == 0;
        } catch (Exception e) {
            return false;
        }
    }

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

    @Transactional
    public void publishExam(Long examId, Long userId, String userRole) {
        Exam exam = checkOwnership(examId, userId, userRole);

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

    @Transactional
    public void endExam(Long examId, Long userId, String userRole) {
        Exam exam = checkOwnership(examId, userId, userRole);
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

    public Exam checkOwnership(Long examId, Long userId, String userRole) {
        Exam exam = getById(examId);
        if (exam == null) {
            throw new BusinessException("考试不存在");
        }
        if (exam.getTeacherId() == null || !exam.getTeacherId().equals(userId)) {
            throw new BusinessException("无权操作该考试");
        }
        return exam;
    }

    public void checkCanDelete(Long examId) {
        List<ExamSession> sessions = examSessionService.getByExamId(examId);
        if (!sessions.isEmpty()) {
            throw new BusinessException("已有 " + sessions.size() + " 名学生参加该考试，无法删除");
        }
    }

    @Transactional
    public void deleteExam(Long examId) {
        examSessionService.lambdaUpdate()
                .eq(ExamSession::getExamId, examId)
                .set(ExamSession::getDeleted, 1)
                .update();
        removeById(examId);
    }

    @Transactional
    public void gradeSubjectiveAnswers(Long operatorId, String operatorRole, GradeSubjectiveRequest request) {
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

            if (answer.getQuestionType() == null || !SUBJECTIVE_TYPES.contains(answer.getQuestionType())) {
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
                .filter(a -> SUBJECTIVE_TYPES.contains(a.getQuestionType()))
                .allMatch(a -> GradingStatusEnum.GRADED.getCode().equals(a.getGradingStatus()));

        if (!allGraded) {
            throw new BusinessException("还有主观题未评分，请完成所有主观题的评分");
        }

        for (ExamSession.Answer answer : answers) {
            if (SUBJECTIVE_TYPES.contains(answer.getQuestionType()) && answer.getScore() != null) {
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

    @Transactional
    public int autoGradeByExam(Long examId, Long operatorId, String operatorRole) {
        checkOwnership(examId, operatorId, operatorRole);

        Exam exam = getById(examId);

        List<ExamSession> sessions = examSessionService.getByExamId(examId);
        if (sessions == null || sessions.isEmpty()) {
            return 0;
        }

        Map<Long, BigDecimal> scoreMap = getScoreMapFromExam(exam);

        int processed = 0;
        for (ExamSession session : sessions) {
            if (session.getAnswers() == null || session.getAnswers().isEmpty()) {
                continue;
            }
            boolean submittedLike = session.getSubmittedAt() != null
                    || ExamSessionStatusEnum.SUBMITTED.getCode().equals(session.getStatus())
                    || ExamSessionStatusEnum.GRADED.getCode().equals(session.getStatus());
            if (!submittedLike) {
                continue;
            }

            BigDecimal objectiveScore = BigDecimal.ZERO;
            BigDecimal gradedEssayScore = BigDecimal.ZERO;
            boolean hasEssay = false;
            boolean allEssayGraded = true;

            for (ExamSession.Answer answer : session.getAnswers()) {
                if (answer == null || answer.getQuestionId() == null) {
                    continue;
                }
                Exam.ExamQuestion examQuestion = findExamQuestion(exam, answer.getQuestionId());
                if (examQuestion == null) {
                    continue;
                }

                answer.setQuestionType(examQuestion.getType());
                if (OBJECTIVE_TYPES.contains(examQuestion.getType())) {
                    boolean isCorrect = checkAnswerByExamQuestion(examQuestion, answer.getAnswer());
                    answer.setIsCorrect(isCorrect);
                    answer.setGradingStatus(GradingStatusEnum.GRADED.getCode());
                    BigDecimal score = isCorrect ? scoreMap.getOrDefault(answer.getQuestionId(), BigDecimal.ZERO) : BigDecimal.ZERO;
                    answer.setScore(score);
                    objectiveScore = objectiveScore.add(score);
                } else if (SUBJECTIVE_TYPES.contains(examQuestion.getType())) {
                    hasEssay = true;
                    if ((GradingStatusEnum.GRADED.getCode().equals(answer.getGradingStatus())
                            || GradingStatusEnum.COMPLETED.getCode().equals(answer.getGradingStatus()))
                            && answer.getScore() != null) {
                        gradedEssayScore = gradedEssayScore.add(answer.getScore());
                        answer.setIsCorrect(answer.getScore().compareTo(BigDecimal.ZERO) > 0);
                    } else {
                        allEssayGraded = false;
                        answer.setIsCorrect(null);
                        answer.setScore(null);
                        answer.setGradingStatus(GradingStatusEnum.PENDING.getCode());
                    }
                }
            }

            if (!hasEssay) {
                session.setScore(objectiveScore);
                session.setStatus(ExamSessionStatusEnum.GRADED.getCode());
                session.setGradingStatus(GradingStatusEnum.COMPLETED.getCode());
            } else if (allEssayGraded) {
                session.setScore(objectiveScore.add(gradedEssayScore));
                session.setStatus(ExamSessionStatusEnum.GRADED.getCode());
                session.setGradingStatus(GradingStatusEnum.COMPLETED.getCode());
            } else {
                session.setScore(objectiveScore);
                session.setStatus(ExamSessionStatusEnum.SUBMITTED.getCode());
                session.setGradingStatus(GradingStatusEnum.PENDING.getCode());
            }

            try {
                examSessionService.updateById(session);
            } catch (OptimisticLockingFailureException e) {
                log.warn("批量阅卷跳过考试记录 {} 会话 {}，已被其他人并发修改",
                        examId, session.getId());
                continue;
            }
            processed++;
        }

        return processed;
    }

    public ExamResultResponse getExamResult(Long examSessionId, Long userId, String userRole) {
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

            if (OBJECTIVE_TYPES.contains(answer.getQuestionType())) {
                objectiveScore = objectiveScore.add(answer.getScore() != null ? answer.getScore() : BigDecimal.ZERO);
            } else if (SUBJECTIVE_TYPES.contains(answer.getQuestionType())) {
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

    public PageResult<Exam> page(PageRequest pageRequest, Long courseId, Long teacherId, String status,
                                  Long currentUserId, String currentUserRole) {
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

        PageResult<Exam> result = new PageResult<>();
        result.setCurrent((int) dbPage.getCurrent());
        result.setSize((int) dbPage.getSize());
        result.setTotal(dbPage.getTotal());
        result.setPages(dbPage.getPages());
        result.setRecords(records);
        result.setHasNext(dbPage.hasNext());
        result.setHasPrevious(dbPage.hasPrevious());
        return result;
    }

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

    private List<ExamSession.Answer> normalizeSubmittedAnswers(List<ExamSession.Answer> answers) {
        if (answers == null || answers.isEmpty()) {
            return List.of();
        }
        return answers.stream()
                .filter(this::hasSubstantiveAnswer)
                .collect(Collectors.toList());
    }

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

    private LocalDateTime sessionDeadline(ExamSession session, Integer durationMinutes) {
        if (session.getStartedAt() == null) {
            throw new BusinessException("考试开始时间异常");
        }
        return session.getStartedAt().plusMinutes(durationMinutes).plusSeconds(30);
    }

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

    // ========== 从 exam.examPaper 获取数据的辅助方法 ==========

    /**
     * 根据题型和正确答案计算单题分值（填空题按空数乘）
     */
    private BigDecimal computeQuestionScore(Exam.ExamQuestion q, Map<String, BigDecimal> typeScores) {
        BigDecimal base = typeScores != null ? typeScores.getOrDefault(q.getType(), BigDecimal.ZERO) : BigDecimal.ZERO;
        if ("FILL_BLANK".equals(q.getType()) && q.getCorrectAnswer() instanceof java.util.List<?> list && !list.isEmpty()) {
            return base.multiply(BigDecimal.valueOf(list.size()));
        }
        return base;
    }

    /**
     * 从 exam.examPaper 构建分值 Map
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
     * 从 exam.examPaper 获取题目ID集合
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
     * 在 exam.examPaper 中查找指定题目
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

    // ========== 状态动态计算 ==========

    private void applyCurrentStatuses(List<Exam> exams) {
        if (exams == null || exams.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        exams.forEach(exam -> applyCurrentStatus(exam, now));
    }

    private void applyCurrentStatus(Exam exam) {
        applyCurrentStatus(exam, LocalDateTime.now());
    }

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
            if (exam.getId() != null) {
                lambdaUpdate().eq(Exam::getId, exam.getId()).set(Exam::getStatus, newStatus).update();
            }
        }
    }

    // ========== 定时任务 ==========

    @Scheduled(fixedRate = 60000)
    public void autoSubmitExpiredSessions() {
        try {
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

        BigDecimal objectiveScore = BigDecimal.ZERO;
        boolean hasSubjective = false;

        if (!answers.isEmpty()) {
            Map<Long, BigDecimal> scoreMap = getScoreMapFromExam(exam);

            for (ExamSession.Answer answer : answers) {
                if (answer == null || answer.getQuestionId() == null) {
                    continue;
                }
                Exam.ExamQuestion examQuestion = findExamQuestion(exam, answer.getQuestionId());
                if (examQuestion == null) {
                    continue;
                }
                answer.setQuestionType(examQuestion.getType());

                if (OBJECTIVE_TYPES.contains(examQuestion.getType())) {
                    boolean isCorrect = checkAnswerByExamQuestion(examQuestion, answer.getAnswer());
                    answer.setIsCorrect(isCorrect);
                    answer.setGradingStatus(GradingStatusEnum.GRADED.getCode());
                    if (isCorrect) {
                        BigDecimal score = scoreMap.getOrDefault(answer.getQuestionId(), BigDecimal.ZERO);
                        answer.setScore(score);
                        objectiveScore = objectiveScore.add(score);
                    } else {
                        answer.setScore(BigDecimal.ZERO);
                    }
                } else if (SUBJECTIVE_TYPES.contains(examQuestion.getType())) {
                    hasSubjective = true;
                    answer.setIsCorrect(null);
                    answer.setScore(null);
                    answer.setGradingStatus(GradingStatusEnum.PENDING.getCode());
                }
            }
        }

        session.setAnswers(answers.isEmpty() ? List.of() : answers);
        session.setScore(objectiveScore);
        session.setSubmittedAt(LocalDateTime.now());
        session.setStatus(hasSubjective ? ExamSessionStatusEnum.SUBMITTED.getCode() : ExamSessionStatusEnum.GRADED.getCode());
        session.setGradingStatus(hasSubjective ? GradingStatusEnum.PENDING.getCode() : GradingStatusEnum.COMPLETED.getCode());
        examSessionService.updateById(session);
        log.info("强制交卷成功: sessionId={}, examId={}, studentId={}, hasSubjective={}",
                session.getId(), session.getExamId(), session.getStudentId(), hasSubjective);
    }

    // ========== 转换方法 ==========

    public ExamResponse convertToResponse(Exam exam) {
        if (exam == null) {
            return null;
        }
        ExamResponse response = new ExamResponse();
        BeanUtils.copyProperties(exam, response);
        return response;
    }

    public List<ExamResponse> convertToResponses(List<Exam> exams) {
        if (exams == null || exams.isEmpty()) {
            return List.of();
        }
        return exams.stream()
                .map(this::convertToResponse)
                .toList();
    }

    public PageResult<ExamResponse> convertToPageResult(PageResult<Exam> pageResult) {
        if (pageResult == null) {
            return PageResult.empty(1, 10);
        }
        PageResult<ExamResponse> response = new PageResult<>();
        List<ExamResponse> records = convertToResponses(pageResult.getRecords());
        injectExamStats(records);
        response.setRecords(records);
        response.setTotal(pageResult.getTotal());
        response.setSize(pageResult.getSize());
        response.setCurrent(pageResult.getCurrent());
        response.setPages(pageResult.getPages());
        response.setHasNext(pageResult.getHasNext());
        response.setHasPrevious(pageResult.getHasPrevious());
        return response;
    }

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

    public ExamSessionResponse convertSessionToResponse(ExamSession session) {
        return examSessionService.convertToResponse(session);
    }

    public PaperResponse convertPaperToResponse(Paper paper) {
        return paperService.convertToResponse(paper);
    }

    public List<QuestionResponse> convertQuestionsToResponses(List<Question> questions, String userRole) {
        return questionService.convertToResponses(questions, userRole);
    }
}