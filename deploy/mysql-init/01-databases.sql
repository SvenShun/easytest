CREATE DATABASE IF NOT EXISTS easytest_core CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS easytest_uiexec CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS easytest_apiexec CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
GRANT ALL PRIVILEGES ON easytest_core.* TO 'root'@'%';
GRANT ALL PRIVILEGES ON easytest_uiexec.* TO 'root'@'%';
GRANT ALL PRIVILEGES ON easytest_apiexec.* TO 'root'@'%';
FLUSH PRIVILEGES;
