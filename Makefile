# Grou Makefile

.PHONY: all test clean run

groot: clean
	mvn package -DskipTests

test:
	mvn test

clean:
	mvn clean

run:
	sleep 5; java -jar target/groot.jar -Dserver.port=8090
