INSERT INTO users (username, email, password, created_at, updated_at)
SELECT
    'user' || x AS username,
    'user' || x || '@test.com' AS email,
    'pass' || x AS password,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM SYSTEM_RANGE(1, 100000) x;