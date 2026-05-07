#!/usr/bin/env bash
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT/backend"
echo "Starting backend on http://localhost:8080..."
export MAVEN_OPTS="-Djdk.tls.client.protocols=TLSv1.2,TLSv1.3 ${MAVEN_OPTS:-}"
./mvnw spring-boot:run -Dmaven.resources.skip=true
