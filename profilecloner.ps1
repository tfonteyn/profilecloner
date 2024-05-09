Write-Host "Using:"
Write-Host "JAVA_HOME=$env:JAVA_HOME"
Write-Host "JBOSS_HOME=$env:JBOSS_HOME"
Write-Host "Starting profilecloner"

$CLASSPATH = "$env:JBOSS_HOME\bin\client\jboss-cli-client.jar;./profilecloner.jar"

java -cp $CLASSPATH org.jboss.tfonteyne.profilecloner.Main $args