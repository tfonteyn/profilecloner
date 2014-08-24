profilecloner
=============

JBoss AS 7 / WildFly / JBoss EAP 6  Profile (and more) Cloner - by Tom Fonteyne - version:2014-08-24
Usage:
 java -cp $JBOSS_HOME/bin/client/jboss-cli-client.jar:profilecloner.jar
    org.jboss.tfonteyne.profilecloner.Main
    --controller=<host> --port=<number> --username=<user> --password=<password>
    --file=<name> --add-deployments=<true|false>
    rootelement from to [rootelement from to] ....

Options:
  --controller=<host> | -c <host>       : Defaults to the setting in jboss-cli.xml if you have one,
  --port=<port>                           or localhost and 9999 (wildfly:9990)
  --username=<user> | -u <user>         : When not set, $local authentication is attempted
  --password=<password> | -p <password>
  --file=<name> | -f <name>             : The resulting CLI commands will be written to the file; if not set, they are output on the console
  --add-deployments=<true|false> | -ad  : By default cloning a server-group will skip the deployments
                                          If you first copy the content folder and clone the deployments, you can enable this

Examples for "rootelement from to":
  Domain mode:
    socket-binding-group full-ha-sockets full-ha-sockets-copy
    profile full-ha full-ha-copy

  Standalone server:
    subsystem security security
    profile
   The latter being a shortcut to clone all subsystems in individual batches

Each set will generate a batch/run-batch. It is recommended to clone the profile last
The names from/to can be equal if you want to execute the script on a different controller.

 Secure connections need:
    -Djavax.net.ssl.trustStore=/path/to/store.jks -Djavax.net.ssl.trustStorePassword=password

Notes:
- there was a question to export individual subsystems from a profile -> use grep

