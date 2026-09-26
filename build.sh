#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mvn -pl afterdeath,advance-magic,evergarden,7sins -am package -DskipTests
mkdir -p dist
cp afterdeath/target/afterdeath.jar dist/afterdeath.jar
cp advance-magic/target/advance-magic.jar dist/advance-magic.jar
cp evergarden/target/evergarden.jar dist/evergarden.jar
cp 7sins/target/7sins.jar dist/7sins.jar
printf 'Built plugin JARs in dist/. No server deployment performed.\n'
