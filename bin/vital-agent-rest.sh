#!/bin/sh

export VITAL_HOME=/home/centos/vital-install

appHome=/home/centos/agentshop-ai-app

cd $appHome

java -jar target/agentshop-ai-app-0.1.0-fat.jar conf/agentshop-ai-app.config > nohup.out 2>&1

