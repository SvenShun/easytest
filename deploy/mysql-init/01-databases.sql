CREATE DATABASE IF NOT EXISTS testhub_core CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS testhub_uiexec CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS testhub_apiexec CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
GRANT ALL PRIVILEGES ON testhub_core.* TO 'root'@'%';
GRANT ALL PRIVILEGES ON testhub_uiexec.* TO 'root'@'%';
GRANT ALL PRIVILEGES ON testhub_apiexec.* TO 'root'@'%';
FLUSH PRIVILEGES;
