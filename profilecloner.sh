#!/bin/bash

CLONER=./target/profilecloner-2014-08-14.jar

java -cp $JBOSS_HOME/bin/client/jboss-cli-client.jar:$CLONER org.jboss.tfonteyne.profilecloner.Main @$

