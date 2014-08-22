profilecloner
=============

JBoss AS 7 / WildFly / JBoss EAP 6  Profile (and more) Cloner - by Tom Fonteyne

Usage (all one line):
~~~
 java -cp $JBOSS_HOME/bin/client/jboss-cli-client.jar:profilecloner.jar
        org.jboss.tfonteyne.profilecloner.Main
        --controller=host --port=number --username=user --password=password --file=name
        rootelement from to [rootelement from to] ....  
~~~
 Where "rootelement from to" is for example:
~~~
      socket-binding-group full-ha-sockets full-ha-sockets-copy 
      profile full-ha full-ha-copy
~~~
 Each set will generate a batch/run-batch. It is recommended to clone the profile last.
 The names from/to can be equal if you want to execute the script on a different domain controller.

Defaults:
~~~
  controller: localhost
  port      : 9999
  file      : to the console
~~~
 For secure connections you need to set the trust and keystores as system properties

Notes:
- exporting server-groups will contain the deployments. Filter these out using standard tools like grep/awk/etc...
- there was a question to export individual subsystems. Same thing, very easy to filter them with grep

