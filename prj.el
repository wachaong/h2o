(jde-project-file-version "1.0")
(jde-set-variables
 '(jde-javadoc-gen-destination-directory "./doc")
 '(jde-run-working-directory "$DESK/h2o")
 '(jde-run-option-classpath (quote ("./target/classes" "./lib/javassist.jar" "./lib/hadoop/cdh4/hadoop-common.jar" "./lib/hadoop/cdh4/hadoop-auth.jar" "./lib/hadoop/cdh4/slf4j-api-1.6.1.jar" "./lib/hadoop/cdh4/slf4j-nop-1.6.1.jar" "./lib/hadoop/cdh4/hadoop-hdfs.jar" "./lib/hadoop/cdh4/protobuf-java-2.4.0a.jar" "./lib/apache/commons-codec-1.4.jar" "./lib/apache/commons-configuration-1.6.jar" "./lib/apache/commons-lang-2.4.jar" "./lib/apache/commons-logging-1.1.1.jar" "./lib/apache/httpclient-4.1.1.jar" "./lib/apache/httpcore-4.1.jar" "./lib/junit/junit-4.11.jar" "./lib/apache/guava-12.0.1.jar" "./lib/gson/gson-2.2.2.jar" "./lib/poi/poi-3.8-20120326.jar" "./lib/poi/poi-ooxml-3.8-20120326.jar" "./lib/poi/poi-ooxml-schemas-3.8-20120326.jar" "./lib/poi/dom4j-1.6.1.jar" "./lib/Jama/Jama.jar" "./lib/s3/aws-java-sdk-1.3.27.jar" "./lib/log4j/log4j-1.2.15.jar" "./lib/joda/joda-time-2.3.jar")))
 '(jde-run-executable-args nil)
 '(jde-run-option-debug nil)
 '(jde-run-option-vm-args (quote ("-XX:+PrintGC")))
 '(jde-compile-option-directory "./target/classes")
 '(jde-run-option-application-args (quote ("-beta" "-mainClass" "org.junit.runner.JUnitCore" "water.exec.DdplyTest")))
 '(jde-debugger (quote ("JDEbug")))
 '(jde-compile-option-source (quote ("1.6")))
 '(jde-compile-option-classpath (quote ("./target/classes" "./lib/javassist.jar" "./lib/hadoop/cdh4/hadoop-common.jar" "./lib/hadoop/cdh4/hadoop-auth.jar" "./lib/hadoop/cdh4/slf4j-api-1.6.1.jar" "./lib/hadoop/cdh4/slf4j-nop-1.6.1.jar" "./lib/hadoop/cdh4/hadoop-hdfs.jar" "./lib/hadoop/cdh4/protobuf-java-2.4.0a.jar" "./lib/apache/commons-codec-1.4.jar" "./lib/apache/commons-configuration-1.6.jar" "./lib/apache/commons-lang-2.4.jar" "./lib/apache/commons-logging-1.1.1.jar" "./lib/apache/httpclient-4.1.1.jar" "./lib/apache/httpcore-4.1.jar" "./lib/junit/junit-4.11.jar" "./lib/apache/guava-12.0.1.jar" "./lib/gson/gson-2.2.2.jar" "./lib/poi/poi-3.8-20120326.jar" "./lib/poi/poi-ooxml-3.8-20120326.jar" "./lib/poi/poi-ooxml-schemas-3.8-20120326.jar" "./lib/poi/dom4j-1.6.1.jar" "./lib/Jama/Jama.jar" "./lib/s3/aws-java-sdk-1.3.27.jar" "./lib/log4j/log4j-1.2.15.jar" "./lib/joda/joda-time-2.3.jar")))
 '(jde-db-option-classpath (quote ("$DESK/Dropbox/Sris and Cliff/H2O/classes")))
 '(jde-run-option-enable-assertions "Everywhere")
 '(jde-compile-option-sourcepath (quote ("./src")))
 '(jde-sourcepath (quote ("./src")))
 '(jde-run-option-hotspot-type (quote server))
 '(jde-compile-option-target (quote ("1.6")))
 '(jde-run-option-heap-size (quote ((2 . "gigabytes") (2 . "gigabytes"))))
 '(jde-run-application-class "water.Boot")
 '(jde-compile-option-debug (quote ("all" (t t t)))))
