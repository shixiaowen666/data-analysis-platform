CREATE DATABASE test;

CREATE TABLE user
(
  id      BIGINT (20) NOT NULL COMMENT '主键ID',
  name    VARCHAR(30) NULL DEFAULT NULL COMMENT '姓名',
  age     INT (11) NULL DEFAULT NULL COMMENT '年龄',
  email   VARCHAR(50) NULL DEFAULT NULL COMMENT '邮箱',
  PRIMARY KEY (id)
);

INSERT INTO user (id, name, age, email)
VALUES (1, 'Jone', 18, 'test1@qq.com'),
       (2, 'Jack', 20, 'test2@qq.com'),
       (3, 'Jack', 20, 'test2@qq.com'),
       (4, 'Jack', 20, 'test2@qq.com'),
       (5, 'Jack', 20, 'test2@qq.com'),
       (6, 'Jack', 20, 'test2@qq.com'),
       (7, 'Jack', 20, 'test2@qq.com'),
       (8, 'Jack', 20, 'test2@qq.com'),
       (9, 'Jack', 20, 'test2@qq.com'),
       (10, 'Jack',20, 'test2@qq.com'),
       (11, 'Jack',20, 'test2@qq.com'),
       (12, 'Jack',20, 'test2@qq.com'),
       (13, 'Jack',20, 'test2@qq.com'),
       (14, 'Jack',20, 'test2@qq.com'),
       (15, 'Tom', 28, 'test3@qq.com'),
       (16, 'Sandy', 21, 'test4@qq.com'),
       (17, 'Billie', 24, 'test5@qq.com');