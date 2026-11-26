FROM maven:3.9.9-eclipse-temurin-21-alpine@sha256:5a8b906c4faa11d33f6c74758f67db8eac25441e14b0729f6d50ff78992be58a AS build
WORKDIR /build
COPY pom.xml ./
COPY contracts ./contracts
COPY src ./src
# Unit tests only: the *IT tests need Docker (Testcontainers) and run in `mvn verify` locally and in CI (spec D1, #214 D17).
# Maven dependencies stay in a cache kept between builds (#219), one per service so no two Maven processes write
# the same files; CI fills it beforehand. Maven's not-found markers are not kept, or a dependency that failed to
# resolve once would not be tried again for 24 h. Maven also retries Maven Central for longer (429 Too Many Requests)
RUN --mount=type=cache,id=farol-maven-events,target=/root/.m2/repository,sharing=locked \
    find /root/.m2/repository -name '*.lastUpdated' -delete && \
    mvn --batch-mode --no-transfer-progress -Daether.connector.http.retryHandler.count=10 package

FROM eclipse-temurin:21-jre-alpine@sha256:82cf3d9ccbe1ed46252008d51b2f2329e6b9e8a2f4394c4bebefc3ea7f4b1372
WORKDIR /app
COPY --from=build /build/target/events.jar ./app.jar
# The map directory has to exist in the image, and belong to the service's user: Docker copies the ownership of the
# mount point into a new named volume, and a volume created from a missing path would be root-owned 0755, which the
# unprivileged service cannot write (review of #253). A volume left by an earlier `up` keeps its owner, so `down -v`.
RUN mkdir -p /var/lib/farol/maps && chown 10001:10001 /var/lib/farol/maps
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
