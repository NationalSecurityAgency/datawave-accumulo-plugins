package accumulo.ohc;

import org.junit.AfterClass;
import org.junit.BeforeClass;

import redis.embedded.RedisServer;

public class RedisServerTest {

  protected static final String PASSWORD = "supersecret";

  protected static RedisServer server;

  @BeforeClass
  public static void beforeAll() throws Exception {
    server = RedisServer.builder()
        .setting("maxmemory 10mb")
        .setting("maxmemory-policy allkeys-lru")
        .setting("requirepass " + PASSWORD)
        .build();
    server.start();
  }

  @AfterClass
  public static void afterAll() throws Exception {
    server.stop();
  }

}
