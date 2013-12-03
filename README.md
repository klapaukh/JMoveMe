#Java Move.Me binding

This is an api for using the Move.Me system with Java. Requires Java 1.6 and later. 

Some example use:

```java
  PSMoveClient client = new PSMoveClient();
  try {
    client.connect(hostname, port);
    client.delayChange(2);
  } catch (IOException e) {
    System.err.println("Connection to Sony Move.Me server failed");
  }

  client.registerListener(this);
  client.setMoveLostListener(this);
```
