# Makefile to compile the system.
# GNUMake in *nix is needed. Not tested with other flavours of Make. This
# file doesn't work in Windows, but should be easy to modify.

# Usage:
# 'make clean' removes every binary leaving the sources and external libs.
# 'make' compiles the system.
# 'make reduced' compiles a reduced version not including nor Windows nor
# logging
# 'make javadoc' creates JavaDoc
# 'make jdepend' creates a JDepend test

# Modify the following vars for your system

# Java Development Kit folder
JAVA_HOME=/usr
# Temporal folder for compiling classes
ODIR=build/classes
# Folder for the final distribution
DDIR=dist
# Folder of JavaDoc
DOCDIR=$(DDIR)/doc
# Source folder
SDIR=src
# ClassPath (includes libraries)
CPATH=$(SDIR):libs/log4j.jar:libs/slp.jar:libs/avetanaBT.jar:libs/klingslib-1.0.jar:libs/iaik_jce_full.jar
# Source files encoding
ENCODING=iso-8859-1
# Files to compile (they must make use of the whole system!)
OBJ=src/org/msd/proxy/*.java src/org/msd/cache/*.java \
    src/org/msd/*.java src/org/msd/comm/*.java src/org/msd/election/*.java
# Packages of the system
PACKAGES=org.msd org.msd.proxy org.msd.cache org.msd.comm
# Final file name
FJAR=msd.jar

# JDepend configuration
# JDepend is a tool to evaluate the code generating design
# qualty metrics for each Java package. It is not necessary to compile nor
# run the code
# Path to the jdepend.jar
JDEPEND=/usr/share/java/jdepend.jar
# Output file for the jdepend tests
JDEPEND_FILE=jdepend.txt

# Compiles the system
all:
	make $(DDIR)/$(FJAR)

# Create the temporal directories of the system
directories:
	/bin/mkdir -p $(DDIR)
	/bin/mkdir -p $(ODIR)

# Compile the classes of the system
allClasses: directories
	$(JAVA_HOME)/bin/javac -encoding $(ENCODING) -classpath $(CPATH) -d $(ODIR) $(OBJ)

# Create Jar file (compiling) and move distribution file to dir
# Finally, give execution privileges to browserSD in dist
$(DDIR)/$(FJAR): allClasses
	cd $(ODIR) ; $(JAVA_HOME)/bin/jar cf $(FJAR) *
	/bin/mv $(ODIR)/$(FJAR) $(DDIR) 
	/bin/cp -r libs/* etc/* $(DDIR)
	/bin/chmod 744 $(DDIR)/browserSD

# Create the JavaDoc
javadoc: directories
	$(JAVA_HOME)/bin/javadoc -encoding $(ENCODING) -sourcepath $(SDIR) -d $(DOCDIR) $(PACKAGES)	

# Performs a JDepend test on the system classes
# JDepend is a tool to evaluate the code generating design
# qualty metrics for each Java package. It is not necessary to compile nor
# run the code
jdepend: clean allClasses
	$(JAVA_HOME)/bin/java -cp $(JDEPEND) jdepend.textui.JDepend -file $(JDEPEND_FILE) $(ODIR)

# Run tests with BeanShell
# Beanshell is a scripting tool reading real Java classes. It is not needed
# for running nor compiling the system, and it is just a testing facility
tester:
	cp bsh $(DDIR)/.
	cd $(DDIR); ./bsh 'log4j.jar:slp.jar:AvetanaBluetooth.jar:$(FJAR)'

# Make a reduced-version (no logging, no bluetooth) storing all classes in
# a single file able to run on Windows
reduced:
	. source -log
	rm -rf `find . -name CVS`
	make all
	mkdir -p reduced
	cp etc-reduced/* reduced/.
	cp dist/msd.jar reduced/.
	cp dist/slp.jar reduced/.
	cp -r dist/lang reduced/.
	cp -r dist/conf reduced/.
	cd reduced; jar xf msd.jar
	cd reduced; jar xf slp.jar
	cd reduced; rm *jar
	cd reduced; jar cfm msd.jar msd.mf com org secureMulticast lang conf
	cd reduced; rm -r org com secureMulticast conf/*properties lang msd.mf META-INF

# Delete not needed directories and archives
clean:
	/bin/rm -rf $(ODIR) $(DDIR) $(DOCDIR) $(JDEPEND_FILE) bak classes build
	/bin/rm -rf reduced
	rm -f `find . -name '*~'`
	. source known clean
