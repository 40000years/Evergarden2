#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mvn -pl advance-magic,evergarden -am package -DskipTests
printf 'Built JARs in advance-magic/target and evergarden/target. No server deployment performed.\n'
