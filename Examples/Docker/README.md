Xponents Xlayer Docker Image
-------------------

**Build:**

Copy this Dockerfile to the Xponents ./dist folder and build the docker image with the Xponents-X.x build in that folder.


```
# This below produces a build of Xponents with Xlayer server scripts packed in.
#
cd Xponents/script
./dist.sh  build

# Stage the Docker image:
cd Xponents/
cp Examples/Docker/Dockerfile ./dist/

XPONENTS_VERSION=3.2
docker build . --tag opensextant:xponents-$XPONENTS_VERSION

```


**Run:**
Choose a port number -- XLAYER_PORT is the only argument to the internal "xlayer-docker.sh" script.
Leave off `--detach` if you want to see console.
Use `docker logs NAME` to see the console, if it was a detached run. NAME is the docker container "--name" argument.

```
docker run -p 8888:8888,7000:7000 -e XLAYER_PORT=8888 --name xponents--rm --detach  opensextant:xponents-3.2
```
