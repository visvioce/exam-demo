package com.southcollege.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.southcollege.exam.dto.request.AutoGeneratePaperRequest;
import com.southcollege.exam.dto.request.PageRequest;
import com.southcollege.exam.dto.request.TypeConfig;
import com.southcollege.exam.dto.response.PageResult;
import com.southcollege.exam.entity.Course;
import com.southcollege.exam.entity.Exam;
import com.southcollege.exam.entity.Paper;
import com.southcollege.exam.entity.Question;
import com.southcollege.exam.enums.RoleEnum;
import com.southcollege.exam.exception.BusinessException;
import com.southcollege.exam.mapper.PaperMapper;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 试卷服务
 * 管理试卷的增删改查、自动组卷和权限控制
 */
@Service
public class PaperService extends ServiceImpl<PaperMapper, Paper> {

    private final ExamService examService;
    private final CourseService courseService;
    private final QuestionService questionService;

    public PaperService(@Lazy ExamService examService, CourseService courseService, QuestionService questionService) {
        this.examService = examService;
        this.courseService = courseService;
        this.questionService = questionService;
    }

    /**
     * 查询某课程下的所有试卷，并填充课程名称
     */
    public List<Paper> getByCourseId(Long courseId) {
        List<Paper> papers = baseMapper.selectByCourseId(courseId);
        fillCourseNames(papers);
        return papers;
    }

    /**
     * 查询某教师创建的所有试卷，并填充课程名称
     */
    public List<Paper> getByTeacherId(Long teacherId) {
        List<Paper> papers = baseMapper.selectByTeacherId(teacherId);
        fillCourseNames(papers);
        return papers;
    }

    /**
     * 查询全部试卷并填充课程名称
     */
    public List<Paper> listWithCourseNames() {
        List<Paper> papers = list();
        fillCourseNames(papers);
        return papers;
    }

    /**
     * 根据ID查询试卷详情，并填充课程名称
     */
    public Paper getByIdWithCourseName(Long id) {
        Paper paper = getById(id);
        if (paper == null) {
            return null;
        }
        fillCourseNames(List.of(paper));
        return paper;
    }

    /**
     * 分页查询试卷，支持关键词搜索和多维度筛选
     * <p>
     * 非管理员自动限制只能查看自己创建的试卷
     *
     * @param pageRequest     分页参数
     * @param keyword         搜索关键词（匹配试卷名）
     * @param teacherId       教师ID筛选
     * @param courseId        课程ID筛选
     * @param type            组卷方式筛选
     * @param status          试卷状态筛选
     * @param currentUserId   当前用户ID
     * @param currentUserRole 当前用户角色
     * @return 分页结果，包含课程名称
     */
    public PageResult<Paper> pageWithFilters(PageRequest pageRequest, String keyword, Long teacherId,
                                              Long courseId, String type, String status,
                                              Long currentUserId, String currentUserRole) {
        LambdaQueryWrapper<Paper> wrapper = new LambdaQueryWrapper<>();

        boolean isAdmin = RoleEnum.ADMIN.getCode().equals(currentUserRole);
        if (!isAdmin && teacherId != null && !teacherId.equals(currentUserId)) {
            return PageResult.empty(pageRequest.getCurrent(), pageRequest.getSize());
        }
        if (teacherId != null) {
            wrapper.eq(Paper::getTeacherId, teacherId);
        } else if (!isAdmin) {
            wrapper.eq(Paper::getTeacherId, currentUserId);
        }

        if (StringUtils.isNotBlank(keyword)) {
            wrapper.like(Paper::getName, keyword);
        }

        if (courseId != null) {
            wrapper.eq(Paper::getCourseId, courseId);
        }

        if (StringUtils.isNotBlank(type)) {
            wrapper.eq(Paper::getType, type);
        }

        if (StringUtils.isNotBlank(status)) {
            wrapper.eq(Paper::getStatus, status);
        }

        wrapper.orderByDesc(Paper::getCreatedAt);

        Page<Paper> page = new Page<>(pageRequest.getCurrent(), pageRequest.getSize());
        Page<Paper> result = page(page, wrapper);
        fillCourseNames(result.getRecords());
        return PageResult.from(result);
    }

    /**
     * 校验试卷操作权限，管理员可操作所有试卷
     */
    public void checkOwnership(Long paperId, Long userId, String userRole) {
        Paper paper = getById(paperId);
        if (paper == null) {
            throw new BusinessException("试卷不存在");
        }
        if (RoleEnum.ADMIN.getCode().equals(userRole)) {
            return;
        }
        if (!paper.getTeacherId().equals(userId)) {
            throw new BusinessException("无权操作该试卷");
        }
    }

    /**
     * 检查试卷是否可删除：被考试引用时不允许删除
     */
    public void checkCanDelete(Long paperId) {
        List<Exam> exams = examService.getByPaperId(paperId);
        if (!exams.isEmpty()) {
            throw new BusinessException("该试卷已被 " + exams.size() + " 场考试引用，无法删除");
        }
    }

    /**
     * 批量填充试卷中的课程名称
     */
    private void fillCourseNames(List<Paper> papers) {
        if (papers == null || papers.isEmpty()) {
            return;
        }
        List<Long> courseIds = papers.stream()
                .map(Paper::getCourseId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        Map<Long, String> courseNameMap = courseService.listByIds(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, Course::getName, (a, _b) -> a));
        for (Paper paper : papers) {
            if (paper.getCourseId() != null) {
                paper.setCourseName(courseNameMap.get(paper.getCourseId()));
            }
        }
    }

    /**
     * 自动组卷：根据配置从题库随机抽题生成试卷
     *
     * @param request   组卷配置请求
     * @param teacherId 创建教师ID
     * @return 生成的试卷
     */
    public Paper autoGenerate(AutoGeneratePaperRequest request, Long teacherId) {
        List<Paper.PaperQuestion> questions = new ArrayList<>();
        BigDecimal totalScore = BigDecimal.ZERO;

        // 按题型配置或简单参数抽取题目
        totalScore = processTypeConfig(questions, totalScore, request,
                "SINGLE_CHOICE", request.getSingleChoice(),
                request.getSingleChoiceCount(), request.getSingleChoiceScore(), teacherId);
        totalScore = processTypeConfig(questions, totalScore, request,
                "MULTIPLE_CHOICE", request.getMultipleChoice(),
                request.getMultipleChoiceCount(), request.getMultipleChoiceScore(), teacherId);
        totalScore = processTypeConfig(questions, totalScore, request,
                "TRUE_FALSE", request.getTrueFalse(),
                request.getTrueFalseCount(), request.getTrueFalseScore(), teacherId);
        totalScore = processTypeConfig(questions, totalScore, request,
                "FILL_BLANK", request.getFillBlank(),
                request.getFillBlankCount(), request.getFillBlankScore(), teacherId);
        totalScore = processTypeConfig(questions, totalScore, request,
                "ESSAY", request.getEssay(),
                request.getEssayCount(), request.getEssayScore(), teacherId);

        if (questions.isEmpty()) {
            throw new BusinessException("请至少选择一种题型并设置数量");
        }

        Paper paper = new Paper();
        paper.setName(request.getName());
        paper.setDescription(request.getDescription());
        paper.setCourseId(request.getCourseId());
        paper.setTeacherId(teacherId);
        paper.setType("AUTO");
        paper.setStatus("DRAFT");
        paper.setQuestions(questions);
        paper.setTotalScore(totalScore);

        save(paper);
        return paper;
    }

    /**
     * 从题库中按条件随机抽取指定数量的题目
     */
    private List<Question> selectRandomQuestions(String type, String subject, String difficulty, int count, Long teacherId) {
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Question::getType, type);
        wrapper.eq(Question::getTeacherId, teacherId);

        if (subject != null && !subject.isEmpty()) {
            wrapper.like(Question::getSubject, subject);
        }
        if (difficulty != null && !difficulty.isEmpty()) {
            wrapper.eq(Question::getDifficulty, difficulty);
        }

        List<Question> allQuestions = questionService.list(wrapper);
        
        if (allQuestions.size() < count) {
            throw new BusinessException("题库中" + type + "类型题目不足，当前只有" + allQuestions.size() + "题，需要" + count + "题");
        }

        Collections.shuffle(allQuestions);
        return allQuestions.subList(0, count);
    }

    /**
     * 将抽选的题目添加到试卷题目列表
     */
    private void addQuestionsToList(List<Paper.PaperQuestion> questions, List<Question> selectedQuestions, BigDecimal score) {
        for (Question q : selectedQuestions) {
            Paper.PaperQuestion pq = new Paper.PaperQuestion();
            pq.setQuestionId(q.getId());
            pq.setScore(score);
            questions.add(pq);
        }
    }

    /**
     * 处理单个题型的抽题配置，优先使用详细配置，其次使用简单参数
     */
    private BigDecimal processTypeConfig(List<Paper.PaperQuestion> questions, BigDecimal totalScore,
                                         AutoGeneratePaperRequest request,
                                         String type, TypeConfig config,
                                         Integer simpleCount, BigDecimal simpleScore,
                                         Long teacherId) {
        if (config != null && config.getCount() != null && config.getCount() > 0) {
            // 使用详细题型配置
            List<Question> selected = selectRandomQuestions(
                    type,
                    config.getSubject() != null ? config.getSubject() : request.getSubject(),
                    config.getDifficulty() != null ? config.getDifficulty() : request.getDifficulty(),
                    config.getCount(),
                    teacherId
            );
            addQuestionsToList(questions, selected, config.getScore());
            return totalScore.add(config.getScore().multiply(BigDecimal.valueOf(config.getCount())));
        } else if (simpleCount != null && simpleCount > 0) {
            // 使用简单的数量和分值参数
            List<Question> selected = selectRandomQuestions(
                    type,
                    request.getSubject(),
                    request.getDifficulty(),
                    simpleCount,
                    teacherId
            );
            addQuestionsToList(questions, selected, simpleScore);
            return totalScore.add(simpleScore.multiply(BigDecimal.valueOf(simpleCount)));
        }
        return totalScore;
    }
}