# Groot Makefile
GROOT_VERSION ?= 0.0.1
VERSION=${GROOT_VERSION}
RPM_VER=${GROOT_VERSION}
RELEASE=17

PORT ?= 8090

.PHONY: all test clean run

groot: clean
	mvn package -DskipTests

test:
	mvn test

clean:
	mvn clean

run:
	java -Dserver.port=${PORT} -jar target/groot.jar

dist: groot
	type fpm > /dev/null 2>&1 && \
    cd target && \
    mkdir -p lib conf logs/tmp && \
    echo "#version ${VERSION}" > lib/VERSION && \
    git show --summary >> lib/VERSION && \
    cp -av ../dist/wrapper lib/ && \
    cp -v ../dist/wrapper.conf conf/ && \
    [ -f ../dist/logback.xml ] && cp -v ../dist/logback.xml conf/ || true && \
    cp -av ../dist/scripts . || true  && \
    cp -v groot.jar lib/ && \
    cp -av ../dist/initscript lib/wrapper/bin/ && \
    fpm -s dir \
        -t rpm \
        -n "groot" \
        -v ${RPM_VER} \
        --iteration ${RELEASE}.el7 \
        -a noarch \
        --rpm-os linux \
        --prefix /opt/groot \
        -m '<a-team@corp.globo.com>' \
        --vendor 'Globo.com' \
        --description 'Groot service' \
        --after-install scripts/postinstall \
        -f -p ../groot-${RPM_VER}-${RELEASE}.el7.noarch.rpm lib conf logs scripts
