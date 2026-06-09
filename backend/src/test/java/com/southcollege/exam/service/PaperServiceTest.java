package com.southcollege.exam.service;

import com.southcollege.exam.entity.Paper;
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
class PaperServiceTest {

    @Mock
    private ExamService examService;

    @Mock
    private QuestionService questionService;

    @Spy
    @InjectMocks
    private PaperService paperService;

    private Paper testPaper;

    @BeforeEach
    void setUp() {
        testPaper = new Paper();
        testPaper.setId(1L);
        testPaper.setName("Test Paper");
        testPaper.setTeacherId(1L);
    }

    @Test
    void testCheckOwnership_OwnerCanAccess() {
        doReturn(testPaper).when(paperService).getById(1L);
        assertDoesNotThrow(() -> paperService.checkOwnership(1L, 1L, "TEACHER"));
    }

    @Test
    void testCheckOwnership_NotOwnerThrowsException() {
        testPaper.setTeacherId(999L);
        doReturn(testPaper).when(paperService).getById(1L);
        assertThrows(BusinessException.class, () -> paperService.checkOwnership(1L, 1L, "TEACHER"));
    }

    @Test
    void testCheckOwnership_AdminNotOwnerThrowsException() {
        testPaper.setTeacherId(999L);
        doReturn(testPaper).when(paperService).getById(1L);
        assertThrows(BusinessException.class, () -> paperService.checkOwnership(1L, 1L, "ADMIN"));
    }

    @Test
    void testCheckOwnership_PaperNotFound() {
        doReturn(null).when(paperService).getById(999L);
        assertThrows(BusinessException.class, () -> paperService.checkOwnership(999L, 1L, "TEACHER"));
    }

    }