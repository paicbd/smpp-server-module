# SMPP Server Module

The `smpp-server-module` is a core component in the SMSC Short Message Service Center (SMSC) environment, designed to handle communication with external systems or service providers using the Short Message Peer-to-Peer protocol (SMPP). This service efficiently processes message deliveries, retries, and delivery reports, integrating with Redis for queue management and offering WebSocket support for real-time interaction. Additionally, the module supports JMX monitoring, allowing administrators to track server performance and status.

## Key Features

- **SMPP Protocol Handling**: Manages incoming and outgoing messages via the SMPP protocol.
- **Redis Integration**: Efficiently stores and processes message queues and service provider configurations.
- **WebSocket Support**: Enables real-time communication and monitoring via WebSocket.
- **Thread Pool Management**: Configurable thread pool to manage concurrent sessions and message processing.
- **JMX Monitoring**: Provides detailed performance and health monitoring using JMX.

## Key Configurable Variables

### JVM Settings

- `JVM_XMS`: Minimum heap size for the Java Virtual Machine. Default: `512 MB` (`-Xms512m`).
- `JVM_XMX`: Maximum heap size for the Java Virtual Machine. Default: `1024 MB` (`-Xmx1024m`).

### Application Settings

- `APPLICATION_NAME`: The name of the SMPP server instance. Default: `"smpp-server-instance-01"`.
- `SERVER_IP`: IP address of the server. Default: `"127.0.0.1"`.
- `SERVER_PORT`: Port number the service will use. Default: `9988`.
- `INITIAL_STATUS`: Initial status of the SMPP server on startup. Default: `"STARTED"`.
- `PROTOCOL`: Protocol used by the service. Default: `"SMPP"`.

### Redis Configuration

- `CLUSTER_NODES`: Redis cluster nodes for message and configuration management. Default: `"localhost:7000,localhost:7001,...,localhost:7009"`.
- `DELIVER_SM_QUEUE`: Queue for storing SMPP delivery reports. Default: `"smpp_dlr"`.

### Thread Pool Settings

- `THREAD_POOL_MAX_TOTAL`: Maximum number of threads in the pool. Default: `60`.
- `THREAD_POOL_MAX_IDLE`: Maximum number of idle threads. Default: `50`.
- `THREAD_POOL_MIN_IDLE`: Minimum number of idle threads. Default: `10`.
- `THREAD_POOL_BLOCK_WHEN_EXHAUSTED`: Blocks further operations when the thread pool is exhausted. Default: `true`.

### SMPP Server Configuration

- `SMPP_SERVER_IP`: IP address for the SMPP server. Default: `"127.0.0.1"`.
- `SMPP_SERVER_PORT`: Port number for the SMPP server. Default: `2776`.
- `SMPP_SERVER_TRANSACTION_TIMER`: Timeout for SMPP transactions in milliseconds. Default: `5000`.
- `SMPP_SERVER_WAIT_FOR_BIND`: Time to wait for binding requests in milliseconds. Default: `5000`.
- `SMPP_SERVER_PROCESSOR_DEGREE`: Number of processors to handle SMPP transactions. Default: `15`.
- `SMPP_SERVER_QUEUE_CAPACITY`: Maximum capacity for the SMPP message queue. Default: `1000`.

### WebSocket Configuration

- `WEBSOCKET_SERVER_ENABLED`: Enables WebSocket for real-time communication. Default: `true`.
- `WEBSOCKET_SERVER_HOST`: WebSocket server host address. Default: `"{WEBSOCKET_SERVER_HOST}"`.
- `WEBSOCKET_SERVER_PORT`: WebSocket server port. Default: `9000`.
- `WEBSOCKET_SERVER_PATH`: WebSocket server path. Default: `"/ws"`.
- `WEBSOCKET_SERVER_RETRY_INTERVAL`: Retry interval for WebSocket connections in seconds. Default: `10`.

### Service Provider and SMPP Configuration

- `SERVICE_PROVIDERS_HASH_NAME`: Redis hash for service provider data. Default: `"service_providers"`.
- `SMPP_SERVER_CONFIGURATIONS_HASH_NAME`: Redis hash for server configurations. Default: `"configurations"`.
- `SMPP_SERVER_KEY_NAME`: Key name for SMPP server configurations in Redis. Default: `"smpp_server"`.

### JMX Monitoring

- `ENABLE_JMX`: Enables JMX for monitoring. Default: `"true"`.
- `IP_JMX`: JMX server IP address. Default: `"127.0.0.1"`.
- `JMX_PORT`: JMX server port. Default: `9012`.

### Miscellaneous Settings

- `CONSUMER_WORKERS`: Number of workers for consuming from the SMPP queues. Default: `11`.
- `CONSUMER_BATCH_SIZE`: Number of messages processed per batch by consumers. Default: `10000`.
- `CONSUMER_SCHEDULER`: Interval for scheduling message consumption (in milliseconds). Default: `1000`.
- `REDIS_PRE_MESSAGE_LIST`: Redis list for pre-processed messages. Default: `"preMessage"`.

## Example Docker Compose Configuration

```yaml
services:
  smpp-server-module:
    image: paic/smpp-server-module:latest
    ulimits:
      nofile:
        soft: 1000000
        hard: 1000000
    environment:
      JVM_XMS: "-Xms512m"
      JVM_XMX: "-Xmx1024m"
      APPLICATION_NAME: "smpp-server-instance-01"
      SERVER_IP: "127.0.0.1"
      SERVER_PORT: "9988"
      INITIAL_STATUS: "STARTED"
      PROTOCOL: "SMPP"
      SCHEME: ""
      RATING_REQUEST_API_KEY: "{RATING_REQUEST_API_KEY}"
      # Redis Cluster and queues
      CLUSTER_NODES: "localhost:7000,localhost:7001,localhost:7002,localhost:7003,localhost:7004,localhost:7005,localhost:7006,localhost:7007,localhost:7008,localhost:7009"
      THREAD_POOL_MAX_TOTAL: 60
      THREAD_POOL_MAX_IDLE: 50
      THREAD_POOL_MIN_IDLE: 10
      THREAD_POOL_BLOCK_WHEN_EXHAUSTED: true
      DELIVER_SM_QUEUE: "smpp_dlr"
      CONSUMER_WORKERS: 11
      CONSUMER_BATCH_SIZE: 10000
      CONSUMER_SCHEDULER: 1000
      # SMPP server configurations
      SMPP_SERVER_IP: "127.0.0.1"
      SMPP_SERVER_PORT: 2776
      SMPP_SERVER_TRANSACTION_TIMER: 5000
      SMPP_SERVER_WAIT_FOR_BIND: 5000
      SMPP_SERVER_PROCESSOR_DEGREE: 15
      SMPP_SERVER_QUEUE_CAPACITY: 1000
      # Services Providers Configurations
      SERVICE_PROVIDERS_HASH_NAME: "service_providers"
      # WebSocket server configurations
      WEBSOCKET_SERVER_ENABLED: true
      WEBSOCKET_SERVER_HOST: "{WEBSOCKET_SERVER_HOST}"
      WEBSOCKET_SERVER_PORT: 9000
      WEBSOCKET_SERVER_PATH: "/ws"
      WEBSOCKET_SERVER_RETRY_INTERVAL: 10
      WEBSOCKET_HEADER_NAME: "Authorization"
      WEBSOCKET_HEADER_VALUE: "{WEBSOCKET_HEADER_VALUE}"
      # Configuration for SmppServer
      SMPP_SERVER_CONFIGURATIONS_HASH_NAME: "configurations"
      SMPP_SERVER_KEY_NAME: "smpp_server"
      SMPP_SERVER_GENERAL_SETTINGS_HASH: "general_settings"
      SMPP_SERVER_GENERAL_SETTINGS_KEY: "smpp_http"
      # Management
      ENDPOINTS_WEB_EXPOSURE_INCLUDE: "loggers"
      ENDPOINT_LOGGERS_ENABLED: true
      # Configuration for the virtual threads
      THREADS_VIRTUAL_ENABLED: true
      # Process message
      REDIS_PRE_MESSAGE_LIST: "preMessage"
      # JMX Configuration
      ENABLE_JMX: "true"
      IP_JMX: "127.0.0.1"
      JMX_PORT: "9012"
    volumes:
      - /opt/paic/smsc-docker/smpp/smpp-server-module-docker/resources/conf/logback.xml:/opt/paic/SMPP_SERVER_MODULE/conf/logback.xml
    network_mode: host
