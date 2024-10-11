# HTTP Client Module

The `http-client-module` is an essential component the Short Message Service Center (SMSC) environment. It manages outbound HTTP communication, and handles retries for message delivery. This service integrates tightly with Redis for managing message queues and interacting with gateways, and routing rules, ensuring efficient message processing and delivery.

## Key Responsibilities

- **Outbound Message Handling**: Sends SMS messages or requests via HTTP to external gateways.
- **Queue Management**: Utilizes Redis queues to manage message submission and retries for undelivered messages.
- **WebSocket Communication**: Enables real-time communication via WebSocket for status updates or control messages.
- **Virtual Threads**: Utilizes virtual threads to optimize concurrency and performance for handling high-throughput SMS traffic.
- **DLR (Delivery Receipt) Processing**: Manages pre-delivery lists for DLRs to ensure reliable message processing and delivery.
- **JMX Monitoring**: Enables JMX for monitoring the service, providing insights into performance, thread usage, and other metrics.

## Key Configurable Variables

### JVM Settings
- **`JVM_XMS`**: Sets the initial heap size for the JVM (default: `-Xms512m`).
- **`JVM_XMX`**: Sets the maximum heap size for the JVM (default: `-Xmx1024m`).

### Server Settings
- **`SERVER_PORT`**: Port on which the HTTP client service runs (default: `7118`).
- **`APPLICATION_NAME`**: Name of the client application instance (default: `http-client-module`).

### Redis Cluster Settings
- **`CLUSTER_NODES`**: Specifies the list of Redis nodes in the cluster (e.g., `localhost:7000,localhost:7001,...,localhost:7009`).

### Thread Pool Settings
- **`THREAD_POOL_MAX_TOTAL`**: Maximum number of threads available for the service (default: `60`).
- **`THREAD_POOL_MAX_IDLE`**: Maximum number of idle threads (default: `50`).
- **`THREAD_POOL_MIN_IDLE`**: Minimum number of idle threads (default: `10`).
- **`THREAD_POOL_BLOCK_WHEN_EXHAUSTED`**: Blocks when no threads are available (default: `true`).

### SMPP Settings
- **`KEY_GATEWAYS`**: Redis key for storing gateway configurations.
- **`KEY_SERVICE_PROVIDERS`**: Redis key for storing service provider configurations.
- **`KEY_ERROR_CODE_MAPPING`**: Redis key for mapping error codes.
- **`KEY_ROUTING_RULES`**: Redis key for storing routing rules.

### Message Queue Settings
- **`SUBMIT_SM_RESULTS`**: Redis queue for storing the results of submitted messages (default: `http_submit_sm_result`).
- **`RETRY_MESSAGES_QUEUE`**: Redis queue for retrying undelivered messages (default: `sms_retry`).

### WebSocket Settings
- **`WEBSOCKET_SERVER_ENABLED`**: Enables WebSocket server (default: `true`).
- **`WEBSOCKET_SERVER_HOST`**: Host IP for the WebSocket server (default: `127.0.0.1`).
- **`WEBSOCKET_SERVER_PORT`**: Port for WebSocket communication (default: `9087`).
- **`WEBSOCKET_SERVER_PATH`**: Path for WebSocket communication (default: `/ws`).
- **`WEBSOCKET_SERVER_RETRY_INTERVAL`**: Retry interval for WebSocket reconnections (default: `10` seconds).
- **`WEBSOCKET_HEADER_NAME`**: WebSocket header used for authentication (default: `Authorization`).
- **`WEBSOCKET_HEADER_VALUE`**: Authorization token for WebSocket communication.

### HTTP/2 Settings
- **`APPLICATION_USE_HTTP2`**: Enables HTTP/2 support for faster and more secure communication (default: `true`).

### Virtual Threads
- **`THREADS_VIRTUAL_ENABLED`**: Enables virtual threads for concurrency optimization (default: `true`).

### Worker and Gateway Settings
- **`WORKS_PER_GATEWAY`**: Number of workers per gateway (default: `10`).
- **`RECORDS_PER_GATEWAY`**: Number of records to process per gateway (default: `10000`).
- **`JOB_EXECUTE_EVERY`**: Interval in milliseconds for executing jobs (default: `1000`).

### DLR (Delivery Receipts) Settings
- **`PRE_DELIVER_LIST`**: List for pre-delivery message processing (default: `preDeliver`).

### JMX Monitoring
- **`ENABLE_JMX`**: Enables JMX for service monitoring and management (default: `true`).
- **`IP_JMX`**: IP address for JMX communication (default: `127.0.0.1`).
- **`JMX_PORT`**: Port for JMX access (default: `9010`).

## Docker Compose Example

Below is an example of a Docker Compose configuration for the `http-client-module`:

```yaml
version: '3.8'

services:
  http-client-module:
    image: paic/http-client-module:latest
    ulimits:
      nofile:
        soft: 1000000
        hard: 1000000
    environment:
      JVM_XMS: "-Xms512m"
      JVM_XMX: "-Xmx1024m"
      SERVER_PORT: 7118
      APPLICATION_NAME: "http-client-module"
      CLUSTER_NODES: "localhost:7000,localhost:7001,localhost:7002,localhost:7003,localhost:7004,localhost:7005,localhost:7006,localhost:7007,localhost:7008,localhost:7009"
      THREAD_POOL_MAX_TOTAL: 60
      THREAD_POOL_MAX_IDLE: 50
      THREAD_POOL_MIN_IDLE: 10
      THREAD_POOL_BLOCK_WHEN_EXHAUSTED: true
      KEY_GATEWAYS: "gateways"
      KEY_SERVICE_PROVIDERS: "service_providers"
      KEY_ERROR_CODE_MAPPING: "error_code_mapping"
      KEY_ROUTING_RULES: "routing_rules"
      SUBMIT_SM_RESULTS: "http_submit_sm_result"
      WEBSOCKET_SERVER_ENABLED: true
      WEBSOCKET_SERVER_HOST: "127.0.0.1"
      WEBSOCKET_SERVER_PORT: 9087
      WEBSOCKET_SERVER_PATH: "/ws"
      WEBSOCKET_SERVER_RETRY_INTERVAL: 10
      WEBSOCKET_HEADER_NAME: "Authorization"
      WEBSOCKET_HEADER_VALUE: "{WEBSOCKET_HEADER_VALUE}"
      APPLICATION_USE_HTTP2: true
      THREADS_VIRTUAL_ENABLED: true
      PRE_DELIVER_LIST: "preDeliver"
      WORKS_PER_GATEWAY: 10
      RECORDS_PER_GATEWAY: 10000
      JOB_EXECUTE_EVERY: 1000
      RETRY_MESSAGES_QUEUE: "sms_retry"
      ENABLE_JMX: "true"
      IP_JMX: "127.0.0.1"
      JMX_PORT: "9010"
    volumes:
      - /opt/paic/smsc-docker/http/http-client-module-docker/resources/conf/logback.xml:/opt/paic/HTTP_CLIENT_MODULE/conf/logback.xml
    network_mode: host
