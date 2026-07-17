FROM clojure:temurin-26-tools-deps-bullseye-slim

ARG TAG=unstable

# a UTF-8 locale, or emoji and other non-ASCII output shows as '?'
# (recent JDKs and JLine versions respect the declared charset, and the
# image's default locale is POSIX i.e. ASCII)
ENV LANG=C.UTF-8 \
    LC_ALL=C.UTF-8

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

# NB the jar lives OUTSIDE the home-dir: a volume over /home/deps-try-user
# (see README) must only persist data — not pin the application version
RUN curl -sLo /opt/deps-try-bb.jar https://github.com/eval/deps-try/releases/download/${TAG}/deps-try-bb.jar

USER deps-try-user
WORKDIR /home/deps-try-user

RUN bb /opt/deps-try-bb.jar -P && bb /opt/deps-try-bb.jar recipes

ENTRYPOINT ["/usr/local/bin/bb", "/opt/deps-try-bb.jar"]
