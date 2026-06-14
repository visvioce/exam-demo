package com.southcollege.exam.service;

import cn.hutool.http.HtmlUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.southcollege.exam.dto.request.PageRequest;
import com.southcollege.exam.dto.response.PageResult;
import com.southcollege.exam.dto.response.QuestionResponse;
import com.southcollege.exam.entity.Question;
import com.southcollege.exam.enums.RoleEnum;
import com.southcollege.exam.exception.BusinessException;
import com.southcollege.exam.mapper.QuestionMapper;
import com.southcollege.exam.mapstruct.QuestionDtoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class QuestionService extends ServiceImpl<QuestionMapper, Question> {

    private final QuestionDtoMapper questionDtoMapper;

    @Transactional
    @Override
    public boolean save(Question entity) {
        sanitizeHtmlFields(entity);
        return super.save(entity);
    }

    @Transactional
    @Override
    public boolean updateById(Question entity) {
        sanitizeHtmlFields(entity);
        return super.updateById(entity);
    }

    /**
     * 清理题目 HTML 内容
     */
    private void sanitizeHtmlFields(Question question) {
        if (question.getContent() != null) {
            question.setContent(HtmlUtil.filter(question.getContent()));
        }
        if (question.getExplanation() != null) {
            question.setExplanation(HtmlUtil.filter(question.getExplanation()));
        }
    }

    public long countQuestions(String type, String subject, String difficulty, Long teacherId) {
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Question::getType, type);
        wrapper.eq(Question::getTeacherId, teacherId);
        if (subject != null && !subject.isEmpty()) {
            wrapper.eq(Question::getSubject, subject);
        }
        if (difficulty != null && !difficulty.isEmpty()) {
            wrapper.eq(Question::getDifficulty, difficulty);
        }
        return this.count(wrapper);
    }

    @Transactional
    public void deleteQuestion(Long questionId) {
        this.removeById(questionId);
    }

    public List<String> getDistinctSubjects() {
        return baseMapper.selectAllSubjects();
    }

    /**
     * 查询某教师创建的所有题目
     */
    public List<Question> getByTeacherId(Long teacherId) {
        return lambdaQuery().eq(Question::getTeacherId, teacherId).list();
    }

    /**
     * 统计指定教师创建的题目数量
     * @param teacherId 教师ID
     * @return 题目数量
     */
    public long countByTeacherId(Long teacherId) {
        return lambdaQuery().eq(Question::getTeacherId, teacherId).count();
    }

    /**
     * 查询指定类型的所有题目
     */
    public List<Question> getByType(String type) {

        return lambdaQuery().eq(Question::getType, type).list();
    }

    /**
     * 查询指定教师创建的指定类型题目
     */
    public List<Question> getByType(String type, Long teacherId) {
        return lambdaQuery().eq(Question::getType, type)
                .eq(Question::getTeacherId, teacherId)
                .list();
    }

    /**
     * 校验题目操作权限：管理员和教师权限相同，只能操作自己的题目
     */
    public void checkOwnership(Long questionId, Long userId) {
        Question question = getById(questionId);
        if (question == null) {
            throw new BusinessException("题目不存在");
        }
        if (question.getTeacherId() == null || !question.getTeacherId().equals(userId)) {
            throw new BusinessException("无权操作该题目");
        }
    }

    /**
     * 分页查询题目：管理员和教师权限相同，只能查看自己的题目
     */
    public PageResult<Question> page(PageRequest pageRequest, Long teacherId, String type, String keyword,
                                      String difficulty, Long currentUserId) {
        Page<Question> page = new Page<>(pageRequest.getCurrent(), pageRequest.getSize());
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<>();

        if (teacherId != null && !teacherId.equals(currentUserId)) {
            return PageResult.empty(pageRequest.getCurrent(), pageRequest.getSize());
        }
        wrapper.eq(Question::getTeacherId, teacherId != null ? teacherId : currentUserId);

        if (StringUtils.isNotBlank(type)) {
            wrapper.eq(Question::getType, type);
        }

        if (StringUtils.isNotBlank(difficulty)) {
            wrapper.eq(Question::getDifficulty, difficulty);
        }

        if (StringUtils.isNotBlank(keyword)) {
            wrapper.and(w -> w.like(Question::getContent, keyword)
                    .or()
                    .like(Question::getSubject, keyword));
        }

        wrapper.orderByDesc(Question::getId);
        Page<Question> result = page(page, wrapper);
        return PageResult.from(result);
    }

    public QuestionResponse convertToResponse(Question question) {
        return convertToResponse(question, null);
    }

    public QuestionResponse convertToResponse(Question question, String userRole) {
        if (question == null) return null;
        QuestionResponse response = questionDtoMapper.toResponse(question);
        if (RoleEnum.STUDENT.getCode().equals(userRole)) {
            response.setCorrectAnswer(null);
            response.setExplanation(null);
        }
        return response;
    }

    public List<QuestionResponse> convertToResponses(List<Question> questions) {
        return convertToResponses(questions, null);
    }

    public List<QuestionResponse> convertToResponses(List<Question> questions, String userRole) {
        if (questions == null || questions.isEmpty()) {
            return List.of();
        }
        return questions.stream()
                .map(q -> convertToResponse(q, userRole))
                .toList();
    }

    public PageResult<QuestionResponse> convertToPageResult(PageResult<Question> pageResult) {
        return PageResult.map(pageResult, this::convertToResponses);
    }
}