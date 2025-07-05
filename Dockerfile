# Build container

FROM --platform=$BUILDPLATFORM eclipse-temurin:21-alpine AS build
ARG TARGETOS
ARG TARGETARCH
# Add tools to run gradle wrapper
RUN apk add --no-cache bash unzip coreutils

WORKDIR /app

COPY . .

RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

RUN ./gradlew build --no-daemon

# Run container

FROM eclipse-temurin:21-alpine AS runtime

WORKDIR /opt/votl

ARG UID=10101
RUN adduser \
    --disabled-password \
    --gecos "" \
    --uid "${UID}" \
    votl && \
    chown votl:votl -R /opt/votl && \
    chmod u+w /opt/votl && \
    chmod 0755 -R /opt/votl

USER votl

COPY --from=build /app/VOTL.jar /bin/

CMD ["java","-jar","/bin/VOTL.jar"]