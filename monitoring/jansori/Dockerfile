FROM rust:1.94-slim-bookworm AS build
RUN apt-get update \
    && apt-get install -y --no-install-recommends git ca-certificates \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /src
ARG JANSORI_COMMIT=9d2db8b882e1f0805ef2e90a79cf27d8a16c07a6
RUN git clone https://github.com/charsyam/jansori.git . \
    && git checkout --detach "$JANSORI_COMMIT"
RUN cargo build --release --locked

FROM debian:bookworm-slim
COPY --from=build /src/target/release/jansori /usr/local/bin/jansori
ENV JANSORI_BIND=0.0.0.0:8080
EXPOSE 8080
USER nobody
ENTRYPOINT ["jansori"]
