Xponents Xlayer Docker Image
-------------------

**Build:**

Move the Xponents distribution locally to this folder, the continue the build.


```
# This below produces a build of Xponents with Xlayer server scripts packed in.
#
cd Xponents/script
./dist.sh  build

cd ../Xlayer
./dist.sh 


# Stage the Docker image:
mv ../dist/Xponents-3.1 .

docker build . --tag xponents:xlayer311

```


**Run:**
Choose a port number -- XLAYER_PORT is the only argument to the internal "xlayer-docker.sh" script.
Leave off `--detach` if you want to see console.
Use `docker logs xlayer` to see the console, if it was a detached run.

```
docker run -p 8888:8888 -e XLAYER_PORT=8888 --name xlayer --rm --detach  xponents:xlayer311 
```
