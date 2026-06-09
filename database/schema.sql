-- =====================================================
-- 南方职业学院在线考试系统 - 数据库表结构
-- =====================================================
-- 使用方法：
--   mysql -u root -p < schema.sql
-- 注意：
--   密码使用 BCrypt 加密存储，种子数据默认密码为 123456
--   生产环境请修改 JWT_SECRET / AES_SECRET 等环境变量
--   采用逻辑删除策略（deleted 字段），考试记录永久保留
-- =====================================================

DROP DATABASE IF EXISTS exam_system;
CREATE DATABASE exam_system DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE exam_system;

-- =====================================================
-- 核心表
-- =====================================================

-- 1. 用户表
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL COMMENT '用户名',
    password VARCHAR(255) NOT NULL COMMENT '密码(BCrypt加密)',
    nickname VARCHAR(100) NOT NULL COMMENT '昵称',
    avatar VARCHAR(255) COMMENT '头像URL',
    role ENUM('ADMIN', 'TEACHER', 'STUDENT') NOT NULL DEFAULT 'STUDENT' COMMENT '角色',
    status ENUM('ACTIVE', 'INACTIVE', 'SUSPENDED') NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=正常，1=已删除',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 2. 课程表
CREATE TABLE courses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL COMMENT '课程名称',
    code VARCHAR(20) UNIQUE NOT NULL COMMENT '课程代码',
    description TEXT COMMENT '课程描述',
    cover_url VARCHAR(500) COMMENT '课程封面URL',
    teacher_id BIGINT NOT NULL COMMENT '授课教师ID',
    credits DECIMAL(3,1) NOT NULL DEFAULT 1.0 COMMENT '学分',
    status ENUM('ACTIVE', 'INACTIVE') NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=正常，1=已删除',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    deadline TIMESTAMP NULL COMMENT '课程截止时间',
    FOREIGN KEY (teacher_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程表';

-- 3. 课程成员表（学生选课关联，物理删除，无 deleted 字段）
CREATE TABLE course_members (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    course_id BIGINT NOT NULL COMMENT '课程ID',
    student_id BIGINT NOT NULL COMMENT '学生ID',
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    FOREIGN KEY (course_id) REFERENCES courses(id),
    FOREIGN KEY (student_id) REFERENCES users(id),
    UNIQUE KEY uk_course_student (course_id, student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程成员表（物理删除，退课即删除记录）';

-- 4. 题目表
CREATE TABLE questions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    content TEXT NOT NULL COMMENT '题目内容',
    type ENUM('SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'TRUE_FALSE', 'FILL_BLANK', 'ESSAY') NOT NULL COMMENT '题目类型',
    difficulty ENUM('EASY', 'MEDIUM', 'HARD') NOT NULL DEFAULT 'MEDIUM' COMMENT '难度',
    teacher_id BIGINT NOT NULL COMMENT '创建教师ID',
    subject VARCHAR(100) COMMENT '学科',
    options JSON COMMENT '选项（选择题）',
    correct_answer JSON COMMENT '正确答案',
    scoring_criteria JSON COMMENT '评分标准（简答题）',
    explanation TEXT COMMENT '答案解析',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=正常，1=已删除',
    FOREIGN KEY (teacher_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题目表（题目内容模板，不含分值）';

-- 5. 试卷表
CREATE TABLE papers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL COMMENT '试卷名称',
    description TEXT COMMENT '试卷描述',
    teacher_id BIGINT NOT NULL COMMENT '创建教师ID',
    question_ids JSON COMMENT '题目ID列表，格式：[1, 2, 3, ...]',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    FOREIGN KEY (teacher_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='试卷表（仅题目ID列表，用于选题工具）';

-- 6. 考试表
CREATE TABLE exams (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL COMMENT '考试标题',
    description TEXT COMMENT '考试描述',
    course_id BIGINT NOT NULL COMMENT '所属课程ID',
    teacher_id BIGINT NOT NULL COMMENT '创建教师ID',
    started_at TIMESTAMP NOT NULL COMMENT '开始时间',
    ended_at TIMESTAMP NOT NULL COMMENT '结束时间',
    duration INT COMMENT '考试时长(分钟，NULL表示以结束时间为准)',
    total_score DECIMAL(5,2) NOT NULL DEFAULT 0.00 COMMENT '总分（创建时根据题型分值×题目数量动态计算）',
    pass_score DECIMAL(5,2) NOT NULL DEFAULT 0.00 COMMENT '及格分（创建时根据总分×及格分率动态计算）',
    status ENUM('DRAFT', 'PUBLISHED', 'STARTED', 'ENDED') NOT NULL DEFAULT 'DRAFT' COMMENT '状态（STARTED/ENDED由系统自动设置，教师提前结束也设为ENDED）',
    exam_paper JSON NOT NULL COMMENT '考试试卷快照（创建考试时从选定试卷+题目表完整复制，包含题目详情+题型分值w，之后与试卷/题库完全解耦）',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=正常，1=已删除',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    FOREIGN KEY (course_id) REFERENCES courses(id),
    FOREIGN KEY (teacher_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='考试表（题目快照，与试卷/题库完全解耦）';

-- 7. 学生考试记录表
CREATE TABLE exam_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    exam_id BIGINT NOT NULL COMMENT '考试ID',
    student_id BIGINT NOT NULL COMMENT '学生ID',
    started_at TIMESTAMP NOT NULL COMMENT '开始时间',
    submitted_at TIMESTAMP NULL COMMENT '提交时间',
    score DECIMAL(5,2) NULL COMMENT '得分',
    total_score DECIMAL(5,2) NOT NULL COMMENT '总分',
    status ENUM('NOT_STARTED', 'IN_PROGRESS', 'SUBMITTED', 'GRADED') NOT NULL DEFAULT 'NOT_STARTED' COMMENT '状态',
    grading_status ENUM('PENDING', 'GRADING', 'GRADED', 'COMPLETED') DEFAULT 'PENDING' COMMENT '评分状态',
    answers JSON COMMENT '答题记录（只存学生答案，题目信息从exams表获取）',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=正常，1=已删除',
    FOREIGN KEY (exam_id) REFERENCES exams(id),
    FOREIGN KEY (student_id) REFERENCES users(id),
    UNIQUE KEY uk_exam_student (exam_id, student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学生考试记录表（永久保留）';

-- 8. 公告表
CREATE TABLE announcements (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL COMMENT '标题',
    content TEXT NOT NULL COMMENT '内容',
    type ENUM('SYSTEM', 'EXAM', 'COURSE') NOT NULL DEFAULT 'SYSTEM' COMMENT '公告类型',
    priority ENUM('LOW', 'MEDIUM', 'HIGH') NOT NULL DEFAULT 'MEDIUM' COMMENT '优先级',
    status ENUM('DRAFT', 'PUBLISHED') NOT NULL DEFAULT 'DRAFT' COMMENT '状态',
    publisher_id BIGINT NOT NULL COMMENT '发布者ID',
    published_at TIMESTAMP NULL COMMENT '发布时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=正常，1=已删除',
    FOREIGN KEY (publisher_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公告表';

-- 9. AI配置表（物理删除，无 deleted 字段）
CREATE TABLE user_ai_configs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    name VARCHAR(100) NOT NULL COMMENT '配置名称',
    base_url VARCHAR(500) NOT NULL COMMENT 'API基础地址',
    api_key VARCHAR(500) NOT NULL COMMENT 'API密钥（加密存储）',
    models JSON NOT NULL COMMENT '模型列表（JSON数组）',
    active_model VARCHAR(100) COMMENT '当前激活的模型',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户AI配置表（物理删除）';

-- 10. 轮播图表
CREATE TABLE carousels (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL COMMENT '标题',
    image_url VARCHAR(500) NOT NULL COMMENT '图片URL',
    link_url VARCHAR(500) COMMENT '跳转链接',
    description TEXT COMMENT '描述',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序',
    status ENUM('ACTIVE', 'INACTIVE') NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=正常，1=已删除',
    start_at TIMESTAMP NULL COMMENT '展示开始时间',
    end_at TIMESTAMP NULL COMMENT '展示结束时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='轮播图表';

-- =====================================================
-- 索引
-- =====================================================

CREATE INDEX idx_users_role ON users(role);
CREATE INDEX idx_users_deleted ON users(deleted);
CREATE INDEX idx_courses_teacher ON courses(teacher_id);
CREATE INDEX idx_courses_deleted ON courses(deleted);
CREATE INDEX idx_exams_course ON exams(course_id);
CREATE INDEX idx_exams_teacher ON exams(teacher_id);
CREATE INDEX idx_exams_status ON exams(status);
CREATE INDEX idx_exams_deleted ON exams(deleted);
CREATE INDEX idx_questions_teacher ON questions(teacher_id);
CREATE INDEX idx_questions_type ON questions(type);
CREATE INDEX idx_questions_type_teacher ON questions(type, teacher_id);
CREATE INDEX idx_questions_deleted ON questions(deleted);
CREATE INDEX idx_exam_sessions_exam ON exam_sessions(exam_id);
CREATE INDEX idx_exam_sessions_student ON exam_sessions(student_id);
CREATE INDEX idx_exam_sessions_status ON exam_sessions(status);
CREATE INDEX idx_exam_sessions_deleted ON exam_sessions(deleted);
CREATE INDEX idx_user_ai_configs_user ON user_ai_configs(user_id);
CREATE INDEX idx_user_ai_configs_active_model ON user_ai_configs(active_model);
CREATE INDEX idx_papers_teacher ON papers(teacher_id);
CREATE INDEX idx_announcements_status ON announcements(status);
CREATE INDEX idx_announcements_priority ON announcements(priority);
CREATE INDEX idx_announcements_deleted ON announcements(deleted);
CREATE INDEX idx_carousels_status ON carousels(status);
CREATE INDEX idx_carousels_sort ON carousels(sort_order);
CREATE INDEX idx_carousels_deleted ON carousels(deleted);
CREATE INDEX idx_course_members_student ON course_members(student_id);
CREATE INDEX idx_exam_sessions_exam_status ON exam_sessions(exam_id, status);
CREATE INDEX idx_exams_course_status ON exams(course_id, status);
CREATE INDEX idx_exams_time_range ON exams(started_at, ended_at);

-- =====================================================
-- 视图（只查未逻辑删除的数据）
-- =====================================================

CREATE OR REPLACE VIEW v_system_stats AS
SELECT
    (SELECT COUNT(*) FROM users WHERE deleted = 0) AS total_users,
    (SELECT COUNT(*) FROM users WHERE role = 'TEACHER' AND deleted = 0) AS total_teachers,
    (SELECT COUNT(*) FROM users WHERE role = 'STUDENT' AND deleted = 0) AS total_students,
    (SELECT COUNT(*) FROM courses WHERE deleted = 0) AS total_courses,
    (SELECT COUNT(*) FROM exams WHERE deleted = 0) AS total_exams,
    (SELECT COUNT(*) FROM questions WHERE deleted = 0) AS total_questions,
    (SELECT COUNT(*) FROM papers) AS total_papers,
    (SELECT COUNT(*) FROM exam_sessions WHERE deleted = 0) AS total_sessions,
    (SELECT COUNT(*) FROM announcements WHERE deleted = 0) AS total_announcements,
    CURRENT_TIMESTAMP AS last_updated;

CREATE OR REPLACE VIEW v_exam_statistics AS
SELECT
    es.exam_id,
    e.title AS exam_title,
    c.name AS course_name,
    COUNT(*) AS total_participants,
    COUNT(es.score) AS graded_count,
    AVG(es.score) AS avg_score,
    MAX(es.score) AS max_score,
    MIN(es.score) AS min_score
FROM exam_sessions es
LEFT JOIN exams e ON es.exam_id = e.id AND e.deleted = 0
LEFT JOIN courses c ON e.course_id = c.id AND c.deleted = 0
WHERE es.deleted = 0
GROUP BY es.exam_id, e.title, c.name;

CREATE OR REPLACE VIEW v_student_rankings AS
SELECT
    es.exam_id,
    es.student_id,
    u.username,
    u.nickname,
    es.score,
    es.total_score,
    ROUND(es.score / NULLIF(es.total_score, 0) * 100, 2) AS percentage,
    RANK() OVER (PARTITION BY es.exam_id ORDER BY es.score DESC) AS rank_position
FROM exam_sessions es
JOIN users u ON es.student_id = u.id AND u.deleted = 0
WHERE es.status = 'GRADED' AND es.deleted = 0;

CREATE OR REPLACE VIEW v_paper_info AS
SELECT
    p.id AS paper_id,
    p.name AS paper_name,
    p.description,
    u.nickname AS teacher_name,
    JSON_LENGTH(p.question_ids) AS question_count,
    p.created_at
FROM papers p
LEFT JOIN users u ON p.teacher_id = u.id AND u.deleted = 0;

-- =====================================================
-- 种子数据
-- =====================================================
-- 默认密码: 123456 的 BCrypt 哈希
-- =====================================================
