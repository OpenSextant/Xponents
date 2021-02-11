FROM adoptopenjdk:12-jdk-hotspot

ENV XLAYER_PORT=8787
ENV RELEASE_NAME=Xponents-3.3
ENV JAVA_XMS=1000m
ENV JAVA_XMX=1000m

RUN adduser --system --group --uid 110 opensextant

ENV XPONENTS=/home/opensextant/Xponents

COPY ./ $XPONENTS/

WORKDIR $XPONENTS
EXPOSE $XLAYER_PORT 7000

ENTRYPOINT ./script/xlayer-docker.sh $XLAYER_PORT
