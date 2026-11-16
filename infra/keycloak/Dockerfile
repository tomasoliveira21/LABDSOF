# Keycloak with its build step done in the image (ADR-006, #189): `start --optimized` then runs on a
# read-only file system like every other container. `start-dev` cannot, because it rebuilds into
# /opt/keycloak/lib/quarkus at every start. Change a build option here and rebuild the image.
FROM quay.io/keycloak/keycloak:26.8.2@sha256:c68e286cacd87b942ac1939a0a1481461109d6acd3a57dc496969a319a76bbf0
RUN /opt/keycloak/bin/kc.sh build --db=postgres --health-enabled=true
