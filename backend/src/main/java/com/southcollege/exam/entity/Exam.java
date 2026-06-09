package com.southcollege.exam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 考试实体
 * <p>
 * 表示一场完整的考试，包含考试基本信息、试卷快照、时间设置、状态管理等。
 * 考试创建时从选定试卷+题目表完整复制题目内容（快照），之后与试卷/题库完全解耦，独立存在。
 * </p>
 *
 * @see com.southcollege.exam.service.ExamService
 */
@Data
@TableName(value = "exams", autoResultMap = true)
public class Exam {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 考试标题 */
    private String title;

    /** 考试描述/说明 */
    private String description;

    /** 关联的课程ID（{@link Course#id}） */
    private Long courseId;

    /** 创建该考试的教师ID（{@link User#id}） */
    private Long teacherId;

    /** 教师显示名（非数据库字段，查询时填充） */
    @TableField(exist = false)
    private String teacherName;

    /** 课程名称（非数据库字段，查询时填充） */
    @TableField(exist = false)
    private String courseName;

    /** 考试开始时间 */
    private LocalDateTime startedAt;

    /** 考试结束时间 */
    private LocalDateTime endedAt;

    /** 考试时长（分钟），null表示不限制时长，以结束时间为准 */
    private Integer duration;

    /** 试卷总分（由各题分值汇总） */
    private BigDecimal totalScore;

    /** 及格分数 */
    private BigDecimal passScore;

    /**
     * 考试状态
     * @see com.southcollege.exam.enums.ExamStatusEnum
     */
    private String status;

    /**
     * 考试试卷快照（创建考试时写入，之后与试卷/题库完全解耦）
     * <p>包含题目列表（items）和题型分值总标注（typeScores），对应数据库 exam_paper 列</p>
     */
    @TableField(value = "exam_paper", typeHandler = JacksonTypeHandler.class)
    private ExamPaperData examPaper;

    /** 创建时间 */
    private LocalDateTime createdAt;

    @TableLogic
    private Integer deleted = 0;

    /**
     * 当前学生对该考试的状态（非数据库字段，仅用于前端展示）
     * 可选值：NOT_STARTED / IN_PROGRESS / SUBMITTED / GRADED
     * @see com.southcollege.exam.enums.ExamSessionStatusEnum
     */
    @TableField(exist = false)
    private String studentExamStatus;

    /**
     * 考试试卷数据包装类
     * <p>JSON 结构：{ "items": [...], "typeScores": { "SINGLE_CHOICE": 2, ... } }</p>
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ExamPaperData {
        /** 题目列表 */
        private List<ExamQuestion> items;
        /** 题型分值总标注，key为题型代码，value为每题分值 */
        private Map<String, BigDecimal> typeScores;
    }

    /**
     * 考试题目内部类
     * <p>记录一道题目在考试中的完整信息（创建时从题目表快照复制）</p>
     */
    @Data
    public static class ExamQuestion {
        private Long questionId;

        private String content;

        private String type;

        private String difficulty;

        private List<Option> options;

        private Object correctAnswer;

        private String explanation;

        private Integer blankCount;

        private List<ScoringCriterion> scoringCriteria;
    }

    /**
     * 选项内部类
     */
    @Data
    public static class Option {
        private String id;
        private String text;
    }

    @Data
    public static class ScoringCriterion {
        private String point;
        private BigDecimal score;
    }
}