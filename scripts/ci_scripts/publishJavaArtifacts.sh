#!/bin/bash -e
#
# Copyright IBM Corp All Rights Reserved
#
# SPDX-License-Identifier: Apache-2.0
#
# Get the version from pom.xml
cd $WORKSPACE/$BASE_DIR
version_check=$(cat pom.xml | grep "version" | grep -c "SNAPSHOT")
#version=$(xmllint --xpath "//*[local-name()='project']/*[local-name()='version']/text()" pom.xml)
version=1.4.0-SNAPSHOT
if [ $version_check -gt 0 ]; then
    # Publish gateway-java jar files to nexus
    for artifacts in $version $version-javadoc; do
        echo "Pushing fabric-gateway-java-$artifacts.jar to Nexus.."
        mvn org.apache.maven.plugins:maven-deploy-plugin:deploy-file \
          -DupdateReleaseInfo=true \
          -Dfile=$WORKSPACE/$BASE_DIR/target/fabric-gateway-java-$artifacts.jar \
          -DrepositoryId=hyperledger-snapshots \
          -Durl=https://nexus.hyperledger.org/content/repositories/snapshots/ \
          -DgroupId=org.hyperledger.fabric-gateway-java \
          -Dversion= $version \
          -DartifactId=fabric-gateway-java \
          -DgeneratePom=true \
          -DuniqueVersion=false \
          -Dpackaging=jar \
          -gs $GLOBAL_SETTINGS_FILE -s $SETTINGS_FILE
          echo "========> DONE <======="
    done
else
    # Publish gateway-java jar files to nexus
    echo "Pushing fabric-gateway-java-$version-SNAPSHOT.jar to Nexus releases.."
    mvn org.apache.maven.plugins:maven-deploy-plugin:deploy-file \
      -DupdateReleaseInfo=true \
      -Dfile=$WORKSPACE/$BASE_DIR/target/fabric-gateway-java-$version.jar \
      -DrepositoryId=hyperledger-releases \
      -Durl=https://nexus.hyperledger.org/content/repositories/releases/ \
      -DgroupId=org.hyperledger.fabric-gateway-java \
      -Dversion= $version\
      -DartifactId=fabric-gateway-java \
      -DgeneratePom=true \
      -DuniqueVersion=false \
      -Dpackaging=jar \
      -gs $GLOBAL_SETTINGS_FILE -s $SETTINGS_FILE
      echo "========> DONE <======="
fi
