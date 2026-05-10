package com.southcollege.exam.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.southcollege.exam.dto.request.ExamCreateRequest;
import com.southcollege.exam.dto.request.ExamUpdateRequest;
import com.southcollege.exam.dto.request.GradeSubjectiveRequest;
import com.southcollege.exam.dto.request.PageRequest;
import com.southcollege.exam.dto.response.ExamResultResponse;
import com.southcollege.exam.dto.response.PageResult;
import com.southcollege.exam.dto.response.QuestionForExamResponse;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        return baseMapper.selectByCourseId(courseId);
    }

    public List<Exam> getByTeacherId(Long teacherId) {
        List<Exam> exams = baseMapper.selectByTeacherId(teacherId);
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
     * <p>
     * 管理员：任意查看
     * 教师：只能查看自己创建的考试
     * 学生：只能查看已发布、进行中或已结束的考试，且必须是课程成员
     */
    public Exam getByIdWithPermission(Long id, Long userId, String userRole) {
        Exam exam = getByIdWithDisplayFields(id);
        if (exam == null) {
            throw new BusinessException("考试不存在");
        }

        if (RoleEnum.ADMIN.getCode().equals(userRole)) {
            return exam;
        }

        if (RoleEnum.TEACHER.getCode().equals(userRole)) {
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
            return exam;
        }

        throw new BusinessException("无权查看该考试");
    }

    /**
     * 创建考试：校验试卷有效性，从试卷获取题目和分值，设置初始状态为草稿
     *
     * @param examRequest 考试创建请求
     * @param teacherId   创建教师ID
     * @return 是否成功
     */
    @Transactional
    public boolean createExam(ExamCreateRequest examRequest, Long teacherId) {
        if (examRequest.getPaperId() == null) {
            throw new BusinessException("试卷ID不能为空");
        }

        Paper paper = paperService.getById(examRequest.getPaperId());
        if (paper == null) {
            throw new BusinessException("试卷不存在");
        }

        Exam exam = new Exam();
        BeanUtils.copyProperties(examRequest, exam);
        exam.setTeacherId(teacherId);
        exam.setTotalScore(paper.getTotalScore());
        exam.setStatus(ExamStatusEnum.DRAFT.getCode());
        return save(exam);
    }

    /**
     * 更新考试：
     * 草稿状态可修改全部字段（包括试卷、课程等），会自动同步总分；
     * 非草稿状态锁定关键字段（试卷、课程、时间、分值），防止发布后被篡改
     *
     * @param id           考试ID
     * @param examRequest  考试更新请求
     * @param userId       操作者ID
     * @param userRole     操作者角色
     * @return 是否成功
     */
    @Transactional
    public boolean updateExam(Long id, ExamUpdateRequest examRequest, Long userId, String userRole) {
        checkOwnership(id, userId, userRole);

        Exam originalExam = getById(id);
        if (originalExam == null) {
            throw new BusinessException("考试不存在");
        }

        Exam exam = new Exam();
        BeanUtils.copyProperties(examRequest, exam);
        exam.setId(id);
        exam.setTeacherId(originalExam.getTeacherId());
        exam.setStatus(originalExam.getStatus());

        if (!ExamStatusEnum.DRAFT.getCode().equals(originalExam.getStatus())) {
            exam.setPaperId(originalExam.getPaperId());
            exam.setCourseId(originalExam.getCourseId());
            exam.setStartedAt(originalExam.getStartedAt());
            exam.setEndedAt(originalExam.getEndedAt());
            exam.setDuration(originalExam.getDuration());
            exam.setTotalScore(originalExam.getTotalScore());
        } else {
            if (examRequest.getPaperId() != null) {
                Paper paper = paperService.getById(examRequest.getPaperId());
                if (paper == null) {
                    throw new BusinessException("试卷不存在");
                }
                exam.setTotalScore(paper.getTotalScore());
            }
        }

        return updateById(exam);
    }

    public List<Exam> getByPaperId(Long paperId) {
        return lambdaQuery()
                .eq(Exam::getPaperId, paperId)
                .list();
    }

    public List<Exam> getByStatus(String status) {
        return baseMapper.selectByStatus(status);
    }

    public List<Exam> getPublishedExams(Long studentId) {
        List<Exam> exams = lambdaQuery()
                .notIn(Exam::getStatus, ExamStatusEnum.DRAFT.getCode(), ExamStatusEnum.CANCELLED.getCode())
                .ge(Exam::getEndedAt, LocalDateTime.now())
                .list();
        applyCurrentStatuses(exams);
        exams = exams.stream()
                .filter(exam -> ExamStatusEnum.PUBLISHED.getCode().equals(exam.getStatus())
                        || ExamStatusEnum.STARTED.getCode().equals(exam.getStatus()))
                .toList();
        fillExamDisplayFields(exams);
        return exams;
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

        Paper paper = paperService.getById(exam.getPaperId());
        if (paper == null || paper.getQuestions() == null) {
            return List.of();
        }

        List<Long> questionIds = paper.getQuestions().stream()
                .map(Paper.PaperQuestion::getQuestionId)
                .toList();

        List<Question> questions = questionService.listByIds(questionIds);
        return questions.stream()
                .map(QuestionForExamResponse::from)
                .toList();
    }

    public Paper getExamPaper(Long examId, Long userId, String userRole) {
        Exam exam = checkReviewPermission(examId, userId, userRole);
        Paper paper = paperService.getById(exam.getPaperId());
        if (paper == null) {
            throw new BusinessException("试卷不存在");
        }
        return paper;
    }

    public List<Question> getReviewQuestions(Long examId, Long userId, String userRole) {
        Exam exam = checkReviewPermission(examId, userId, userRole);
        Paper paper = paperService.getById(exam.getPaperId());
        if (paper == null || paper.getQuestions() == null) {
            return List.of();
        }
        List<Long> questionIds = paper.getQuestions().stream()
                .map(Paper.PaperQuestion::getQuestionId)
                .toList();
        return questionService.listByIds(questionIds);
    }

    private Exam checkReviewPermission(Long examId, Long userId, String userRole) {
        Exam exam = getById(examId);
        if (exam == null) {
            throw new BusinessException("考试不存在");
        }
        applyCurrentStatus(exam);

        if (RoleEnum.ADMIN.getCode().equals(userRole)) {
            return exam;
        }

        if (RoleEnum.TEACHER.getCode().equals(userRole)) {
            if (!exam.getTeacherId().equals(userId)) {
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
                    || ExamSessionStatusEnum.SUBMITTED.getCode().equals(session.getStatus())
                    || ExamSessionStatusEnum.GRADED.getCode().equals(session.getStatus());
            if (!canView) {
                throw new BusinessException("考试尚未结束，暂时无法查看答案");
            }
            return exam;
        }

        throw new BusinessException("无权查看该考试");
    }

    public List<Exam> getMyExams(Long studentId) {
        List<Course> myCourses = courseService.getMyCourses(studentId);
        List<Long> courseIds = myCourses.stream()
                .map(Course::getId)
                .toList();
        if (courseIds.isEmpty()) {
            return List.of();
        }
        List<Exam> exams = lambdaQuery()
                .in(Exam::getCourseId, courseIds)
                .ne(Exam::getStatus, ExamStatusEnum.DRAFT.getCode())
                .list();
        applyCurrentStatuses(exams);

        List<Long> examIds = exams.stream().map(Exam::getId).toList();
        Map<Long, ExamSession> sessionMap = examSessionService.getByExamIdsAndStudentId(examIds, studentId);

        for (Exam exam : exams) {
            ExamSession session = sessionMap.get(exam.getId());
            if (session != null) {
                exam.setStudentExamStatus(session.getStatus());
            } else {
                exam.setStudentExamStatus(ExamSessionStatusEnum.NOT_STARTED.getCode());
            }
        }

        fillExamDisplayFields(exams);
        return exams;
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
                return existing;
            }
            throw new BusinessException("创建考试记录失败");
        }

        return session;
    }

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
            throw new BusinessException("当前考试状态不允许保存");
        }
        if (exam.getDuration() != null) {
            LocalDateTime durationDeadline = sessionDeadline(session, exam.getDuration());
            if (LocalDateTime.now().isAfter(durationDeadline)) {
                throw new BusinessException("考试已超时，无法继续保存");
            }
        }

        session.setAnswers(answers);
        try {
            examSessionService.updateById(session);
        } catch (OptimisticLockingFailureException e) {
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

        validateAnswers(answers);
        answers = normalizeSubmittedAnswers(answers);

        BigDecimal objectiveScore = BigDecimal.ZERO;
        boolean hasSubjective = false;
        Paper paper = paperService.getById(exam.getPaperId());
        Set<Long> paperQuestionIds = getPaperQuestionIdSet(paper);
        validateAnswerQuestionIds(answers, paperQuestionIds);

        List<Long> questionIds = answers.stream()
                .map(ExamSession.Answer::getQuestionId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, Question> questionMap = questionService.listByIds(questionIds).stream()
                .collect(Collectors.toMap(Question::getId, q -> q));

        if (paper != null && paper.getQuestions() != null) {
            for (ExamSession.Answer answer : answers) {
                Question question = questionMap.get(answer.getQuestionId());
                if (question != null) {
                    answer.setQuestionType(question.getType());

                    if (OBJECTIVE_TYPES.contains(question.getType())) {
                        boolean isCorrect = checkAnswer(question, answer.getAnswer());
                        answer.setIsCorrect(isCorrect);
                        answer.setGradingStatus(GradingStatusEnum.GRADED.getCode());

                        if (isCorrect) {
                            BigDecimal score = getQuestionScore(paper, answer.getQuestionId());
                            answer.setScore(score);
                            objectiveScore = objectiveScore.add(score);
                        } else {
                            answer.setScore(BigDecimal.ZERO);
                        }
                    } else if (SUBJECTIVE_TYPES.contains(question.getType())) {
                        hasSubjective = true;
                        answer.setIsCorrect(null);
                        answer.setScore(null);
                        answer.setGradingStatus(GradingStatusEnum.PENDING.getCode());
                    }
                }
            }
        }

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

    private boolean checkAnswer(Question question, String answer) {
        if (question.getCorrectAnswer() == null || answer == null) {
            return false;
        }

        if (QuestionTypeEnum.MULTIPLE_CHOICE.getCode().equals(question.getType())) {
            try {
                List<String> studentAnswers = JSONUtil.toList(answer, String.class);
                List<String> correctAnswers = JSONUtil.toList(question.getCorrectAnswer().toString(), String.class);

                studentAnswers.sort(String::compareTo);
                correctAnswers.sort(String::compareTo);

                return studentAnswers.equals(correctAnswers);
            } catch (Exception e) {
                return question.getCorrectAnswer().toString().equalsIgnoreCase(answer.trim());
            }
        }

        if (QuestionTypeEnum.TRUE_FALSE.getCode().equals(question.getType())) {
            String normalizedStudent = normalizeTrueFalseAnswer(answer);
            String normalizedCorrect = normalizeTrueFalseAnswer(question.getCorrectAnswer().toString());
            if (normalizedStudent != null && normalizedCorrect != null) {
                return normalizedStudent.equals(normalizedCorrect);
            }
        }

        if (QuestionTypeEnum.FILL_BLANK.getCode().equals(question.getType())) {
            return isFillBlankAnswerCorrect(answer, question.getCorrectAnswer());
        }

        return question.getCorrectAnswer().toString().trim().equalsIgnoreCase(answer.trim());
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
        } catch (Exception ignored) {
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

    private BigDecimal getQuestionScore(Paper paper, Long questionId) {
        if (paper.getQuestions() == null) {
            return BigDecimal.ZERO;
        }
        return paper.getQuestions().stream()
                .filter(q -> q.getQuestionId().equals(questionId))
                .findFirst()
                .map(Paper.PaperQuestion::getScore)
                .orElse(BigDecimal.ZERO);
    }

    private String getQuestionType(Paper paper, Long questionId) {
        if (paper == null || questionId == null) {
            return null;
        }
        Question question = questionService.getById(questionId);
        return question == null ? null : question.getType();
    }

    @Transactional
    public void publishExam(Long examId) {
        Exam exam = getById(examId);
        if (exam == null) {
            throw new BusinessException("考试不存在");
        }

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

        exam.setStatus(ExamStatusEnum.PUBLISHED.getCode());
        updateById(exam);
    }

    @Transactional
    public void cancelExam(Long examId) {
        Exam exam = getById(examId);
        if (exam == null) {
            throw new BusinessException("考试不存在");
        }

        boolean cancellable = ExamStatusEnum.DRAFT.getCode().equals(exam.getStatus())
                || ExamStatusEnum.PUBLISHED.getCode().equals(exam.getStatus());
        if (!cancellable) {
            throw new BusinessException("当前考试状态不允许取消");
        }

        exam.setStatus(ExamStatusEnum.CANCELLED.getCode());
        updateById(exam);
    }

    public void checkOwnership(Long examId, Long userId, String userRole) {
        if (RoleEnum.ADMIN.getCode().equals(userRole)) {
            return;
        }

        Exam exam = getById(examId);
        if (exam == null) {
            throw new BusinessException("考试不存在");
        }
        if (!exam.getTeacherId().equals(userId)) {
            throw new BusinessException("无权操作该考试");
        }
    }

    public void checkCanDelete(Long examId) {
        List<ExamSession> sessions = examSessionService.getByExamId(examId);
        if (!sessions.isEmpty()) {
            throw new BusinessException("已有 " + sessions.size() + " 名学生参加该考试，无法删除");
        }
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
        if (!RoleEnum.ADMIN.getCode().equals(operatorRole) && !exam.getTeacherId().equals(operatorId)) {
            throw new BusinessException("无权评分该考试");
        }

        if (!ExamSessionStatusEnum.SUBMITTED.getCode().equals(session.getStatus())) {
            throw new BusinessException("考试尚未提交");
        }
        if (!GradingStatusEnum.PENDING.getCode().equals(session.getGradingStatus())) {
            throw new BusinessException("该考试无需评分");
        }

        Paper paper = paperService.getById(exam.getPaperId());

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
                String questionType = getQuestionType(paper, grade.getQuestionId());
                if (questionType == null) {
                    throw new BusinessException("题目 " + grade.getQuestionId() + " 类型信息获取失败");
                }
                answer.setQuestionType(questionType);
                answer.setAnswer(null);
                answers.add(answer);
            }

            if (answer.getQuestionType() == null || !SUBJECTIVE_TYPES.contains(answer.getQuestionType())) {
                throw new BusinessException("题目 " + grade.getQuestionId() + " 不是主观题，不能手动评分");
            }

            BigDecimal maxScore = getQuestionScore(paper, grade.getQuestionId());
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
        if (exam == null) {
            throw new BusinessException("考试不存在");
        }

        List<ExamSession> sessions = examSessionService.getByExamId(examId);
        if (sessions == null || sessions.isEmpty()) {
            return 0;
        }

        Paper paper = paperService.getById(exam.getPaperId());
        if (paper == null) {
            throw new BusinessException("试卷不存在");
        }

        List<Long> questionIds = sessions.stream()
                .filter(s -> s.getAnswers() != null && !s.getAnswers().isEmpty())
                .flatMap(s -> s.getAnswers().stream())
                .map(ExamSession.Answer::getQuestionId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, Question> questionMap = questionIds.isEmpty()
                ? Map.of()
                : questionService.listByIds(questionIds).stream()
                .collect(Collectors.toMap(Question::getId, q -> q));

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
                Question question = questionMap.get(answer.getQuestionId());
                if (question == null) {
                    continue;
                }

                answer.setQuestionType(question.getType());
                if (OBJECTIVE_TYPES.contains(question.getType())) {
                    boolean isCorrect = checkAnswer(question, answer.getAnswer());
                    answer.setIsCorrect(isCorrect);
                    answer.setGradingStatus(GradingStatusEnum.GRADED.getCode());
                    BigDecimal score = isCorrect ? getQuestionScore(paper, answer.getQuestionId()) : BigDecimal.ZERO;
                    answer.setScore(score);
                    objectiveScore = objectiveScore.add(score);
                } else if (SUBJECTIVE_TYPES.contains(question.getType())) {
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

        boolean canViewAsTeacher = RoleEnum.ADMIN.getCode().equals(userRole) || exam.getTeacherId().equals(userId);
        if (!session.getStudentId().equals(userId) && !canViewAsTeacher) {
            throw new BusinessException("无权查看该考试结果");
        }

        Paper paper = paperService.getById(exam.getPaperId());

        BigDecimal objectiveScore = BigDecimal.ZERO;
        BigDecimal subjectiveScore = BigDecimal.ZERO;

        List<ExamResultResponse.AnswerDetail> answerDetails = new ArrayList<>();

        List<ExamSession.Answer> sessionAnswers = session.getAnswers();
        if (sessionAnswers == null || sessionAnswers.isEmpty()) {
            throw new BusinessException("暂无答题记录");
        }
        List<Long> questionIds = sessionAnswers.stream()
                .map(ExamSession.Answer::getQuestionId)
                .collect(Collectors.toList());
        Map<Long, Question> questionMap = questionService.listByIds(questionIds).stream()
                .collect(Collectors.toMap(Question::getId, q -> q));

        for (ExamSession.Answer answer : sessionAnswers) {
            Question question = questionMap.get(answer.getQuestionId());
            BigDecimal maxScore = getQuestionScore(paper, answer.getQuestionId());

            if (OBJECTIVE_TYPES.contains(answer.getQuestionType())) {
                objectiveScore = objectiveScore.add(answer.getScore() != null ? answer.getScore() : BigDecimal.ZERO);
            } else if (SUBJECTIVE_TYPES.contains(answer.getQuestionType())) {
                subjectiveScore = subjectiveScore.add(answer.getScore() != null ? answer.getScore() : BigDecimal.ZERO);
            }

            ExamResultResponse.AnswerDetail detail = new ExamResultResponse.AnswerDetail();
            detail.setQuestionId(answer.getQuestionId());
            detail.setQuestionContent(question != null ? question.getContent() : "");
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
        result.setGradingStatus(session.getGradingStatus());
        result.setAnswers(answerDetails);

        return result;
    }

    public PageResult<Exam> page(PageRequest pageRequest, Long courseId, Long teacherId, String status,
                                  Long currentUserId, String currentUserRole) {
        boolean isAdmin = RoleEnum.ADMIN.getCode().equals(currentUserRole);
        if (!isAdmin && teacherId != null && !teacherId.equals(currentUserId)) {
            return PageResult.empty(pageRequest.getCurrent(), pageRequest.getSize());
        }
        LambdaQueryWrapper<Exam> wrapper = new LambdaQueryWrapper<>();
        if (teacherId != null) {
            wrapper.eq(Exam::getTeacherId, teacherId);
        } else if (!isAdmin) {
            wrapper.eq(Exam::getTeacherId, currentUserId);
        }
        if (courseId != null) {
            wrapper.eq(Exam::getCourseId, courseId);
        }
        if (StringUtils.isNotBlank(status)
                && (ExamStatusEnum.DRAFT.getCode().equals(status) || ExamStatusEnum.CANCELLED.getCode().equals(status))) {
            wrapper.eq(Exam::getStatus, status);
        }

        List<Exam> exams = list(wrapper);
        applyCurrentStatuses(exams);

        if (StringUtils.isNotBlank(status)) {
            exams = exams.stream()
                    .filter(exam -> status.equals(exam.getStatus()))
                    .collect(Collectors.toList());
        }

        sortExams(exams, pageRequest);

        int current = pageRequest.getCurrent();
        int size = pageRequest.getSize();
        int fromIndex = Math.max((current - 1) * size, 0);
        if (fromIndex >= exams.size()) {
            return PageResult.empty(current, size);
        }

        int toIndex = Math.min(fromIndex + size, exams.size());
        List<Exam> pageRecords = new ArrayList<>(exams.subList(fromIndex, toIndex));
        fillExamDisplayFields(pageRecords);

        PageResult<Exam> result = new PageResult<>();
        result.setCurrent(current);
        result.setSize(size);
        result.setTotal((long) exams.size());
        result.setPages((long) Math.ceil((double) exams.size() / size));
        result.setRecords(pageRecords);
        result.setHasNext(toIndex < exams.size());
        result.setHasPrevious(current > 1);
        return result;
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

    private void sortExams(List<Exam> exams, PageRequest pageRequest) {
        String orderBy = StringUtils.isBlank(pageRequest.getOrderBy()) ? "id" : pageRequest.getOrderBy().toLowerCase();
        Comparator<Exam> comparator = switch (orderBy) {
            case "createtime", "created_at" ->
                    Comparator.comparing(Exam::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo));
            case "startedat", "started_at", "starttime" ->
                    Comparator.comparing(Exam::getStartedAt, Comparator.nullsLast(LocalDateTime::compareTo));
            case "endedat", "ended_at", "endtime" ->
                    Comparator.comparing(Exam::getEndedAt, Comparator.nullsLast(LocalDateTime::compareTo));
            case "totalscore" ->
                    Comparator.comparing(Exam::getTotalScore, Comparator.nullsLast(BigDecimal::compareTo));
            case "passscore" ->
                    Comparator.comparing(Exam::getPassScore, Comparator.nullsLast(BigDecimal::compareTo));
            case "duration" ->
                    Comparator.comparing(Exam::getDuration, Comparator.nullsLast(Integer::compareTo));
            case "status" ->
                    Comparator.comparing(Exam::getStatus, Comparator.nullsLast(String::compareTo));
            default ->
                    Comparator.comparing(Exam::getId, Comparator.nullsLast(Long::compareTo));
        };

        if (!Boolean.TRUE.equals(pageRequest.getAsc())) {
            comparator = comparator.reversed();
        }
        exams.sort(comparator);
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
            } catch (Exception ignored) {
                return false;
            }
        }

        return true;
    }

    private LocalDateTime sessionDeadline(ExamSession session, Integer durationMinutes) {
        return session.getStartedAt().plusMinutes(durationMinutes).plusSeconds(30);
    }

    private Set<Long> getPaperQuestionIdSet(Paper paper) {
        if (paper == null || paper.getQuestions() == null) {
            return Set.of();
        }
        return paper.getQuestions().stream()
                .map(Paper.PaperQuestion::getQuestionId)
                .collect(Collectors.toSet());
    }

    private void validateAnswerQuestionIds(List<ExamSession.Answer> answers, Set<Long> paperQuestionIds) {
        if (answers == null || answers.isEmpty()) {
            return;
        }
        if (paperQuestionIds.isEmpty()) {
            throw new BusinessException("考试试卷题目不存在，无法提交");
        }

        Set<Long> submittedQuestionIds = new HashSet<>();
        for (ExamSession.Answer answer : answers) {
            Long questionId = answer.getQuestionId();
            if (!submittedQuestionIds.add(questionId)) {
                throw new BusinessException("题目 " + questionId + " 重复提交");
            }
            if (!paperQuestionIds.contains(questionId)) {
                throw new BusinessException("题目 " + questionId + " 不属于当前考试");
            }
        }
    }

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
                || ExamStatusEnum.CANCELLED.getCode().equals(exam.getStatus())) {
            return;
        }
        if (exam.getStartedAt() == null || exam.getEndedAt() == null) {
            return;
        }

        if (now.isAfter(exam.getEndedAt())) {
            exam.setStatus(ExamStatusEnum.ENDED.getCode());
        } else if (!now.isBefore(exam.getStartedAt())) {
            exam.setStatus(ExamStatusEnum.STARTED.getCode());
        } else {
            exam.setStatus(ExamStatusEnum.PUBLISHED.getCode());
        }
    }
}