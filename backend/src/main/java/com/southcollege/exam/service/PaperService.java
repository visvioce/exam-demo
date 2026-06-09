package com.southcollege.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.southcollege.exam.dto.request.AutoGeneratePaperRequest;
import com.southcollege.exam.dto.request.PageRequest;
import com.southcollege.exam.dto.request.TypeConfig;
import com.southcollege.exam.dto.response.PageResult;
import com.southcollege.exam.dto.response.PaperResponse;
import com.southcollege.exam.entity.Paper;
import com.southcollege.exam.entity.Question;
import com.southcollege.exam.enums.RoleEnum;
import com.southcollege.exam.exception.BusinessException;
import com.southcollege.exam.mapper.PaperMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 试卷服务
 * 管理试卷的增删改查、自动组卷和权限控制。
 * <p>
 * 懒清理策略：题目被物理删除后，试卷的 questionIds 中可能残留失效 ID。
 * 在加载试卷时（getById / 分页查询）自动检查并清理，避免删题时全表扫描。
 * 考试创建时也会做同样检查。
 * </p>
 */
@Service
public class PaperService extends ServiceImpl<PaperMapper, Paper> {

    private static final Logger log = LoggerFactory.getLogger(PaperService.class);

    private final QuestionService questionService;

    public PaperService(QuestionService questionService) {
        this.questionService = questionService;
    }

    /**
     * 查询某教师创建的所有试卷（已清理失效题目ID）
     */
    public List<Paper> getByTeacherId(Long teacherId) {
        LambdaQueryWrapper<Paper> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Paper::getTeacherId, teacherId)
               .orderByDesc(Paper::getCreatedAt);
        List<Paper> papers = list(wrapper);
        for (Paper paper : papers) {
            cleanupStaleQuestionIds(paper);
        }
        return papers;
    }

    /**
     * 获取试卷并清理失效题目ID
     */
    @Override
    public Paper getById(java.io.Serializable id) {
        Paper paper = super.getById(id);
        if (paper != null) {
            cleanupStaleQuestionIds(paper);
        }
        return paper;
    }

    /**
     * 分页查询试卷并清理失效题目ID
     */
    public PageResult<Paper> pageWithFilters(PageRequest pageRequest, String keyword, Long teacherId,
                                              Long currentUserId) {
        LambdaQueryWrapper<Paper> wrapper = new LambdaQueryWrapper<>();

        if (teacherId != null && !teacherId.equals(currentUserId)) {
            return PageResult.empty(pageRequest.getCurrent(), pageRequest.getSize());
        }
        if (teacherId != null) {
            wrapper.eq(Paper::getTeacherId, teacherId);
        } else {
            wrapper.eq(Paper::getTeacherId, currentUserId);
        }

        if (StringUtils.isNotBlank(keyword)) {
            wrapper.like(Paper::getName, keyword);
        }

        wrapper.orderByDesc(Paper::getCreatedAt);

        Page<Paper> page = new Page<>(pageRequest.getCurrent(), pageRequest.getSize());
        Page<Paper> result = page(page, wrapper);

        for (Paper paper : result.getRecords()) {
            cleanupStaleQuestionIds(paper);
        }

        return PageResult.from(result);
    }

    /**
     * 懒清理试卷中不存在的题目ID，并持久化到数据库
     * <p>
     * 在试卷加载时自动调用。如果发现失效题目ID，从 questionIds 中移除并更新数据库。
     * </p>
     */
    @Transactional
    public void cleanupStaleQuestionIds(Paper paper) {
        List<Long> questionIds = paper.getQuestionIds();
        if (questionIds == null || questionIds.isEmpty()) {
            return;
        }

        List<Long> nonNullIds = questionIds.stream()
                .filter(Objects::nonNull)
                .toList();

        if (nonNullIds.isEmpty()) {
            return;
        }

        List<Question> validQuestions = questionService.listByIds(nonNullIds);
        Set<Long> validIdSet = validQuestions.stream()
                .map(Question::getId)
                .collect(Collectors.toSet());

        List<Long> validIds = nonNullIds.stream()
                .filter(validIdSet::contains)
                .toList();

        int removedCount = nonNullIds.size() - validIds.size();
        if (removedCount > 0) {
            log.warn("试卷 {} (id={}) 存在 {} 个失效题目ID，已自动清理", 
                    paper.getName(), paper.getId(), removedCount);
            paper.setQuestionIds(validIds);
            baseMapper.updateById(paper);
        }
    }

    /**
     * 校验试卷操作权限：管理员和教师权限相同，只能操作自己的试卷
     */
    public void checkOwnership(Long paperId, Long userId, String userRole) {
        if (!RoleEnum.fromCode(userRole).hasPermission(RoleEnum.TEACHER)) {
            throw new BusinessException("无权操作该试卷");
        }
        Paper paper = getById(paperId);
        if (paper == null) {
            throw new BusinessException("试卷不存在");
        }
        if (paper.getTeacherId() == null || !paper.getTeacherId().equals(userId)) {
            throw new BusinessException("无权操作该试卷");
        }
    }

    /**
     * 自动组卷：根据配置从题库随机抽题生成试卷
     */
    @Transactional
    public Paper autoGenerate(AutoGeneratePaperRequest request, Long teacherId) {
        List<Long> questionIds = new ArrayList<>();

        questionIds = processTypeConfig(questionIds, request,
                "SINGLE_CHOICE", request.getSingleChoice(),
                request.getSingleChoiceCount(), teacherId);
        questionIds = processTypeConfig(questionIds, request,
                "MULTIPLE_CHOICE", request.getMultipleChoice(),
                request.getMultipleChoiceCount(), teacherId);
        questionIds = processTypeConfig(questionIds, request,
                "TRUE_FALSE", request.getTrueFalse(),
                request.getTrueFalseCount(), teacherId);
        questionIds = processTypeConfig(questionIds, request,
                "FILL_BLANK", request.getFillBlank(),
                request.getFillBlankCount(), teacherId);
        questionIds = processTypeConfig(questionIds, request,
                "ESSAY", request.getEssay(),
                request.getEssayCount(), teacherId);

        if (questionIds.isEmpty()) {
            throw new BusinessException("请至少选择一种题型并设置数量");
        }

        Paper paper = new Paper();
        paper.setName(request.getName());
        paper.setDescription(request.getDescription());
        paper.setTeacherId(teacherId);
        paper.setQuestionIds(questionIds);

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
            wrapper.eq(Question::getSubject, subject);
        }
        if (difficulty != null && !difficulty.isEmpty()) {
            wrapper.eq(Question::getDifficulty, difficulty);
        }

        List<Question> allQuestions = questionService.list(wrapper);

        if (allQuestions.size() < count) {
            throw new BusinessException("题库中" + type + "类型题目不足，当前只有" + allQuestions.size() + "题，需要" + count + "题");
        }

        Collections.shuffle(allQuestions, ThreadLocalRandom.current());
        return new ArrayList<>(allQuestions.subList(0, count));
    }

    /**
     * 将抽选的题目ID添加到列表
     */
    private void addQuestionsToList(List<Long> questionIds, List<Question> selectedQuestions) {
        for (Question q : selectedQuestions) {
            questionIds.add(q.getId());
        }
    }

    /**
     * 处理单个题型的抽题配置
     */
    private List<Long> processTypeConfig(List<Long> questionIds,
                                         AutoGeneratePaperRequest request,
                                         String type, TypeConfig config,
                                         Integer simpleCount,
                                         Long teacherId) {
        if (config != null && config.getCount() != null && config.getCount() > 0) {
            List<Question> selected = selectRandomQuestions(
                    type,
                    config.getSubject() != null ? config.getSubject() : request.getSubject(),
                    config.getDifficulty() != null ? config.getDifficulty() : request.getDifficulty(),
                    config.getCount(),
                    teacherId
            );
            addQuestionsToList(questionIds, selected);
        } else if (simpleCount != null && simpleCount > 0) {
            List<Question> selected = selectRandomQuestions(
                    type,
                    request.getSubject(),
                    request.getDifficulty(),
                    simpleCount,
                    teacherId
            );
            addQuestionsToList(questionIds, selected);
        }
        return questionIds;
    }

    public PaperResponse convertToResponse(Paper entity) {
        if (entity == null) return null;
        PaperResponse response = new PaperResponse();
        BeanUtils.copyProperties(entity, response);
        return response;
    }

    public List<PaperResponse> convertToResponses(List<Paper> entities) {
        if (entities == null || entities.isEmpty()) return List.of();
        return entities.stream().map(this::convertToResponse).toList();
    }

    public PageResult<PaperResponse> convertToPageResult(PageResult<Paper> pageResult) {
        if (pageResult == null) return PageResult.empty(1, 10);
        PageResult<PaperResponse> response = new PageResult<>();
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