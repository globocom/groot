# Groot Makefile
GROOT_VERSION ?= 2.0.8
VERSION=${GROOT_VERSION}
RPM_VER=${GROOT_VERSION}
RELEASE=1

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
        --rpm-rpmbuild-define '_binaries_in_noarch_packages_terminate_build 0' \
        -t rpm \
        -n "groot2" \
        -v ${RPM_VER} \
        --iteration ${RELEASE}.el7 \
        -a noarch \
        --rpm-os linux \
        --prefix /opt/groot2 \
        -m '<a-team@corp.globo.com>' \
        --vendor 'Globo.com' \
        --description 'Groot2 service' \
        --after-install scripts/postinstall \
        -f -p ../groot2-${RPM_VER}-${RELEASE}.el7.noarch.rpm lib conf logs scripts
