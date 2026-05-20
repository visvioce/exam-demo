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

INSERT INTO users (username, password, nickname, role, status) VALUES
('admin',   '$2a$10$QG0Cor8qAuVTYGGFR8hgn.7s2WhjmQDNPvaa4v.agBcxeR9GDhRz6', '系统管理员', 'ADMIN', 'ACTIVE'),
('teacher', '$2a$10$QG0Cor8qAuVTYGGFR8hgn.7s2WhjmQDNPvaa4v.agBcxeR9GDhRz6', '张老师', 'TEACHER', 'ACTIVE'),
('student', '$2a$10$QG0Cor8qAuVTYGGFR8hgn.7s2WhjmQDNPvaa4v.agBcxeR9GDhRz6', '李同学', 'STUDENT', 'ACTIVE');

INSERT INTO courses (name, code, description, teacher_id, status) VALUES
('计算机基础', 'CS101', '计算机科学入门课程', 2, 'ACTIVE'),
('高等数学', 'MATH201', '高等数学基础课程', 2, 'ACTIVE');

INSERT INTO course_members (course_id, student_id) VALUES
(1, 3),
(2, 3);

INSERT INTO announcements (title, content, type, priority, status, publisher_id, published_at) VALUES
('系统上线', '欢迎使用南方职业学院在线考试系统！', 'SYSTEM', 'HIGH', 'PUBLISHED', 1, NOW());

-- =====================================================
-- 题库种子数据（教师 ID = 2 张老师）
-- =====================================================

-- 单选题
INSERT INTO questions (teacher_id, type, content, options, correct_answer, difficulty, explanation, subject, deleted) VALUES
(2, 'SINGLE_CHOICE', 'CPU的全称是什么？',
 '[{"id": "A", "text": "Central Processing Unit"},{"id": "B", "text": "Core Program Utility"},{"id": "C", "text": "Computer Personal Unit"},{"id": "D", "text": "Central Program Unit"}]',
 '"A"', 'EASY', 'CPU即中央处理器，是计算机的核心部件。', '计算机基础', 0),
(2, 'SINGLE_CHOICE', '以下哪种数据结构是先进后出的？',
 '[{"id": "A", "text": "队列"},{"id": "B", "text": "栈"},{"id": "C", "text": "链表"},{"id": "D", "text": "数组"}]',
 '"B"', 'EASY', '栈(Stack)是先进后出(FILO)的数据结构。', '数据结构', 0),
(2, 'SINGLE_CHOICE', 'HTTP协议的默认端口号是？',
 '[{"id": "A", "text": "21"},{"id": "B", "text": "443"},{"id": "C", "text": "80"},{"id": "D", "text": "8080"}]',
 '"C"', 'EASY', 'HTTP默认端口80，HTTPS默认端口443。', '计算机网络', 0),
(2, 'SINGLE_CHOICE', 'Java中，以下哪个关键字用于实现接口？',
 '[{"id": "A", "text": "extends"},{"id": "B", "text": "implements"},{"id": "C", "text": "abstract"},{"id": "D", "text": "interface"}]',
 '"B"', 'EASY', 'Java中使用implements关键字实现接口。', 'Java编程', 0),
(2, 'SINGLE_CHOICE', '下列哪种排序算法的时间复杂度最低（最优情况）？',
 '[{"id": "A", "text": "冒泡排序 O(n²)"},{"id": "B", "text": "插入排序 O(n)"},{"id": "C", "text": "快速排序 O(n log n)"},{"id": "D", "text": "归并排序 O(n log n)"}]',
 '"B"', 'MEDIUM', '插入排序在数据基本有序时最优复杂度为O(n)。', '算法', 0);

-- 多选题
INSERT INTO questions (teacher_id, type, content, options, correct_answer, difficulty, explanation, subject, deleted) VALUES
(2, 'MULTIPLE_CHOICE', '以下哪些是关系型数据库？',
 '[{"id": "A", "text": "MySQL"},{"id": "B", "text": "MongoDB"},{"id": "C", "text": "PostgreSQL"},{"id": "D", "text": "Redis"}]',
 '["A","C"]', 'EASY', 'MySQL和PostgreSQL是关系型数据库，MongoDB是文档数据库，Redis是键值存储。', '数据库', 0),
(2, 'MULTIPLE_CHOICE', '计算机网络OSI模型中，以下哪些属于传输层协议？',
 '[{"id": "A", "text": "TCP"},{"id": "B", "text": "HTTP"},{"id": "C", "text": "UDP"},{"id": "D", "text": "IP"}]',
 '["A","C"]', 'MEDIUM', 'TCP和UDP是传输层协议，HTTP是应用层，IP是网络层。', '计算机网络', 0),
(2, 'MULTIPLE_CHOICE', '以下哪些是Java的基本数据类型？',
 '[{"id": "A", "text": "int"},{"id": "B", "text": "String"},{"id": "C", "text": "boolean"},{"id": "D", "text": "float"}]',
 '["A","C","D"]', 'EASY', 'Java的基本数据类型包括int、boolean、float等，String是引用类型。', 'Java编程', 0);

-- 判断题
INSERT INTO questions (teacher_id, type, content, options, correct_answer, difficulty, explanation, subject, deleted) VALUES
(2, 'TRUE_FALSE', 'TCP是面向无连接的协议。',
 NULL,
 'false', 'EASY', 'TCP是面向连接的协议，UDP才是无连接的。', '计算机网络', 0),
(2, 'TRUE_FALSE', 'Java中一个类可以实现多个接口。',
 NULL,
 'true', 'EASY', 'Java支持多接口实现，但不支持多类继承。', 'Java编程', 0),
(2, 'TRUE_FALSE', 'HTTP是状态保持协议。',
 NULL,
 'false', 'MEDIUM', 'HTTP是无状态协议，需要Cookie/Session来保持状态。', '计算机网络', 0);

-- 填空题
INSERT INTO questions (teacher_id, type, content, options, correct_answer, difficulty, explanation, subject, deleted) VALUES
(2, 'FILL_BLANK', '在TCP/IP协议栈中，IP协议工作在网络层，TCP协议工作在____层。',
 NULL,
 '["传输"]', 'EASY', 'TCP工作在传输层。', '计算机网络', 0),
(2, 'FILL_BLANK', 'Java的父类构造方法通过____关键字调用。',
 NULL,
 '["super"]', 'EASY', 'Java中使用super调用父类构造方法。', 'Java编程', 0);

-- 简答题
INSERT INTO questions (teacher_id, type, content, options, correct_answer, difficulty, explanation, subject, deleted) VALUES
(2, 'ESSAY', '请简述HTTP与HTTPS的主要区别。',
 NULL,
 '"HTTP是明文传输，HTTPS通过SSL/TLS加密传输，默认端口80和443。HTTPS需要CA证书验证服务器身份，更安全。"', 'MEDIUM',
 'HTTP与HTTPS的核心区别在于加密和安全性。', '计算机网络', 0),
(2, 'ESSAY', '请解释面向对象编程(OOP)的三大特性：封装、继承、多态。',
 NULL,
 '"封装：数据和操作封装在类中，通过访问控制保护数据。继承：子类继承父类的属性和方法。多态：同一接口可以有不同实现，通过重写和重载体现。"', 'MEDIUM',
 'OOP三特性是面向对象设计的核心。', 'Java编程', 0);

-- =====================================================
-- 试卷种子数据（教师 ID = 2）
-- =====================================================

INSERT INTO papers (name, description, teacher_id, question_ids, created_at) VALUES
('计算机基础单元测验', '涵盖计算机基础、网络和数据结构的选择题+判断+填空混合卷', 2,
 '[1,2,3,6,7,9,10,12,13]', NOW()),
('Java综合测试卷', 'Java语法、OOP特性及数据库知识的综合试卷', 2,
 '[4,8,11,14,15,16]', NOW()),
('计算机网络专项', '计算机网络核心知识测试卷', 2,
 '[3,7,9,10,12,13]', NOW());

-- =====================================================
-- 考试种子数据（课程 ID = 1 计算机基础）
-- exam_paper JSON 包含题目快照 + 题型分值，与试卷完全解耦
-- =====================================================

INSERT INTO exams (title, description, course_id, teacher_id, exam_paper, total_score, status, started_at, ended_at, duration, pass_score, deleted, created_at) VALUES
(
  '第一单元测验',
  '计算机基础第一次单元测验，请认真作答',
  1, 2,
  '{"items":[{"questionId":1,"content":"CPU的全称是什么？","type":"SINGLE_CHOICE","difficulty":"EASY","correctAnswer":"A","options":[{"id":"A","text":"Central Processing Unit"},{"id":"B","text":"Core Program Utility"},{"id":"C","text":"Computer Personal Unit"},{"id":"D","text":"Central Program Unit"}]},{"questionId":2,"content":"以下哪种数据结构是先进后出的？","type":"SINGLE_CHOICE","difficulty":"EASY","correctAnswer":"B","options":[{"id":"A","text":"队列"},{"id":"B","text":"栈"},{"id":"C","text":"链表"},{"id":"D","text":"数组"}]},{"questionId":6,"content":"以下哪些是关系型数据库？","type":"MULTIPLE_CHOICE","difficulty":"EASY","correctAnswer":["A","C"],"options":[{"id":"A","text":"MySQL"},{"id":"B","text":"MongoDB"},{"id":"C","text":"PostgreSQL"},{"id":"D","text":"Redis"}]},{"questionId":9,"content":"TCP是面向无连接的协议。","type":"TRUE_FALSE","difficulty":"EASY","correctAnswer":false},{"questionId":10,"content":"Java中一个类可以实现多个接口。","type":"TRUE_FALSE","difficulty":"EASY","correctAnswer":true},{"questionId":12,"content":"在TCP/IP协议栈中，IP协议工作在网络层，TCP协议工作在____层。","type":"FILL_BLANK","difficulty":"EASY","correctAnswer":["传输"]},{"questionId":14,"content":"请简述HTTP与HTTPS的主要区别。","type":"ESSAY","difficulty":"MEDIUM","correctAnswer":"HTTP是明文传输，HTTPS通过SSL/TLS加密传输，默认端口80和443。HTTPS需要CA证书验证服务器身份，更安全。"}],"typeScores":{"SINGLE_CHOICE":10,"MULTIPLE_CHOICE":10,"TRUE_FALSE":5,"FILL_BLANK":10,"ESSAY":20}}',
  100, 'PUBLISHED',
  DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_ADD(NOW(), INTERVAL 28 DAY),
  60, 60.00, 0, NOW()
),
(
  '期中考试-计算机基础',
  '计算机基础课程期中考试',
  1, 2,
  '{"items":[{"questionId":1,"content":"CPU的全称是什么？","type":"SINGLE_CHOICE","difficulty":"EASY","correctAnswer":"A","options":[{"id":"A","text":"Central Processing Unit"},{"id":"B","text":"Core Program Utility"},{"id":"C","text":"Computer Personal Unit"},{"id":"D","text":"Central Program Unit"}]},{"questionId":2,"content":"以下哪种数据结构是先进后出的？","type":"SINGLE_CHOICE","difficulty":"EASY","correctAnswer":"B","options":[{"id":"A","text":"队列"},{"id":"B","text":"栈"},{"id":"C","text":"链表"},{"id":"D","text":"数组"}]},{"questionId":5,"content":"下列哪种排序算法的时间复杂度最低（最优情况）？","type":"SINGLE_CHOICE","difficulty":"MEDIUM","correctAnswer":"B","options":[{"id":"A","text":"冒泡排序 O(n²)"},{"id":"B","text":"插入排序 O(n)"},{"id":"C","text":"快速排序 O(n log n)"},{"id":"D","text":"归并排序 O(n log n)"}]},{"questionId":6,"content":"以下哪些是关系型数据库？","type":"MULTIPLE_CHOICE","difficulty":"EASY","correctAnswer":["A","C"],"options":[{"id":"A","text":"MySQL"},{"id":"B","text":"MongoDB"},{"id":"C","text":"PostgreSQL"},{"id":"D","text":"Redis"}]},{"questionId":9,"content":"TCP是面向无连接的协议。","type":"TRUE_FALSE","difficulty":"EASY","correctAnswer":false},{"questionId":10,"content":"Java中一个类可以实现多个接口。","type":"TRUE_FALSE","difficulty":"EASY","correctAnswer":true},{"questionId":12,"content":"在TCP/IP协议栈中，IP协议工作在网络层，TCP协议工作在____层。","type":"FILL_BLANK","difficulty":"EASY","correctAnswer":["传输"]},{"questionId":14,"content":"请简述HTTP与HTTPS的主要区别。","type":"ESSAY","difficulty":"MEDIUM","correctAnswer":"HTTP是明文传输，HTTPS通过SSL/TLS加密传输，默认端口80和443。HTTPS需要CA证书验证服务器身份，更安全。"}],"typeScores":{"SINGLE_CHOICE":10,"MULTIPLE_CHOICE":15,"TRUE_FALSE":5,"FILL_BLANK":10,"ESSAY":20}}',
  100, 'PUBLISHED',
  DATE_ADD(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 31 DAY),
  90, 60.00, 0, NOW()
);

-- =====================================================
-- 考试记录种子数据（学生 ID = 3 李同学）
-- =====================================================

INSERT INTO exam_sessions (exam_id, student_id, status, grading_status, answers, started_at, score, total_score, deleted) VALUES
(
  1, 3, 'GRADED', 'COMPLETED',
  '[{"questionId":1,"answer":"A","questionType":"SINGLE_CHOICE"},{"questionId":2,"answer":"B","questionType":"SINGLE_CHOICE"},{"questionId":6,"answer":["A","C"],"questionType":"MULTIPLE_CHOICE"},{"questionId":9,"answer":false,"questionType":"TRUE_FALSE"},{"questionId":10,"answer":true,"questionType":"TRUE_FALSE"},{"questionId":12,"answer":["传输"],"questionType":"FILL_BLANK"},{"questionId":14,"answer":"HTTP是明文传输，HTTPS加密传输。","questionType":"ESSAY"}]',
  DATE_SUB(NOW(), INTERVAL 1 DAY),
  75.00, 100.00, 0
);