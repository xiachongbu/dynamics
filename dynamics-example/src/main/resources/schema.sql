CREATE TABLE t_user (
    id   BIGINT PRIMARY KEY,
    name VARCHAR(64),
    dept VARCHAR(64)
);

INSERT INTO t_user (id, name, dept) VALUES (1, 'alice', 'RD');
INSERT INTO t_user (id, name, dept) VALUES (2, 'bob', 'OPS');