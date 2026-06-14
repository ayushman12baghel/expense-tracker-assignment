@echo off
for /f "tokens=1,2 delims==" %%a in (.env) do (
    if not "%%a"=="" if not "%%a"==" " (
        set %%a=%%b
    )
)
java -Dmaven.multiModuleProjectDirectory="%~dp0." -cp ".mvn\wrapper\maven-wrapper.jar" org.apache.maven.wrapper.MavenWrapperMain spring-boot:run
