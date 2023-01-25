#!/bin/bash
set -xo
set -e
echo $(pwd)
docker run -p "8089:80" --rm -v $(pwd):/var/www/html mattrayner/lamp:latest-1804