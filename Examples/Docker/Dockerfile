FROM eclipse-temurin:18-jdk

ENV XLAYER_PORT=8787
ENV RELEASE_NAME=Xponents-3.5
ENV JAVA_XMS=3500m
ENV JAVA_XMX=3500m
ENV XPONENTS=/home/opensextant/Xponents

RUN adduser opensextant --home /home/opensextant --disabled-password 
USER opensextant

# --------------------------
# Copy ./Xponents-3.x/<CONTENT> to target $XPONENTS/
#
COPY --chown=opensextant:opensextant . $XPONENTS/


WORKDIR $XPONENTS
EXPOSE $XLAYER_PORT 7000

ENTRYPOINT ./script/xlayer-docker.sh $XLAYER_PORT

