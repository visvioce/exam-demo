package com.southcollege.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.southcollege.exam.dto.request.PageRequest;
import com.southcollege.exam.dto.response.PageResult;
import com.southcollege.exam.entity.Course;
import com.southcollege.exam.entity.CourseMember;
import com.southcollege.exam.entity.Exam;
import com.southcollege.exam.entity.User;
import com.southcollege.exam.enums.RoleEnum;
import com.southcollege.exam.exception.BusinessException;
import com.southcollege.exam.mapper.CourseMapper;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 课程服务
 * 管理课程的增删改查、选课退课、成员管理和权限控制
 */
@Service
public class CourseService extends ServiceImpl<CourseMapper, Course> {

    private final CourseMemberService courseMemberService;
    private final UserService userService;
    private final ExamService examService;

    public CourseService(CourseMemberService courseMemberService, UserService userService, @Lazy ExamService examService) {
        this.courseMemberService = courseMemberService;
        this.userService = userService;
        this.examService = examService;
    }

    /**
     * 查询某教师创建的所有课程，并填充教师姓名
     */
    public List<Course> getByTeacherId(Long teacherId) {
        List<Course> courses = lambdaQuery().eq(Course::getTeacherId, teacherId).list();
        fillTeacherNames(courses);
        return courses;
    }

    /**
     * 根据ID查询课程详情，并填充教师姓名
     */
    public Course getByIdWithTeacherName(Long id) {
        Course course = getById(id);
        if (course == null) {
            return null;
        }
        fillTeacherNames(List.of(course));
        return course;
    }

    /**
     * 根据ID和用户权限查询课程详情
     * <p>
     * 管理员：查看全部
     * 教师：只能查看自己创建的课程（其他教师课程无权限）
     * 学生：有课程访问权限即可
     */
    public Course getByIdWithPermission(Long id, Long userId, String userRole) {
        Course course = getByIdWithTeacherName(id);
        if (course == null) {
            throw new BusinessException("课程不存在");
        }

        if (RoleEnum.ADMIN.getCode().equals(userRole)) {
            return course;
        }

        if (RoleEnum.TEACHER.getCode().equals(userRole)) {
            if (!course.getTeacherId().equals(userId)) {
                throw new BusinessException("无权查看该课程");
            }
            return course;
        }

        if (RoleEnum.STUDENT.getCode().equals(userRole)) {
            return course;
        }

        throw new BusinessException("无权查看该课程");
    }

    /**
     * 查询全部课程并填充教师姓名
     */
    public List<Course> listWithTeacherNames() {
        List<Course> courses = list();
        fillTeacherNames(courses);
        return courses;
    }

    /**
     * 查询正在进行或将要开始的活跃课程
     */
    public List<Course> getActiveCourses() {
        List<Course> courses = lambdaQuery()
                .eq(Course::getStatus, "ACTIVE")
                .and(wrapper -> wrapper
                    .isNull(Course::getDeadline)
                    .or()
                    .gt(Course::getDeadline, LocalDateTime.now())
                )
                .list();
        fillTeacherNames(courses);
        return courses;
    }

    /**
     * 查询学生已加入的课程列表
     */
    public List<Course> getMyCourses(Long studentId) {
        List<CourseMember> members = courseMemberService.getByStudentId(studentId);
        List<Long> courseIds = members.stream()
                .map(CourseMember::getCourseId)
                .collect(Collectors.toList());
        if (courseIds.isEmpty()) {
            return List.of();
        }
        List<Course> courses = listByIds(courseIds);
        fillTeacherNames(courses);
        return courses;
    }

    /**
     * 学生加入课程：校验课程状态和截止时间限制
     *
     * @param courseId  课程ID
     * @param studentId 学生ID
     */
    @Transactional
    public void joinCourse(Long courseId, Long studentId) {
        Course course = getById(courseId);
        if (course == null) {
            throw new BusinessException("课程不存在");
        }
        if (!"ACTIVE".equals(course.getStatus())) {
            throw new BusinessException("课程未开放");
        }
        if (course.getDeadline() != null && course.getDeadline().isBefore(LocalDateTime.now())) {
            throw new BusinessException("选课已截止");
        }

        List<CourseMember> existing = courseMemberService.lambdaQuery()
                .eq(CourseMember::getCourseId, courseId)
                .eq(CourseMember::getStudentId, studentId)
                .list();
        if (!existing.isEmpty()) {
            throw new BusinessException("已加入该课程");
        }

        CourseMember member = new CourseMember();
        member.setCourseId(courseId);
        member.setStudentId(studentId);
        member.setJoinedAt(LocalDateTime.now());
        courseMemberService.save(member);
    }

    /**
     * 学生退出课程
     *
     * @param courseId  课程ID
     * @param studentId 学生ID
     */
    @Transactional
    public void leaveCourse(Long courseId, Long studentId) {
        List<CourseMember> members = courseMemberService.lambdaQuery()
                .eq(CourseMember::getCourseId, courseId)
                .eq(CourseMember::getStudentId, studentId)
                .list();
        if (members.isEmpty()) {
            throw new BusinessException("未加入该课程");
        }
        courseMemberService.removeById(members.get(0).getId());
    }

    /**
     * 查询课程全部成员
     */
    public List<User> getCourseMembers(Long courseId) {
        List<CourseMember> members = courseMemberService.getByCourseId(courseId);
        List<Long> studentIds = members.stream()
                .map(CourseMember::getStudentId)
                .collect(Collectors.toList());
        if (studentIds.isEmpty()) {
            return List.of();
        }
        return userService.listByIds(studentIds);
    }

    /**
     * 根据权限查询课程成员
     * <p>
     * 管理员：查看全部成员
     * 课程教师：查看本课程全体成员
     * 课程成员学生：查看本课程全体成员
     * 非成员：无权查看
     */
    public List<User> getCourseMembersWithPermission(Long courseId, Long userId, String userRole) {
        Course course = getById(courseId);
        if (course == null) {
            throw new BusinessException("课程不存在");
        }
        if (RoleEnum.ADMIN.getCode().equals(userRole)) {
            return getCourseMembers(courseId);
        }
        if (course.getTeacherId().equals(userId)) {
            return getCourseMembers(courseId);
        }
        if (!isCourseMember(courseId, userId)) {
            throw new BusinessException("无权查看此课程成员");
        }
        return getCourseMembers(courseId);
    }

    /**
     * 判断学生是否为课程成员
     */
    public boolean isCourseMember(Long courseId, Long studentId) {
        return courseMemberService.lambdaQuery()
                .eq(CourseMember::getCourseId, courseId)
                .eq(CourseMember::getStudentId, studentId)
                .count() > 0;
    }

    /**
     * 校验课程操作权限：管理员可操作所有，教师只能操作自己的课程
     */
    public void checkOwnership(Long courseId, Long userId, String userRole) {
        Course course = getById(courseId);
        if (course == null) {
            throw new BusinessException("课程不存在");
        }
        if (RoleEnum.ADMIN.getCode().equals(userRole)) {
            return;
        }
        if (!course.getTeacherId().equals(userId)) {
            throw new BusinessException("无权操作该课程");
        }
    }

    /**
     * 更新课程：权限校验 + 保留原创建教师不变
     *
     * @param id       课程ID
     * @param course   更新的课程信息
     * @param userId   操作者ID
     * @param userRole 操作者角色
     * @return 是否成功
     */
    @Transactional
    public boolean updateCourse(Long id, Course course, Long userId, String userRole) {
        checkOwnership(id, userId, userRole);

        Course originalCourse = getById(id);
        if (originalCourse == null) {
            throw new BusinessException("课程不存在");
        }

        course.setId(id);
        course.setTeacherId(originalCourse.getTeacherId());

        return updateById(course);
    }

    /**
     * 检查课程是否可删除：有成员或有考试时不允许删除
     */
    public void checkCanDelete(Long courseId) {
        List<CourseMember> members = courseMemberService.getByCourseId(courseId);
        if (!members.isEmpty()) {
            throw new BusinessException("该课程已有 " + members.size() + " 名学生加入，无法删除");
        }

        List<Exam> exams = examService.getByCourseId(courseId);
        if (!exams.isEmpty()) {
            throw new BusinessException("该课程已有 " + exams.size() + " 场考试，无法删除");
        }
    }

    /**
     * 分页查询课程，支持状态、关键词筛选和多字段排序
     * <p>
     * 非管理员自动限制只能查看自己创建的课程
     *
     * @param pageRequest     分页参数
     * @param teacherId       教师ID筛选
     * @param status          课程状态筛选
     * @param keyword         搜索关键词（匹配课程名称和编号）
     * @param currentUserId   当前用户ID
     * @param currentUserRole 当前用户角色
     * @return 分页结果，包含教师姓名
     */
    public PageResult<Course> page(PageRequest pageRequest, Long teacherId, String status, String keyword,
                                    Long currentUserId, String currentUserRole) {
        Page<Course> page = new Page<>(pageRequest.getCurrent(), pageRequest.getSize());
        LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<>();

        boolean isAdmin = RoleEnum.ADMIN.getCode().equals(currentUserRole);
        if (!isAdmin && teacherId != null && !teacherId.equals(currentUserId)) {
            return PageResult.empty(pageRequest.getCurrent(), pageRequest.getSize());
        }
        if (teacherId != null) {
            wrapper.eq(Course::getTeacherId, teacherId);
        } else if (!isAdmin) {
            wrapper.eq(Course::getTeacherId, currentUserId);
        }

        if (StringUtils.isNotBlank(status)) {
            wrapper.eq(Course::getStatus, status);
        }

        if (StringUtils.isNotBlank(keyword)) {
            wrapper.and(w -> w.like(Course::getName, keyword)
                    .or()
                    .like(Course::getCode, keyword));
        }

        applySorting(wrapper, pageRequest);

        Page<Course> result = page(page, wrapper);
        fillTeacherNames(result.getRecords());
        return PageResult.from(result);
    }

    /**
     * 批量填充课程中的教师显示名称
     */
    private void fillTeacherNames(List<Course> courses) {
        if (courses == null || courses.isEmpty()) {
            return;
        }
        List<Long> teacherIds = courses.stream()
                .map(Course::getTeacherId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        Map<Long, String> teacherNameMap = userService.getDisplayNameMap(teacherIds);
        for (Course course : courses) {
            if (course.getTeacherId() == null) {
                continue;
            }
            String displayName = teacherNameMap.get(course.getTeacherId());
            if (StringUtils.isNotBlank(displayName)) {
                course.setTeacherName(displayName);
            }
        }
    }

    /**
     * 根据分页请求中的排序参数应用排序条件
     */
    private void applySorting(LambdaQueryWrapper<Course> wrapper, PageRequest pageRequest) {
        if (StringUtils.isBlank(pageRequest.getOrderBy())) {
            wrapper.orderByDesc(Course::getId);
            return;
        }

        boolean isAsc = pageRequest.getAsc();
        String orderBy = pageRequest.getOrderBy().toLowerCase();

        switch (orderBy) {
            case "id" -> {
                if (isAsc) wrapper.orderByAsc(Course::getId);
                else wrapper.orderByDesc(Course::getId);
            }
            case "createtime", "created_at" -> {
                if (isAsc) wrapper.orderByAsc(Course::getCreatedAt);
                else wrapper.orderByDesc(Course::getCreatedAt);
            }
            case "deadline" -> {
                if (isAsc) wrapper.orderByAsc(Course::getDeadline);
                else wrapper.orderByDesc(Course::getDeadline);
            }
            case "credits" -> {
                if (isAsc) wrapper.orderByAsc(Course::getCredits);
                else wrapper.orderByDesc(Course::getCredits);
            }
            case "status" -> {
                if (isAsc) wrapper.orderByAsc(Course::getStatus);
                else wrapper.orderByDesc(Course::getStatus);
            }
            default -> wrapper.orderByDesc(Course::getId);
        }
    }
}