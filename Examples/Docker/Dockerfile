FROM eclipse-temurin:21-jdk

ENV RELEASE_NAME=Xponents-3.8
ENV JAVA_XMS=3500m
ENV JAVA_XMX=3500m
ENV XPONENTS=/home/opensextant/Xponents

RUN apt-get update  && apt-get upgrade -y
RUN adduser opensextant --home /home/opensextant --disabled-password 
USER opensextant
WORKDIR $XPONENTS

# --------------------------
# Copy ./Xponents-3.x/<CONTENT> to target $XPONENTS/
#
COPY etc/ src/ lib/ script/  python/ xponents-solr/ VERSION.txt ./
RUN mkdir ./log

EXPOSE 8787 7000

ENTRYPOINT [ "./script/xlayer-docker.sh", "8787" ]

