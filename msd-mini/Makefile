# Makefile to compile the system.
# GNUMake in *nix is needed. Not tested with other flavours of Make. This
# file doesn't work in Windows, but should be easy to modify.

# Usage:
# 'make clean' removes every binary leaving the sources and external libs.
# 'make' compiles the whole system. (J2SE 1.4)
# 'make mini' compiles the mini version (J2ME MIDP2.0, CLDC1.0 and JSR82)
# 'make medium' compiles the medium version (J2ME PP1.0)
# 'make javadoc' creates the JavaDoc

# Modify the following vars for your system

# Java Development Kit folders
# SE
JDK_HOME=/usr
# J2ME MIDP 2.0 and CLDC1.0
J2ME_HOME=/home/juanvi/javalibs/WTK2.2
# J2ME PP 1.0
J2MEPP_HOME=/home/juanvi/javalibs/j2me-pp1.0
# Programs
JAVAC=$(JDK_HOME)/bin/javac
JAR=$(JDK_HOME)/bin/jar
PREVERIFY=$(J2ME_HOME)/bin/preverify
JAVADOC=$(JDK_HOME)/bin/javadoc
# Temporal folder for compiling classes
ODIR=build/classes
ODIR_MINI=build/classes-mini
ODIR_MEDIUM=build/classes-medium
# Folder for the final distribution
DDIR=dist
# Directory for docs
DOCDIR=$(DDIR)/doc
# The directory for resources
ETCDIR=etc
ETCDIR_MINI=etc
ETCDIR_MEDIUM=etc-medium
# Source folder
SDIR=src
# ClassPath and BootClassPath
CPATH=$(SDIR):libs/AvetanaBluetooth.jar
BCPATH_MINI=$(J2ME_HOME)/lib/midpapi20.jar:$(J2ME_HOME)/lib/cldcapi10.jar
CPATH_MINI=$(J2ME_HOME)/lib/jsr082.jar:$(J2ME_HOME)/lib/mmapi.jar
CPATH_MEDIUM=libs/AvetanaBluetooth.jar:libs/jce1_2_2.jar
BCPATH_MEDIUM=$(J2MEPP_HOME)/btclasses.zip
# Source files encoding
ENCODING=iso-8859-1
# Files to compile (they must make use of the whole system!)
OBJ=$(SDIR)/org/msd/proxy/*.java $(SDIR)/org/msd/cache/*.java \
    $(SDIR)/org/msd/comm/*.java $(SDIR)/org/msd/MSDPrinter2.java \
    $(SDIR)/org/msd/ImageClient.java
OBJ_MINI=$(SDIR)/org/msd/MSDPrinter.java $(SDIR)/org/msd/ImageClientMini.java
OBJ_MEDIUM=$(SDIR)/org/msd/ImageClient2.java $(SDIR)/org/msd/ImageClient3.java 
# Packages of the system
PACKAGES=org.msd org.msd.proxy org.msd.cache org.msd.comm
# Final file name
FJAR=msd.jar
FJAR_MINI=msd-mini.jar
FJAR_MEDIUM=msd-medium.jar
# Other files
JAD_MINI=msd-mini.jad
MF_MINI=msd-mini.mf
MF_MEDIUM=msd-medium.mf
# Resources of the system (arguments to jar program)
RES_MINI=-C $(ETCDIR_MINI) logo.png -C $(ETCDIR_MINI) msd.png
RES_MEDIUM=

###############################################################
# Whole system

# Compile the system 
all: clean directories
	make $(DDIR)/$(FJAR)

# Compile the source
allClasses:
	$(JDK_HOME)/bin/javac -encoding $(ENCODING) -classpath $(CPATH) -d $(ODIR) $(OBJ)

# Create Jar file (compiling) and move distribution file to dir
# Finally, give execution privileges to browserSD in dist
$(DDIR)/$(FJAR): allClasses
	cp -r $(ETCDIR)/* $(ODIR)
	cd $(ODIR) ; $(JDK_HOME)/bin/jar cf $(FJAR) *
	/bin/mv $(ODIR)/$(FJAR) $(DDIR) 

##################################################################
# The mini version for MIDP2.0, CLDC1.0 and JSR82
mini: clean directories
	$(JAVAC) -g:none -encoding $(ENCODING) \
		-bootclasspath $(BCPATH_MINI) \
		-classpath $(CPATH_MINI) \
		-target 1.1 -source 1.3 \
		-sourcepath $(SDIR) \
		-d $(ODIR_MINI) $(OBJ_MINI)
	$(PREVERIFY) -classpath $(BCPATH_MINI):$(CPATH_MINI) -d $(ODIR_MINI) $(ODIR_MINI)
	$(JAR) cfm $(FJAR_MINI) $(MF_MINI) $(RES_MINI) -C $(ODIR_MINI) .
	/bin/cp $(MF_MINI) $(JAD_MINI)
	/bin/echo -n "MIDlet-Jar-Size: " >> $(JAD_MINI)
	/bin/echo `/usr/bin/stat --format="%s" $(FJAR_MINI)` >> $(JAD_MINI)
	/bin/echo "MIDlet-Jar-URL: " $(FJAR_MINI) >> $(JAD_MINI)
	/bin/mv $(FJAR_MINI) $(JAD_MINI) $(DDIR)/.

###################################################################
# The medium version for Personal Profile 1.0
medium: clean directories
	$(JAVAC) -encoding $(ENCODING) \
		-bootclasspath $(BCPATH_MEDIUM) -extdirs "" \
		-classpath $(CPATH_MEDIUM) \
		-target 1.1 -source 1.3 \
		-sourcepath $(SDIR) \
		-d $(ODIR_MEDIUM) $(OBJ_MEDIUM)
	$(JAR) cfm $(FJAR_MEDIUM) $(MF_MEDIUM) $(RES_MEDIUM) -C $(ODIR_MEDIUM) .
	/bin/mv $(FJAR_MEDIUM) $(DDIR)
	/bin/cp $(ETCDIR_MEDIUM)/msd-medium.lnk $(DDIR)
	/bin/cp $(ETCDIR_MEDIUM)/virtual-printer.lnk $(DDIR)

###################################################################
# Common tasks

# Create directories
directories:
	/bin/mkdir -p $(DDIR)
	/bin/mkdir -p $(ODIR) $(ODIR_MINI) $(ODIR_MEDIUM)

# Run tests with BeanShell
tester:
	cp bsh $(DDIR)/.
	cd $(DDIR); ./bsh '../libs/AvetanaBluetooth.jar:../libs/slp.jar:$(FJAR)'

# Create the JavaDoc
javadoc:
	/bin/mkdir -p $(DOCDIR)
	$(JAVADOC) -encoding $(ENCODING) -sourcepath $(SDIR) -d $(DOCDIR) $(PACKAGES)	

# Delete not needed directories and archives
clean:
	/bin/rm -rf $(ODIR) $(ODIR_MINI) $(ODIR_MEDIUM) $(DDIR) $(DOCDIR) \
		bak classes build
	/bin/rm -f `find . -name '*~'`
