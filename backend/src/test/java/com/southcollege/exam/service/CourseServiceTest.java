package com.southcollege.exam.service;

import com.southcollege.exam.entity.Course;
import com.southcollege.exam.entity.Exam;
import com.southcollege.exam.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    @Mock
    private CourseMemberService courseMemberService;

    @Mock
    private UserService userService;

    @Mock
    private ExamService examService;

    @Spy
    @InjectMocks
    private CourseService courseService;

    private Course testCourse;

    @BeforeEach
    void setUp() {
        testCourse = new Course();
        testCourse.setId(1L);
        testCourse.setName("Test Course");
        testCourse.setCode("CS101");
        testCourse.setTeacherId(1L);
        testCourse.setStatus("ACTIVE");
    }

    @Test
    void testCheckOwnership_AdminCanAccess() {
        doReturn(testCourse).when(courseService).getById(1L);
        assertDoesNotThrow(() -> courseService.checkOwnership(1L, 1L, "ADMIN"));
    }

    @Test
    void testCheckOwnership_OwnerCanAccess() {
        doReturn(testCourse).when(courseService).getById(1L);
        assertDoesNotThrow(() -> courseService.checkOwnership(1L, 1L, "TEACHER"));
    }

    @Test
    void testCheckOwnership_NotOwnerThrowsException() {
        testCourse.setTeacherId(999L);
        doReturn(testCourse).when(courseService).getById(1L);
        assertThrows(BusinessException.class, () -> courseService.checkOwnership(1L, 1L, "TEACHER"));
    }

    @Test
    void testCheckOwnership_CourseNotFound() {
        doReturn(null).when(courseService).getById(999L);
        assertThrows(BusinessException.class, () -> courseService.checkOwnership(999L, 1L, "TEACHER"));
    }

    @Test
    void testGetByIdWithPermission_StudentCanAccess() {
        doReturn(testCourse).when(courseService).getByIdWithTeacherName(1L);
        Course result = courseService.getByIdWithPermission(1L, 2L, "STUDENT");
        assertNotNull(result);
        assertEquals("Test Course", result.getName());
    }

    @Test
    void testGetByIdWithPermission_NotTeacherCannotAccessOtherCourse() {
        testCourse.setTeacherId(999L);
        doReturn(testCourse).when(courseService).getByIdWithTeacherName(1L);
        assertThrows(BusinessException.class, () -> courseService.getByIdWithPermission(1L, 1L, "TEACHER"));
    }
}