# Accumulo Shared Off-Heap Block Cache

This project started by copying https://github.com/keith-turner/accumulo-ohc and replacing the `OHC` component with Redis.
By connecting to a Redis process on the server, multiple server processes (TabletServer's and ScanServer's) can use a 
shared cache and may make better use of memory on the server. Redis will need to be configured with `maxmemory` and a
`maxmemory-policy` to evict entries when the Redis server fills up its memory allocation. Also, you will need to set a
password using the `requirepass` Redis configuration property.

# Building
```
mvn clean package dependency:copy-dependencies
```

# Installation

After building, copy the jar files from the `target` and `target/dependency` directories to a directory on Accumulo's classpath.

# Configuration

Once the jar files are in the correct location, the following values will need to be put into the `accumulo.properties` file.

```
#
# This configuration creates an on-heap cache of 1MB and then uses
# Redis for the off-heap cache.
#
tserver.cache.manager.class=accumulo.ohc.RedisBackedBlockCacheManager
tserver.cache.config.redis.default.logInterval=180
tserver.cache.config.redis.data.on-heap.maximumWeight=1048576
tserver.cache.config.redis.data.off-heap.hostname=127.0.0.1
tserver.cache.config.redis.data.off-heap.port=6379
tserver.cache.config.redis.data.off-heap.password=<password>
tserver.cache.config.redis.index.on-heap.maximumWeight=1048576
tserver.cache.config.redis.index.off-heap.hostname=127.0.0.1
tserver.cache.config.redis.index.off-heap.port=6379
tserver.cache.config.redis.index.off-heap.password=<password>
tserver.cache.config.redis.summary.on-heap.maximumWeight=1048576
tserver.cache.config.redis.summary.off-heap.hostname=127.0.0.1
tserver.cache.config.redis.summary.off-heap.port=6379
tserver.cache.config.redis.summary.off-heap.password=<password>
```
