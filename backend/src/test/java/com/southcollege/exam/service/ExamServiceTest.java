package com.southcollege.exam.service;

import com.southcollege.exam.entity.Exam;
import com.southcollege.exam.entity.ExamSession;
import com.southcollege.exam.enums.ExamSessionStatusEnum;
import com.southcollege.exam.enums.ExamStatusEnum;
import com.southcollege.exam.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 考试服务测试
 */
@ExtendWith(MockitoExtension.class)
class ExamServiceTest {

    @Mock
    private ExamSessionService examSessionService;

    @Mock
    private PaperService paperService;

    @Mock
    private QuestionService questionService;

    @Mock
    private CourseService courseService;

    @Spy
    @InjectMocks
    private ExamService examService;

    private Exam testExam;
    private ExamSession testSession;

    @BeforeEach
    void setUp() {
        testExam = new Exam();
        testExam.setId(1L);
        testExam.setTitle("Test Exam");
        testExam.setTeacherId(1L);
        testExam.setStatus(ExamStatusEnum.PUBLISHED.getCode());
        testExam.setStartedAt(LocalDateTime.now().minusHours(1));
        testExam.setEndedAt(LocalDateTime.now().plusHours(1));
        testExam.setDuration(60);
        testExam.setTotalScore(BigDecimal.valueOf(100));

        testSession = new ExamSession();
        testSession.setId(1L);
        testSession.setExamId(1L);
        testSession.setStudentId(1L);
        testSession.setStartedAt(LocalDateTime.now().minusMinutes(30));
        testSession.setStatus(ExamSessionStatusEnum.IN_PROGRESS.getCode());
    }

    @Test
    void testStartExam_Success() {
        testExam.setCourseId(1L);
        testExam.setStatus(ExamStatusEnum.STARTED.getCode());
        doReturn(testExam).when(examService).getById(1L);
        when(courseService.isCourseMember(1L, 1L)).thenReturn(true);
        when(examSessionService.getByExamIdAndStudentId(1L, 1L)).thenReturn(null);
        when(examSessionService.save(any(ExamSession.class))).thenReturn(true);

        ExamSession session = examService.startExam(1L, 1L);

        assertNotNull(session);
        assertEquals(1L, session.getExamId());
        assertEquals(1L, session.getStudentId());
    }

    @Test
    void testStartExam_ExamNotFound() {
        doReturn(null).when(examService).getById(999L);

        assertThrows(BusinessException.class, () -> {
            examService.startExam(999L, 1L);
        });
    }

    @Test
    void testStartExam_ExamNotPublished() {
        testExam.setStatus(ExamStatusEnum.DRAFT.getCode());
        doReturn(testExam).when(examService).getById(1L);

        assertThrows(BusinessException.class, () -> {
            examService.startExam(1L, 1L);
        });
    }

    @Test
    void testStartExam_ExamNotStarted() {
        testExam.setCourseId(1L);
        testExam.setStatus(ExamStatusEnum.PUBLISHED.getCode());
        testExam.setStartedAt(LocalDateTime.now().plusHours(1));
        testExam.setEndedAt(LocalDateTime.now().plusHours(2));
        doReturn(testExam).when(examService).getById(1L);

        assertThrows(BusinessException.class, () -> {
            examService.startExam(1L, 1L);
        });
    }

    @Test
    void testStartExam_ExamEnded() {
        testExam.setStatus(ExamStatusEnum.ENDED.getCode());
        doReturn(testExam).when(examService).getById(1L);

        assertThrows(BusinessException.class, () -> {
            examService.startExam(1L, 1L);
        });
    }

    @Test
    void testStartExam_AlreadySubmitted() {
        testExam.setCourseId(1L);
        testExam.setStatus(ExamStatusEnum.STARTED.getCode());
        testSession.setStatus(ExamSessionStatusEnum.SUBMITTED.getCode());
        doReturn(testExam).when(examService).getById(1L);
        when(courseService.isCourseMember(1L, 1L)).thenReturn(true);
        when(examSessionService.getByExamIdAndStudentId(1L, 1L)).thenReturn(testSession);

        assertThrows(BusinessException.class, () -> {
            examService.startExam(1L, 1L);
        });
    }

    @Test
    void testStartExam_ContinueExam() {
        testExam.setCourseId(1L);
        testExam.setStatus(ExamStatusEnum.STARTED.getCode());
        doReturn(testExam).when(examService).getById(1L);
        when(courseService.isCourseMember(1L, 1L)).thenReturn(true);
        when(examSessionService.getByExamIdAndStudentId(1L, 1L)).thenReturn(testSession);

        ExamSession session = examService.startExam(1L, 1L);

        assertNotNull(session);
        assertEquals(testSession.getId(), session.getId());
    }

    @Test
    void testCheckOwnership_Success() {
        // Given
        doReturn(testExam).when(examService).getById(1L);

        // When & Then - no exception thrown
        assertDoesNotThrow(() -> {
            examService.checkOwnership(1L, 1L, "TEACHER");
        });
    }

    @Test
    void testCheckOwnership_AdminNotOwnerThrowsException() {
        testExam.setTeacherId(999L);
        doReturn(testExam).when(examService).getById(1L);

        assertThrows(BusinessException.class, () -> {
            examService.checkOwnership(1L, 1L, "ADMIN");
        });
    }

    @Test
    void testCheckOwnership_NotOwner() {
        // Given
        testExam.setTeacherId(999L);
        doReturn(testExam).when(examService).getById(1L);

        // When & Then
        assertThrows(BusinessException.class, () -> {
            examService.checkOwnership(1L, 1L, "TEACHER");
        });
    }

    @Test
    void testCheckOwnership_ExamNotFound() {
        // Given
        doReturn(null).when(examService).getById(999L);

        // When & Then
        assertThrows(BusinessException.class, () -> {
            examService.checkOwnership(999L, 1L, "TEACHER");
        });
    }

    @Test
    void testPublishExam_Success() {
        testExam.setStatus(ExamStatusEnum.DRAFT.getCode());
        testExam.setStartedAt(LocalDateTime.now().plusDays(1));
        testExam.setEndedAt(LocalDateTime.now().plusDays(1).plusHours(2));
        Exam.ExamQuestion eq = new Exam.ExamQuestion();
        eq.setType("SINGLE_CHOICE");
        testExam.setExamPaper(new Exam.ExamPaperData(
                List.of(eq), Map.of("SINGLE_CHOICE", BigDecimal.TEN)));
        testExam.setTotalScore(BigDecimal.TEN);
        doReturn(testExam).when(examService).getById(1L);
        doReturn(true).when(examService).updateById(any(Exam.class));

        examService.publishExam(1L, 1L, "TEACHER");

        assertEquals(ExamStatusEnum.PUBLISHED.getCode(), testExam.getStatus());
        verify(examService).updateById(any(Exam.class));
    }

}
