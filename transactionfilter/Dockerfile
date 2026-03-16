FROM rust:latest AS build
WORKDIR /build
RUN apt-get update && apt-get install -y cmake && rm -rf /var/lib/apt/lists/*
COPY Cargo.toml Cargo.lock ./
RUN mkdir src && echo "fn main() {}" > src/main.rs
RUN cargo build --release
RUN rm -rf src
COPY src/ ./src/
RUN touch src/main.rs && cargo build --release

FROM debian:trixie-slim
RUN apt-get update && apt-get install -y ca-certificates && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /build/target/release/transactionfilter .
ENTRYPOINT ["./transactionfilter"]
