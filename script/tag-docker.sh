#!/bin/bash

IMG_VERSION=$1
IMG_TARGET=3.5
REPO=mubaldino

shift
CMD=$1

if [ -n "$IMG_VERSION" ] ; then
   docker tag opensextant:xponents-$IMG_VERSION  $REPO/opensextant:xponents-$IMG_TARGET
   docker tag opensextant:xponents-offline-$IMG_VERSION  $REPO/opensextant:xponents-offline-$IMG_TARGET
   # Final.
   docker tag $REPO/opensextant:xponents-$IMG_TARGET $REPO/opensextant:latest
fi

if [ -n "$CMD" ]; then
   docker push $REPO/opensextant:xponents-$IMG_TARGET
   docker push $REPO/opensextant:xponents-offline-$IMG_TARGET
   docker push $REPO/opensextant:latest
fi
