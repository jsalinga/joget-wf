set MYSQL_HOME=C:\Program Files\MySQL\MySQL Server 8.0
set MYSQL_INI_HOME=.\
start %MYSQL_HOME%\bin\mysqld-nt --defaults-file="%MYSQL_HOME%/my.ini"
