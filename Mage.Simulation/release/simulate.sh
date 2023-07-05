#!/usr/bin/env bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
java -Dlog4j.configurationFile=config/log4j2.xml -jar $DIR/lib/mage-simulation.jar "$@"