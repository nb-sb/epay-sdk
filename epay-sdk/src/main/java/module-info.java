/** 公开包只有 api、model、spi 和三个协议包。internal 只供本模块使用。 */
module io.github.nb.sb.epay.sdk {
  exports com.nbsb.epaysdk.api;
  exports com.nbsb.epaysdk.model;
  exports com.nbsb.epaysdk.spi;
  exports com.nbsb.epaysdk.protocol.epayv1;
  exports com.nbsb.epaysdk.protocol.epayv2;
  exports com.nbsb.epaysdk.protocol.mzf;

  requires org.apache.httpcomponents.client5.httpclient5;
  requires org.apache.httpcomponents.core5.httpcore5;
}
