#!/bin/bash
# The first argument should be to lib blosc if it isn't installed globally.
export loc=$(realpath $1)
mvn clean
mvn -Djna.library.path="$loc" package
