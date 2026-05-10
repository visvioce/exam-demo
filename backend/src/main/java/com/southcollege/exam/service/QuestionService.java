package com.southcollege.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.southcollege.exam.dto.request.PageRequest;
import com.southcollege.exam.dto.response.PageResult;
import com.southcollege.exam.entity.Question;
import com.southcollege.exam.enums.RoleEnum;
import com.southcollege.exam.exception.BusinessException;
import com.southcollege.exam.mapper.QuestionMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 题库服务
 * 管理题目的增删改查、权限控制和分页查询
 */
@Service
public class QuestionService extends ServiceImpl<QuestionMapper, Question> {

    /**
     * 查询某教师创建的所有题目
     */
    public List<Question> getByTeacherId(Long teacherId) {
        return lambdaQuery().eq(Question::getTeacherId, teacherId).list();
    }

    /**
     * 查询指定类型的所有题目
     */
    public List<Question> getByType(String type) {
        return lambdaQuery().eq(Question::getType, type).list();
    }

    /**
     * 查询指定教师创建的指定类型题目
     *
     * @param type      题目类型
     * @param teacherId 教师ID
     */
    public List<Question> getByType(String type, Long teacherId) {
        return lambdaQuery().eq(Question::getType, type)
                .eq(Question::getTeacherId, teacherId)
                .list();
    }

    /**
     * 校验题目操作权限：管理员可操作所有，教师只能操作自己的题目
     */
    public void checkOwnership(Long questionId, Long userId, String userRole) {
        Question question = getById(questionId);
        if (question == null) {
            throw new BusinessException("题目不存在");
        }
        if (RoleEnum.ADMIN.getCode().equals(userRole)) {
            return;
        }
        if (question.getTeacherId() == null || !question.getTeacherId().equals(userId)) {
            throw new BusinessException("无权操作该题目");
        }
    }

    /**
     * 分页查询题目，支持题型、关键字筛选和角色数据隔离
     * <p>
     * 非管理员自动限制只能查看自己创建的题目
     *
     * @param pageRequest     分页参数
     * @param teacherId       教师ID筛选
     * @param type            题目类型筛选
     * @param keyword         搜索关键词（匹配题目内容和科目）
     * @param currentUserId   当前用户ID
     * @param currentUserRole 当前用户角色
     * @return 分页结果
     */
    public PageResult<Question> page(PageRequest pageRequest, Long teacherId, String type, String keyword,
                                      Long currentUserId, String currentUserRole) {
        Page<Question> page = new Page<>(pageRequest.getCurrent(), pageRequest.getSize());
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<>();

        boolean isAdmin = RoleEnum.ADMIN.getCode().equals(currentUserRole);
        if (!isAdmin && teacherId != null && !teacherId.equals(currentUserId)) {
            return PageResult.empty(pageRequest.getCurrent(), pageRequest.getSize());
        }
        if (teacherId != null) {
            wrapper.eq(Question::getTeacherId, teacherId);
        } else if (!isAdmin) {
            wrapper.eq(Question::getTeacherId, currentUserId);
        }

        if (StringUtils.isNotBlank(type)) {
            wrapper.eq(Question::getType, type);
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
}