set JAVA_HOME=C:\Program Files (x86)\Java\jre1.8.0_201
set CATALINA_HOME=C:\Users\sydney\Desktop\freelance\proximax\apache-tomcat-8.5.38

set JAVA_OPTS=-Xmx512M -Dwflow.home=C:\Users\sydney\Desktop\freelance\proximax\joget\joget_src\jw-community\wflow-install\src\main\resources\wflow-home -javaagent:C:\Users\sydney\Desktop\freelance\proximax\joget\joget_src\jw-community\wflow-install\src\main\resources\wflow-home\aspectjweaver-1.8.5.jar
REM set JAVA_OPTS=-Xmx1024M -Xdebug -Xnoagent -Djava.compiler=NONE -Xrunjdwp:transport=dt_socket,suspend=n,server=y,address=5115 -Dwflow.home=C:\Users\sydney\Desktop\freelance\proximax\joget\joget_src\jw-community\wflow-install\src\main\resources\wflow-home -javaagent:C:\Users\sydney\Desktop\freelance\proximax\joget\joget_src\jw-community\wflow-install\src\main\resources\wflow-home\aspectjweaver-1.8.5.jar

%CATALINA_HOME%\bin\startup.bat

