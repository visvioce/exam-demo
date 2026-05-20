package com.southcollege.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.southcollege.exam.entity.CourseMember;
import com.southcollege.exam.mapper.CourseMemberMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CourseMemberService extends ServiceImpl<CourseMemberMapper, CourseMember> {

    public List<CourseMember> getByCourseId(Long courseId) {
        return baseMapper.selectByCourseId(courseId);
    }

    public List<CourseMember> getByStudentId(Long studentId) {
        return baseMapper.selectByStudentId(studentId);
    }

    public void physicalDeleteByCourseId(Long courseId) {
        LambdaQueryWrapper<CourseMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CourseMember::getCourseId, courseId);
        getBaseMapper().delete(wrapper);
    }

    public void physicalDeleteByStudentId(Long studentId) {
        LambdaQueryWrapper<CourseMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CourseMember::getStudentId, studentId);
        getBaseMapper().delete(wrapper);
    }
}
