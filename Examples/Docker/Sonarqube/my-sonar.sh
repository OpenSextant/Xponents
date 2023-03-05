
PORT=9900
echo Using token '$SONAR_TOKEN'

pushd ./Xponents/Core
    mvn sonar:sonar \
      -Dsonar.sourceEncoding=UTF-8 \
      -Dsonar.projectKey=opensextant-xponents-core \
      -Dsonar.host.url=http://localhost:$PORT \
      -Dsonar.login=$SONAR_TOKEN \
      -Dsonar.inclusions="**/*.java"
    
popd
pushd ./Xponents
    mvn sonar:sonar \
      -Dsonar.sourceEncoding=UTF-8 \
      -Dsonar.projectKey=opensextant-xponents \
      -Dsonar.host.url=http://localhost:$PORT \
      -Dsonar.login=$SONAR_TOKEN \
      -Dsonar.inclusions="**/*.java"
      
popd
