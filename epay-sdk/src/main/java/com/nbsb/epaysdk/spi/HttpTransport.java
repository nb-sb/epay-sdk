package com.nbsb.epaysdk.spi;

/** HTTP 扩展边界；不得自行重试写请求、记录敏感报文或跟随重定向。 */
@FunctionalInterface
public interface HttpTransport extends AutoCloseable {
  TransportResponse execute(TransportRequest request);

  /** 外部传输默认仍由调用方持有，只有明确转移所有权时客户端才关闭它。 */
  @Override
  default void close() {}
}
