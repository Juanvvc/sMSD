@echo off
rem Ejecutable Windows.
rem Uso:
rem - 'browserSD' ejecuta el browser gráfico
rem - 'browserSD texto' ejecuta el browser de texto
rem Necesita tener la variable JAVA_HOME correctamente definida.
rem Nota: este archivo solo se ejecuta desde el directorio raíz del proyecto

rem Configuración para el modo gráfico
rem JVM con salida a consola
set JAVA=%JAVA_HOME%\bin\java
rem JVM inmediata (sin salida a consola)
rem JAVA=%JAVA_HOME%\bin\javaw
rem Opciones adiocionales a la JVM
set JAVAOPT=-Djava.library.path=.
rem CLASSPATH del proyecto
set CLASSPATH=BlueCove.jar;log4j.jar;slp.jar;msd.jar;klingslib-1.0.jar;.
rem Clase principal para ejecutar
set MAINCLASS=org.msd.BrowserSD

rem Si nos pidieron el modo texto saltamos a su apartado
if "%%1"=="texto" then goto texto
if "%%1"=="example" then goto example

rem En otro caso, ejecuta
goto ejecuta

rem Configuración para el modo texto
:texto
set MAINCLASS=org.msd.BrowserSD
set JAVA=%JAVA_HOME%\bin\java

rem Configuración para el ejemplo de impresión
:example
set MAINCLASS=org.msd.PrinterExample
set JAVA=%JAVA_HOME%\bin\java

rem Ejecuta el proxy
:ejecuta
%JAVA% %JAVAOPT% -cp %CLASSPATH% %MAINCLASS% 
