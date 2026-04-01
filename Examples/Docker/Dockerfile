FROM eclipse-temurin:25-jdk

ENV RELEASE_NAME=Xponents-3.8
ENV JAVA_XMS=3500m
ENV JAVA_XMX=3500m
ENV XPONENTS=/home/opensextant/Xponents

RUN apt-get update  && apt-get upgrade -y
RUN adduser --disabled-login --uid 18787 --home /home/opensextant --disabled-password  opensextant

USER opensextant
WORKDIR /home/opensextant/Xponents

# --------------------------
# Copy ./Xponents-3.x/<CONTENT> to target $XPONENTS/
#
COPY etc etc
COPY src src
COPY lib lib
COPY script script
COPY LICENSE NOTICE VERSION.txt ./
COPY --chown=18787 xponents-solr ./xponents-solr
RUN mkdir -p log


EXPOSE 8787 7000

ENTRYPOINT [ "./script/xlayer-docker.sh", "8787" ]

