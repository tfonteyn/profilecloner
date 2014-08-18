#!/bin/bash

CLONER=$(dirname "$0")/profilecloner.jar

java -cp $JBOSS_HOME/bin/client/jboss-cli-client.jar:$CLONER org.jboss.tfonteyne.profilecloner.Main @$

