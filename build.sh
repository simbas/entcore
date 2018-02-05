#!/bin/bash

if [ ! -e node_modules ]
then
  mkdir node_modules
fi

if [ -z ${USER_UID:+x} ]
then
  export USER_UID=1000
  export GROUP_GID=1000
fi

if [ "$1" == "clean" ]
then
  docker-compose run --rm -u "$USER_UID:$GROUP_GID" gradle gradle clean
fi
if [ $? -eq 0 ]
then
  docker-compose run --rm -u "$USER_UID:$GROUP_GID" node sh -c "npm install && node_modules/gulp/bin/gulp.js build" && docker-compose run --rm -u "$USER_UID:$GROUP_GID" gradle gradle shadowJar install publishToMavenLocal
fi
if [ $? -eq 0 ]
then
  if [ "$2" == "publish" ]
  then
    docker-compose run --rm -u "$USER_UID:$GROUP_GID" gradle gradle publish
  fi
fi

