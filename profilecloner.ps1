echo "Using:"
echo "JAVA_HOME=$env:JAVA_HOME"
echo "JBOSS_HOME=$env:JBOSS_HOME"
echo "Starting profilecloner"

$CLASSPATH = "$env:JBOSS_HOME\bin\client\jboss-cli-client.jar;./profilecloner.jar"

java -cp $CLASSPATH org.jboss.tfonteyne.profilecloner.Main $args