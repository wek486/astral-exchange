-- Run as a MySQL administrator, then create an application user locally.
CREATE DATABASE IF NOT EXISTS astral_exchange CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- Example (replace the password before running):
-- CREATE USER 'astral'@'localhost' IDENTIFIED BY 'your-local-password';
-- GRANT ALL PRIVILEGES ON astral_exchange.* TO 'astral'@'localhost';
-- Flyway creates application and Spring Session tables on backend startup.
