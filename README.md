#### Running application on the default port 8080
```shell script
    ./gradlew clean build
    java -jar ./build/libs/wallet-api-all.jar
```
#### Running application on a custom port
```shell script
    ./gradlew clean build
    java -jar -Dserver.port=9090 ./build/libs/wallet-api-all.jar
```
