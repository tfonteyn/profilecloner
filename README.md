profilecloner
=============
~~~
JBoss WildFly / JBoss EAP 6.1+ Profile (and more) Cloner.

Building:  it's 2025, no more maven xml-hell!
Use: "gradlew assemble" and the jar will be in "build/libs"

Tested with Wildfly 35.

Usage:
 java -cp $JBOSS_HOME/bin/client/jboss-cli-client.jar:profilecloner.jar
    org.jboss.tfonteyne.profilecloner.Main
    --controller=<host> --port=<number> --username=<user> --password=<password>
    --file=<name> --add-deployments=<true|false>
    /from=value destinationvalue [/from=value destinationvalue] ....

Options:
  --controller=<host> | -c <host>       : Defaults to the setting in jboss-cli.xml if you have one,
  --port=<port>                           or localhost and 9999 (wildfly:9990)
  --username=<user> | -u <user>         : When not set, $local authentication is attempted
  --password=<password> | -p <password>
  --file=<name> | -f <name>             : The resulting CLI commands will be written to the file
                                          If not set, they are output on the console
  --add-deployments=<true|false> | -ad  : By default cloning a server-group will skip the deployments
                                          If you first copy the content folder and clone the deployments,
                                          you can enable this

Examples for "/from=value destinationvalue":
  Domain mode:
    /socket-binding-group=full-ha-sockets full-ha-sockets-copy
    /profile=full-ha full-ha-copy
    /profile=full-ha/subsystem=web web

  Standalone server:
    /subsystem=security security
    profile
   The latter being a shortcut to clone all subsystems in individual batches

Each set will generate a batch/run-batch. It is recommended to clone the profile last
The names from/to can be equal if you want to execute the script on a different controller.

 Secure connections need:
    -Djavax.net.ssl.trustStore=/path/to/store.jks -Djavax.net.ssl.trustStorePassword=password

Note that EAP 6.0.x is **not** supported.
The cloner will break on the "module-option" entries inside "login-module" sections.
Workaround is to remove those manually before cloning.
The file "jboss-cli-client.jar" does also not exist in those versions,
instead take a look at jconsole.sh for the equivalent set of files you will need.
As EAP 6.0.x is very old now, you really should be upgrading anyhow.
~~~
