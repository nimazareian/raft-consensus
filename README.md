# CPSC416_Capstone
Distributed day trading server

## Running Simulated Environment with Docker

1. Install docker. You can find the instructions for your OS here: https://docs.docker.com/get-docker/
2. Build the repository (generates the jar file) with `./gradlew build`
3. Create an image with the jar file with the following command: `docker build -t raft-trading-server:latest .`
4. Run the simulated environment with 3 containers representing 3 separate raft nodes: `docker compose up -d`
   - Note that the `-d` flag is use for running in daemon thread. You definitely *WANT* to do this, especially on compose,
    since if you don't include it, the container(s) will be tied to the terminal session in which it was called, 
    and will stop if you hit ^C or close the window. 
