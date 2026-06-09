package com.southcollege.exam.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.southcollege.exam.entity.Question;
import com.southcollege.exam.exception.BusinessException;
import com.southcollege.exam.mapper.PaperMapper;
import com.southcollege.exam.mapper.QuestionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuestionServiceTest {

    @Mock
    private PaperMapper paperMapper;

    @Mock
    private QuestionMapper questionMapper;

    @Spy
    @InjectMocks
    private QuestionService questionService;

    private Question testQuestion;

    @BeforeEach
    void setUp() {
        testQuestion = new Question();
        testQuestion.setId(1L);
        testQuestion.setContent("Test question");
        testQuestion.setType("SINGLE_CHOICE");
        testQuestion.setTeacherId(1L);
    }

    @Test
    void testCheckOwnership_AdminCanAccess() {
        doReturn(testQuestion).when(questionService).getById(1L);
        assertDoesNotThrow(() -> questionService.checkOwnership(1L, 1L, "ADMIN"));
    }

    @Test
    void testCheckOwnership_OwnerCanAccess() {
        doReturn(testQuestion).when(questionService).getById(1L);
        assertDoesNotThrow(() -> questionService.checkOwnership(1L, 1L, "TEACHER"));
    }

    @Test
    void testCheckOwnership_NotOwnerThrowsException() {
        testQuestion.setTeacherId(999L);
        doReturn(testQuestion).when(questionService).getById(1L);
        assertThrows(BusinessException.class, () -> questionService.checkOwnership(1L, 1L, "TEACHER"));
    }

    @Test
    void testCheckOwnership_QuestionNotFound() {
        doReturn(null).when(questionService).getById(999L);
        assertThrows(BusinessException.class, () -> questionService.checkOwnership(999L, 1L, "TEACHER"));
    }

    @Test
    void testSave_SanitizesHtml() throws Exception {
        testQuestion.setContent("<p>Hello <script>alert('xss')</script>World</p>");
        testQuestion.setExplanation("<b>Explanation</b><img src=x onerror=alert(1)>");

        // @InjectMocks 不会注入到 ServiceImpl 父类的 baseMapper 字段，需要用反射设置
        java.lang.reflect.Field mapperField = ServiceImpl.class.getDeclaredField("baseMapper");
        mapperField.setAccessible(true);
        mapperField.set(questionService, questionMapper);

        when(questionMapper.insert(any(Question.class))).thenReturn(1);
        doCallRealMethod().when(questionService).save(testQuestion);

        questionService.save(testQuestion);

        assertFalse(testQuestion.getContent().contains("<script>"));
        assertFalse(testQuestion.getExplanation().contains("onerror"));
    }
}