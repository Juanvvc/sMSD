JAVA_HOME=/usr/lib/java
JAVAC=$(JAVA_HOME)/bin/javac
JAVA=$(JAVA_HOME)/bin/java
JAR=$(JAVA_HOME)/bin/jar
JAVAC_OPS=-nowarn
SRC=src
OBJ=$(SRC)/es/upc/ImageServer.java
CPATH=libs/slp.jar
RES=res
TEMP=classes
JAR_FILE=imageServer.jar
MAIN_CLASS=es.upc.ImageServer

# Construct the server
all: mrproper
	/bin/mkdir -p $(TEMP)
	$(JAVAC) $(JAVAC_OPS) -classpath $(CPATH) -d $(TEMP) -sourcepath $(SRC) $(OBJ)
	/bin/cp -r $(RES)/* $(TEMP)/.
	$(JAR) cf $(JAR_FILE) -C $(TEMP) .

# Constructs and executes the server
server: all
	$(JAVA) -cp $(JAR_FILE):$(CPATH) $(MAIN_CLASS) $(ATTRS)

# Remove the temporal file
clean:
	/bin/rm -rf $(TEMP) bak
	/bin/rm -rf `/usr/bin/find . -name '*~' -print`
# Remove all constructed files
mrproper: clean
	/bin/rm -rf $(JAR_FILE)
