#!/bin/bash

# ./versions/builds.json is in the following format:
# {
# "builds": {
#   "target1": {
#     "platforms: ["platform1", "platform2"],
#   },
#   "target2": {
#     "platforms: ["platform1", "platform2"]
#   }
# }
# it should be flattened to produce the following output:
# ["target1-platform1", "target1-platform2", "target2-platform1", "target2-platform2"]

TARGETS=$(jq -c '[.builds | to_entries[] | .value.platforms[] as $platform | "\(.key)-\($platform)"]' ./versions/builds.json)
echo "targets=$TARGETS" >> $GITHUB_OUTPUT
