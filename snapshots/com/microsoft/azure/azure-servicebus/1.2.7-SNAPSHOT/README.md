## Proxy & WebSocket Support for Azure Service Bus Java SDK

#### To Use:

Add the repository to your pom.xml:

```
<repositories>
    <repository>
      <id>Preview version of Azure Service Bus Java SDK with proxy and WebSocket support.</id>
      <url>http://raw.github.com/lee0c/azure-service-bus-java/proxy-support-preview/snapshots/</url>
    </repository>
  </repositories>
```

And add the dependency to the dependencies section of your pom.sml:

```
<dependency>
    <groupId>com.microsoft.azure</groupId>
    <artifactId>azure-servicebus</artifactId>
    <version>1.2.7-SNAPSHOT</version>
  </dependency>
```

Then set up proxy settings via the ClientSettings object:

```
clientSettings.setProxyHostName(...
clientSettings.setProxyHostPort(...
clientSettings.setProxyUserName(...
clientSettings.setProxyPassword(...
```

Additionally, set the transport type to Amqp, either through client settings or the connection string.