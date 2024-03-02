FROM clojure:temurin-21-tools-deps-bullseye-slim

ARG TAG=unstable

RUN apt-get -yqq update && \
    apt-get -yqq upgrade && \
    apt-get -yqq install sudo && \
    apt-get -yqq install git curl && \
    rm -rf /var/lib/apt/lists/*

RUN curl -sLO https://raw.githubusercontent.com/babashka/babashka/master/install && \
    chmod +x install && \
    ./install && \
    rm ./install


# Image will default non-root user: deps-try-user
RUN groupadd deps-try-user && \
    useradd --create-home --shell /bin/bash --gid deps-try-user deps-try-user && \
    usermod -a -G sudo deps-try-user && \
    echo 'ALL ALL = (ALL) NOPASSWD: ALL' >> /etc/sudoers && \
    echo 'deps-try-user:secret' | chpasswd
ENV HOME=/home/deps-try-user

USER deps-try-user
WORKDIR /home/deps-try-user

RUN curl -sLO https://github.com/eval/deps-try/releases/download/${TAG}/deps-try-bb.jar && \
  bb deps-try-bb.jar -P

ENTRYPOINT ["/usr/local/bin/bb", "deps-try-bb.jar"]