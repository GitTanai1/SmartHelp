CREATE USER IF NOT EXISTS 'smarthelp'@'localhost' IDENTIFIED BY 'smarthelp';
CREATE USER IF NOT EXISTS 'smarthelp'@'%' IDENTIFIED BY 'smarthelp';

GRANT ALL PRIVILEGES ON smarthelp.* TO 'smarthelp'@'localhost';
GRANT ALL PRIVILEGES ON smarthelp.* TO 'smarthelp'@'%';

FLUSH PRIVILEGES;
