# Project Description

This project was created as a Spike of a necessity to control processing volume when necessary.

This was necessary to keep healthy downstream systems that can have problems with high volume of requests.

This solution tries to implement the Leaky Bucket strategy in a distributed environment where we can have multiple instances.

# How to run

You only need a fully Docker Compose environment, and run the commands below.

1. Bring up the support tools:
    ```shell
    docker compose up -d
    ```

1. Populate a queue with messages to be consumed. Note.: You don't need to wait until all messages are sent.
    ```shell
    curl -X POST http://localhost:8081/messages/publish/1000
    ```
   OR
    ```shell
    ./send-1000-sqs-messages.sh
    ```

1. Start the services and watch the magic in front of your eyes! (Okay, I exaggerated...)
    ```shell
    docker compose --profile all up -d && docker compose logs -f app1 app2
    ```

# Changing the Limit
This solution has a feature to change dynamically the limit. To change it, run the command below with the number of records per hour to be processed.
```shell
curl -X POST http://localhost:8081/config/limit/7200
```

# Stopping Everything

Run the command below

```shell
docker compose --profile all down -v
```
