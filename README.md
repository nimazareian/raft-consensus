# CPSC416_Capstone
Distributed day trading server

# Docker

You can create an image by locally building it with the following command:

`docker build --tag=app:latest`.

To run a test simulation, you can start a docker network that will run a node server in every container

`docker compose up -d`

Note that the `-d` flag is use for running in daemon thread. You definitely *WANT* to do this, especially on compose,
since if you don't include it, the container(s) will be tied to the terminal session in which it was called, 
and will stop if you hit ^C or close the window. 
