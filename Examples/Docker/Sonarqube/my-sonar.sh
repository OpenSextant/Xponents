
PORT=9900
echo Using tokens set as vars XPTCORE and XPTSDK
echo "Run from dev folder that contains both Xponents and Xponents-Core"


pushd ./Xponents-Core

mvn clean verify org.sonarsource.scanner.maven:sonar-maven-plugin:sonar \
  -Dsonar.projectKey=Xponents-Core \
  -Dsonar.sourceEncoding=UTF-8 \
  -Dsonar.projectName='Xponents Core' \
  -Dsonar.host.url=http://localhost:9900 \
  -Dsonar.token=$XPTCORE \
  -Dsonar.inclusions="**/*.java"
popd


SONAR_TOKEN=sqp_b178932f97bc2e05b9cfb9e600d67d81eb6beafa


pushd ./Xponent

mvn clean verify org.sonarsource.scanner.maven:sonar-maven-plugin:sonar \
  -Dsonar.projectKey=Xponents \
  -Dsonar.sourceEncoding=UTF-8 \
  -Dsonar.projectName='Xponents' \
  -Dsonar.host.url=http://localhost:9900 \
  -Dsonar.token=$XPTSDK \
  -Dsonar.inclusions="**/*.java"

popd
