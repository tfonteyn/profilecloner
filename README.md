profilecloner
=============

JBoss AS 7 / EAP  Profile (and more) Cloner - by Tom Fonteyne

Usage:
 java -cp $JBOSS_HOME/bin/client/jboss-cli-client.jar:profilecloner.jar org.jboss.tfonteyne.profilecloner.Main --controller=host
        --username=user --password=password --port=number  --file=name rootelement from to rootelement from to ....  

 Where "rootelement from to" is for example:
      socket-binding-group from-group to-group profile fromprofile toprofile  ....

 Each set will generate a batch/run-batch. It is recommended to clone the profile last.
 The names from/to can be equal if you want to execute the script on a different domain.

Defaults:
  controller: localhost
  port      : 9999
  file      : to the console

 Secure connections need:
    java -Djavax.net.ssl.trustStore=/path/to/store.jks -Djavax.net.ssl.trustStorePassword=password  ...

